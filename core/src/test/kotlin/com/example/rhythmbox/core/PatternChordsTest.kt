package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/** 打ち込みの中に置いたコード。 */
class PatternChordsTest {

    private val c = Chord(0)
    private val f = Chord(5)
    private val g = Chord(7)

    private fun pattern(bars: Int = 1) = Pattern.empty("A").withBarCount(bars)

    @Test
    fun `a pattern with nothing placed says so`() {
        // 何も置いていなければ、コードは今までどおり曲構成が決める。
        val plain = pattern()
        assertFalse(plain.hasChords)
        assertNull(plain.chordAt(0, 0))
        assertNull(plain.chordAt(3, 15))
    }

    @Test
    fun `a placed chord sounds from where it was put`() {
        val placed = pattern().withChordAt(0, 4, f)
        assertTrue(placed.hasChords)
        assertEquals(f, placed.chordAt(0, 4))
        assertEquals(f, placed.chordAt(0, 15))
    }

    @Test
    fun `a chord holds until the next one takes over`() {
        val placed = pattern().withChordAt(0, 0, c).withChordAt(0, 8, g)
        assertEquals(c, placed.chordAt(0, 0))
        assertEquals(c, placed.chordAt(0, 7))
        assertEquals(g, placed.chordAt(0, 8))
        assertEquals(g, placed.chordAt(0, 15))
    }

    @Test
    fun `a chord carries into the bars after it`() {
        val placed = pattern(4).withChordAt(0, 0, c).withChordAt(2, 0, f)
        assertEquals(c, placed.chordAt(0, 0))
        assertEquals(c, placed.chordAt(1, 8)) // 2 小節目は置いていないが続く
        assertEquals(f, placed.chordAt(2, 0))
        assertEquals(f, placed.chordAt(3, 15))
    }

    @Test
    fun `the last chord carries round the loop`() {
        // パターンは繰り返す。3 小節目で変えた和音は、次の周の頭まで続く。
        val placed = pattern(4).withChordAt(2, 0, g)
        assertEquals(g, placed.chordAt(0, 0))
        assertEquals(g, placed.chordAt(1, 15))
        assertEquals(g, placed.chordAt(2, 0))
    }

    @Test
    fun `putting one on top of another replaces it`() {
        val placed = pattern().withChordAt(0, 4, f).withChordAt(0, 4, g)
        assertEquals(g, placed.chordAt(0, 4))
        assertEquals(1, placed.gridAt(0).chords.size)
    }

    @Test
    fun `placed chords come back in order`() {
        val placed = pattern().withChordAt(0, 12, g).withChordAt(0, 4, f).withChordAt(0, 0, c)
        assertEquals(listOf(0, 4, 12), placed.gridAt(0).chords.map { it.step })
    }

    @Test
    fun `taking one away lets the one before it through again`() {
        val placed = pattern().withChordAt(0, 0, c).withChordAt(0, 8, g)
        val removed = placed.withoutChordAt(0, 8)
        assertEquals(c, removed.chordAt(0, 12))
    }

    @Test
    fun `clearing them all hands the chords back to the song`() {
        val placed = pattern(2).withChordAt(0, 0, c).withChordAt(1, 4, g)
        val cleared = placed.withoutChords()
        assertFalse(cleared.hasChords)
        assertNull(cleared.chordAt(1, 8))
    }

    @Test
    fun `clearing the punched-in notes keeps the chords`() {
        // 打点は「いつ弾くか」、置いたコードは「何の和音か」で別のもの。
        val placed = pattern().withChordAt(0, 0, c).set(ROW_CHORD, 0, true)
        val cleared = placed.clearedRhythm()
        assertEquals(c, cleared.chordAt(0, 0))
        assertFalse(cleared.isOn(ROW_CHORD, 0))
    }

    @Test
    fun `making the pattern longer carries the chords along`() {
        val placed = pattern().withChordAt(0, 0, c)
        val longer = placed.withBarCount(4)
        assertEquals(c, longer.chordAt(0, 0))
        assertEquals(c, longer.chordAt(3, 15))
    }

    @Test
    fun `a chord placed past the end of the bar is dropped`() {
        val broken = Pattern.empty("A").copy(chords = listOf(ChordAt(99, c), ChordAt(-1, f)))
        assertEquals(emptyList<ChordAt>(), broken.normalized().gridAt(0).chords)
    }

    @Test
    fun `placed chords survive being saved and read back`() {
        val song = Song.newSong("s", "test", 0L).let { base ->
            base.withPattern(0, base.pattern(0).withBarCount(2).withChordAt(0, 0, c).withChordAt(1, 8, g))
        }
        val read = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(song), song.id)))
        val pattern = read?.songs?.single()?.pattern(0)
        assertEquals(c, pattern?.chordAt(0, 0))
        assertEquals(g, pattern?.chordAt(1, 8))
    }

    @Test
    fun `a file written before this existed still reads`() {
        // この仕組みが入る前の保存データ＝ chords の項目がどこにも無いファイル。
        // 文字を消して作ると消し損ねに気づけないので、JSON として取り除く。
        val original = SongCodec.encode(SongLibrary(listOf(Song.newSong("s", "old", 0L))))
        val text = Json.encodeToString(
            JsonElement.serializer(),
            withoutKey(Json.parseToJsonElement(original), "chords"),
        )
        assertFalse("chords が残っている", text.contains("\"chords\""))
        assertTrue("打ち込みまで消えている", text.contains("\"rows\""))

        val read = SongCodec.decode(text)
        assertTrue("読めなかった", read != null)
        val pattern = read!!.songs.single().pattern(0)
        assertFalse(pattern.hasChords)
        assertNull(pattern.chordAt(0, 0))
        // 打ち込みそのものは今までどおり読める。
        assertTrue(pattern.hitCount() > 0)
    }

    /** [element] の中から [key] という項目を、入れ子の奥まで取り除く。 */
    private fun withoutKey(element: JsonElement, key: String): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it != key }.mapValues { withoutKey(it.value, key) },
        )
        is JsonArray -> JsonArray(element.map { withoutKey(it, key) })
        else -> element
    }
}
