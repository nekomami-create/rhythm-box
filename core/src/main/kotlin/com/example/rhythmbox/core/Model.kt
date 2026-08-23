package com.example.rhythmbox.core

import kotlinx.serialization.Serializable

/** 1 小節あたりのステップ数（16 分音符 x 16）。 */
const val STEPS_PER_BAR = 16

/** ドラム音色の数。 */
val DRUM_COUNT = Voice.entries.size

/** グリッドに並ぶ行数（ドラム + コード + ベース）。リードはピアノロールで別扱い。 */
val STEP_ROW_COUNT = DRUM_COUNT + 2

/** ステップ行としてのコード / ベースの位置。 */
val ROW_CHORD = DRUM_COUNT
val ROW_BASS = DRUM_COUNT + 1

/** ミキサーのチャンネル数（ドラム 8 + コード / ベース / リード）。 */
val TRACK_COUNT = DRUM_COUNT + Instrument.entries.size

/** リズムボックスの音色。並び順がそのままトラックの並び順になる。 */
enum class Voice(val label: String, val shortLabel: String) {
    KICK("キック", "BD"),
    SNARE("スネア", "SD"),
    CLOSED_HAT("クローズドハット", "CH"),
    OPEN_HAT("オープンハット", "OH"),
    CLAP("クラップ", "CP"),
    RIM("リムショット", "RS"),
    TOM("タム", "TM"),
    COWBELL("カウベル", "CB"),
}

/** 音程を持つ楽器。ミキサー上ではドラム 8 音色の後ろに並ぶ。 */
enum class Instrument(val label: String, val shortLabel: String) {
    CHORD("コード", "CHD"),
    BASS("ベース", "BAS"),
    LEAD("リード", "LED");

    /** ミキサー / トラック設定での位置。 */
    val trackIndex: Int get() = DRUM_COUNT + ordinal
}

/** コードの種類。[intervals] はルートからの半音差。 */
enum class ChordQuality(val suffix: String, val intervals: List<Int>) {
    MAJOR("", listOf(0, 4, 7)),
    MINOR("m", listOf(0, 3, 7)),
    SEVENTH("7", listOf(0, 4, 7, 10)),
    MINOR_SEVENTH("m7", listOf(0, 3, 7, 10)),
    MAJOR_SEVENTH("M7", listOf(0, 4, 7, 11)),
    SUS4("sus4", listOf(0, 5, 7)),
    DIMINISHED("dim", listOf(0, 3, 6)),
    AUGMENTED("aug", listOf(0, 4, 8)),
}

/** 和音。[root] は C=0 の半音番号。 */
@Serializable
data class Chord(
    val root: Int = 0,
    val quality: ChordQuality = ChordQuality.MAJOR,
) {
    val name: String get() = ROOT_NAMES[root.mod(12)] + quality.suffix

    /**
     * 実際に鳴らす構成音（MIDI ノート番号）。
     * ルートが高いコードは 1 オクターブ下げて、コードが変わっても
     * 音の高さが飛び跳ねないようにしている（クローズドボイシング風）。
     */
    fun voicing(): List<Int> {
        val r = root.mod(12)
        val base = if (r >= 6) CHORD_BASE_MIDI - 12 + r else CHORD_BASE_MIDI + r
        return quality.intervals.map { base + it }
    }

    /** ベースが弾くルート音（MIDI ノート番号）。 */
    fun bassMidi(): Int = BASS_BASE_MIDI + root.mod(12)

    companion object {
        val ROOT_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /** コードの基準（C4）。 */
        const val CHORD_BASE_MIDI = 60

        /** ベースの基準（C2）。 */
        const val BASS_BASE_MIDI = 36

        fun of(name: String): Chord? {
            val rootLength = if (name.length >= 2 && name[1] == '#') 2 else 1
            val root = ROOT_NAMES.indexOf(name.take(rootLength))
            if (root < 0) return null
            val suffix = name.drop(rootLength)
            val quality = ChordQuality.entries.firstOrNull { it.suffix == suffix } ?: return null
            return Chord(root, quality)
        }
    }
}

/**
 * 1 小節ぶんのパターン。
 * ドラム・コード・ベースの ON/OFF は [rows] の 1 要素（Int）のビットで持つ（bit n = ステップ n）。
 * リードだけは音程が要るので [lead] にステップごとの MIDI ノート番号を持つ（[REST] は休符）。
 */
