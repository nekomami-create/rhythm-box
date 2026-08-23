package com.example.rhythmbox.core

import kotlinx.serialization.json.Json

/** 曲データ（ライブラリ）と JSON テキストの相互変換。 */
object SongCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(library: SongLibrary): String = json.encodeToString(SongLibrary.serializer(), library)

    /** 壊れたファイルでもアプリを落とさないよう、読めなければ null を返す。 */
    fun decode(text: String): SongLibrary? = runCatching {
        json.decodeFromString(SongLibrary.serializer(), text)
    }.getOrNull()?.let(::sanitize)

    /** 想定外の値（トラック数不足・範囲外の BPM など）を安全な形に整える。 */
    private fun sanitize(library: SongLibrary): SongLibrary {
        val songs = library.songs.map { song ->
            song.copy(
                bpm = song.bpm.coerceIn(Song.MIN_BPM, Song.MAX_BPM),
                masterVolume = song.masterVolume.coerceIn(0f, 1f),
                patterns = List(Song.PATTERN_COUNT) { index ->
                    song.patterns.getOrNull(index) ?: Pattern.empty(('A' + index).toString())
                },
                tracks = List(VOICE_COUNT) { index ->
                    val track = song.tracks.getOrNull(index) ?: TrackSetting()
                    track.copy(volume = track.volume.coerceIn(0f, 1f))
                },
                arrangement = song.arrangement
                    .filter { it.patternIndex in 0 until Song.PATTERN_COUNT }
                    .map { it.copy(repeat = it.repeat.coerceIn(1, PlaybackPlan.MAX_REPEAT)) },
            )
        }
        return library.copy(songs = songs)
    }
}
