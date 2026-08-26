package com.example.rhythmbox.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** パターンを 2 小節以上にできるようにしたぶんの確認。 */
class PatternBarsTest {

    private fun kick(vararg steps: Int): Int = steps.fold(0) { bits, step -> bits or (1 shl step) }

    @Test
    fun `a new pattern is one bar long`() {
        val pattern = Pattern.empty("A")
        assertEquals(1, pattern.barCount)
        assertTrue("1 小節なら余分な小節は持たない", pattern.extraBars.isEmpty())
    }

    @Test
    fun `lengthening a pattern copies what is already there`() {
        // 1 小節のループを 2 小節にしたとき、後半が無音になると
        // 「長さを変えた」ではなく「壊れた」と聞こえる。
        val one = Pattern.of("A", "x...x...x...x...")
        val two = one.withBarCount(2)

        assertEquals(2, two.barCount)
        assertEquals(one.rowAt(0), two.at(0).rowAt(0))
        assertEquals(one.rowAt(0), two.at(1).rowAt(0))
    }

    @Test
    fun `each bar can hold a different rhythm`() {
        val pattern = Pattern.of("A", "x...x...x...x...")
            .withBarCount(2)
            .toggleAt(1, 0, 14)

        assertEquals(kick(0, 4, 8, 12), pattern.at(0).rowAt(0))
        assertEquals(kick(0, 4, 8, 12, 14), pattern.at(1).rowAt(0))
        // 3 小節目は無いので 1 小節目に折り返す
        assertEquals(pattern.at(0).rowAt(0), pattern.at(2).rowAt(0))
    }

    @Test
    fun `editing one bar leaves the others alone`() {
        val pattern = Pattern.of("A", "x...x...x...x...").withBarCount(4)
        val edited = pattern.clearRowAt(2, 0)

        assertEquals(kick(0, 4, 8, 12), edited.at(0).rowAt(0))
        assertEquals(kick(0, 4, 8, 12), edited.at(1).rowAt(0))
        assertEquals(0, edited.at(2).rowAt(0))
        assertEquals(kick(0, 4, 8, 12), edited.at(3).rowAt(0))
    }

    @Test
    fun `strength stays with the bar it was written in`() {
        val pattern = Pattern.of("A", "x...x...x...x...")
            .withBarCount(2)
            .cycleLevelAt(1, 0, 0)

        assertEquals(Pattern.Level.NORMAL, pattern.at(0).levelAt(0, 0))
        assertEquals(Pattern.Level.ACCENT, pattern.at(1).levelAt(0, 0))
    }

    @Test
    fun `a bar view does not carry the other bars`() {
        // 切り出したものを渡し回しても迷子にならない。
        // at() は旋律を残すので barCount は元のままだが、打ち込みはもう 1 小節ぶんしかなく、
        // 何小節目を訊いても同じものが返る。
        val pattern = Pattern.of("A", "x...............").withBarCount(2).toggleAt(1, 0, 8)
        val second = pattern.at(1)

        assertTrue("打ち込みは切り出したぶんだけ", second.extraBars.isEmpty())
        assertEquals(second.rowAt(0), second.at(0).rowAt(0))
        assertEquals(second.rowAt(0), second.at(1).rowAt(0))
        // 旋律は残っているので、切り出した後も小節を指定して読める
        assertEquals(pattern.leadBarCount, second.leadBarCount)
    }

    @Test
    fun `counting hits looks at every bar`() {
        val pattern = Pattern.of("A", "x...x...x...x...").withBarCount(2).toggleAt(1, 0, 2)
        assertEquals(4 + 5, pattern.hitCount())
        assertFalse(pattern.isEmpty())
    }

