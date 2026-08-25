package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCodecTest {

    private val library = SongLibrary(
        songs = listOf(
            Song.newSong("id-1", "はじめての曲", now = 1_000L)
                .withPattern(2, Pattern.of("C", "x.x.x.x.x.x.x.x.").withLead(0, 4, 67))
                .withPatternChord(2, Chord(9, ChordQuality.MINOR_SEVENTH)),
            Song.newSong("id-2", "2 曲目", now = 2_000L),
        ),
        currentId = "id-2",
    )

    @Test
    fun `round trips through json`() {
        val restored = SongCodec.decode(SongCodec.encode(library))
        assertEquals(library, restored)
    }

    @Test
    fun `held notes survive the round trip`() {
        val stretched = SongLibrary(
            songs = listOf(
                Song.newSong("id-1", "曲", now = 1L)
                    .withPattern(0, Pattern.empty("A").withLead(0, 0, 72).withLeadTie(0, 0, 9)),
            ),
            currentId = "id-1",
        )
        val song = SongCodec.decode(SongCodec.encode(stretched))!!.songs.first()

        assertEquals(72, song.pattern(0).leadAt(0, 0))
        assertEquals(9, song.pattern(0).tieRun(0, 0))
    }

    @Test
    fun `chords and lead notes survive the round trip`() {
        val song = SongCodec.decode(SongCodec.encode(library))!!.songs.first()
        assertEquals(Chord(9, ChordQuality.MINOR_SEVENTH), song.patternChord(2))
        assertEquals(67, song.pattern(2).leadAt(0, 4))
        assertEquals(Chord(0, ChordQuality.MAJOR), song.arrangement.first().chords.first())
    }

    @Test
    fun `broken json decodes to null instead of throwing`() {
        assertNull(SongCodec.decode("{ this is not json"))
        assertNull(SongCodec.decode(""))
    }

    @Test
    fun `out of range values are repaired on load`() {
        val json = """
            {
              "songs": [
                {
                  "id": "x",
                  "name": "壊れた曲",
                  "bpm": 9000,
                  "masterVolume": 3.0,
                  "patterns": [],
                  "arrangement": [{"patternIndex": 42, "repeat": 0}, {"patternIndex": 1, "repeat": 2}],
                  "tracks": []
                }
              ],
              "currentId": "x"
            }
        """.trimIndent()
        val song = SongCodec.decode(json)?.current()
        assertNotNull(song)
        requireNotNull(song)
        assertEquals(Song.MAX_BPM, song.bpm)
        assertEquals(1f, song.masterVolume, 1e-6f)
        assertEquals(Song.PATTERN_COUNT, song.patterns.size)
        assertEquals(TRACK_COUNT, song.tracks.size)
        assertEquals(listOf(ArrangementStep(1, 2)), song.arrangement)
    }

    @Test
    fun `songs saved before chords were added still load`() {
        // ドラム 8 行だけ・コードもリードも無い、以前のバージョンの保存データ。
        val json = """
            {
              "songs": [
                {
                  "id": "old",
                  "name": "むかしの曲",
                  "bpm": 100,
                  "patterns": [
                    {"name": "A", "rows": [1, 0, 0, 0, 0, 0, 0, 0]}
                  ],
                  "arrangement": [{"patternIndex": 0, "repeat": 2}],
                  "tracks": [{"volume": 0.5, "muted": false}]
                }
              ],
              "currentId": "old"
            }
        """.trimIndent()
        val song = SongCodec.decode(json)?.current()
        assertNotNull(song)
        requireNotNull(song)
        assertEquals(100, song.bpm)
        assertTrue(song.pattern(0).isOn(0, 0))
        assertEquals(STEP_ROW_COUNT, song.pattern(0).rows.size)
        assertEquals(STEPS_PER_BAR, song.pattern(0).leadBars.first().size)
        assertEquals(TRACK_COUNT, song.tracks.size)
        assertEquals(0.5f, song.track(0).volume, 1e-6f)
        assertEquals(Song.PATTERN_COUNT, song.patternChords.size)
        // コード指定の無い小節でも、パターンの試聴コードで鳴らせる
        assertEquals(song.patternChord(0), PlaybackPlan.arrangement(song).chordAt(0))
    }

    @Test
    fun `library replace and remove keep the current song sane`() {
        val edited = library.current()!!.copy(name = "改名")
        val next = library.replace(edited)
        assertEquals("改名", next.current()?.name)
        assertEquals(2, next.songs.size)

        val removed = next.remove("id-2")
        assertEquals(1, removed.songs.size)
        assertEquals("id-1", removed.current()?.id)

        assertTrue(removed.remove("id-1").songs.isEmpty())
        assertNull(removed.remove("id-1").current())
    }

    @Test
    fun `a single song can be written out and read back`() {
        val song = Song.newSong("id-x", "持ち出す曲", now = 5L)
            .copy(bpm = 137, swing = 0.4f, chordStyle = ChordStyle.UP_DOWN)
            .let { it.withPattern(0, Pattern.of("A", "x...x...x...x...").withLevel(0, 0, Pattern.Level.ACCENT)) }

        val restored = SongCodec.decodeSong(SongCodec.encodeSong(song))

        assertNotNull(restored)
        assertEquals("持ち出す曲", restored!!.name)
        assertEquals(137, restored.bpm)
        assertEquals(0.4f, restored.swing, 1e-6f)
        assertEquals(ChordStyle.UP_DOWN, restored.chordStyle)
        assertEquals(Pattern.Level.ACCENT, restored.pattern(0).levelAt(0, 0))
    }

    @Test
    fun `a library file can also be read as a single song`() {
        val restored = SongCodec.decodeSong(SongCodec.encode(library))
        assertNotNull(restored)
        assertEquals("はじめての曲", restored!!.name)
    }

    @Test
    fun `something that is not a song file reads as nothing`() {
        assertNull(SongCodec.decodeSong("{\"これは\": \"曲ではない\""))
        assertNull(SongCodec.decodeSong(""))
    }
}
