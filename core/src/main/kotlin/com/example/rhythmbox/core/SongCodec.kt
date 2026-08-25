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

    /**
     * 想定外の値（トラック数不足・範囲外の BPM など）を安全な形に整える。
     * 音程まわりを足す前の古い保存データも、ここで今の形に揃える。
     */
    private fun sanitize(library: SongLibrary): SongLibrary {
        val songs = library.songs.map { song ->
            song.copy(
                bpm = song.bpm.coerceIn(Song.MIN_BPM, Song.MAX_BPM),
                masterVolume = song.masterVolume.coerceIn(0f, 1f),
                swing = song.swing.coerceIn(0f, 1f),
                patterns = List(Song.PATTERN_COUNT) { index ->
                    val pattern = song.patterns.getOrNull(index) ?: Pattern.empty(('A' + index).toString())
                    // normalized() が、鳴らないステップに残った強弱も落とす。
                    pattern.normalized().let { fixed ->
                        // 音域の外の音は休符にしておく（壊れたファイル対策）。
                        fixed.withLeads(
                            fixed.leadBars.map { notes ->
                                notes.map {
                                    if (it in MIN_MIDI..MAX_MIDI || it == Pattern.TIE) it else Pattern.REST
                                }
                            },
                        )
                    }
                },
                patternChords = List(Song.PATTERN_COUNT) { index ->
                    sanitizeChord(song.patternChords.getOrNull(index))
                },
                tracks = List(TRACK_COUNT) { index ->
                    val track = song.tracks.getOrNull(index) ?: TrackSetting()
                    track.copy(
                        volume = track.volume.coerceIn(0f, 1f),
                        hold = track.hold.coerceIn(0f, 1f),
                    )
                },
                arrangement = song.arrangement
                    .filter { it.patternIndex in 0 until Song.PATTERN_COUNT }
                    .map { step ->
                        step.copy(
                            repeat = step.repeat.coerceIn(1, PlaybackPlan.MAX_REPEAT),
                            chords = step.chords.map { sanitizeChord(it) },
                        )
                    },
            )
        }
        return library.copy(songs = songs)
    }

    private fun sanitizeChord(chord: Chord?): Chord =
        if (chord == null) Chord() else chord.copy(root = chord.root.mod(12))

    private const val MIN_MIDI = 24
    private const val MAX_MIDI = 108
}
