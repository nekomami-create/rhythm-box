package com.example.rhythmbox.core

import kotlin.math.roundToInt

/**
 * 曲を標準 MIDI ファイル（SMF フォーマット 1）に書き出す。
 *
 * 音声（M4A）は「聴く」ためのもので、そこから先に手を入れられない。
 * MIDI で出せれば DAW に持っていって続きが作れる。
 *
 * Android の API を使わない純 Kotlin なので、JVM の単体テストで
 * 書き出した中身をそのまま検証できる。
 */
object MidiExporter {

    /** 4 分音符あたりのティック数。16 分 1 ステップがちょうど 120 になる。 */
    const val TICKS_PER_QUARTER = 480

    /** 1 ステップ（16 分音符）のティック数。 */
    const val TICKS_PER_STEP = TICKS_PER_QUARTER / 4

    /**
     * ドラムの音を GM のドラムマップに対応づける。
     * チャンネル 10（0 から数えて 9）で鳴らすと、どの音源でもそれらしい音が出る。
     */
    private val DRUM_NOTES = mapOf(
        Voice.KICK to 36,
        Voice.SNARE to 38,
        Voice.CLOSED_HAT to 42,
        Voice.OPEN_HAT to 46,
        Voice.CLAP to 39,
        Voice.RIM to 37,
        Voice.TOM to 45,
        Voice.COWBELL to 56,
    )

    private const val DRUM_CHANNEL = 9

    /** 音程のあるパートのチャンネルと、GM の音色番号。 */
    private val PROGRAMS = mapOf(
        Instrument.CHORD to (0 to 18), // ロックオルガン
        Instrument.BASS to (1 to 38), // シンセベース
        Instrument.LEAD to (2 to 80), // シンセリード
    )

    /** 強さを MIDI のベロシティに移す。 */
    private fun velocityOf(level: Pattern.Level): Int = when (level) {
        Pattern.Level.GHOST -> 54
        Pattern.Level.NORMAL -> 100
        Pattern.Level.ACCENT -> 127
    }

    /** 1 つの音。[start] と [length] はティック。 */
    private data class Note(val start: Int, val length: Int, val midi: Int, val velocity: Int)

    /**
     * [plan] の内容を SMF のバイト列にする。
     *
     * 鳴らし方は再生と同じ規則で決める。ハネはステップの開始位置に、
     * 強弱はベロシティに、コードの弾き方は鳴らす音の選び方に反映される。
     */
    fun export(song: Song, plan: PlaybackPlan): ByteArray {
        val starts = stepStarts(song, plan.barCount)
        val drums = mutableListOf<Note>()
        val parts = Instrument.entries.associateWith { mutableListOf<Note>() }

        for (bar in 0 until plan.barCount) {
            val pattern = plan.patternAt(bar)
            val chord = plan.chordAt(bar)
            val leadBar = plan.leadBarAt(bar)
            for (step in 0 until STEPS_PER_BAR) {
                val at = bar * STEPS_PER_BAR + step
                val start = starts[at]
                val level = pattern.levelAt(0, step)

                for (voice in Voice.entries) {
                    if (!pattern.isOn(voice.ordinal, step)) continue
                    drums += Note(
                        start = start,
                        // 打楽器は長さを持たないので、短く置いて切る。
                        length = TICKS_PER_STEP / 2,
                        midi = DRUM_NOTES.getValue(voice),
                        velocity = velocityOf(pattern.levelAt(voice.ordinal, step)),
                    )
                }

                if (pattern.isOn(ROW_CHORD, step)) {
                    val length = spanTicks(starts, at, pattern.nextHit(ROW_CHORD, step) - step, bar, plan.barCount)
                    val velocity = velocityOf(pattern.levelAt(ROW_CHORD, step))
                    val index = chordHitIndex(pattern, step)
                    for (midi in song.chordStyle.notesAt(chord.voicing(), index)) {
                        parts.getValue(Instrument.CHORD) += Note(start, length, midi, velocity)
                    }
                }

                if (pattern.isOn(ROW_BASS, step)) {
                    val steps = minOf(pattern.nextHit(ROW_BASS, step) - step, BASS_MAX_STEPS)
                    parts.getValue(Instrument.BASS) += Note(
                        start = start,
                        length = spanTicks(starts, at, steps, bar, plan.barCount),
                        midi = chord.bassMidi(),
                        velocity = velocityOf(pattern.levelAt(ROW_BASS, step)),
                    )
                }

                val lead = pattern.leadAt(leadBar, step)
                if (Pattern.isNote(lead)) {
                    parts.getValue(Instrument.LEAD) += Note(
                        start = start,
                        length = leadTicks(starts, plan, bar, step, at),
                        midi = lead,
                        velocity = velocityOf(level).coerceAtLeast(100),
                    )
                }
            }
        }

        val tracks = mutableListOf<ByteArray>()
        tracks += tempoTrack(song)
        for ((instrument, notes) in parts) {
            if (notes.isEmpty()) continue
            val (channel, program) = PROGRAMS.getValue(instrument)
            tracks += noteTrack(instrument.label, channel, program, notes)
        }
        if (drums.isNotEmpty()) tracks += noteTrack("ドラム", DRUM_CHANNEL, null, drums)
        return file(tracks)
    }

    /** リードの音の長さ。タイで伸びていればそのぶんまで。 */
    private fun leadTicks(starts: IntArray, plan: PlaybackPlan, bar: Int, step: Int, at: Int): Int {
        var held = 1
        var cursorBar = bar
        var cursorStep = step
        while (held < STEPS_PER_BAR * 4) {
            cursorStep++
            if (cursorStep >= STEPS_PER_BAR) {
                cursorStep = 0
                cursorBar++
                if (cursorBar >= plan.barCount) break
            }
            if (plan.patternAt(cursorBar).leadAt(plan.leadBarAt(cursorBar), cursorStep) != Pattern.TIE) break
            held++
        }
        if (held > 1) return spanTicks(starts, at, held, bar, plan.barCount)
        val next = plan.patternAt(bar).nextLead(plan.leadBarAt(bar), step)
        return spanTicks(starts, at, minOf(next - step, LEAD_MAX_STEPS), bar, plan.barCount)
    }