    @Test
    fun `clearing a pattern also brings it back to one bar`() {
        val pattern = Pattern.of("A", "x...x...x...x...").withBarCount(4)
        val cleared = pattern.cleared()
        assertEquals(1, cleared.barCount)
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `shortening a pattern drops the bars past the end`() {
        val pattern = Pattern.of("A", "x...............")
            .withBarCount(4)
            .toggleAt(3, 0, 8)
        val short = pattern.withBarCount(2)

        assertEquals(2, short.barCount)
        // 4 小節目に書いたものは戻しても返らない
        assertEquals(short.at(0).rowAt(0), short.withBarCount(4).at(3).rowAt(0))
    }

    @Test
    fun `playing a pattern walks through all of its bars`() {
        val song = Song(id = "s", name = "s").let { base ->
            base.withPattern(0, base.pattern(0).withBarCount(3))
        }
        val plan = PlaybackPlan.single(song, 0)
        assertEquals(3, plan.barCount)
        assertEquals(listOf(0, 1, 2), plan.bars.map { it.patternBar })
    }

    @Test
    fun `playback reads the bar it is actually on`() {
        // 小節ごとに中身が違うパターンで、プランが小節ぶんを切り出して返すこと。
        val base = Song(id = "s", name = "s")
        val two = base.pattern(0).withBarCount(2).clearRowAt(1, 0)
        val song = base.withPattern(0, two)
        val plan = PlaybackPlan.single(song, 0)

        assertNotEquals(0, plan.patternAt(0).rowAt(0))
        assertEquals(0, plan.patternAt(1).rowAt(0))
    }

    @Test
    fun `a chain plays each pattern to its end`() {
        val base = Song(id = "s", name = "s")
        val song = base.withPattern(0, base.pattern(0).withBarCount(2))
        val plan = PlaybackPlan.chain(song, listOf(0, 1))

        assertEquals(3, plan.barCount)
        assertEquals(listOf(0, 0, 1), plan.bars.map { it.patternIndex })
        assertEquals(listOf(0, 1, 0), plan.bars.map { it.patternBar })
    }

    @Test
    fun `songs saved when only the melody could be longer still play their drums`() {
        // 打ち込みは 1 小節ぶん・旋律は 4 小節ぶん、という古い保存データ。
        val legacy = Pattern(
            name = "A",
            rows = List(STEP_ROW_COUNT) { if (it == 0) kick(0, 4, 8, 12) else 0 },
            leads = List(4) { Pattern.emptyLead() },
        )
        assertEquals(4, legacy.barCount)
        assertTrue("打ち込みは残っていない", legacy.extraBars.isEmpty())
        // 4 小節とも同じドラムが鳴る（前と同じ聞こえ方）。
        repeat(4) { bar -> assertEquals(kick(0, 4, 8, 12), legacy.at(bar).rowAt(0)) }
    }

    @Test
    fun `writing into a later bar of such a song keeps the earlier ones`() {
        val legacy = Pattern(
            name = "A",
            rows = List(STEP_ROW_COUNT) { if (it == 0) kick(0, 8) else 0 },
            leads = List(4) { Pattern.emptyLead() },
        )
        val edited = legacy.toggleAt(2, 0, 4)

        assertEquals(kick(0, 8), edited.at(0).rowAt(0))
        assertEquals(kick(0, 8), edited.at(1).rowAt(0))
        assertEquals(kick(0, 4, 8), edited.at(2).rowAt(0))
        assertEquals(kick(0, 8), edited.at(3).rowAt(0))
        assertEquals(4, edited.barCount)
    }

    // --- 保存と読み込み ------------------------------------------------------

    private fun roundTrip(pattern: Pattern): Pattern {
        val library = SongLibrary(
            songs = listOf(Song.newSong("id", "曲", now = 1L).withPattern(0, pattern)),
            currentId = "id",
        )
        return SongCodec.decode(SongCodec.encode(library))!!.songs.first().pattern(0)
    }

    @Test
    fun `a multi bar pattern survives saving and loading`() {
        val pattern = Pattern.of("A", "x...x...x...x...")
            .withBarCount(3)
            .clearRowAt(1, 0)
            .toggleAt(2, 0, 2)
            .cycleLevelAt(2, 0, 2)
        val restored = roundTrip(pattern)

        assertEquals(3, restored.barCount)
        assertEquals(kick(0, 4, 8, 12), restored.at(0).rowAt(0))
        assertEquals(0, restored.at(1).rowAt(0))
        assertEquals(kick(0, 2, 4, 8, 12), restored.at(2).rowAt(0))
        assertEquals(Pattern.Level.ACCENT, restored.at(2).levelAt(0, 2))
    }

    @Test
    fun `a one bar pattern is saved the same as before`() {
        // 小節数を足したせいで、1 小節の曲の保存内容が変わっていないこと。
        val plain = Pattern.of("A", "x...x...x...x...")
        assertTrue(plain.extraBars.isEmpty())
        assertFalse("空の小節を並べただけの中身は書かない", SongCodec.encode(
            SongLibrary(listOf(Song.newSong("id", "曲", 1L).withPattern(0, plain)), "id"),
        ).contains("\"extraBars\": [\n"))
    }

    @Test
    fun `a file saved before patterns could be longer still loads`() {
        val json = """
            {
              "songs": [
                {
                  "id": "old", "name": "前の形", "bpm": 100,
                  "patterns": [{"name": "A", "rows": [4369, 0, 0, 0, 0, 0, 0, 0]}],
                  "arrangement": [{"patternIndex": 0, "repeat": 2}]
                }
              ],
              "currentId": "old"
            }
        """.trimIndent()
        val pattern = SongCodec.decode(json)!!.current()!!.pattern(0)

        assertEquals(1, pattern.barCount)
        assertEquals(kick(0, 4, 8, 12), pattern.at(0).rowAt(0))
    }

    @Test
    fun `an older app reading a longer pattern still gets the first bar`() {
        // 新しい形で保存したものから、古いアプリが知らない項目をまるごと落として読ませる。
        val pattern = Pattern.of("A", "x...x...x...x...").withBarCount(2).clearRowAt(1, 0)
        val library = SongLibrary(listOf(Song.newSong("id", "曲", 1L).withPattern(0, pattern)), "id")
        val stripped = withoutKey(Json.parseToJsonElement(SongCodec.encode(library)), "extraBars")
        val restored = SongCodec.decode(stripped.toString())!!.current()!!.pattern(0)

        // 小節ごとの打ち込みは落ちるが、1 小節目は無事で、
        // 2 小節目はそれを繰り返す（小節数を足す前と同じ鳴り方に戻るだけ）。
        assertTrue(restored.extraBars.isEmpty())
        assertEquals(kick(0, 4, 8, 12), restored.at(0).rowAt(0))
        assertEquals(kick(0, 4, 8, 12), restored.at(1).rowAt(0))
    }

    /** JSON から [key] を取り除く（その項目を知らないアプリが読んだ状態を作る）。 */
    private fun withoutKey(element: JsonElement, key: String): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it != key }.mapValues { withoutKey(it.value, key) },
        )
        is JsonArray -> JsonArray(element.map { withoutKey(it, key) })
        else -> element
    }
}
