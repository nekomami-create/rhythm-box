package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 旋律の強弱の確認。
 *
 * ドラム・コード・ベースには強弱があるのに、いちばん抑揚が要る旋律だけ
 * 全部同じ音量だった。
 */
class LeadDynamicsTest {

    private val sampleRate = 48_000
    private val bpm = 120

    private fun withNote(step: Int = 0, midi: Int = 72) =
        Pattern.empty("A").withLead(0, step, midi)

    private fun render(pattern: Pattern): FloatArray {
        val song = Song("s", "test", bpm = bpm).withPattern(0, pattern)
        val engine = PlaybackEngine(sampleRate, List(DRUM_COUNT) { FloatArray(1) })
        engine.config = EngineConfig(
            plan = PlaybackPlan.single(song, 0),
            bpm = bpm,
            masterVolume = 1f,
            trackVolumes = List(TRACK_COUNT) { 1f },
            mutes = List(TRACK_COUNT) { false },
        )
        engine.start()
        val out = FloatArray((sampleRate * secondsPerStep(bpm) * 2).roundToInt())
        engine.renderLeft(out)
        return out
    }

    private fun peak(buffer: FloatArray) = buffer.maxOf { abs(it) }

    @Test
    fun `a note starts out at the normal strength`() {
        assertEquals(Pattern.Level.NORMAL, withNote().leadLevelAt(0, 0))
    }

    @Test
    fun `holding a note cycles it through the three strengths`() {
        var pattern = withNote()
        pattern = pattern.cycleLeadLevel(0, 0)
        assertEquals(Pattern.Level.ACCENT, pattern.leadLevelAt(0, 0))
        pattern = pattern.cycleLeadLevel(0, 0)
        assertEquals(Pattern.Level.GHOST, pattern.leadLevelAt(0, 0))
        pattern = pattern.cycleLeadLevel(0, 0)
        assertEquals(Pattern.Level.NORMAL, pattern.leadLevelAt(0, 0))
    }

    @Test
    fun `an empty step cannot be given a strength`() {
        val empty = Pattern.empty("A")
        assertEquals(empty, empty.cycleLeadLevel(0, 4))
    }

    @Test
    fun `a song without any strengths keeps its saved form unchanged`() {
        // 使っていない曲の保存内容が、この機能を足したせいで増えないこと。
        val plain = withNote()
        assertTrue(plain.leadAccents.isEmpty())
        assertTrue(plain.leadGhosts.isEmpty())
    }

    @Test
    fun `removing a note takes its strength with it`() {
        // 置き直したときに前の強さが残っていると驚く。
        val accented = withNote().cycleLeadLevel(0, 0)
        assertTrue(accented.leadAccents.isNotEmpty())

        val removed = accented.withLead(0, 0, Pattern.REST)
        assertTrue("音を消したのに強さが残っている", removed.leadAccents.all { it == 0 })

        // 同じところに置き直したら、普通の強さから始まる。
        assertEquals(Pattern.Level.NORMAL, removed.withLead(0, 0, 72).leadLevelAt(0, 0))
    }

    @Test
    fun `strengths follow the bar they were written in`() {
        val pattern = Pattern.empty("A")
            .withBarCount(2)
            .withLead(0, 0, 72)
            .withLead(1, 0, 74)
            .cycleLeadLevel(1, 0)

        assertEquals(Pattern.Level.NORMAL, pattern.leadLevelAt(0, 0))
        assertEquals(Pattern.Level.ACCENT, pattern.leadLevelAt(1, 0))
    }

    @Test
    fun `clearing one bar clears its strengths too`() {
        val pattern = Pattern.empty("A")
            .withBarCount(2)
            .withLead(0, 0, 72)
            .withLead(1, 0, 74)
            .cycleLeadLevel(0, 0)
            .cycleLeadLevel(1, 0)
        val cleared = pattern.clearLead(0)

        assertEquals(Pattern.Level.NORMAL, cleared.leadLevelAt(0, 0))
        assertEquals("残した小節の強さは消えない", Pattern.Level.ACCENT, cleared.leadLevelAt(1, 0))
    }

    @Test
    fun `a stronger note really is louder`() {
        val plain = peak(render(withNote()))
        val accented = peak(render(withNote().cycleLeadLevel(0, 0)))
        val ghost = peak(render(withNote().cycleLeadLevel(0, 0).cycleLeadLevel(0, 0)))

        assertTrue("強い音が普通より大きい（強 $accented / 普通 $plain）", accented > plain)
        assertTrue("弱い音が普通より小さい（弱 $ghost / 普通 $plain）", ghost < plain)
    }

    @Test
    fun `the strengths survive saving and loading`() {
        val saved = Song("s", "曲").withPattern(
            0,
            Pattern.empty("A").withLead(0, 0, 72).withLead(0, 4, 74).cycleLeadLevel(0, 4),
        )
        val restored = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(saved), "s")))!!
            .songs
            .first()
            .pattern(0)

        assertEquals(Pattern.Level.NORMAL, restored.leadLevelAt(0, 0))
        assertEquals(Pattern.Level.ACCENT, restored.leadLevelAt(0, 4))
    }

    @Test
    fun `a song saved before the lead had strengths opens with none`() {
        val json = """
            {"songs": [{"id": "old", "name": "前の形"}], "currentId": "old"}
        """.trimIndent()
        val pattern = SongCodec.decode(json)!!.current()!!.pattern(0)
        assertTrue(pattern.leadAccents.isEmpty())
        assertTrue(pattern.leadGhosts.isEmpty())
    }
}