@Serializable
data class Pattern(
    val name: String,
    val rows: List<Int> = List(STEP_ROW_COUNT) { 0 },
    val lead: List<Int> = List(STEPS_PER_BAR) { REST },
) {
    fun isOn(row: Int, step: Int): Boolean = (rowAt(row) shr step) and 1 == 1

    fun rowAt(row: Int): Int = rows.getOrElse(row) { 0 } and STEP_MASK

    fun toggle(row: Int, step: Int): Pattern = withRow(row, rowAt(row) xor (1 shl step))

    fun set(row: Int, step: Int, on: Boolean): Pattern {
        val bit = 1 shl step
        return withRow(row, if (on) rowAt(row) or bit else rowAt(row) and bit.inv())
    }

    fun clearRow(row: Int): Pattern = withRow(row, 0)

    /** [step] の次にこの行が鳴るステップ。小節内に無ければ [STEPS_PER_BAR]（＝小節末）。 */
    fun nextHit(row: Int, step: Int): Int {
        val bits = rowAt(row)
        for (next in (step + 1) until STEPS_PER_BAR) {
            if ((bits shr next) and 1 == 1) return next
        }
        return STEPS_PER_BAR
    }

    fun leadAt(step: Int): Int = lead.getOrElse(step) { REST }

    fun withLead(step: Int, midi: Int): Pattern =
        copy(lead = normalizedLead().toMutableList().also { it[step] = midi })

    /** [step] の次にリードが鳴るステップ。無ければ [STEPS_PER_BAR]。 */
    fun nextLead(step: Int): Int {
        for (next in (step + 1) until STEPS_PER_BAR) {
            if (leadAt(next) != REST) return next
        }
        return STEPS_PER_BAR
    }

    fun clearLead(): Pattern = copy(lead = List(STEPS_PER_BAR) { REST })

    fun cleared(): Pattern = copy(rows = List(STEP_ROW_COUNT) { 0 }, lead = List(STEPS_PER_BAR) { REST })

    fun isEmpty(): Boolean = rows.all { it and STEP_MASK == 0 } && lead.all { it == REST }

    fun hitCount(): Int =
        rows.sumOf { Integer.bitCount(it and STEP_MASK) } + lead.count { it != REST }

    /** 古い保存データ（行数が足りないもの）を今の形に揃える。 */
    fun normalized(): Pattern = copy(
        rows = List(STEP_ROW_COUNT) { rowAt(it) },
        lead = normalizedLead(),
    )

    private fun normalizedLead(): List<Int> = List(STEPS_PER_BAR) { leadAt(it) }

    private fun withRow(row: Int, value: Int): Pattern =
        copy(rows = List(STEP_ROW_COUNT) { if (it == row) value and STEP_MASK else rowAt(it) })

    companion object {
        /** 有効なステップだけを残すマスク。 */
        const val STEP_MASK = (1 shl STEPS_PER_BAR) - 1

        /** リードの休符。 */
        const val REST = -1

        fun empty(name: String) = Pattern(name)

        /** "x..x..x." のような文字列からパターンを組み立てる（テストやプリセット用）。 */
        fun of(name: String, vararg rowSpecs: String): Pattern {
            require(rowSpecs.size <= STEP_ROW_COUNT) { "too many rows" }
            val rows = MutableList(STEP_ROW_COUNT) { 0 }
            rowSpecs.forEachIndexed { row, spec ->
                var bits = 0
                spec.take(STEPS_PER_BAR).forEachIndexed { step, c ->
                    if (c != '.' && c != ' ' && c != '-') bits = bits or (1 shl step)
                }
                rows[row] = bits
            }
            return Pattern(name, rows)
        }
    }
}

/** ソング（曲構成）の 1 ブロック: パターンを [repeat] 小節ぶん繰り返す。 */
@Serializable
data class ArrangementStep(
    val patternIndex: Int,
    val repeat: Int = 1,
    /** 小節ごとのコード。足りないぶんは最後のコードが続く。 */
    val chords: List<Chord> = emptyList(),
) {
    fun chordAt(barInBlock: Int, fallback: Chord): Chord =
        chords.getOrNull(barInBlock) ?: chords.lastOrNull() ?: fallback

    /** コードの数を [repeat] に合わせる（増えたぶんは直前のコードを引き継ぐ）。 */
    fun withChordSlots(fallback: Chord): ArrangementStep =
        copy(chords = List(repeat.coerceAtLeast(1)) { chordAt(it, fallback) })

    fun withChord(barInBlock: Int, chord: Chord, fallback: Chord): ArrangementStep {
        val slots = withChordSlots(fallback).chords.toMutableList()
        if (barInBlock !in slots.indices) return this
        slots[barInBlock] = chord
        return copy(chords = slots)
    }
}

/** トラックごとのミキサー設定。 */
@Serializable
data class TrackSetting(
    val volume: Float = 0.7f,
    val muted: Boolean = false,
)

