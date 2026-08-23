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
                .withPattern(2, Pattern.of("C", "x.x.x.x.x.x.x.x.")),
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
        assertEquals(VOICE_COUNT, song.tracks.size)
        assertEquals(listOf(ArrangementStep(1, 2)), song.arrangement)
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
}