    /** [at] から [steps] ステップぶんのティック数。小節をまたいでも正しく測る。 */
    private fun spanTicks(starts: IntArray, at: Int, steps: Int, bar: Int, barCount: Int): Int {
        val end = (at + steps.coerceAtLeast(1)).coerceAtMost(barCount * STEPS_PER_BAR)
        val endTick = if (end < starts.size) starts[end] else starts.last() + TICKS_PER_STEP
        return (endTick - starts[at]).coerceAtLeast(1)
    }

    /** そのステップが、その小節の何回目のコードか。 */
    private fun chordHitIndex(pattern: Pattern, step: Int): Int {
        val bits = pattern.rowAt(ROW_CHORD)
        var count = 0
        for (earlier in 0 until step) {
            if ((bits shr earlier) and 1 == 1) count++
        }
        return count
    }

    /**
     * 各ステップの開始ティック。
     * ハネているときは、表を長く・裏を同じだけ短くする（再生と同じ扱い）。
     */
    private fun stepStarts(song: Song, barCount: Int): IntArray {
        val total = barCount * STEPS_PER_BAR
        val starts = IntArray(total + 1)
        val shift = song.swing.coerceIn(0f, 1f) * 0.5
        var tick = 0.0
        for (index in 0 until total) {
            starts[index] = tick.roundToInt()
            val step = index % STEPS_PER_BAR
            tick += TICKS_PER_STEP * if (step % 2 == 0) 1.0 + shift else 1.0 - shift
        }
        starts[total] = tick.roundToInt()
        return starts
    }

    // --- SMF の組み立て -----------------------------------------------------

    private fun tempoTrack(song: Song): ByteArray {
        val events = mutableListOf<Byte>()
        // テンポ（1 拍あたりのマイクロ秒）
        val microsPerBeat = (60_000_000.0 / song.bpm).roundToInt()
        events += 0 // デルタタイム
        events += byteArrayOf(0xFF.toByte(), 0x51, 0x03).toList()
        events += ((microsPerBeat shr 16) and 0xFF).toByte()
        events += ((microsPerBeat shr 8) and 0xFF).toByte()
        events += (microsPerBeat and 0xFF).toByte()
        // 拍子 4/4
        events += 0
        events += byteArrayOf(0xFF.toByte(), 0x58, 0x04, 0x04, 0x02, 0x18, 0x08).toList()
        events += trackName(song.name)
        events += endOfTrack()
        return events.toByteArray()
    }

    private fun noteTrack(name: String, channel: Int, program: Int?, notes: List<Note>): ByteArray {
        val events = mutableListOf<Byte>()
        events += trackName(name)
        if (program != null) {
            events += 0
            events += (0xC0 or channel).toByte()
            events += program.toByte()
        }
        // 「鳴らす」「止める」を時刻順に並べ替えてから、差分の時刻で書く。
        data class Event(val tick: Int, val on: Boolean, val midi: Int, val velocity: Int)
        val ordered = notes
            .flatMap {
                listOf(
                    Event(it.start, true, it.midi, it.velocity),
                    Event(it.start + it.length, false, it.midi, 0),
                )
            }
            // 同じ時刻なら「止める」を先に書く。同じ音が重なったときに切れ残らない。
            .sortedWith(compareBy({ it.tick }, { it.on }))

        var last = 0
        for (event in ordered) {
            events += variableLength(event.tick - last)
            events += ((if (event.on) 0x90 else 0x80) or channel).toByte()
            events += event.midi.coerceIn(0, 127).toByte()
            events += event.velocity.coerceIn(0, 127).toByte()
            last = event.tick
        }
        events += endOfTrack()
        return events.toByteArray()
    }

    private fun trackName(name: String): List<Byte> {
        val bytes = name.toByteArray(Charsets.UTF_8).take(127)
        return listOf<Byte>(0, 0xFF.toByte(), 0x03) + variableLength(bytes.size) + bytes
    }

    private fun endOfTrack(): List<Byte> = listOf(0, 0xFF.toByte(), 0x2F, 0x00)

    /** SMF の可変長数値。7 ビットずつ、続きがあれば最上位ビットを立てる。 */
    private fun variableLength(value: Int): List<Byte> {
        val safe = value.coerceAtLeast(0)
        val out = ArrayDeque<Byte>()
        out.addFirst((safe and 0x7F).toByte())
        var rest = safe shr 7
        while (rest > 0) {
            out.addFirst(((rest and 0x7F) or 0x80).toByte())
            rest = rest shr 7
        }
        return out.toList()
    }

    private fun file(tracks: List<ByteArray>): ByteArray {
        val out = mutableListOf<Byte>()
        out += "MThd".toByteArray().toList()
        out += intBytes(6)
        out += shortBytes(1) // フォーマット 1（パートごとにトラックを分ける）
        out += shortBytes(tracks.size)
        out += shortBytes(TICKS_PER_QUARTER)
        for (track in tracks) {
            out += "MTrk".toByteArray().toList()
            out += intBytes(track.size)
            out += track.toList()
        }
        return out.toByteArray()
    }

    private fun intBytes(value: Int): List<Byte> = listOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun shortBytes(value: Int): List<Byte> =
        listOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())

    private const val BASS_MAX_STEPS = 4
    private const val LEAD_MAX_STEPS = 4
}
