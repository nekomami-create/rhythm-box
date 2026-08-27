package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OfflineRendererTest {

    private val sampleRate = 44_100

    private fun drums() = DrumSynth.renderAll(sampleRate)

    private fun song() = Song.newSong("s", "書き出しテスト", 0L).copy(bpm = 120)

    @Test
    fun `the length matches the song plus the tail`() {
        val song = song()
        val plan = PlaybackPlan.arrangement(song)
        val audio = OfflineRenderer.render(song, plan, drums(), sampleRate, tailSeconds = 2.0)

        // 120 BPM の 1 小節 = 2 秒。既定の曲は 8 小節なので 16 秒 + 余韻 2 秒。
        assertEquals(8, plan.barCount)
        assertEquals((18.0 * sampleRate).toInt(), OfflineRenderer.frames(audio))
        assertEquals(18.0, OfflineRenderer.seconds(audio, sampleRate), 1e-6)
    }

    @Test
    fun `the rendered audio is audible and never clips`() {
        val song = song()
        val audio = OfflineRenderer.render(song, PlaybackPlan.arrangement(song), drums(), sampleRate)
        val peak = audio.maxOf { abs(it) }
        assertTrue("無音になっている", peak > 0.2f)
        assertTrue("音が割れている (peak=$peak)", peak <= 1.0f)
    }

    @Test
    fun `the tail fades out instead of cutting off`() {
        val song = song()
        val audio = OfflineRenderer.render(song, PlaybackPlan.arrangement(song), drums(), sampleRate)
        // 最後の 0.2 秒は、曲の途中よりずっと静かになっているはず。
        val middle = rms(audio, audio.size / 2, audio.size / 2 + sampleRate / 5)
        val ending = rms(audio, audio.size - sampleRate / 5, audio.size)
        assertTrue("末尾 $ending / 途中 $middle", ending < middle * 0.1)
    }

    @Test
    fun `progress runs from start to finish`() {
        val song = song()
        val values = mutableListOf<Float>()
        OfflineRenderer.render(song, PlaybackPlan.single(song, 0), drums(), sampleRate) {
            values += it
        }
        assertTrue(values.isNotEmpty())
        assertTrue(values.first() > 0f)
        assertEquals(1f, values.last(), 1e-6f)
        assertTrue("進捗が戻っている", values.zipWithNext().all { it.first <= it.second })
    }

    @Test
    fun `an empty plan renders nothing`() {
        assertEquals(0, OfflineRenderer.render(song(), PlaybackPlan(emptyList(), emptyList()), drums()).size)
    }

    @Test
    fun `muted tracks are left out of the file too`() {
        val song = song().let { base ->
            base.copy(tracks = base.tracks.map { it.copy(muted = true) })
        }
        val audio = OfflineRenderer.render(song, PlaybackPlan.arrangement(song), drums(), sampleRate)
        assertEquals(0f, audio.maxOf { abs(it) }, 1e-6f)
    }

    @Test
    fun `pcm16 conversion keeps the waveform and stays in range`() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f, -1f, 2f, -2f)
        val pcm = OfflineRenderer.toPcm16(samples)
        assertEquals(0, pcm[0].toInt())
        // 32767 倍して四捨五入するので、負側は 1 だけ絶対値が小さくなる（耳では分からない差）。
        assertEquals(16384, pcm[1].toInt())
        assertEquals(-16383, pcm[2].toInt())
        assertEquals(32767, pcm[3].toInt())
        assertEquals(-32767, pcm[4].toInt())
        assertEquals(32767, pcm[5].toInt()) // 範囲外は丸める
        assertEquals(-32767, pcm[6].toInt())
    }

    private fun rms(buffer: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until minOf(to, buffer.size)) sum += buffer[i].toDouble() * buffer[i]
        return Math.sqrt(sum / (to - from))
    }
}
