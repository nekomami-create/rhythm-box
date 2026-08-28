package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiExporterTest {

    /** 書き出した中身を読み返すための、最小限の SMF 読み取り。 */
    private class Reader(private val bytes: ByteArray) {
        var at = 0
        fun u8(): Int = bytes[at++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Int = (u16() shl 16) or u16()
        fun text(length: Int): String = String(bytes, at, length, Charsets.UTF_8).also { at += length }
        fun variable(): Int {
            var value = 0
            while (true) {
                val b = u8()
                value = (value shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) return value
            }
        }
    }

    data class Note(val track: Int, val channel: Int, val midi: Int, val velocity: Int, val start: Int, val end: Int)

    /** 書き出したファイルから、鳴っている音を全部取り出す。 */
    private fun notesOf(bytes: ByteArray): List<Note> {
        val reader = Reader(bytes)
        assertEquals("MThd", reader.text(4))
        assertEquals(6, reader.u32())
        assertEquals(1, reader.u16()) // フォーマット 1
        val trackCount = reader.u16()
        assertEquals(MidiExporter.TICKS_PER_QUARTER, reader.u16())

        val notes = mutableListOf<Note>()
        repeat(trackCount) { track ->
            assertEquals("MTrk", reader.text(4))
            val length = reader.u32()
            val end = reader.at + length
            var tick = 0
            val open = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
            while (reader.at < end) {
                tick += reader.variable()
                val status = reader.u8()
                when {
                    status == 0xFF -> {
                        reader.u8()
                        // variable() が at を動かすので、長さを取ってから足す。
                        val skip = reader.variable()
                        reader.at += skip
                    }
                    status and 0xF0 == 0xC0 -> reader.u8()
                    status and 0xF0 == 0x90 || status and 0xF0 == 0x80 -> {
                        val channel = status and 0x0F
                        val midi = reader.u8()
                        val velocity = reader.u8()
                        val key = channel to midi
                        if (status and 0xF0 == 0x90 && velocity > 0) {
                            open[key] = tick to velocity
                        } else {
                            val started = open.remove(key)
                            if (started != null) {
                                notes += Note(track, channel, midi, started.second, started.first, tick)
                            }
                        }
                    }
                    else -> reader.at += 2
                }
            }
            reader.at = end
        }
        return notes
    }

    private fun songOf(vararg rows: String, bpm: Int = 120): Song =
        Song("s", "テスト曲", bpm = bpm).withPattern(0, Pattern.of("A", *rows))

    @Test
    fun `the file parses back and the drums are where they were punched in`() {
        val song = songOf("x...x...x...x...", "....x.......x...")
        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))

        val kicks = notes.filter { it.midi == 36 }.sortedBy { it.start }
        val snares = notes.filter { it.midi == 38 }.sortedBy { it.start }
        assertEquals(4, kicks.size)
        assertEquals(2, snares.size)
        // ドラムはチャンネル 10（0 から数えて 9）。
        assertTrue(notes.filter { it.midi == 36 }.all { it.channel == 9 })
        // 4 分ごと、つまり 1 ステップ 120 ティックの 4 つ分ずつ。
        kicks.forEachIndexed { index, note ->
            assertEquals(index * 4 * MidiExporter.TICKS_PER_STEP, note.start)
        }
        assertEquals(4 * MidiExporter.TICKS_PER_STEP, snares.first().start)
    }

    @Test
    fun `accents come out as louder notes`() {
        val pattern = Pattern.of("A", "x.x.x.x.........")
            .withLevel(0, 0, Pattern.Level.ACCENT)
            .withLevel(0, 2, Pattern.Level.GHOST)
        val song = Song("s", "test").withPattern(0, pattern)
        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.midi == 36 }
            .sortedBy { it.start }

        // 打ち込みは 0・2・4・6 ステップ。0 が強、2 が弱、残りは普通。
        assertEquals(4, notes.size)
        val velocities = notes.map { it.velocity }
        assertTrue("$velocities", notes[0].velocity > notes[2].velocity)
        assertTrue("$velocities", notes[1].velocity < notes[2].velocity)
        assertEquals(notes[2].velocity, notes[3].velocity)
    }

    @Test
    fun `swing moves the offbeat in the file too`() {
        val rows = arrayOf("xxxxxxxxxxxxxxxx")
        val straight = notesOf(MidiExporter.export(songOf(*rows), PlaybackPlan.single(songOf(*rows), 0)))
            .filter { it.midi == 36 }.sortedBy { it.start }
        val swungSong = songOf(*rows).copy(swing = 0.6f)
        val swung = notesOf(MidiExporter.export(swungSong, PlaybackPlan.single(swungSong, 0)))
            .filter { it.midi == 36 }.sortedBy { it.start }

        assertEquals(straight[0].start, swung[0].start)
        assertTrue("${straight[1].start} vs ${swung[1].start}", swung[1].start > straight[1].start)
        // 2 つで元に戻るので、拍の頭はずれない。
        assertEquals(straight[2].start, swung[2].start)
        assertEquals(straight[4].start, swung[4].start)
    }

    @Test
    fun `a held lead note is one long note, not several short ones`() {
        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        lead[0] = 72
        for (step in 1..11) lead[step] = Pattern.TIE
        val song = Song("s", "test").withPattern(0, Pattern.empty("A").withLeads(listOf(lead)))
        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.midi == 72 }

        assertEquals(1, notes.size)
        assertEquals(12 * MidiExporter.TICKS_PER_STEP, notes.single().end - notes.single().start)
    }

    @Test
    fun `an arpeggio comes out as single notes in order`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x.x.x..........."
        val pattern = Pattern.of("A", *rows)
        val song = Song("s", "test")
            .withPattern(0, pattern)
            .withPatternChord(0, Chord(0, ChordQuality.MAJOR))
            .copy(chordStyle = ChordStyle.UP)
        val voicing = Chord(0, ChordQuality.MAJOR).voicing()
        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.channel == 0 }
            .sortedBy { it.start }

        assertEquals(3, notes.size)
        assertEquals(voicing[0], notes[0].midi)
        assertEquals(voicing[1], notes[1].midi)
        assertEquals(voicing[2], notes[2].midi)
    }

    @Test
    fun `smooth voicings are written into the file, not the plain ones`() {
        // 書き出した音は、アプリで聴いている音と同じでなければ意味がない。
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        val song = Song("s", "test")
            .withPattern(0, Pattern.of("A", *rows))
            .copy(
                arrangement = listOf(
                    ArrangementStep(0, 4, listOf(Chord(0), Chord(7), Chord(9, ChordQuality.MINOR), Chord(5))),
                ),
                chordVoicing = ChordVoicing.SMOOTH,
            )
        val plan = PlaybackPlan.arrangement(song)
        val notes = notesOf(MidiExporter.export(song, plan)).filter { it.channel == 0 }

        (0 until plan.barCount).forEach { bar ->
            val expected = plan.voicingAt(bar).sorted()
            val start = bar * STEPS_PER_BAR * MidiExporter.TICKS_PER_STEP
            val actual = notes.filter { it.start == start }.map { it.midi }.sorted()
            assertEquals("$bar 小節目", expected, actual)
        }
        // 素の積み方とは違っている（＝繋がりを解いた結果が書かれている）。
        val plain = (0 until plan.barCount).map { plan.chordAt(it).voicing().sorted() }
        val written = (0 until plan.barCount).map { plan.voicingAt(it).sorted() }
        assertTrue("解いた結果が素の積み方と同じ", plain != written)
    }

    @Test
    fun `the thick setting writes the low root too`() {
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        val song = Song("s", "test")
            .withPattern(0, Pattern.of("A", *rows))
            .withPatternChord(0, Chord(0, ChordQuality.MAJOR))
            .copy(chordVoicing = ChordVoicing.THICK)
        val plan = PlaybackPlan.single(song, 0)
        val notes = notesOf(MidiExporter.export(song, plan)).filter { it.channel == 0 }
        assertEquals(plan.voicingAt(0).size + 1, notes.size)
        assertEquals(Voicing.lowRoot(Chord(0)), notes.minOf { it.midi })
    }

    @Test
    fun `the chip arpeggio is left alone by the thick setting`() {
        // 1 声部で和音を装う技なので、音を 1 つ足すと効き目が崩れる。
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x..............."
        val song = Song("s", "test")
            .withPattern(0, Pattern.of("A", *rows))
            .withPatternChord(0, Chord(0, ChordQuality.MAJOR))
            .copy(chordVoicing = ChordVoicing.THICK, chordStyle = ChordStyle.CHIP_ARPEGGIO)
        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.channel == 0 }
        assertEquals(1, notes.size)
        assertTrue("低いルートが足されている", notes.single().midi >= Voicing.LOW_ROOT_BASE + 12)
    }

    @Test
    fun `a chord placed inside the bar is written into the file`() {
        // 1 小節に 2 つ置いたら、書き出したファイルにも 2 つ出てくる。
        val rows = Array(STEP_ROW_COUNT) { "................" }
        rows[ROW_CHORD] = "x.......x......."
        val g = Chord(7)
        val c = Chord(0)
        val song = Song("s", "test")
            .withPattern(
                0,
                Pattern.of("A", *rows).withChordAt(0, 0, g).withChordAt(0, 8, c),
            )
            .withPatternChord(0, Chord(9, ChordQuality.MINOR)) // 置いたほうが勝つ
            .copy(chordVoicing = ChordVoicing.PLAIN)

        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.channel == 0 }
        val head = notes.filter { it.start == 0 }.map { it.midi }.sorted()
        val later = notes.filter { it.start == 8 * MidiExporter.TICKS_PER_STEP }.map { it.midi }.sorted()

        assertEquals(g.voicing().sorted(), head)
        assertEquals(c.voicing().sorted(), later)
    }

    @Test
    fun `the tempo is written into the file`() {
        val bytes = MidiExporter.export(songOf("x...............", bpm = 140), PlaybackPlan.single(songOf("x..............."), 0))
        // FF 51 03 のあとの 3 バイトが 1 拍あたりのマイクロ秒。
        val index = (0 until bytes.size - 6).first {
            bytes[it] == 0xFF.toByte() && bytes[it + 1] == 0x51.toByte() && bytes[it + 2] == 0x03.toByte()
        }
        val micros = ((bytes[index + 3].toInt() and 0xFF) shl 16) or
            ((bytes[index + 4].toInt() and 0xFF) shl 8) or
            (bytes[index + 5].toInt() and 0xFF)
        assertEquals(60_000_000 / 140, micros, 2)
    }

    @Test
    fun `the lead velocity does not follow the kick`() {
        // 旋律の強さをドラムの行から取っていたことがあった。
        // キックにアクセントを付けても、旋律の音は変わらないはず。
        val lead = MutableList(STEPS_PER_BAR) { Pattern.REST }
        lead[0] = 72
        lead[4] = 74
        val pattern = Pattern.of("A", "x...x...........")
            .withLevel(0, 0, Pattern.Level.ACCENT)
            .withLevel(0, 4, Pattern.Level.GHOST)
            .withLeads(listOf(lead))
        val song = Song("s", "test").withPattern(0, pattern)

        val notes = notesOf(MidiExporter.export(song, PlaybackPlan.single(song, 0)))
            .filter { it.midi == 72 || it.midi == 74 }
            .sortedBy { it.start }

        assertEquals(2, notes.size)
        assertEquals(notes[0].velocity, notes[1].velocity)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected=$expected actual=$actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
