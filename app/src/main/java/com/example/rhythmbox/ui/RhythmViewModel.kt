package com.example.rhythmbox.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.rhythmbox.AppContainer
import com.example.rhythmbox.core.ArpeggioSpeed
import com.example.rhythmbox.core.ArrangementStep
import com.example.rhythmbox.core.BassStyle
import com.example.rhythmbox.core.CHANNELS
import com.example.rhythmbox.core.Bar
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.ChordCruiser
import com.example.rhythmbox.core.ChordPads
import com.example.rhythmbox.core.ChordStyle
import com.example.rhythmbox.core.ChordVoicing
import com.example.rhythmbox.core.chordStepOf
import com.example.rhythmbox.core.ChordSuggester
import com.example.rhythmbox.core.ChordSuggestion
import com.example.rhythmbox.core.DRUM_COUNT
import com.example.rhythmbox.core.DrumKit
import com.example.rhythmbox.core.EngineConfig
import com.example.rhythmbox.core.GameScene
import com.example.rhythmbox.core.Genre
import com.example.rhythmbox.core.GenreRecipe
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.MelodyDensity
import com.example.rhythmbox.core.MelodyGenerator
import com.example.rhythmbox.core.MidiExporter
import com.example.rhythmbox.core.MusicKey
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.PadRecorder
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.RoomSize
import com.example.rhythmbox.core.PatternGenerator
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.RhythmStyle
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SongEditor
import com.example.rhythmbox.core.SongBuilder
import com.example.rhythmbox.core.SongCodec
import com.example.rhythmbox.core.SoundSet
import com.example.rhythmbox.core.ToneSynth
import com.example.rhythmbox.core.Transposer
import com.example.rhythmbox.core.formatDuration
import com.example.rhythmbox.core.secondsPerStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 再生モード。
 * [PATTERN] は選んでいるパターン 1 小節のループ、[CHAIN] は中身のあるパターンを
 * A→B→C… と 1 小節ずつ繋げたループ、[SONG] は曲構成の通し再生。
 */
enum class PlayMode { PATTERN, CHAIN, SONG }

/** ジャンルを当てはめるときに、どこまで書き換えるか。 */
data class GenreOptions(
    val tempo: Boolean = true,
    val chords: Boolean = true,
    val rhythm: Boolean = true,
    val melody: Boolean = false,
    /** 音色。チップ音源のジャンルでだけ意味がある。 */
    val sound: Boolean = true,
)

/** 音声ファイルに書き出す範囲。 */
enum class ExportScope(val label: String) {
    SONG("曲構成をそのまま"),
    CHAIN("チェーンを繰り返し"),
    PATTERN("このパターンを繰り返し"),
}

data class RhythmUiState(
    val ready: Boolean = false,
    val song: Song = Song("", ""),
    val library: List<Song> = emptyList(),
    val selectedPattern: Int = 0,
    val mode: PlayMode = PlayMode.PATTERN,
    val loopSong: Boolean = true,
    val isPlaying: Boolean = false,
    /** 鳴っているステップ（0..15、止まっていれば -1）。 */
    val playingStep: Int = -1,
    /** 曲構成のうち鳴っている小節（止まっていれば -1）。 */
    val playingBar: Int = -1,
    /** いま鳴っているパターン（止まっていれば -1）。チェーン再生の表示に使う。 */
    val playingPattern: Int = -1,
    /** いま鳴っているパターンの何小節目か（繰り返し何回目か）。止まっていれば -1。 */
    val playingPatternBar: Int = -1,
    /** グリッドとピアノロールで編集している、パターンの中の小節。 */
    val selectedBar: Int = 0,
    /**
     * 鳴っているところに画面を合わせるか（本人の設定）。
     *
     * チェーンや曲を流しているときは、鳴っているパターン・小節が次々に変わる。
     * 合わせておかないと、聴いている音と画面に出ているものが別物になる。
     */
    val followPlayback: Boolean = true,
    /**
     * 追従を一時的に止めているか。
     *
     * 流しながら別の小節を開いたら、そこを見たいということなので追いかけない。
     * ただしそれは「今この曲を見たい」だけなので、次に再生を始めたら戻す。
     * 本人が切ったのか、たまたま別の場所を開いているだけなのかを混ぜないための区別。
     */
    val followSuspended: Boolean = false,
    /** 自動生成の直前の状態に戻せるか。 */
    val canUndo: Boolean = false,
    /** あと何段戻せるか。ボタンに出して、どこまで遡れるか分かるようにする。 */
    val undoDepth: Int = 0,
    /** パッドで叩いたものを打ち込みに書いているか。 */
    val padRecording: Boolean = false,
    /** パッドの音を強く置くか。 */
    val padAccent: Boolean = false,
    /** パッドでドラムを叩くか、コードを弾くか。 */
    val padMode: PadMode = PadMode.DRUM,
    /** メトロノームを鳴らすか。曲には残らない、叩くときの目印。 */
    val metronome: Boolean = false,
    /**
     * 起承転結・終わりでコードを作り直したとき、旋律も一緒に作り直すか。
     * 既定は切（手で書いた旋律を勝手に消さない）。
     */
    val followMelody: Boolean = false,
    /**
     * コードクルーザーで捏ねている進行。空なら開いていない。
     * 曲には入っていない（「差し込む」まで、いじっても曲は変わらない）。
     */
    val cruise: List<Chord> = emptyList(),
    /** クルーザーが差し込む先の、曲構成のブロック番号。 */
    val cruiseBlock: Int = -1,
    /** いま流し込んでいる種の名前。 */
    val cruiseSeed: String = "",
    /** クルーザーの試聴を鳴らしているか。 */
    val cruisePlaying: Boolean = false,
    /** リズムの「ランダム」が書き換える範囲。 */
    val rhythmScope: GenerateScope = GenerateScope.PATTERN,
    /** 旋律の「ランダム」が書き換える範囲。 */
    val leadScope: GenerateScope = GenerateScope.PATTERN,
    /** ピアノロールの長押しが「伸ばす」か「強弱」か。曲には残らない道具の設定。 */
    val leadHoldMode: LeadHoldMode = LeadHoldMode.STRETCH,
    /** 書き出し中の進捗（0.0〜1.0）。書き出していなければ null。 */
    val exportProgress: Float? = null,
    /** 書き出しが終わったときに出す文言。 */
    val exportMessage: String? = null,
) {
    val pattern: Pattern get() = song.pattern(selectedPattern)

    /** いま実際に追従しているか。画面のスイッチもこれを出す。 */
    val following: Boolean get() = followPlayback && !followSuspended

    /** チェーン再生で回すパターン。中身のあるものを A→H の順に並べる。 */
    val chain: List<Int>
        get() = song.patterns.indices
            .filter { !song.pattern(it).isEmpty() }
            .ifEmpty { listOf(selectedPattern) }

    /** "A→B→C" のような表示用のラベル。長くなりすぎたら省略する。 */
    val chainLabel: String
        get() = if (chain.size <= 5) {
            chain.joinToString("→") { song.pattern(it).name }
        } else {
            chain.take(4).joinToString("→") { song.pattern(it).name } + "→…"
        }

    /**
     * グリッドで光らせるステップ。
     * 今そのパターンの、今開いている小節が鳴っているときだけ光る。
     * （複数小節のパターンでは、別の小節を鳴らしている間に光ると位置を見誤る）
     */
    val gridStep: Int
        get() = if (isPlaying && onScreenNow) playingStep else -1

    /** ピアノロールで光らせるステップ。グリッドと同じ条件。 */
    val leadGridStep: Int get() = gridStep

    /**
     * いま鳴っている小節の打ち込み。止まっていれば開いている小節。
     * パッドを今の拍で光らせるのに使う（複数小節のパターンでは小節ごとに中身が違う）。
     */
    val soundingPattern: Pattern
        get() = pattern.at(if (playingPatternBar >= 0) playingPatternBar else selectedBar)

    /** 今まさに画面に出している小節が鳴っているか。 */
    private val onScreenNow: Boolean
        get() = (mode == PlayMode.PATTERN || playingPattern == selectedPattern) &&
            (pattern.barCount == 1 || playingPatternBar == selectedBar)

    /** いま何をどう回しているかの説明（再生ボタンの下に出す）。 */
    val scopeLabel: String
        get() = when (mode) {
            PlayMode.PATTERN -> "パターン ${pattern.name} を 1 小節ループ"
            PlayMode.CHAIN -> "チェーン $chainLabel を" + if (loopSong) "ループ" else "1 回だけ"
            PlayMode.SONG -> "曲を通しで" + if (loopSong) "ループ" else "1 回だけ"
        }

    /** 編集中のパターンを試聴するときのコード。 */
    val patternChord: Chord get() = song.patternChord(selectedPattern)
}

class RhythmViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.engine
    private val audio = container.audioOutput
    private val repository = container.songRepository
    private val keepAlive = container.keepAlive

    private val _uiState = MutableStateFlow(RhythmUiState())
    val uiState: StateFlow<RhythmUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null

    /** 今エンジンに渡しているプラン。鳴っているパターンを割り出すのに使う。 */
    private var currentPlan: PlaybackPlan? = null

    /** 自動生成をやり直すための、直前の曲まるごとの控え。 */
    /**
     * 自動生成の前の曲を、新しいものから順に積んでおく。
     * 1 段しか戻せないと「2 回押してしまった」だけで元に戻せなくなる。
     */
    private val undoStack = ArrayDeque<Song>()

    init {
        audio.onPlaybackFinished = {
            // 音声スレッドから呼ばれるのでメインに戻してから状態を更新する。
            viewModelScope.launch { handlePlaybackFinished() }
        }
        // 通知の「停止」から止められるようにする。音を持っているのはこちらなので、
        // サービスは押されたことを伝えてくるだけ。
        keepAlive.onStopRequested = {
            viewModelScope.launch { stop() }
        }
        viewModelScope.launch {
            repository.load()
        }
        viewModelScope.launch {
            repository.library.collect { library ->
                val song = library.current() ?: return@collect
                _uiState.update {
                    val pattern = it.selectedPattern.coerceIn(song.patterns.indices)
                    it.copy(
                        ready = true,
                        song = song,
                        library = library.songs,
                        selectedPattern = pattern,
                        selectedBar = it.selectedBar
                            .coerceIn(0, song.pattern(pattern).barCount - 1),
                    )
                }
                syncEngine()
            }
        }
    }

    // --- 画面ライフサイクル -------------------------------------------------

    fun onScreenResumed() {
        audio.resume()
        // 画面を消しているあいだは位置の更新を止めてある。見る人が戻ったら再開する。
        if (_uiState.value.isPlaying && positionJob == null) startPositionUpdates()
    }

    fun onScreenPaused() {
        viewModelScope.launch { repository.saveNow() }
        if (_uiState.value.isPlaying) {
            // 鳴っている間は音声スレッドを畳まない（前面サービスがプロセスを保つ）。
            // 見えていない画面のために位置を数え続ける必要は無いので、そこだけ止める。
            positionJob?.cancel()
            positionJob = null
            return
        }
        audio.pause()
    }

    // --- 再生 ---------------------------------------------------------------

    fun play(mode: PlayMode) {
        val state = _uiState.value
        if (mode == PlayMode.SONG && state.song.arrangement.isEmpty()) return
        if (mode == PlayMode.CHAIN && state.chain.isEmpty()) return
        // 鳴らし始めるのは「聴きたい」ということなので、
        // 別の小節を開いていたぶんの一時停止はここで解く（本人が切った設定は残す）。
        _uiState.update { it.copy(mode = mode, isPlaying = true, followSuspended = false) }
        syncEngine()
        audio.resume()
        engine.start()
        // 画面を消してもプロセスを畳ませない。通知から止められるようにもなる。
        keepAlive.start(state.song.name)
        startPositionUpdates()
    }

    fun stop() {
        engine.stop()
        clearPlayingState()
    }

    /** 鳴っていない状態に戻す。自分で止めても自然に終わっても、後始末は同じ。 */
    private fun clearPlayingState() {
        keepAlive.stop()
        positionJob?.cancel()
        positionJob = null
        _uiState.update {
            it.copy(
                isPlaying = false,
                playingStep = -1,
                playingBar = -1,
                playingPattern = -1,
                playingPatternBar = -1,
            )
        }
    }

    fun toggle(mode: PlayMode) {
        val state = _uiState.value
        if (state.isPlaying && state.mode == mode) stop() else play(mode)
    }

    fun setLoopSong(loop: Boolean) {
        _uiState.update { it.copy(loopSong = loop) }
        syncEngine()
    }

    /**
     * 叩く直前に、音声スレッドが回っていることだけ確かめる。
     *
     * resume() と pause() は同じ錠を取る。pause() は音声スレッドの
     * 終了を最大 500 ms 待つので、止める処理と重なったところで叩くと、
     * 錠が空くまで UI スレッドごと止まる。すでに回っているなら
     * 錠に触る必要はないので、先に見てから呼ぶ。
     */
    private fun ensureAudio() {
        if (!audio.isRunning) audio.resume()
    }

    /** グリッドの行（ドラム / コード / ベース）を単発で試聴する。 */
    fun previewRow(row: Int) {
        ensureAudio()
        when (row) {
            ROW_CHORD -> engine.previewChord(_uiState.value.patternChord)
            ROW_BASS -> engine.previewNote(Instrument.BASS, _uiState.value.patternChord.bassMidi())
            else -> if (row < DRUM_COUNT) engine.trigger(row)
        }
    }

    fun previewChord(chord: Chord) {
        ensureAudio()
        engine.previewChord(chord)
    }

    fun previewLead(midi: Int) {
        ensureAudio()
        engine.previewNote(Instrument.LEAD, midi)
    }

    // --- パッド演奏 ---------------------------------------------------------

    /**
     * パッドを叩いた。まず必ず音を出す（演奏がいちばんの目的なので、
     * 録音していてもいなくても手応えは同じにする）。
     * 録音中なら、いま鳴っている位置に合わせて打ち込みへ書く。
     */
    fun padHit(row: Int) {
        val state = _uiState.value
        previewRow(row)
        if (!state.padRecording) return
        val step = recordStep() ?: return
        val level = if (state.padAccent) Pattern.Level.ACCENT else Pattern.Level.NORMAL
        val index = state.selectedPattern
        // 叩いた瞬間に鳴っていた小節に置く。複数小節のパターンでも、
        // 耳で聞いていたところにそのまま入る。
        val bar = state.playingPatternBar.takeIf { it >= 0 } ?: state.selectedBar
        repository.updateCurrentSong { song ->
            val pattern = song.pattern(index)
            song.withPattern(index, pattern.withRhythmAt(bar, PadRecorder.record(pattern.at(bar), row, step, level)))
        }
    }

    /** パッドに並べる 12 個。自分で決めていなければ調から作る。 */
    fun chordPads(): List<Chord> =
        ChordPads.resolve(_uiState.value.song.chordPads, detectedKey())

    fun setPadMode(mode: PadMode) {
        _uiState.update { it.copy(padMode = mode) }
    }

    /** [index] のパッドに別の和音を割り当てる。 */
    fun setChordPad(index: Int, chord: Chord) {
        if (index !in 0 until ChordPads.COUNT) return
        val pads = chordPads()
        repository.updateCurrentSong { song ->
            song.copy(chordPads = List(ChordPads.COUNT) { if (it == index) chord else pads[it] })
        }
    }

    /** パッドの並びを、いまの調の既定に戻す。 */
    fun resetChordPads() {
        snapshotForUndo()
        repository.updateCurrentSong { it.copy(chordPads = emptyList()) }
    }

    /**
     * コードパッドを叩いた。まず鳴らし、録音中なら曲に書く。
     *
     * コードは「小節に 1 つ」なので、ステップごとには置けない。
     * 鳴っている小節のコードを差し替え、あわせて CHD 行のその位置を鳴らす形にする。
     * 曲を流しながら順に叩けば、そのまま進行が入っていく。
     */
    fun chordPadHit(index: Int) {
        val chord = chordPads().getOrNull(index) ?: return
        ensureAudio()
        engine.previewChord(chord)
        if (!_uiState.value.padRecording) return

        val frame = audio.currentFrame() ?: return
        val position = engine.timeline.positionAt(frame) ?: return
        val step = PadRecorder.stepAt(
            step = position.step,
            stepFrame = position.frame,
            hitFrame = frame,
            framesPerStep = engine.framesPerStep(_uiState.value.song.bpm),
        )
        // 書き込む先は「いま鳴っている小節」。選んでいるパターンとは限らない。
        val plan = currentPlan ?: return
        val bar = plan.barAt(position.bar)
        val level = if (_uiState.value.padAccent) Pattern.Level.ACCENT else Pattern.Level.NORMAL
        repository.updateCurrentSong { song ->
            val withStep = song.withPattern(
                bar.patternIndex,
                PadRecorder.record(song.pattern(bar.patternIndex), ROW_CHORD, step, level),
            )
            placeChord(withStep, position.bar, chord)
        }
    }

    /**
     * [barIndex] 小節目のコードを [chord] にする。
     * 曲構成があればその小節を、無ければパターンのコードを差し替える。
     */
    private fun placeChord(song: Song, barIndex: Int, chord: Chord): Song {
        if (song.arrangement.isEmpty()) {
            return song.withPatternChord(_uiState.value.selectedPattern, chord)
        }
        // 曲構成はブロックの並びなので、頭から小節を数えて該当のブロックを探す。
        var first = 0
        val arrangement = song.arrangement.map { step ->
            val slots = step.repeat.coerceAtLeast(1)
            val inBlock = barIndex - first
            first += slots
            if (inBlock in 0 until slots) {
                step.withChord(inBlock, chord, song.patternChord(step.patternIndex))
            } else {
                step
            }
        }
        return song.copy(arrangement = arrangement)
    }

    /** いま叩いた瞬間が、打ち込みのどのステップにあたるか。止まっていれば null。 */
    private fun recordStep(): Int? {
        val frame = audio.currentFrame() ?: return null
        val position = engine.timeline.positionAt(frame) ?: return null
        return PadRecorder.stepAt(
            step = position.step,
            stepFrame = position.frame,
            hitFrame = frame,
            framesPerStep = engine.framesPerStep(_uiState.value.song.bpm),
        )
    }

    /**
     * 録音の開始と終了。
     *
     * 叩く位置を決めるには何かが鳴っていないといけないので、
     * 止まっていればパターンのループを始めてから録音に入る。
     */
    fun togglePadRecording() {
        val state = _uiState.value
        if (state.padRecording) {
            _uiState.update { it.copy(padRecording = false) }
            return
        }
        // 1 回の録音まるごとを「戻す」で取り消せるように、始める前に控える。
        snapshotForUndo()
        if (!state.isPlaying || state.mode != PlayMode.PATTERN) {
            play(PlayMode.PATTERN)
        }
        _uiState.update { it.copy(padRecording = true) }
    }

    /** メトロノームの入り切り。曲には保存しない（端末での作業中の設定）。 */
    fun toggleMetronome() {
        _uiState.update { it.copy(metronome = !it.metronome) }
        syncEngine()
    }

    /** 叩いた音を強くするか。押しっぱなしのつまみではなく、切り替えにしてある。 */
    fun togglePadAccent() {
        _uiState.update { it.copy(padAccent = !it.padAccent) }
    }

    /** いま選んでいるパターンの打ち込みを消す（旋律は残す）。 */
    fun clearPadTake() {
        val index = _uiState.value.selectedPattern
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).clearedRhythm())
        }
    }

    private fun handlePlaybackFinished() {
        clearPlayingState()
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val position = audio.currentPosition()
                val bar = position?.let { currentPlan?.bars?.getOrNull(it.bar) }
                _uiState.update { state ->
                    // 曲構成では、パターンの長さを超える回数ぶん並べられる（2 小節の
                    // パターンを 8 小節ぶん、など）。折り返した番号のほうが画面と噛み合う。
                    val patternBar = bar
                        ?.let { it.patternBar.mod(state.song.pattern(it.patternIndex).barCount) }
                        ?: -1
                    val moved = state.copy(
                        playingStep = position?.step ?: -1,
                        playingBar = position?.bar ?: -1,
                        playingPattern = bar?.patternIndex ?: -1,
                        playingPatternBar = patternBar,
                    )
                    if (bar == null || !state.following) {
                        moved
                    } else {
                        moved.copy(
                            selectedPattern = bar.patternIndex.coerceIn(state.song.patterns.indices),
                            selectedBar = patternBar,
                        )
                    }
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
        // 試聴中は、捏ねている進行のほうを鳴らす。曲には入っていないので、
        // ここで差し込まないと聴きようがない。
        val plan = cruisePlan(state) ?: when (state.mode) {
            PlayMode.PATTERN -> PlaybackPlan.single(song, state.selectedPattern)
            PlayMode.CHAIN -> PlaybackPlan.chain(song, state.chain)
            PlayMode.SONG -> PlaybackPlan.arrangement(song)
        }
        currentPlan = plan
        engine.config = EngineConfig(
            plan = plan,
            bpm = song.bpm,
            masterVolume = song.masterVolume,
            trackVolumes = song.tracks.map { it.volume },
            mutes = song.tracks.map { it.muted },
            trackPans = song.tracks.map { it.pan },
            holds = song.tracks.map { it.hold },
            swing = song.swing,
            chordStyle = song.chordStyle,
            leadVoice = song.leadVoice,
            leadVibrato = song.leadVibrato,
            drumKit = song.drumKit,
            soundSet = song.soundSet,
            arpeggioSpeed = song.arpeggioSpeed,
            reverb = song.reverb,
            roomSize = song.roomSize,
            chordVoicing = song.chordVoicing,
            bassStyle = song.bassStyle,
            metronome = state.metronome,
            loop = state.mode == PlayMode.PATTERN || state.loopSong,
        )
    }

    // --- パターン編集 -------------------------------------------------------

    fun selectPattern(index: Int) {
        clearUndo()
        _uiState.update {
            it.copy(
                selectedPattern = index.coerceIn(it.song.patterns.indices),
                selectedBar = 0,
                followSuspended = true,
            )
        }
        syncEngine()
    }

    fun toggleStep(row: Int, step: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        val turningOn = !state.song.pattern(index).at(bar).isOn(row, step)
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).toggleAt(bar, row, step))
        }
        if (turningOn && !state.isPlaying) previewRow(row)
    }

    /**
     * ステップの強さを 普通 → 強 → 弱 → 普通 と巡回させる（長押し）。
     * タップは今までどおり ON/OFF なので、打ち込みの手順は変わらない。
     */
    fun cycleStepLevel(row: Int, step: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        if (!state.song.pattern(index).at(bar).isOn(row, step)) return
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).cycleLevelAt(bar, row, step))
        }
        if (!state.isPlaying) previewRow(row)
    }

    // --- リード（旋律） -----------------------------------------------------

    /** 編集する小節（パターンの中の何小節目か）を選ぶ。 */
    fun selectBar(bar: Int) {
        _uiState.update {
            it.copy(
                selectedBar = bar.coerceIn(0, it.pattern.barCount - 1),
                followSuspended = true,
            )
        }
    }

    /** 鳴っているところに画面を合わせるかどうか。押した時点の一時停止も解く。 */
    fun setFollowPlayback(on: Boolean) {
        _uiState.update { it.copy(followPlayback = on, followSuspended = false) }
    }

    /** パターンの長さ（小節数）を変える。 */
    fun setBarCount(count: Int) {
        val index = _uiState.value.selectedPattern
        val target = count.coerceIn(1, Pattern.MAX_BARS)
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withBarCount(target))
        }
        _uiState.update { it.copy(selectedBar = it.selectedBar.coerceIn(0, target - 1)) }
        syncEngine()
    }

    /** ピアノロールの 1 マス。同じ音を押し直したら消す。 */
    fun toggleLead(step: Int, midi: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        val current = state.song.pattern(index).leadAt(bar, step)
        val next = if (current == midi) Pattern.REST else midi
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withLead(bar, step, next))
        }
        if (next != Pattern.REST && !state.isPlaying) previewLead(midi)
    }

    /**
     * ピアノロールの長押し。音を長く伸ばしたり、元の長さに戻したりする。
     *
     * - 音の上を長押し → その音の伸ばしを解除する
     * - 音より右の空きマスを長押し → 直前の音をそこまで伸ばす
     *   （もう一度同じところを押すと元に戻る）
     */
    fun holdLead(step: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        val pattern = state.song.pattern(index)
        val head = pattern.stretchTarget(bar, step)
        if (head < 0) return
        // 長押ししたところが音そのものなら、その音の伸ばしを外す。
        val until = if (head == step) head + pattern.tieRun(bar, head) else step
        if (until <= head) return
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withLeadTie(bar, head, until))
        }
    }

    /** いま選んでいる繰り返しの音だけ消す。 */
    /** ピアノロールの長押しで何をするか。 */
    fun setLeadHoldMode(mode: LeadHoldMode) {
        _uiState.update { it.copy(leadHoldMode = mode) }
    }

    /** 旋律の音の強さを 普通 → 強 → 弱 と巡回させる（長押し）。 */
    fun cycleLeadLevel(step: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        if (!Pattern.isNote(state.song.pattern(index).leadAt(bar, step))) return
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).cycleLeadLevel(bar, step))
        }
    }

    fun clearLeadBar() {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).clearLead(bar))
        }
    }

    /** 旋律を全部消して 1 小節ぶんに戻す。 */
    fun clearLead() {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPattern(index, song.pattern(index).clearAllLeads()) }
        _uiState.update { it.copy(selectedBar = 0) }
    }

    /**
     * 旋律を自動生成する。持っている小節数ぶんまとめて作り、
     * それぞれの小節のコードに合わせる（同じ旋律を繰り返すと和音から外れるため）。
     */
    fun generateMelody() {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            when (state.leadScope) {
                GenerateScope.BAR -> song.withPattern(index, melodyBar(song, index, bar))
                GenerateScope.PATTERN -> song.withPattern(index, melodyPattern(song, index))
                // 直前のパターンの続きから書けるよう、書き換えたものを次に渡していく。
                GenerateScope.ALL -> SongEditor.usedPatterns(song, index).fold(song) { acc, target ->
                    acc.withPattern(target, melodyPattern(acc, target))
                }
            }
        }
        if (state.leadScope != GenerateScope.BAR) {
            val bars = melodyBarCount(_uiState.value.song, index)
            _uiState.update { it.copy(selectedBar = it.selectedBar.coerceIn(0, bars - 1)) }
        }
    }

    /** [bar] 小節目だけを書き直したパターン。前後の小節はそのまま残す。 */
    private fun melodyBar(song: Song, index: Int, bar: Int): Pattern {
        val pattern = song.pattern(index)
        val generated = MelodyGenerator.generate(
            chord = leadChords(song, index, bar + 1).last(),
            key = detectedKey(),
            random = Random,
            // ひとつ前の小節の終わりから書き始めるので、繋ぎ目で音が飛ばない。
            previous = if (bar > 0) pattern.leadBars.getOrNull(bar - 1) else null,
            density = MelodyDensity.NORMAL,
        )
        return pattern.withLeads(
            pattern.leadBars.mapIndexed { line, notes -> if (line == bar) generated else notes },
        )
    }

    /** そのパターンの旋律を全小節ぶん書き直したパターン。 */
    private fun melodyPattern(song: Song, index: Int, key: MusicKey = detectedKey()): Pattern {
        val bars = melodyBarCount(song, index)
        val leads = MelodyGenerator.generateBars(
            chords = leadChords(song, index, bars),
            key = key,
            random = Random,
            density = MelodyDensity.NORMAL,
            previous = if (index > 0) song.pattern(index - 1).leadBars.lastOrNull() else null,
        )
        return song.pattern(index).withLeads(leads)
    }

    /**
     * パターン [index] の旋律を何小節ぶん作るか。
     * パターンの長さと、曲構成でそれを鳴らす小節数の長いほうに合わせる。
     */
    private fun melodyBarCount(song: Song, index: Int): Int {
        val block = song.arrangement.firstOrNull { it.patternIndex == index }
        return maxOf(song.pattern(index).barCount, block?.repeat ?: 1)
            .coerceAtMost(Pattern.MAX_BARS)
    }

    /** [bar] 回目の小節で鳴るコード。曲構成で使われていれば、そこのコードを見る。 */
    fun chordForBar(bar: Int): Chord =
        chordAtStep(_uiState.value.song, _uiState.value.selectedPattern, bar, 0)

    /** 選んでいるパターンの [bar] 小節目、[slot] 番目の枠に置いてあるコード。 */
    fun placedChordAt(bar: Int, slot: Int): Chord? =
        _uiState.value.pattern.chordSlotAt(bar, slot)

    /**
     * [bar] 小節目の [slot] 番目の枠の、ひとつ手前で鳴っているコード。
     * 「この流れなら次はこれ」を出すのに使う。
     */
    fun placedChordBefore(bar: Int, slot: Int): Chord? {
        val state = _uiState.value
        if (slot <= 0 && bar <= 0) return null
        val step = chordStepOf(slot) - 1
        return if (step >= 0) {
            chordAtStep(state.song, state.selectedPattern, bar, step)
        } else {
            chordAtStep(state.song, state.selectedPattern, bar - 1, STEPS_PER_BAR - 1)
        }
    }

    /** [bar] 小節目の [slot] 番目の枠にコードを置く。 */
    fun placeChord(bar: Int, slot: Int, chord: Chord) {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withChordAt(bar, chordStepOf(slot), chord))
        }
        if (!_uiState.value.isPlaying) previewChord(chord)
    }

    /** [bar] 小節目の [slot] 番目の枠に置いたコードを外す。 */
    fun clearChordAt(bar: Int, slot: Int) {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withoutChordAt(bar, chordStepOf(slot)))
        }
    }

    /**
     * 置いたコードをすべて外して、コードを曲構成に任せる形に戻す。
     * 1 つでも置いてあるとパターン側が勝つので、戻る道を用意しておく。
     */
    fun clearPlacedChords() {
        val index = _uiState.value.selectedPattern
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withoutChords())
        }
    }

    /**
     * パターン [index] の [bar] 小節目 [step] で、実際に鳴っている和音。
     *
     * 決まる順番は [PlaybackPlan.single] と同じにしてある。打ち込みに置いて
     * あればそれ、無ければそのパターンを使っている曲構成のブロック、
     * それも無ければパターンのコード。ここがずれると、画面に出ている
     * コード名と鳴っている音が食い違う。
     */
    fun chordAtStep(song: Song, index: Int, bar: Int, step: Int): Chord {
        song.pattern(index).chordAt(bar, step)?.let { return it }
        val fallback = song.patternChord(index)
        val block = song.arrangement.firstOrNull { it.patternIndex == index }
        return block?.chordAt(bar, fallback) ?: fallback
    }

    /** パターン [index] を [bars] 小節ぶん鳴らすときの、小節ごとのコード。 */
    private fun leadChords(song: Song, index: Int, bars: Int): List<Chord> =
        List(bars) { chordAtStep(song, index, it, 0) }

    fun clearPattern() {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPattern(index, song.pattern(index).cleared()) }
    }

    /** いま開いている小節の打ち込みだけ消す（旋律とほかの小節は残す）。 */
    fun clearBar() {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withRhythmAt(bar, Pattern.empty("clear")))
        }
    }

    /** いま開いている小節の、その行だけ消す。 */
    fun clearRow(row: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedBar
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).clearRowAt(bar, row))
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

    // --- 自動生成・レコメンド -----------------------------------------------

    /** 曲の調。指定してあればそれを使い、無ければコードから推定する。 */
    fun detectedKey(): MusicKey =
        _uiState.value.song.key ?: ChordSuggester.detectKey(SongEditor.chords(_uiState.value.song))

    /** 調と音階を指定する。null に戻すと、またコードから推定する。 */
    fun setKey(key: MusicKey?) {
        repository.updateCurrentSong { it.copy(key = key) }
    }

    /** 指定が無いときに推定される調（指定ダイアログの初期値に使う）。 */
    fun autoKey(): MusicKey = ChordSuggester.detectKey(SongEditor.chords(_uiState.value.song))

    /**
     * [previous] のあと・[next] の前に置いて馴染むコード。
     * どちらも null なら、その調でよく使うコードを返す。
     */
    fun chordSuggestions(previous: Chord?, next: Chord? = null): List<ChordSuggestion> =
        ChordSuggester.suggest(previous, detectedKey(), next)

    /** 曲構成の [stepIndex] 番目・[barInBlock] 小節目から見た、前後の小節のコード。 */
    fun neighbourChordsInSong(stepIndex: Int, barInBlock: Int): Pair<Chord?, Chord?> {
        val song = _uiState.value.song
        if (stepIndex !in song.arrangement.indices) return null to null
        var absolute = barInBlock
        for (i in 0 until stepIndex) absolute += song.arrangement[i].repeat
        val bars = PlaybackPlan.arrangement(song).bars
        return bars.getOrNull(absolute - 1)?.chord to bars.getOrNull(absolute + 1)?.chord
    }


    fun setRhythmScope(scope: GenerateScope) {
        _uiState.update { it.copy(rhythmScope = scope) }
    }

    fun setLeadScope(scope: GenerateScope) {
        _uiState.update { it.copy(leadScope = scope) }
    }

    /** 「全パターン」で書き換える対象。中身のあるものだけを触り、空のパターンは残す。 */

    /**
     * 1 小節ぶんだけコードを引き直す。前後の小節に馴染むものから重み付きで 1 つ選ぶ。
     * いま入っているコードは候補から外すので、押せば必ず何かが変わる。
     */
    fun shuffleChord(previous: Chord?, next: Chord?, current: Chord): Chord? =
        ChordSuggester.pickOne(
            previous = previous,
            key = detectedKey(),
            next = next,
            random = Random,
            exclude = current,
        )

    /**
     * リズムを自動生成する。[style] が null ならスタイルもおまかせ。リードは触らない。
     *
     * 「この小節」だけ振り直せるので、気に入った小節を残したまま
     * 4 小節目にフィルを入れる、といった作り方ができる。
     * 「このパターン」は 1 つのノリを全小節に置く。小節ごとに違うノリを引くと
     * まとまりが無くなるので、変化を付けるのは小節単位の振り直しに任せている。
     */
    fun generateRhythm(style: RhythmStyle?) {
        val state = _uiState.value
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            // 画面の言葉（この小節 / このパターン / 全パターン）を、
            // 「どのパターンの、どの小節を書き換えるか」に訳してから渡す。
            val targets = when (state.rhythmScope) {
                GenerateScope.ALL -> SongEditor.usedPatterns(song, state.selectedPattern)
                else -> listOf(state.selectedPattern)
            }
            val bar = state.selectedBar.takeIf { state.rhythmScope == GenerateScope.BAR }
            SongEditor.withGeneratedRhythm(song, targets, bar, style, Random)
        }
    }

    // --- コードクルーザー ----------------------------------------------------

    /**
     * 試聴のためのプラン。捏ねている進行を、そのブロックのパターンに乗せて並べる。
     *
     * 曲そのものは書き換えない。ここでプランだけ作って鳴らせば、
     * 差し込む前に「今のドラムとベースの上でどう聞こえるか」が分かる。
     */
    private fun cruisePlan(state: RhythmUiState): PlaybackPlan? {
        if (!state.cruisePlaying || state.cruise.isEmpty()) return null
        val song = state.song
        val index = (song.arrangement.getOrNull(state.cruiseBlock)?.patternIndex ?: state.selectedPattern)
            .coerceIn(song.patterns.indices)
        // 声部の繋がりはプランが自分で解くので、ここは並べるだけでいい
        // （試聴と本番で同じ手順を通るので、音が食い違わない）。
        return PlaybackPlan(
            song.patterns,
            state.cruise.mapIndexed { bar, chord -> Bar(index, chord, bar) },
        )
    }

    /** [block] のコードを捏ねはじめる。中身は今そこにあるコード。 */
    fun openCruiser(block: Int) {
        val song = _uiState.value.song
        val step = song.arrangement.getOrNull(block) ?: return
        val fallback = song.patternChord(step.patternIndex)
        val bars = step.repeat.coerceIn(1, PlaybackPlan.MAX_REPEAT)
        _uiState.update {
            it.copy(
                cruise = List(bars) { bar -> step.chordAt(bar, fallback) },
                cruiseBlock = block,
                cruiseSeed = "いまのまま",
                cruisePlaying = false,
            )
        }
    }

    /** 捏ねるのをやめる。曲には何も残らない。 */
    fun closeCruiser() {
        if (_uiState.value.cruisePlaying) stopCruise()
        _uiState.update { it.copy(cruise = emptyList(), cruiseBlock = -1, cruiseSeed = "") }
    }

    /** [bar] 小節目のコードを差し替える。 */
    fun setCruiseChord(bar: Int, chord: Chord) {
        _uiState.update { state ->
            if (bar !in state.cruise.indices) return@update state
            state.copy(
                cruise = state.cruise.toMutableList().also { it[bar] = chord },
                cruiseSeed = "手で直した",
            )
        }
        if (_uiState.value.cruisePlaying) syncEngine() else previewChord(chord)
    }

    /** 種を選べる形で出す。定番の型と、その場で作った進行。 */
    fun cruiserSeeds(): List<ChordCruiser.Seed> {
        val bars = _uiState.value.cruise.size.coerceAtLeast(1)
        return ChordCruiser.seeds(detectedKey(), bars, Random)
    }

    /** 種を流し込む。長さはブロックに合わせる。 */
    fun loadCruiseSeed(seed: ChordCruiser.Seed) {
        _uiState.update { state ->
            if (state.cruise.isEmpty()) return@update state
            state.copy(
                cruise = ChordCruiser.fit(seed.chords, state.cruise.size),
                cruiseSeed = seed.name,
            )
        }
        if (_uiState.value.cruisePlaying) syncEngine()
    }

    /** 捏ねている 4 小節をループで鳴らす。 */
    fun playCruise() {
        if (_uiState.value.cruise.isEmpty()) return
        _uiState.update { it.copy(cruisePlaying = true, isPlaying = true, followSuspended = true) }
        syncEngine()
        audio.resume()
        engine.start()
        keepAlive.start(_uiState.value.song.name)
        startPositionUpdates()
    }

    fun stopCruise() {
        _uiState.update { it.copy(cruisePlaying = false) }
        engine.stop()
        clearPlayingState()
        syncEngine()
    }

    fun toggleCruise() {
        if (_uiState.value.cruisePlaying) stopCruise() else playCruise()
    }

    /**
     * 捏ねた進行をブロックに差し込む。
     *
     * 変わるのはそのブロックのコードだけ。パターンも打ち込みも旋律も触らない
     * （旋律は前のコードに合わせて書かれているので、勝手に作り直さない）。
     */
    fun applyCruise() {
        val state = _uiState.value
        val block = state.cruiseBlock
        val chords = state.cruise
        if (chords.isEmpty() || block < 0) return
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            val next = song.arrangement.toMutableList()
            if (block !in next.indices) return@updateCurrentSong song
            val fallback = song.patternChord(next[block].patternIndex)
            var step = next[block].withChordSlots(fallback)
            chords.forEachIndexed { bar, chord -> step = step.withChord(bar, chord, fallback) }
            next[block] = step
            song.copy(arrangement = next)
        }
        closeCruiser()
    }

    /** 曲構成のコードを、起承転結の流れで埋める。 */
    fun fillProgression() {
        val song = _uiState.value.song
        if (song.arrangement.isEmpty()) return
        val progression = ChordSuggester.generateStory(
            bars = song.totalBars(),
            key = detectedKey(),
            start = song.arrangement.first().chords.firstOrNull(),
            random = Random,
        )
        applyChords(song, progression)
    }

    /**
     * 最後の [bars] 小節を終止形（結）に差し替える。
     * それより前の小節はそのまま残す。
     */
    fun fillCadence(bars: Int) {
        val song = _uiState.value.song
        val total = song.totalBars()
        if (total == 0) return
        val count = bars.coerceIn(1, total)
        val current = PlaybackPlan.arrangement(song).bars.map { it.chord }
        val ending = ChordSuggester.cadence(
            length = count,
            key = detectedKey(),
            previous = current.getOrNull(total - count - 1),
            random = Random,
        )
        val chords = current.toMutableList()
        ending.forEachIndexed { index, chord -> chords[total - count + index] = chord }
        applyChords(song, chords)
    }

    /** 小節ごとのコード列を、曲構成のブロックに割り振って書き戻す。 */
    private fun applyChords(song: Song, chords: List<Chord>) {
        snapshotForUndo()
        val follow = _uiState.value.followMelody
        repository.updateCurrentSong { current ->
            var index = 0
            val arrangement = current.arrangement.map { step ->
                step.copy(chords = List(step.repeat) { chords.getOrElse(index++) { Chord() } })
            }
            val rewritten = current.copy(arrangement = arrangement)
            if (!follow) return@updateCurrentSong rewritten
            // 旋律は前のコードに合わせて書かれているので、進行を変えると和音から外れる。
            // 調も新しいコードから取り直す（古い進行のまま推定すると、ずれた調で作ってしまう）。
            val key = rewritten.key ?: ChordSuggester.detectKey(
                PlaybackPlan.arrangement(rewritten).bars.map { it.chord },
            )
            rewritten.arrangement.map { it.patternIndex }.distinct()
                .filter { it in rewritten.patterns.indices }
                .fold(rewritten) { acc, target ->
                    acc.withPattern(target, melodyPattern(acc, target, key))
                }
        }
    }

    /** 進行を作り直したときに旋律も追従させるか。 */
    fun setFollowMelody(follow: Boolean) {
        _uiState.update { it.copy(followMelody = follow) }
    }

    /** 自動生成の前に、今の曲を控えておく。 */
    private fun snapshotForUndo() {
        undoStack.addLast(_uiState.value.song)
        // 際限なく持つとメモリを食うので、古いものから捨てる。
        while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        _uiState.update { it.copy(canUndo = true, undoDepth = undoStack.size) }
    }

    /** 自動生成を 1 段ずつ取り消す。 */
    fun undoGenerate() {
        val songId = _uiState.value.song.id
        // 別の曲に切り替わっていたら戻さない（別の曲を上書きしてしまうため）。
        while (undoStack.isNotEmpty()) {
            val previous = undoStack.removeLast()
            if (previous.id != songId) continue
            repository.updateCurrentSong { previous }
            break
        }
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), undoDepth = undoStack.size) }
    }

    private fun clearUndo() {
        undoStack.clear()
        if (_uiState.value.canUndo) {
            _uiState.update { it.copy(canUndo = false, undoDepth = 0) }
        }
    }

    /** 調の推定に使うコード。曲構成があればその並び、無ければパターンのコード。 */

    /**
     * ジャンルを 1 つ選んで、[bars] 小節の曲をまるごと作る（[genre] が null ならジャンルもおまかせ）。
     * 旋律も小節ごとに作る（同じ旋律を 4 回繰り返すと、下のコードが変わったときに合わなくなるため）。
     */
    fun generateSong(genre: Genre?, scene: GameScene?, bars: Int = DEFAULT_SONG_BARS) {
        val chosen = genre ?: Genre.entries.random(Random)
        val recipe = SongEditor.recipeFor(chosen, scene, Random)
        val key = detectedKey()
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            SongBuilder.build(SongEditor.withChipSound(song, recipe), recipe, key, bars, Random)
        }
        // 作ったあとは前半のパターンを開いておく（やり直しの控えは残す）。
        _uiState.update { it.copy(selectedPattern = SongBuilder.FIRST_PATTERN) }
        syncEngine()
    }

    /**
     * 当てはめる中身。場面を持つジャンルは、選ばれていなければ 1 つ引く。
     * 「おまかせ」でゲーム音楽が出たときも、どれかの場面にはなる。
     */

    /**
     * ジャンルのプリセットを当てはめる。
     * テンポ・コード進行・リズム・旋律をまとめて設定するので、
     * ドラムの型だけを変えるより「そのジャンルらしく」なる。
     */
    fun applyGenre(genre: Genre, scene: GameScene?, options: GenreOptions) {
        val state = _uiState.value
        val index = state.selectedPattern
        val recipe = SongEditor.recipeFor(genre, scene, Random)
        // 進行の型が音階を決めているなら、そちらに合わせて解決する。
        // 主音は曲のまま残すので、同じ音を中心にしたまま雰囲気だけが変わる。
        val progression = recipe.pickProgression(Random)
        val key = progression.keyFor(detectedKey())
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            var next = song
            if (options.tempo) {
                next = next.copy(bpm = recipe.pickBpm(Random))
            }
            if (options.sound) {
                next = SongEditor.withChipSound(next, recipe)
            }
            if (options.chords) {
                // 使った音階を曲の調として残す。残さないとコードから推定し直され、
                // 次に旋律を作るときに違う音階で書かれてしまう。
                next = next.copy(key = key)
                // 曲構成がまだ無いときは、進行の長さぶんの構成を作ってから埋める。
                val arrangement = next.arrangement.ifEmpty {
                    listOf(ArrangementStep(index, progression.degrees.size.coerceAtMost(8)))
                }
                val bars = arrangement.sumOf { it.repeat }
                val chords = progression.fill(key, bars)
                var barIndex = 0
                next = next.copy(
                    arrangement = arrangement.map { step ->
                        step.copy(chords = List(step.repeat) { chords.getOrElse(barIndex++) { Chord() } })
                    },
                )
                // パターン単体で鳴らしたときも進行の頭の響きになるように合わせる。
                chords.firstOrNull()?.let { next = next.withPatternChord(index, it) }
            }
            if (options.rhythm) {
                val generated = PatternGenerator.generate(
                    style = recipe.pickRhythm(Random),
                    random = Random,
                    name = next.pattern(index).name,
                )
                next = next.withPattern(
                    index,
                    SongEditor.withGrooveEverywhere(next.pattern(index), generated),
                )
            }
            if (options.melody) {
                val lead = MelodyGenerator.generate(
                    chord = next.patternChord(index),
                    key = key,
                    random = Random,
                    density = recipe.melodyDensity,
                )
                next = next.withPattern(index, next.pattern(index).withLeads(listOf(lead)))
            }
            next
        }
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

    /** 左右の位置。-1 で左端、0 で中央、1 で右端。 */
    fun setTrackPan(track: Int, pan: Float) {
        repository.updateCurrentSong { song ->
            song.withTrack(track, song.track(track).copy(pan = pan.coerceIn(-1f, 1f)))
        }
    }

    /** 全部を中央に戻す。振りすぎて分からなくなったときの逃げ道。 */
    fun centreAll() {
        repository.updateCurrentSong { song ->
            song.copy(tracks = song.tracks.map { it.copy(pan = 0f) })
        }
    }

    /** 音の伸び（サステイン）。コード / ベース / リードだけで効く。 */
    fun setTrackHold(track: Int, hold: Float) {
        repository.updateCurrentSong { song ->
            song.withTrack(track, song.track(track).copy(hold = hold.coerceIn(0f, 1f)))
        }
    }

    /** ハネ具合。0 でまっすぐ、0.67 あたりが三連のシャッフル。 */
    fun setSwing(swing: Float) {
        repository.updateCurrentSong { it.copy(swing = swing.coerceIn(0f, 1f)) }
    }

    /** コード行の弾き方（和音 / 上へ / 下へ / 上下）。 */
    fun setChordStyle(style: ChordStyle) {
        repository.updateCurrentSong { it.copy(chordStyle = style) }
    }

    /** リードの音色。 */
    fun setLeadVoice(voice: ToneSynth.LeadVoice) {
        repository.updateCurrentSong { it.copy(leadVoice = voice) }
    }

    /** リードの揺れ（ビブラート）。 */
    fun setLeadVibrato(amount: Float) {
        repository.updateCurrentSong { it.copy(leadVibrato = amount.coerceIn(0f, 1f)) }
    }

    /**
     * 端末が実際に返した音声の設定。遅れの原因は端末ごとに違うので、
     * 推測ではなく出てきた値を見て詰められるようにヘルプに出す。
     */
    fun audioReport(): String? {
        val report = audio.report ?: return null
        val path = if (report.lowLatency) "低遅延の経路" else "通常の経路（低遅延は断られた）"
        val underruns = if (report.underruns == 0) "途切れなし" else "途切れ ${report.underruns} 回"
        return "%,d Hz ・ %d ch ・ %d フレームずつ書き込み ・ 溜め %d フレーム（%.1f ms）・ %s ・ %s".format(
            report.sampleRate,
            CHANNELS,
            report.blockFrames,
            report.bufferFrames,
            report.bufferMillis,
            path,
            underruns,
        )
    }

    /** ドラムの音の作り方。 */
    fun setDrumKit(kit: DrumKit) {
        repository.updateCurrentSong { it.copy(drumKit = kit) }
    }

    /** 和音の積み方（そのまま / なめらか / 厚く）。 */
    fun setChordVoicing(voicing: ChordVoicing) {
        repository.updateCurrentSong { it.copy(chordVoicing = voicing) }
    }

    /** ベースの動き方（ルート / 5度も / 動く）。 */
    fun setBassStyle(style: BassStyle) {
        repository.updateCurrentSong { it.copy(bassStyle = style) }
    }

    /** 残響の量。0 で掛けない。 */
    fun setReverb(amount: Float) {
        repository.updateCurrentSong { it.copy(reverb = amount.coerceIn(0f, 1f)) }
    }

    /** 残響の広さ。 */
    fun setRoomSize(size: RoomSize) {
        repository.updateCurrentSong { it.copy(roomSize = size) }
    }

    /** コードとベースの音の作り方。 */
    fun setSoundSet(set: SoundSet) {
        repository.updateCurrentSong { it.copy(soundSet = set) }
    }

    /** 高速アルペジオで音を進める速さ。 */
    fun setArpeggioSpeed(speed: ArpeggioSpeed) {
        repository.updateCurrentSong { it.copy(arpeggioSpeed = speed) }
    }

    /**
     * 曲まるごとのキーを [semitones] 半音だけ動かす。
     * 「戻す」で元に戻せるよう、控えを取ってから書き換える。
     */
    fun transpose(semitones: Int) {
        if (semitones == 0) return
        snapshotForUndo()
        repository.updateCurrentSong { Transposer.transpose(it, semitones) }
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
            // 2 小節以上のパターンは、まず最後まで鳴る長さで置く。
            // 1 小節ぶんだけ置くと、書いた後半が曲では鳴らないことになる。
            val bars = song.pattern(patternIndex).barCount
            song.copy(
                arrangement = song.arrangement +
                    ArrangementStep(patternIndex, bars, List(bars) { chord }),
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

    // --- 音声の書き出し -----------------------------------------------------

    /** 書き出す範囲に対応する再生プラン。 */
    fun exportPlan(scope: ExportScope, repeats: Int): PlaybackPlan {
        val state = _uiState.value
        val song = state.song
        return when (scope) {
            ExportScope.SONG -> PlaybackPlan.arrangement(song)
            ExportScope.CHAIN -> PlaybackPlan.chain(song, state.chain).repeated(repeats)
            ExportScope.PATTERN -> PlaybackPlan.single(song, state.selectedPattern).repeated(repeats)
        }
    }

    /** 書き出したときのおおよその長さ（表示用）。 */
    fun exportLengthLabel(scope: ExportScope, repeats: Int): String {
        val song = _uiState.value.song
        val bars = exportPlan(scope, repeats).barCount
        if (bars == 0) return "0 小節"
        val seconds = bars * STEPS_PER_BAR * secondsPerStep(song.bpm) +
            OfflineRenderer.DEFAULT_TAIL_SECONDS
        return "$bars 小節 ・ 約 ${formatDuration(seconds)}"
    }

    /** 保存ダイアログに出すファイル名の芯（ファイル名に使えない文字は _ に置き換える）。 */
    private fun safeName(): String = _uiState.value.song.name
        .trim()
        .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
        .joinToString("")
        .ifBlank { "BreakBox" }

    /** 保存ダイアログに出すファイル名。 */
    fun suggestedFileName(): String = "${safeName()}.m4a"

    fun suggestedMidiName(): String = "${safeName()}.mid"

    fun suggestedSongName(): String = "${safeName()}.breakbox.json"

    /** [destination] に MIDI を書き出す。 */
    fun exportMidi(destination: Uri, scope: ExportScope, repeats: Int) {
        val song = _uiState.value.song
        val plan = exportPlan(scope, repeats)
        val message = runCatching {
            val bytes = MidiExporter.export(song, plan)
            container.fileExporter.write(destination, bytes)
            bytes.size
        }.fold(
            onSuccess = { "MIDI を書き出しました（${plan.barCount} 小節 ・ ${it / 1024 + 1} KB）" },
            onFailure = { "書き出せませんでした: ${it.message ?: it::class.java.simpleName}" },
        )
        _uiState.update { it.copy(exportMessage = message) }
    }

    /** [destination] に、いま開いている曲を 1 ファイルとして書き出す。 */
    fun exportSongFile(destination: Uri) {
        val song = _uiState.value.song
        val message = runCatching {
            container.fileExporter.write(destination, SongCodec.encodeSong(song).toByteArray())
        }.fold(
            onSuccess = { "「${song.name}」を書き出しました" },
            onFailure = { "書き出せませんでした: ${it.message ?: it::class.java.simpleName}" },
        )
        _uiState.update { it.copy(exportMessage = message) }
    }

    /** 曲のファイルを読み込んで、ライブラリに足す（今の曲は上書きしない）。 */
    fun importSongFile(source: Uri) {
        val message = runCatching {
            val song = SongCodec.decodeSong(container.fileExporter.readText(source))
                ?: error("BreakBox の曲ファイルではないようです")
            stop()
            clearUndo()
            repository.addSong(song)
            song.name
        }.fold(
            onSuccess = { "「$it」を読み込みました" },
            onFailure = { "読み込めませんでした: ${it.message ?: it::class.java.simpleName}" },
        )
        _uiState.update { it.copy(exportMessage = message) }
    }

    /** バックアップのファイル名。日付を入れて、いつの控えか分かるようにする。 */
    fun suggestedLibraryName(): String =
        "BreakBox-${java.time.LocalDate.now()}.breakbox-all.json"

    /** [destination] に、保存してあるすべての曲を 1 ファイルとして書き出す。 */
    fun exportLibraryFile(destination: Uri) {
        val library = repository.library.value
        val message = runCatching {
            container.fileExporter.write(destination, SongCodec.encode(library).toByteArray())
            library.songs.size
        }.fold(
            onSuccess = { "$it 曲をバックアップしました" },
            onFailure = { "バックアップできませんでした: ${it.message ?: it::class.java.simpleName}" },
        )
        _uiState.update { it.copy(exportMessage = message) }
    }

    /**
     * バックアップから戻す。同じ曲（id が同じもの）は上書きし、無いものは足す。
     * 同じファイルを 2 回読んでも曲は増えない。
     */
    fun importLibraryFile(source: Uri) {
        val message = runCatching {
            val library = SongCodec.decode(container.fileExporter.readText(source))
                ?: error("BreakBox のファイルではないようです")
            require(library.songs.isNotEmpty()) { "曲が入っていません" }
            stop()
            clearUndo()
            repository.restore(library)
        }.fold(
            onSuccess = { "$it 曲を戻しました" },
            onFailure = { "戻せませんでした: ${it.message ?: it::class.java.simpleName}" },
        )
        _uiState.update { it.copy(exportMessage = message) }
    }

    /** [destination] に M4A を書き出す。 */
    fun exportAudio(destination: Uri, scope: ExportScope, repeats: Int) {
        if (_uiState.value.exportProgress != null) return
        stop() // 書き出し中は再生を止めて、CPU を取り合わないようにする
        val song = _uiState.value.song
        val plan = exportPlan(scope, repeats)
        _uiState.update { it.copy(exportProgress = 0f, exportMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                container.audioExporter.export(
                    song = song,
                    plan = plan,
                    voiceSamples = container.drumSamples,
                    chipVoiceSamples = container.chipDrumSamples,
                    sampleRate = container.sampleRate,
                    destination = destination,
                ) { progress ->
                    _uiState.update { it.copy(exportProgress = progress.coerceIn(0f, 1f)) }
                }
            }
            val message = result.fold(
                onSuccess = {
                    val megabytes = it.bytes / 1024f / 1024f
                    "書き出しました（%s ・ %.1f MB）".format(formatDuration(it.seconds), megabytes)
                },
                onFailure = { "書き出せませんでした: ${it.message ?: it::class.java.simpleName}" },
            )
            _uiState.update { it.copy(exportProgress = null, exportMessage = message) }
        }
    }

    fun dismissExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    // --- 曲の管理 -----------------------------------------------------------

    fun renameSong(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        repository.updateCurrentSong { it.copy(name = trimmed) }
    }

    fun createSong(name: String) {
        stop()
        clearUndo()
        repository.createSong(name.trim().ifEmpty { "新しい曲" })
    }

    fun duplicateSong() {
        stop()
        clearUndo()
        val current = _uiState.value.song
        repository.duplicateCurrentSong("${current.name} のコピー")
    }

    fun selectSong(id: String) {
        stop()
        clearUndo()
        repository.selectSong(id)
    }

    fun deleteSong(id: String) {
        stop()
        clearUndo()
        repository.deleteSong(id)
    }

    override fun onCleared() {
        audio.onPlaybackFinished = null
        keepAlive.onStopRequested = null
        // 画面が完全に畳まれたのに通知だけ残る、という状態を作らない。
        keepAlive.stop()
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_MS = 24L

        /** オート作曲で最初に選ばれている小節数。 */
        const val DEFAULT_SONG_BARS = 8

        /** 何段まで戻せるか。 */
        private const val MAX_UNDO = 20

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RhythmViewModel(container) }
        }
    }
}
