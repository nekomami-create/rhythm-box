package com.example.rhythmbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.rhythmbox.AppContainer
import com.example.rhythmbox.core.ArrangementStep
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.DRUM_COUNT
import com.example.rhythmbox.core.EngineConfig
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 再生モード。パターン単体のループか、曲構成の通し再生か。 */
enum class PlayMode { PATTERN, SONG }

data class RhythmUiState(
    val ready: Boolean = false,
    val song: Song = Song("", ""),
    val library: List<Song> = emptyList(),
    val selectedPattern: Int = 0,
    val mode: PlayMode = PlayMode.PATTERN,
    val loopSong: Boolean = false,
    val isPlaying: Boolean = false,
    /** 鳴っているステップ（0..15、止まっていれば -1）。 */
    val playingStep: Int = -1,
    /** 曲構成のうち鳴っている小節（止まっていれば -1）。 */
    val playingBar: Int = -1,
) {
    val pattern: Pattern get() = song.pattern(selectedPattern)

    /** 編集中のパターンを試聴するときのコード。 */
    val patternChord: Chord get() = song.patternChord(selectedPattern)
}

class RhythmViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.engine
    private val audio = container.audioOutput
    private val repository = container.songRepository

    private val _uiState = MutableStateFlow(RhythmUiState())
    val uiState: StateFlow<RhythmUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null

    init {
        audio.onPlaybackFinished = {
            // 音声スレッドから呼ばれるのでメインに戻してから状態を更新する。
            viewModelScope.launch { handlePlaybackFinished() }
        }
        viewModelScope.launch {
            repository.load()
        }
        viewModelScope.launch {
            repository.library.collect { library ->
                val song = library.current() ?: return@collect
                _uiState.update {
                    it.copy(
                        ready = true,
                        song = song,
                        library = library.songs,
                        selectedPattern = it.selectedPattern.coerceIn(song.patterns.indices),
                    )
                }
                syncEngine()
            }
        }
    }

    // --- 画面ライフサイクル -------------------------------------------------

    fun onScreenResumed() {
        audio.resume()
    }

    fun onScreenPaused() {
        stop()
        audio.pause()
        viewModelScope.launch { repository.saveNow() }
    }

    // --- 再生 ---------------------------------------------------------------

    fun play(mode: PlayMode) {
        val state = _uiState.value
        if (mode == PlayMode.SONG && state.song.arrangement.isEmpty()) return
        _uiState.update { it.copy(mode = mode, isPlaying = true) }
        syncEngine()
        audio.resume()
        engine.start()
        startPositionUpdates()
    }

    fun stop() {
        engine.stop()
        positionJob?.cancel()
        positionJob = null
        _uiState.update { it.copy(isPlaying = false, playingStep = -1, playingBar = -1) }
    }

    fun toggle(mode: PlayMode) {
        val state = _uiState.value
        if (state.isPlaying && state.mode == mode) stop() else play(mode)
    }

    fun setLoopSong(loop: Boolean) {
        _uiState.update { it.copy(loopSong = loop) }
        syncEngine()
    }

    /** グリッドの行（ドラム / コード / ベース）を単発で試聴する。 */
    fun previewRow(row: Int) {
        audio.resume()
        when (row) {
            ROW_CHORD -> engine.previewChord(_uiState.value.patternChord)
            ROW_BASS -> engine.previewNote(Instrument.BASS, _uiState.value.patternChord.bassMidi())
            else -> if (row < DRUM_COUNT) engine.trigger(row)
        }
    }

    fun previewChord(chord: Chord) {
        audio.resume()
        engine.previewChord(chord)
    }

    fun previewLead(midi: Int) {
        audio.resume()
        engine.previewNote(Instrument.LEAD, midi)
    }

    private fun handlePlaybackFinished() {
        positionJob?.cancel()
        positionJob = null
        _uiState.update { it.copy(isPlaying = false, playingStep = -1, playingBar = -1) }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val position = audio.currentPosition()
                _uiState.update {
                    it.copy(
                        playingStep = position?.step ?: -1,
                        playingBar = position?.bar ?: -1,
                    )
                }
                delay(POSITION_POLL_MS)
            }
        }
    }

    /** UI の状態を音声エンジンに反映する。編集中でも音は止めない。 */
    private fun syncEngine() {
        val state = _uiState.value
        val song = state.song
        if (song.patterns.isEmpty()) return
        val plan = when (state.mode) {
            PlayMode.PATTERN -> PlaybackPlan.single(song, state.selectedPattern)
            PlayMode.SONG -> PlaybackPlan.arrangement(song)
        }
        engine.config = EngineConfig(
            plan = plan,
            bpm = song.bpm,
            masterVolume = song.masterVolume,
            trackVolumes = song.tracks.map { it.volume },
            mutes = song.tracks.map { it.muted },
            loop = state.mode == PlayMode.PATTERN || state.loopSong,
        )
    }

    // --- パターン編集 -------------------------------------------------------

    fun selectPattern(index: Int) {
        _uiState.update { it.copy(selectedPattern = index.coerceIn(it.song.patterns.indices)) }
        syncEngine()
    }

    fun toggleStep(row: Int, step: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val turningOn = !state.song.pattern(index).isOn(row, step)
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).toggle(row, step))
        }
        if (turningOn && !state.isPlaying) previewRow(row)
    }

    /** ピアノロールの 1 マス。同じ音を押し直したら消す。 */
    fun toggleLead(step: Int, midi: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val current = state.song.pattern(index).leadAt(step)
        val next = if (current == midi) Pattern.REST else midi
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withLead(step, next))
        }
        if (next != Pattern.REST && !state.isPlaying) previewLead(midi)
    }

    fun clearLead() {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPattern(index, song.pattern(index).clearLead()) }
    }

    fun clearPattern() {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPattern(index, song.pattern(index).cleared()) }
    }

    fun clearRow(row: Int) {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).clearRow(row))
        }
    }

    /** 選択中のパターンを別のパターンへコピーする（コードも一緒に持っていく）。 */
    fun copyPatternTo(target: Int) {
        val source = _uiState.value.selectedPattern
        if (source == target) return
        repository.updateCurrentSong { song ->
            song.withPattern(target, song.pattern(source).copy(name = song.pattern(target).name))
                .withPatternChord(target, song.patternChord(source))
        }
        selectPattern(target)
    }

    /** パターンを単体で鳴らすときのコード。 */
    fun setPatternChord(chord: Chord) {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPatternChord(index, chord) }
        if (!_uiState.value.isPlaying) previewChord(chord)
    }

    // --- 音量・テンポ -------------------------------------------------------

    fun setBpm(bpm: Int) {
        val clamped = bpm.coerceIn(Song.MIN_BPM, Song.MAX_BPM)
        repository.updateCurrentSong { it.copy(bpm = clamped) }
    }

    fun setMasterVolume(volume: Float) {
        repository.updateCurrentSong { it.copy(masterVolume = volume.coerceIn(0f, 1f)) }
    }

    fun setTrackVolume(track: Int, volume: Float) {
        repository.updateCurrentSong { song ->
            song.withTrack(track, song.track(track).copy(volume = volume.coerceIn(0f, 1f)))
        }
    }

    fun toggleMute(track: Int) {
        repository.updateCurrentSong { song ->
            song.withTrack(track, song.track(track).copy(muted = !song.track(track).muted))
        }
    }

    fun unmuteAll() {
        repository.updateCurrentSong { song ->
            song.copy(tracks = song.tracks.map { it.copy(muted = false) })
        }
    }

    // --- 曲構成 -------------------------------------------------------------

    fun addArrangementStep(patternIndex: Int) {
        repository.updateCurrentSong { song ->
            val chord = song.patternChord(patternIndex)
            song.copy(
                arrangement = song.arrangement + ArrangementStep(patternIndex, 1, listOf(chord)),
            )
        }
    }

    fun setArrangementRepeat(index: Int, repeat: Int) {
        repository.updateCurrentSong { song ->
            val next = song.arrangement.toMutableList()
            if (index !in next.indices) return@updateCurrentSong song
            val fallback = song.patternChord(next[index].patternIndex)
            next[index] = next[index]
                .copy(repeat = repeat.coerceIn(1, PlaybackPlan.MAX_REPEAT))
                .withChordSlots(fallback)
            song.copy(arrangement = next)
        }
    }

    fun setArrangementPattern(index: Int, patternIndex: Int) {
        repository.updateCurrentSong { song ->
            val next = song.arrangement.toMutableList()
            if (index !in next.indices) return@updateCurrentSong song
            next[index] = next[index].copy(patternIndex = patternIndex)
            song.copy(arrangement = next)
        }
    }

    /** 曲構成の [index] 番目のブロックの、[barInBlock] 小節目のコードを変える。 */
    fun setArrangementChord(index: Int, barInBlock: Int, chord: Chord) {
        repository.updateCurrentSong { song ->
            val next = song.arrangement.toMutableList()
            if (index !in next.indices) return@updateCurrentSong song
            val fallback = song.patternChord(next[index].patternIndex)
            next[index] = next[index].withChord(barInBlock, chord, fallback)
            song.copy(arrangement = next)
        }
        if (!_uiState.value.isPlaying) previewChord(chord)
    }

    fun moveArrangementStep(index: Int, offset: Int) {
        repository.updateCurrentSong { song ->
            val next = song.arrangement.toMutableList()
            val target = index + offset
            if (index !in next.indices || target !in next.indices) return@updateCurrentSong song
            next.add(target, next.removeAt(index))
            song.copy(arrangement = next)
        }
    }

    fun removeArrangementStep(index: Int) {
        repository.updateCurrentSong { song ->
            if (index !in song.arrangement.indices) return@updateCurrentSong song
            song.copy(arrangement = song.arrangement.filterIndexed { i, _ -> i != index })
        }
    }

    // --- 曲の管理 -----------------------------------------------------------

    fun renameSong(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        repository.updateCurrentSong { it.copy(name = trimmed) }
    }

    fun createSong(name: String) {
        stop()
        repository.createSong(name.trim().ifEmpty { "新しい曲" })
    }

    fun duplicateSong() {
        stop()
        val current = _uiState.value.song
        repository.duplicateCurrentSong("${current.name} のコピー")
    }

    fun selectSong(id: String) {
        stop()
        repository.selectSong(id)
    }

    fun deleteSong(id: String) {
        stop()
        repository.deleteSong(id)
    }

    override fun onCleared() {
        audio.onPlaybackFinished = null
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_MS = 24L

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RhythmViewModel(container) }
        }
    }
}
