package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 展開形で声部を繋ぐ／低いルートを足す。 */
class VoicingTest {

    private val key = MusicKey(0, Scale.MAJOR)

    private val templates = listOf(
        ProgressionTemplate.POP_PUNK,
        ProgressionTemplate.ROYAL_ROAD,
        ProgressionTemplate.KOMURO,
        ProgressionTemplate.CANON,
        ProgressionTemplate.CITY,
        ProgressionTemplate.TURNAROUND,
        ProgressionTemplate.TWO_FIVE_ONE,
    )

    /** 一周ぶんの声部の移動量（最後から最初へ戻るぶんも含む）。 */
    private fun travel(voicings: List<List<Int>>): Int =
        voicings.indices.sumOf { Voicing.distance(voicings[it], voicings[(it + 1) % voicings.size]) }

    @Test
    fun `smooth voicings move the voices much less than plain ones`() {
        templates.forEach { template ->
            val chords = template.chords(key)
            val plain = travel(chords.map { it.voicing() })
            val smooth = travel(Voicing.lead(chords))
            assertTrue(
                "${template.name}: そのまま $plain → なめらか $smooth",
                smooth < plain * 0.6,
            )
        }
    }

    @Test
    fun `a progression that is already smooth is left alone`() {
        // 隣り合う和音がもともと近い型は、直しようがないので変わらない。
        val chords = ProgressionTemplate.GAME_FIELD.chords(MusicKey(0, Scale.NATURAL_MINOR))
        assertEquals(chords.map { it.voicing() }, Voicing.lead(chords))
    }

    @Test
    fun `every note still belongs to its chord`() {
        // 動かして良いのは高さだけ。構成音そのものを変えてはいけない。
        templates.forEach { template ->
            val chords = template.chords(key)
            Voicing.lead(chords).forEachIndexed { index, notes ->
                val allowed = chords[index].voicing().map { it.mod(12) }.toSet()
                notes.forEach { note ->
                    assertTrue("${template.name} $index: $note は ${chords[index].name} の音ではない", note.mod(12) in allowed)
                }
                assertEquals(chords[index].voicing().size, notes.size)
            }
        }
    }

    @Test
    fun `the voicings stay inside the register`() {
        templates.forEach { template ->
            Voicing.lead(template.chords(key)).forEach { notes ->
                assertTrue("$notes が下へ外れている", notes.min() >= Voicing.LOWEST)
                assertTrue("$notes が上へ外れている", notes.max() <= Voicing.HIGHEST)
            }
        }
    }

    @Test
    fun `the loop seam is worked out too`() {
        // 曲は最後まで行ったら頭へ戻る。継ぎ目も声部の進行の一部として解く。
        // 2 周した結果を出発点にしてもう一周しても、もう動かない＝落ち着いている。
        templates.forEach { template ->
            val chords = template.chords(key)
            val once = Voicing.lead(chords)
            assertEquals("${template.name} が落ち着いていない", once, Voicing.lead(chords))
            val seam = Voicing.distance(once.last(), once.first())
            val inside = once.indices.drop(1).maxOf { Voicing.distance(once[it - 1], once[it]) }
            assertTrue("${template.name}: 継ぎ目 $seam が中の最大 $inside より大きい", seam <= inside * 2)
        }
    }

    @Test
    fun `the first chord starts near the middle of the register`() {
        // 曲ごとに開始の高さが暴れると、同じ進行でも印象が変わってしまう。
        templates.forEach { template ->
            val first = Voicing.lead(template.chords(key)).first()
            val centre = (first.first() + first.last()) / 2
            assertTrue("${template.name}: 中心が $centre", kotlin.math.abs(centre - Voicing.CENTRE) <= 6)
        }
    }

    // --- 低いルート ---------------------------------------------------------

    @Test
    fun `the low root always lands an octave above the bass track`() {
        // 単純に「和音の 1 オクターブ下」にすると、ルートが F#〜B のときだけ
        // ベースと同じ音になる（和音の基準がその帯だけ 12 下がっているため）。
        for (root in 0 until 12) {
            val chord = Chord(root, ChordQuality.MAJOR)
            val low = Voicing.lowRoot(chord)
            assertEquals("ルート $root の音名が違う", root, low.mod(12))
            assertTrue("ルート $root でベース ${chord.bassMidi()} と重なる", low >= chord.bassMidi() + 12)
            assertTrue(low in Voicing.LOW_ROOT_BASE until Voicing.LOW_ROOT_BASE + 12)
        }
    }

    @Test
    fun `a slash chord puts its own bass note down there`() {
        // C/E なら下に置くのは E。ルートを置くと分数コードの意味が消える。
        val chord = Chord(0, ChordQuality.MAJOR, bass = 4)
        assertEquals(4, Voicing.lowRoot(chord).mod(12))
    }

    // --- 曲の設定 -----------------------------------------------------------

    @Test
    fun `a new song sounds the way it always did`() {
        assertEquals(ChordVoicing.PLAIN, Song.newSong("s", "test", 0L).chordVoicing)
        assertTrue(!ChordVoicing.PLAIN.smooth && !ChordVoicing.PLAIN.lowRoot)
        assertTrue(ChordVoicing.SMOOTH.smooth && !ChordVoicing.SMOOTH.lowRoot)
        assertTrue(ChordVoicing.THICK.smooth && ChordVoicing.THICK.lowRoot)
    }

    @Test
    fun `the plan always works out the voicings, whatever the setting says`() {
        // 設定を切り替えるたびにプランを作り直さずに済むよう、常に解いておく。
        val song = Song.newSong("s", "test", 0L)
        val plan = PlaybackPlan.arrangement(song)
        assertTrue(plan.barCount > 0)
        (0 until plan.barCount).forEach { bar ->
            assertTrue("小節 $bar が解かれていない", plan.barAt(bar).voicing.isNotEmpty())
        }
    }

    // 書き出したファイルの中身との突き合わせは MidiExporterTest 側にある
    // （あちらにファイルを読み解く道具が揃っているため）。

    @Test
    fun `the same song always gives the same voicings`() {
        val song = Song.newSong("s", "test", 0L)
        assertEquals(
            PlaybackPlan.arrangement(song).bars.map { it.voicing },
            PlaybackPlan.arrangement(song).bars.map { it.voicing },
        )
    }
}
