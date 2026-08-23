package com.example.rhythmbox.core

import kotlinx.serialization.Serializable

/** 1 小節あたりのステップ数（16 分音符 x 16）。 */
const val STEPS_PER_BAR = 16

/** 音色（トラック）の数。 */
val VOICE_COUNT = Voice.entries.size

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

/**
 * 16 ステップ x 8 音色のパターン。
 * 各音色の ON/OFF は [rows] の 1 要素（Int）のビットで持つ（bit n = ステップ n）。
 */
@Serializable
data class Pattern(
    val name: String,
    val rows: List<Int> = List(VOICE_COUNT) { 0 },
) {
    init {
        require(rows.size == VOICE_COUNT) { "rows must have $VOICE_COUNT entries" }
    }

    fun isOn(voice: Int, step: Int): Boolean = (rows[voice] shr step) and 1 == 1

    fun toggle(voice: Int, step: Int): Pattern = withRow(voice, rows[voice] xor (1 shl step))

    fun set(voice: Int, step: Int, on: Boolean): Pattern {
        val bit = 1 shl step
        return withRow(voice, if (on) rows[voice] or bit else rows[voice] and bit.inv())
    }

    fun clearVoice(voice: Int): Pattern = withRow(voice, 0)

    fun cleared(): Pattern = copy(rows = List(VOICE_COUNT) { 0 })

    fun isEmpty(): Boolean = rows.all { it == 0 }

    fun hitCount(): Int = rows.sumOf { Integer.bitCount(it and STEP_MASK) }

    private fun withRow(voice: Int, value: Int): Pattern =
        copy(rows = rows.toMutableList().also { it[voice] = value and STEP_MASK })

    companion object {
        /** 有効なステップだけを残すマスク。 */
        const val STEP_MASK = (1 shl STEPS_PER_BAR) - 1

        fun empty(name: String) = Pattern(name)

        /** "x..x..x." のような文字列からパターンを組み立てる（テストやプリセット用）。 */
        fun of(name: String, vararg rowSpecs: String): Pattern {
            require(rowSpecs.size <= VOICE_COUNT) { "too many rows" }
            val rows = MutableList(VOICE_COUNT) { 0 }
            rowSpecs.forEachIndexed { voice, spec ->
                var bits = 0
                spec.take(STEPS_PER_BAR).forEachIndexed { step, c ->
                    if (c != '.' && c != ' ' && c != '-') bits = bits or (1 shl step)
                }
                rows[voice] = bits
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
)

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
    val arrangement: List<ArrangementStep> = emptyList(),
    val tracks: List<TrackSetting> = List(VOICE_COUNT) { TrackSetting() },
    val updatedAt: Long = 0L,
) {
    fun pattern(index: Int): Pattern = patterns[index.coerceIn(patterns.indices)]

    fun withPattern(index: Int, pattern: Pattern): Song =
        copy(patterns = patterns.toMutableList().also { it[index] = pattern })

    fun withTrack(index: Int, setting: TrackSetting): Song =
        copy(tracks = tracks.toMutableList().also { it[index] = setting })

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
                )
                else -> Pattern.empty(name)
            }
        }

        fun newSong(id: String, name: String, now: Long = 0L) = Song(
            id = id,
            name = name,
            arrangement = listOf(ArrangementStep(0, 4), ArrangementStep(1, 4)),
            updatedAt = now,
        )
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
