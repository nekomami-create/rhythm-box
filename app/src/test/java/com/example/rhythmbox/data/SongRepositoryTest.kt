package com.example.rhythmbox.data

import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SongRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun repository(scope: TestScope, file: File = File(folder.root, "songs.json")) =
        SongRepository(file, scope, now = { 1_000L })

    @Test
    fun `first launch creates a starter song and saves it`() = runTest(StandardTestDispatcher()) {
        val file = File(folder.root, "songs.json")
        val repository = repository(this, file)

        repository.load()
        advanceUntilIdle()

        val song = repository.library.value.current()
        assertNotNull(song)
        assertEquals(Song.PATTERN_COUNT, song!!.patterns.size)
        assertTrue("初期パターンが空", song.patterns.first().hitCount() > 0)
        assertTrue("ファイルが作られていない", file.exists())
    }

    @Test
    fun `edits are written back and can be reloaded`() = runTest(StandardTestDispatcher()) {
        val file = File(folder.root, "songs.json")
        val first = repository(this, file)
        first.load()
        advanceUntilIdle()

        first.updateCurrentSong { song ->
            song.copy(bpm = 96, name = "テスト曲")
                .withPattern(0, Pattern.of("A", "x.x.x.x.x.x.x.x."))
        }
        advanceUntilIdle()

        val reloaded = repository(this, file)
        reloaded.load()
        advanceUntilIdle()

        val song = reloaded.library.value.current()!!
        assertEquals("テスト曲", song.name)
        assertEquals(96, song.bpm)
        assertEquals(8, song.pattern(0).hitCount())
    }

    @Test
    fun `a corrupted file is backed up and replaced with a fresh song`() = runTest(StandardTestDispatcher()) {
        val file = File(folder.root, "songs.json")
        file.writeText("これは JSON ではありません")

        val repository = repository(this, file)
        repository.load()
        advanceUntilIdle()

        assertTrue(File(folder.root, "songs.json.bak").exists())
        assertEquals(1, repository.library.value.songs.size)
        assertNotNull(repository.library.value.current())
    }

    @Test
    fun `songs can be created, switched and deleted`() = runTest(StandardTestDispatcher()) {
        val repository = repository(this)
        repository.load()
        advanceUntilIdle()
        val firstId = repository.library.value.current()!!.id

        val secondId = repository.createSong("2 曲目")
        assertEquals(secondId, repository.library.value.current()?.id)
        assertEquals(2, repository.library.value.songs.size)

        repository.selectSong(firstId)
        assertEquals(firstId, repository.library.value.current()?.id)

        repository.deleteSong(firstId)
        assertEquals(1, repository.library.value.songs.size)
        assertEquals(secondId, repository.library.value.current()?.id)

        // 最後の 1 曲を消しても、必ず編集できる曲が残る。
        repository.deleteSong(secondId)
        assertEquals(1, repository.library.value.songs.size)
        assertFalse(repository.library.value.songs.first().id == secondId)
        advanceUntilIdle()
    }

    @Test
    fun `duplicating keeps the patterns but uses a new id`() = runTest(StandardTestDispatcher()) {
        val repository = repository(this)
        repository.load()
        advanceUntilIdle()
        repository.updateCurrentSong { it.withPattern(3, Pattern.of("D", "x...x...x...x...")) }

        val original = repository.library.value.current()!!
        val copyId = repository.duplicateCurrentSong("コピー")
        advanceUntilIdle()

        val copy = repository.library.value.current()!!
        assertEquals(copyId, copy.id)
        assertFalse(copy.id == original.id)
        assertEquals("コピー", copy.name)
        assertEquals(original.patterns, copy.patterns)
    }
}
