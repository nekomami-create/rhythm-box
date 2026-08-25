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

    /** [semitones] 半音だけ動かしたコード。種類は変わらない。 */
    fun transposed(semitones: Int): Chord = copy(root = (root + semitones).mod(12))

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
 *
 * リードだけは音程が要るので、ステップごとの MIDI ノート番号を [leads] に持つ（[REST] は休符）。
 * [leads] は「繰り返し何回目か」ごとに 1 小節ぶんずつ入っている。
 * ドラムは 4 小節同じでも構わないが、旋律は下のコードが変わるたびに変えないと
 * 曲として成立しないため、リードだけ小節ごとに持てるようにしてある。
 */
@Serializable
data class Pattern(
    val name: String,
    val rows: List<Int> = List(STEP_ROW_COUNT) { 0 },
    /** 旧形式（1 小節ぶんだけの旋律）。以前の保存データを読むためだけに残している。 */
    val lead: List<Int> = emptyList(),
    /** 繰り返しごとの旋律。1 つ目が 1 回目の小節、2 つ目が 2 回目…。 */
    val leads: List<List<Int>> = listOf(emptyLead()),
    /** 強く鳴らすステップ（[rows] と同じ形のビット）。空なら強弱なし。 */
    val accents: List<Int> = emptyList(),
    /** 弱く鳴らすステップ（[rows] と同じ形のビット）。 */
    val ghosts: List<Int> = emptyList(),
) {
    /** 実際に使う旋律の一覧。旧形式しか無いときはそれを 1 小節ぶんとして扱う。 */
    val leadBars: List<List<Int>>
        get() = when {
            // 新しい形が空のままで旧形式に中身があるなら、そちらを使う（読み込み互換）。
            lead.isNotEmpty() && leads.all { line -> line.all { it == REST } } -> listOf(lead)
            leads.isEmpty() -> listOf(emptyLead())
            else -> leads
        }

    /** 旋律を何小節ぶん持っているか。 */
    val leadBarCount: Int get() = leadBars.size

    fun isOn(row: Int, step: Int): Boolean = (rowAt(row) shr step) and 1 == 1

    fun rowAt(row: Int): Int = rows.getOrElse(row) { 0 } and STEP_MASK

    fun toggle(row: Int, step: Int): Pattern = withRow(row, rowAt(row) xor (1 shl step))

    fun set(row: Int, step: Int, on: Boolean): Pattern {
        val bit = 1 shl step
        return withRow(row, if (on) rowAt(row) or bit else rowAt(row) and bit.inv())
    }

    fun clearRow(row: Int): Pattern = withRow(row, 0)

    /** [step] の強さ。アクセントを付けていなければ [Level.NORMAL]。 */
    fun levelAt(row: Int, step: Int): Level = when {
        !isOn(row, step) -> Level.NORMAL
        (maskAt(accents, row) shr step) and 1 == 1 -> Level.ACCENT
        (maskAt(ghosts, row) shr step) and 1 == 1 -> Level.GHOST
        else -> Level.NORMAL
    }

    /** [step] の強さを 普通 → 強 → 弱 → 普通 と巡回させる。鳴っていないステップは変えない。 */
    fun cycleLevel(row: Int, step: Int): Pattern {
        if (!isOn(row, step)) return this
        return withLevel(row, step, next(levelAt(row, step)))
    }

    fun withLevel(row: Int, step: Int, level: Level): Pattern {
        val bit = 1 shl step
        val accent = maskAt(accents, row).let { if (level == Level.ACCENT) it or bit else it and bit.inv() }
        val ghost = maskAt(ghosts, row).let { if (level == Level.GHOST) it or bit else it and bit.inv() }
        return copy(
            accents = compact(List(STEP_ROW_COUNT) { if (it == row) accent else maskAt(accents, it) }),
            ghosts = compact(List(STEP_ROW_COUNT) { if (it == row) ghost else maskAt(ghosts, it) }),
        )
    }

    /** 行ごとの強さをまとめて置く（生成用）。 */
    fun withLevels(row: Int, accent: Int, ghost: Int): Pattern = copy(
        accents = compact(List(STEP_ROW_COUNT) { if (it == row) accent and STEP_MASK else maskAt(accents, it) }),
        ghosts = compact(List(STEP_ROW_COUNT) { if (it == row) ghost and STEP_MASK else maskAt(ghosts, it) }),
    )

    /**
     * [other] のリズム（打ち込みと強弱）を丸ごと受け取る。旋律はこちらのものを残す。
     *
     * 自動生成の結果を書き戻すときに使う。copy(rows = ...) と個別に写していると、
     * 強弱のような項目を足したときに写し忘れて静かに落ちるので、ここに集約する。
     */
    fun withRhythmOf(other: Pattern): Pattern = copy(
        rows = List(STEP_ROW_COUNT) { other.rowAt(it) },
        accents = compact(List(STEP_ROW_COUNT) { other.maskAt(other.accents, it) }),
        ghosts = compact(List(STEP_ROW_COUNT) { other.maskAt(other.ghosts, it) }),
    )

    /** その行の強さをすべて普通に戻す。 */
    fun clearLevels(row: Int): Pattern = withLevels(row, 0, 0)

    internal fun maskAt(masks: List<Int>, row: Int): Int = masks.getOrElse(row) { 0 } and STEP_MASK

    /**
     * 強弱がひとつも無ければ空のままにする。
     * 0 が並んだだけの行を持たせると、強弱を使っていない曲の保存内容が
     * 意味もなく変わってしまう。
     */
    internal fun compact(masks: List<Int>): List<Int> =
        if (masks.all { it and STEP_MASK == 0 }) emptyList() else masks

    /** [step] の次にこの行が鳴るステップ。小節内に無ければ [STEPS_PER_BAR]（＝小節末）。 */
    fun nextHit(row: Int, step: Int): Int {
        val bits = rowAt(row)
        for (next in (step + 1) until STEPS_PER_BAR) {
            if ((bits shr next) and 1 == 1) return next
        }
        return STEPS_PER_BAR
    }

    /** [bar] 回目の小節の [step] にある音。範囲外の [bar] は折り返す。 */
    fun leadAt(bar: Int, step: Int): Int =
        leadBars[bar.mod(leadBarCount)].getOrElse(step) { REST }

    fun withLead(bar: Int, step: Int, midi: Int): Pattern {
        val bars = leadBars
        val index = bar.mod(bars.size)
        // 音を消すときは、その音に続いていたタイも一緒に消す（行き場のない印を残さない）。
        val tail = if (midi == REST) step + tieRun(bar, step) else step
        val updated = bars.mapIndexed { line, notes ->
            if (line == index) {
                List(STEPS_PER_BAR) {
                    when {
                        it == step -> midi
                        it in (step + 1)..tail -> REST
                        else -> notes.getOrElse(it) { REST }
                    }
                }
            } else {
                notes
            }
        }
        return withLeads(updated)
    }

    /**
     * [step] の音を [until] まで伸ばす（間をタイで埋める）。
     * すでにちょうどそこまで伸びていれば元の長さに戻す。
     */
    fun withLeadTie(bar: Int, step: Int, until: Int): Pattern {
        if (!isNote(leadAt(bar, step)) || until <= step) return this
        val end = step + tieRun(bar, step)
        // すでにちょうどそこまで伸びているなら、伸ばす前の長さに戻す。
        val shrinkToNote = end == until
        val bars = leadBars
        val index = bar.mod(bars.size)
        val updated = bars.mapIndexed { line, notes ->
            if (line == index) {
                List(STEPS_PER_BAR) {
                    when {
                        it in (step + 1)..until -> if (shrinkToNote) REST else TIE
                        it in (until + 1)..end -> REST
                        else -> notes.getOrElse(it) { REST }
                    }
                }
            } else {
                notes
            }
        }
        return withLeads(updated)
    }

    /** [bar] 回目の小節で、[step] の次に新しい音が鳴るステップ。無ければ [STEPS_PER_BAR]。 */
    fun nextLead(bar: Int, step: Int): Int {
        for (next in (step + 1) until STEPS_PER_BAR) {
            if (isNote(leadAt(bar, next))) return next
        }
        return STEPS_PER_BAR
    }

    /** [step] の音に続くタイの数（小節内だけを見る）。 */
    fun tieRun(bar: Int, step: Int): Int {
        var count = 0
        for (next in (step + 1) until STEPS_PER_BAR) {
            if (leadAt(bar, next) != TIE) break
            count++
        }
        return count
    }

    /**
     * [step] で鳴っている音の始まりのステップ。タイの途中なら遡る。
     * この小節より前から続いている（先頭がタイ）ときは -1。
     */
    fun leadHead(bar: Int, step: Int): Int {
        var cursor = step
        while (cursor >= 0) {
            val value = leadAt(bar, cursor)
            if (isNote(value)) return cursor
            if (value != TIE) return -1
            cursor--
        }
        return -1
    }

    /**
     * [step] を長押ししたときに、長さを変える対象になる音の位置。無ければ -1。
     *
     * 長押ししたところが音そのものならそれ自身、そうでなければ「それより前にある
     * いちばん近い音」。間に休符が挟まっていても遡る（休符ぶんを埋めて伸ばすため）。
     */
    fun stretchTarget(bar: Int, step: Int): Int {
        if (isNote(leadAt(bar, step))) return step
        for (cursor in (step - 1) downTo 0) {
            if (isNote(leadAt(bar, cursor))) return cursor
        }
        return -1
    }

    /** [step] で実際に鳴っている音の高さ。鳴っていなければ [REST]。 */
    fun soundingLead(bar: Int, step: Int): Int {
        val head = leadHead(bar, step)
        if (head >= 0) return leadAt(bar, head)
        // ここまでが全部タイでなければ、単に鳴っていない。
        if ((0..step).any { leadAt(bar, it) != TIE }) return REST
        // 小節の頭から続いている。前の小節の終わりの音を引き継ぐ。
        var cursor = bar - 1
        repeat(leadBarCount) {
            val previous = leadHead(cursor, STEPS_PER_BAR - 1)
            if (previous >= 0) return leadAt(cursor, previous)
            if (leadAt(cursor, 0) != TIE) return REST
            cursor--
        }
        return REST
    }

    /** 旋律を何小節ぶん持つかを変える。増やしたぶんは空。 */
    fun withLeadBarCount(count: Int): Pattern {
        val target = count.coerceIn(1, MAX_LEAD_BARS)
        val bars = leadBars
        return withLeads(List(target) { bars.getOrElse(it) { emptyLead() } })
    }

    fun withLeads(bars: List<List<Int>>): Pattern = copy(
        lead = emptyList(),
        leads = bars.ifEmpty { listOf(emptyLead()) }
            .take(MAX_LEAD_BARS)
            .map { notes -> List(STEPS_PER_BAR) { notes.getOrElse(it) { REST } } },
    )

    /** [bar] 回目の小節の音だけ消す。 */
    fun clearLead(bar: Int): Pattern =
        withLeads(leadBars.mapIndexed { line, notes -> if (line == bar.mod(leadBarCount)) emptyLead() else notes })

    /** 旋律をすべて消して 1 小節ぶんに戻す。 */
    fun clearAllLeads(): Pattern = withLeads(listOf(emptyLead()))

    fun cleared(): Pattern = copy(
        rows = List(STEP_ROW_COUNT) { 0 },
        accents = emptyList(),
        ghosts = emptyList(),
        lead = emptyList(),
        leads = listOf(emptyLead()),
    )

    fun isEmpty(): Boolean =
        rows.all { it and STEP_MASK == 0 } && leadBars.all { line -> line.all { it == REST } }

    fun leadNoteCount(): Int = leadBars.sumOf { line -> line.count { isNote(it) } }

    fun hitCount(): Int = rows.sumOf { Integer.bitCount(it and STEP_MASK) } + leadNoteCount()

    /** 古い保存データ（行数が足りない・旧形式の旋律）を今の形に揃える。 */
    fun normalized(): Pattern = copy(
        rows = List(STEP_ROW_COUNT) { rowAt(it) },
        // 鳴らないステップに強弱だけ残っていても意味がないので落とす。
        accents = compact(List(STEP_ROW_COUNT) { maskAt(accents, it) and rowAt(it) }),
        ghosts = compact(List(STEP_ROW_COUNT) { maskAt(ghosts, it) and rowAt(it) }),
        lead = emptyList(),
        leads = leadBars.map { notes -> List(STEPS_PER_BAR) { notes.getOrElse(it) { REST } } },
    )

    private fun withRow(row: Int, value: Int): Pattern {
        val bits = value and STEP_MASK
        // 消したステップの強弱も落とす。置き直したときに前の強さが残っていると驚く。
        return copy(
            rows = List(STEP_ROW_COUNT) { if (it == row) bits else rowAt(it) },
            accents = compact(List(STEP_ROW_COUNT) { maskAt(accents, it).let { m -> if (it == row) m and bits else m } }),
            ghosts = compact(List(STEP_ROW_COUNT) { maskAt(ghosts, it).let { m -> if (it == row) m and bits else m } }),
        )
    }

    /**
     * ステップの強さ。
     *
     * 実機のリズムマシンにアクセントが付いているのと同じ理由で入れている。
     * すべて同じ音量だと、打ち込みがどうしても機械的に聞こえる。
     */
    enum class Level(val gain: Float) {
        /** 幽霊音。裏拍の細かい刻みなど、鳴っているのが分かればいい音。 */
        GHOST(0.4f),

        /** 既定。アクセントを付けていない曲はすべてこれで、今までと同じ音になる。 */
        NORMAL(1.0f),

        /** アクセント。 */
        ACCENT(1.5f),
    }

    companion object {
        /** 有効なステップだけを残すマスク。 */
        const val STEP_MASK = (1 shl STEPS_PER_BAR) - 1

        /** リードの休符。 */
        const val REST = -1

        /**
         * 直前の音を伸ばす印（タイ）。このステップでは発音せず、前の音がそのまま続く。
         * 小節をまたいでも続くので、2 小節にわたる長音も書ける。
         */
        const val TIE = -2

        /** 実際に発音する音（休符でもタイでもない）か。 */
        fun isNote(value: Int): Boolean = value >= 0

        /** 1 つのパターンが持てる旋律の小節数の上限。 */
        const val MAX_LEAD_BARS = 8

        /** 巡回の順番。押すたびに 普通 → 強 → 弱 と変わって元に戻る。 */
        fun next(level: Level): Level = when (level) {
            Level.NORMAL -> Level.ACCENT
            Level.ACCENT -> Level.GHOST
            Level.GHOST -> Level.NORMAL
        }

        /** 音の入っていない 1 小節ぶんの旋律。 */
        fun emptyLead(): List<Int> = List(STEPS_PER_BAR) { REST }

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
    /**
     * 音の伸び（サステイン）。コード / ベース / リードだけで効く。
     * 0.5 が既定で、古い保存データもこの値として読める。
     */
    val hold: Float = ToneSynth.DEFAULT_HOLD,
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
    /**
     * ハネ具合。0 でまっすぐ（既定）、上げるほど裏の 16 分が後ろにずれる。
     * 0.67 あたりが三連のシャッフル。
     */
    val swing: Float = 0f,
    /** コード行の弾き方。 */
    val chordStyle: ChordStyle = ChordStyle.BLOCK,
    /** リードの音色。 */
    val leadVoice: ToneSynth.LeadVoice = ToneSynth.LeadVoice.SQUARE,
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

/**
 * コード行の弾き方。
 *
 * 和音をまとめて鳴らすだけだと伴奏が硬い。1 音ずつ散らすと、
 * 同じ打ち込みのままで弾いているように聞こえる。
 */
@Serializable
enum class ChordStyle(val label: String) {
    /** 和音をまとめて鳴らす（今までの音）。 */
    BLOCK("和音"),

    /** 低い音から高い音へ 1 つずつ。 */
    UP("上へ"),

    /** 高い音から低い音へ 1 つずつ。 */
    DOWN("下へ"),

    /** 上がって下りてを繰り返す。 */
    UP_DOWN("上下"),
    ;

    /**
     * [index] 回目に鳴らす音を [voicing] から選ぶ。BLOCK なら全部。
     *
     * 何回目かはステップから数える（状態を持たない）ので、
     * ループしても同じところで同じ音が鳴る。
     */
    fun notesAt(voicing: List<Int>, index: Int): List<Int> {
        if (this == BLOCK || voicing.isEmpty()) return voicing
        val size = voicing.size
        val position = when (this) {
            UP -> index.mod(size)
            DOWN -> size - 1 - index.mod(size)
            // 端を 2 回鳴らさないよう、折り返しは 2*(size-1) 周期にする。
            else -> {
                val period = if (size > 1) 2 * (size - 1) else 1
                val step = index.mod(period)
                if (step < size) step else period - step
            }
        }
        return listOf(voicing[position])
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
