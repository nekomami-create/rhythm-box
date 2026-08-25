package com.example.rhythmbox.data

import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SongCodec
import com.example.rhythmbox.core.SongLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 曲データを 1 つの JSON ファイルに保存する。
 * 編集のたびに書き込むと重いので、少し待ってからまとめて保存する。
 */
class SongRepository(
    private val file: File,
    private val scope: CoroutineScope,
    /** ファイル入出力を行うディスパッチャ（テストでは仮想時間のものに差し替える）。 */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _library = MutableStateFlow(SongLibrary())
    val library: StateFlow<SongLibrary> = _library.asStateFlow()

    private val writeMutex = Mutex()
    private var saveJob: Job? = null

    /** 起動時の読み込み。ファイルが無い／壊れている場合は新しい曲を 1 つ用意する。 */
    suspend fun load() {
        val loaded = withContext(ioDispatcher) {
            if (!file.exists()) return@withContext null
            val text = runCatching { file.readText() }.getOrNull() ?: return@withContext null
            val decoded = SongCodec.decode(text)
            if (decoded == null) {
                // 読めないファイルは上書きせずに退避しておく。
                runCatching { file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true) }
            }
            decoded
        }
        _library.value = loaded?.takeIf { it.songs.isNotEmpty() } ?: newLibrary()
        if (loaded == null) saveNow()
    }

    /** 現在編集中の曲を差し替える。 */
    fun updateCurrentSong(transform: (Song) -> Song) {
        val current = _library.value.current() ?: return
        val updated = transform(current).copy(updatedAt = now())
        _library.value = _library.value.replace(updated)
        scheduleSave()
    }

    fun selectSong(id: String) {
        if (_library.value.songs.none { it.id == id }) return
        _library.value = _library.value.copy(currentId = id)
        scheduleSave()
    }

    fun createSong(name: String): String {
        val song = Song.newSong(UUID.randomUUID().toString(), name, now())
        _library.value = _library.value.replace(song)
        scheduleSave()
        return song.id
    }

    /** 現在の曲を複製する（別アレンジを試すとき用）。 */
    fun duplicateCurrentSong(name: String): String? {
        val current = _library.value.current() ?: return null
        val copy = current.copy(id = UUID.randomUUID().toString(), name = name, updatedAt = now())
        _library.value = _library.value.replace(copy)
        scheduleSave()
        return copy.id
    }

    /**
     * 読み込んだ曲をライブラリに足す。
     *
     * id は必ず作り直す。書き出した本人が読み込んだときに、
     * 同じ id の曲を上書きしてしまわないようにするため。
     * 同じ名前がすでにあれば、末尾に番号を付けて区別する。
     */
    fun addSong(song: Song): String {
        val taken = _library.value.songs.map { it.name }.toSet()
        var name = song.name.ifBlank { "読み込んだ曲" }
        var suffix = 2
        while (name in taken) {
            name = "${song.name} ($suffix)"
            suffix++
        }
        val added = song.copy(id = UUID.randomUUID().toString(), name = name, updatedAt = now())
        _library.value = _library.value.replace(added)
        scheduleSave()
        return added.id
    }

    fun deleteSong(id: String) {
        val next = _library.value.remove(id)
        _library.value = if (next.songs.isEmpty()) newLibrary() else next
        scheduleSave()
    }

    private fun newLibrary(): SongLibrary {
        val song = Song.newSong(UUID.randomUUID().toString(), "はじめての曲", now())
        return SongLibrary(songs = listOf(song), currentId = song.id)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    /** 画面を離れるときなど、確実に書き込みたいときに使う。 */
    suspend fun saveNow() {
        val snapshot = _library.value
        withContext(ioDispatcher) {
            writeMutex.withLock {
                runCatching {
                    file.parentFile?.mkdirs()
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(SongCodec.encode(snapshot))
                    if (!temp.renameTo(file)) {
                        file.writeText(SongCodec.encode(snapshot))
                        temp.delete()
                    }
                }
            }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