/** 1 曲ぶんのデータ。パターン一式・曲構成・ミキサー設定をまとめて保存する。 */
@Serializable
data class Song(
    val id: String,
    val name: String,
    val bpm: Int = DEFAULT_BPM,
    val masterVolume: Float = 0.75f,
    val patterns: List<Pattern> = defaultPatterns(),
    /** パターン単体で試聴するときに鳴らすコード。曲構成に足すときの初期値にもなる。 */
    val patternChords: List<Chord> = defaultPatternChords(),
    val arrangement: List<ArrangementStep> = emptyList(),
    val tracks: List<TrackSetting> = List(TRACK_COUNT) { TrackSetting() },
    val updatedAt: Long = 0L,
) {
    fun pattern(index: Int): Pattern = patterns[index.coerceIn(patterns.indices)]

    fun patternChord(index: Int): Chord =
        patternChords.getOrNull(index.coerceIn(patterns.indices)) ?: Chord()

    fun track(index: Int): TrackSetting = tracks.getOrNull(index) ?: TrackSetting()

    fun withPattern(index: Int, pattern: Pattern): Song =
        copy(patterns = patterns.toMutableList().also { it[index] = pattern })

    fun withPatternChord(index: Int, chord: Chord): Song =
        copy(patternChords = List(patterns.size) { if (it == index) chord else patternChord(it) })

    fun withTrack(index: Int, setting: TrackSetting): Song =
        copy(tracks = List(TRACK_COUNT) { if (it == index) setting else track(it) })

    /** 曲構成の総小節数。 */
    fun totalBars(): Int = arrangement.sumOf { it.repeat.coerceAtLeast(0) }

    /** 曲構成を通しで再生したときの秒数。 */
    fun totalSeconds(): Double = totalBars() * STEPS_PER_BAR * secondsPerStep(bpm)

    companion object {
        const val DEFAULT_BPM = 110
        const val MIN_BPM = 40
        const val MAX_BPM = 240
        const val PATTERN_COUNT = 8

        /** A〜H の 8 パターン。最初の 2 つはお手本として埋めておく。 */
        fun defaultPatterns(): List<Pattern> = List(PATTERN_COUNT) { index ->
            val name = ('A' + index).toString()
            when (index) {
                0 -> Pattern.of(
                    name,
                    "x...x...x...x...", // BD
                    "....x.......x...", // SD
                    "x.x.x.x.x.x.x.x.", // CH
                    "................", // OH
                    "................", // CP
                    "................", // RS
                    "................", // TM
                    "................", // CB
                    "x.......x.......", // コード
                    "x...x..x..x.x...", // ベース
                )
                1 -> Pattern.of(
                    name,
                    "x.....x...x.....",
                    "....x.......x...",
                    "x.x.x.x.x.x.x...",
                    "..............x.",
                    "....x.......x...",
                    "................",
                    "............x.x.",
                    "................",
                    "x...x...x...x...",
                    "x.x...x.x.x...x.",
                )
                else -> Pattern.empty(name)
            }
        }

        /** パターンごとの試聴コード（C - Am - F - G を繰り返す）。 */
        fun defaultPatternChords(): List<Chord> {
            val cycle = listOf(
                Chord(0, ChordQuality.MAJOR),   // C
                Chord(9, ChordQuality.MINOR),   // Am
                Chord(5, ChordQuality.MAJOR),   // F
                Chord(7, ChordQuality.MAJOR),   // G
            )
            return List(PATTERN_COUNT) { cycle[it % cycle.size] }
        }

        fun newSong(id: String, name: String, now: Long = 0L): Song {
            val chords = defaultPatternChords()
            return Song(
                id = id,
                name = name,
                arrangement = listOf(
                    ArrangementStep(0, 4, listOf(chords[0], chords[1], chords[2], chords[3])),
                    ArrangementStep(1, 4, listOf(chords[0], chords[1], chords[2], chords[3])),
                ),
                updatedAt = now,
            )
        }
    }
}

/** 保存ファイルの中身（曲のライブラリ）。 */
@Serializable
data class SongLibrary(
    val songs: List<Song> = emptyList(),
    val currentId: String? = null,
) {
    fun current(): Song? = songs.firstOrNull { it.id == currentId } ?: songs.firstOrNull()

    fun replace(song: Song): SongLibrary {
        val index = songs.indexOfFirst { it.id == song.id }
        val next = if (index >= 0) songs.toMutableList().also { it[index] = song } else songs + song
        return copy(songs = next, currentId = song.id)
    }

    fun remove(id: String): SongLibrary {
        val next = songs.filterNot { it.id == id }
        return copy(songs = next, currentId = if (currentId == id) next.firstOrNull()?.id else currentId)
    }
}

/** 16 分音符 1 ステップぶんの秒数。 */
fun secondsPerStep(bpm: Int): Double = 60.0 / (bpm.coerceAtLeast(1) * 4.0)

/** 秒数を m:ss 表記にする。 */
fun formatDuration(seconds: Double): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** MIDI ノート番号を "C4" のような名前にする。 */
fun midiName(midi: Int): String = Chord.ROOT_NAMES[midi.mod(12)] + (midi / 12 - 1)
