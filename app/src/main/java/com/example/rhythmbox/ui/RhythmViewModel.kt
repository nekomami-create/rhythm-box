package com.example.rhythmbox.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.rhythmbox.AppContainer
import com.example.rhythmbox.core.ArrangementStep
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.ChordSuggester
import com.example.rhythmbox.core.ChordSuggestion
import com.example.rhythmbox.core.DRUM_COUNT
import com.example.rhythmbox.core.EngineConfig
import com.example.rhythmbox.core.Genre
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.MelodyDensity
import com.example.rhythmbox.core.MelodyGenerator
import com.example.rhythmbox.core.MusicKey
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.PatternGenerator
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.RhythmStyle
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SongBuilder
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
    /** いま鳴っている旋律の小節（繰り返し何回目か）。止まっていれば -1。 */
    val playingLeadBar: Int = -1,
    /** ピアノロールで編集している繰り返し。 */
    val selectedLeadBar: Int = 0,
    /** 自動生成の直前の状態に戻せるか。 */
    val canUndo: Boolean = false,
    /** 書き出し中の進捗（0.0〜1.0）。書き出していなければ null。 */
    val exportProgress: Float? = null,
    /** 書き出しが終わったときに出す文言。 */
    val exportMessage: String? = null,
) {
    val pattern: Pattern get() = song.pattern(selectedPattern)

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

    /** グリッドで光らせるステップ。今そのパターンが鳴っているときだけ光る。 */
    val gridStep: Int
        get() = if (isPlaying && (mode == PlayMode.PATTERN || playingPattern == selectedPattern)) {
            playingStep
        } else {
            -1
        }

    /** ピアノロールで光らせるステップ。今その小節が鳴っているときだけ光る。 */
    val leadGridStep: Int
        get() = if (playingPattern == selectedPattern && playingLeadBar == selectedLeadBar) {
            gridStep
        } else {
            -1
        }

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

    private val _uiState = MutableStateFlow(RhythmUiState())
    val uiState: StateFlow<RhythmUiState> = _uiState.asStateFlow()

    private var positionJob: Job? = null

    /** 今エンジンに渡しているプラン。鳴っているパターンを割り出すのに使う。 */
    private var currentPlan: PlaybackPlan? = null

    /** 自動生成をやり直すための、直前の曲まるごとの控え。 */
    private var undoSong: Song? = null

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
                    val pattern = it.selectedPattern.coerceIn(song.patterns.indices)
                    it.copy(
                        ready = true,
                        song = song,
                        library = library.songs,
                        selectedPattern = pattern,
                        selectedLeadBar = it.selectedLeadBar
                            .coerceIn(0, song.pattern(pattern).leadBarCount - 1),
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
        if (mode == PlayMode.CHAIN && state.chain.isEmpty()) return
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
        _uiState.update {
            it.copy(
                isPlaying = false,
                playingStep = -1,
                playingBar = -1,
                playingPattern = -1,
                playingLeadBar = -1,
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
        _uiState.update {
            it.copy(
                isPlaying = false,
                playingStep = -1,
                playingBar = -1,
                playingPattern = -1,
                playingLeadBar = -1,
            )
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                val position = audio.currentPosition()
                val bar = position?.let { currentPlan?.bars?.getOrNull(it.bar) }
                _uiState.update {
                    it.copy(
                        playingStep = position?.step ?: -1,
                        playingBar = position?.bar ?: -1,
                        playingPattern = bar?.patternIndex ?: -1,
                        playingLeadBar = bar?.leadBar ?: -1,
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
            holds = song.tracks.map { it.hold },
            loop = state.mode == PlayMode.PATTERN || state.loopSong,
        )
    }

    // --- パターン編集 -------------------------------------------------------

    fun selectPattern(index: Int) {
        clearUndo()
        _uiState.update {
            it.copy(selectedPattern = index.coerceIn(it.song.patterns.indices), selectedLeadBar = 0)
        }
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

    // --- リード（旋律） -----------------------------------------------------

    /** 編集する繰り返し（何回目の小節か）を選ぶ。 */
    fun selectLeadBar(bar: Int) {
        _uiState.update { it.copy(selectedLeadBar = bar.coerceIn(0, it.pattern.leadBarCount - 1)) }
    }

    /** 旋律を何小節ぶん持つかを変える。 */
    fun setLeadBarCount(count: Int) {
        val index = _uiState.value.selectedPattern
        val target = count.coerceIn(1, Pattern.MAX_LEAD_BARS)
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withLeadBarCount(target))
        }
        _uiState.update { it.copy(selectedLeadBar = it.selectedLeadBar.coerceIn(0, target - 1)) }
    }

    /** ピアノロールの 1 マス。同じ音を押し直したら消す。 */
    fun toggleLead(step: Int, midi: Int) {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedLeadBar
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
        val bar = state.selectedLeadBar
        val pattern = state.song.pattern(index)
        // 長押ししたところが音そのものなら、その音の伸ばしだけを外す。
        val head = if (Pattern.isNote(pattern.leadAt(bar, step))) step else pattern.leadHead(bar, step - 1)
        if (head < 0) return
        val until = if (head == step) head + pattern.tieRun(bar, head) else step
        if (until <= head) return
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).withLeadTie(bar, head, until))
        }
    }

    /** いま選んでいる繰り返しの音だけ消す。 */
    fun clearLeadBar() {
        val state = _uiState.value
        val index = state.selectedPattern
        val bar = state.selectedLeadBar
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).clearLead(bar))
        }
    }

    /** 旋律を全部消して 1 小節ぶんに戻す。 */
    fun clearLead() {
        val index = _uiState.value.selectedPattern
        repository.updateCurrentSong { song -> song.withPattern(index, song.pattern(index).clearAllLeads()) }
        _uiState.update { it.copy(selectedLeadBar = 0) }
    }

    /**
     * 旋律を自動生成する。持っている小節数ぶんまとめて作り、
     * それぞれの小節のコードに合わせる（同じ旋律を繰り返すと和音から外れるため）。
     */
    fun generateMelody() {
        val state = _uiState.value
        val index = state.selectedPattern
        val song = state.song
        // 曲構成で 4 小節使われているのに旋律が 1 小節ぶんしか無ければ、そのぶんまで広げる。
        val block = song.arrangement.firstOrNull { it.patternIndex == index }
        val bars = maxOf(song.pattern(index).leadBarCount, block?.repeat ?: 1)
            .coerceAtMost(Pattern.MAX_LEAD_BARS)
        snapshotForUndo()
        val leads = MelodyGenerator.generateBars(
            chords = leadChords(song, index, bars),
            key = detectedKey(),
            random = Random,
            density = MelodyDensity.NORMAL,
            previous = if (index > 0) song.pattern(index - 1).leadBars.lastOrNull() else null,
        )
        repository.updateCurrentSong { it.withPattern(index, it.pattern(index).withLeads(leads)) }
        _uiState.update { it.copy(selectedLeadBar = it.selectedLeadBar.coerceIn(0, bars - 1)) }
    }

    /** [bar] 回目の小節で鳴るコード。曲構成で使われていれば、そこのコードを見る。 */
    fun chordForLeadBar(bar: Int): Chord =
        leadChords(_uiState.value.song, _uiState.value.selectedPattern, bar + 1).last()

    /** パターン [index] を [bars] 小節ぶん鳴らすときの、小節ごとのコード。 */
    private fun leadChords(song: Song, index: Int, bars: Int): List<Chord> {
        val fallback = song.patternChord(index)
        val block = song.arrangement.firstOrNull { it.patternIndex == index }
        return List(bars) { block?.chordAt(it, fallback) ?: fallback }
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

    // --- 自動生成・レコメンド -----------------------------------------------

    /** 曲全体のコードから調を推定する。おすすめの基準になる。 */
    fun detectedKey(): MusicKey = ChordSuggester.detectKey(songChords())

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

    /**
     * パターン [index] のコードを選ぶときの手がかり。
     * パターンは A → B → C の順に使われることが多いので、1 つ前のパターンのコードを「前のコード」とみなす。
     */
    fun neighbourChordsForPattern(index: Int): Pair<Chord?, Chord?> {
        val song = _uiState.value.song
        val previous = if (index > 0) song.patternChord(index - 1) else null
        return previous to null
    }

    /** リズムを自動生成する。[style] が null ならスタイルもおまかせ。リードは触らない。 */
    fun generateRhythm(style: RhythmStyle?) {
        val index = _uiState.value.selectedPattern
        val current = _uiState.value.song.pattern(index)
        snapshotForUndo()
        val generated = if (style == null) {
            PatternGenerator.generateAny(Random, current.name)
        } else {
            PatternGenerator.generate(style, Random, current.name)
        }
        repository.updateCurrentSong { song ->
            song.withPattern(index, song.pattern(index).copy(rows = generated.rows))
        }
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
        var index = 0
        val arrangement = song.arrangement.map { step ->
            step.copy(chords = List(step.repeat) { chords.getOrElse(index++) { Chord() } })
        }
        repository.updateCurrentSong { it.copy(arrangement = arrangement) }
    }

    /** 自動生成の前に、今の曲を控えておく。 */
    private fun snapshotForUndo() {
        undoSong = _uiState.value.song
        _uiState.update { it.copy(canUndo = true) }
    }

    /** 直前の自動生成を取り消す。 */
    fun undoGenerate() {
        val previous = undoSong ?: return
        // 別の曲に切り替わっていたら戻さない（別の曲を上書きしてしまうため）。
        if (previous.id == _uiState.value.song.id) {
            repository.updateCurrentSong { previous }
        }
        clearUndo()
    }

    private fun clearUndo() {
        undoSong = null
        if (_uiState.value.canUndo) _uiState.update { it.copy(canUndo = false) }
    }

    /** 調の推定に使うコード。曲構成があればその並び、無ければパターンのコード。 */
    private fun songChords(): List<Chord> {
        val song = _uiState.value.song
        val fromArrangement = PlaybackPlan.arrangement(song).bars.map { it.chord }
        return fromArrangement.ifEmpty { song.patternChords }
    }

    /**
     * ジャンルを 1 つ選んで、[bars] 小節の曲をまるごと作る（[genre] が null ならジャンルもおまかせ）。
     * 旋律も小節ごとに作る（同じ旋律を 4 回繰り返すと、下のコードが変わったときに合わなくなるため）。
     */
    fun generateSong(genre: Genre?, bars: Int = DEFAULT_SONG_BARS) {
        val chosen = genre ?: Genre.entries.random(Random)
        val key = detectedKey()
        snapshotForUndo()
        repository.updateCurrentSong { SongBuilder.build(it, chosen, key, bars, Random) }
        // 作ったあとは前半のパターンを開いておく（やり直しの控えは残す）。
        _uiState.update { it.copy(selectedPattern = SongBuilder.FIRST_PATTERN) }
        syncEngine()
    }

    /**
     * ジャンルのプリセットを当てはめる。
     * テンポ・コード進行・リズム・旋律をまとめて設定するので、
     * ドラムの型だけを変えるより「そのジャンルらしく」なる。
     */
    fun applyGenre(genre: Genre, options: GenreOptions) {
        val state = _uiState.value
        val index = state.selectedPattern
        val key = detectedKey()
        val progression = genre.pickProgression(Random)
        snapshotForUndo()
        repository.updateCurrentSong { song ->
            var next = song
            if (options.tempo) {
                next = next.copy(bpm = genre.pickBpm(Random))
            }
            if (options.chords) {
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
                    style = genre.pickRhythm(Random),
                    random = Random,
                    name = next.pattern(index).name,
                )
                next = next.withPattern(index, next.pattern(index).copy(rows = generated.rows))
            }
            if (options.melody) {
                val lead = MelodyGenerator.generate(
                    chord = next.patternChord(index),
                    key = key,
                    random = Random,
                    density = genre.melodyDensity,
                )
                next = next.withPattern(index, next.pattern(index).copy(lead = lead))
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

    /** 音の伸び（サステイン）。コード / ベース / リードだけで効く。 */
    fun setTrackHold(track: Int, hold: Float) {
        repository.updateCurrentSong { song ->
            song.withTrack(track, song.track(track).copy(hold = hold.coerceIn(0f, 1f)))
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

    /** 保存ダイアログに出すファイル名。ファイル名に使えない文字は _ に置き換える。 */
    fun suggestedFileName(): String {
        val name = _uiState.value.song.name
            .trim()
            .map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .ifBlank { "BreakBox" }
        return "$name.m4a"
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
        super.onCleared()
    }

    companion object {
        private const val POSITION_POLL_MS = 24L

        /** オート作曲で最初に選ばれている小節数。 */
        const val DEFAULT_SONG_BARS = 8

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { RhythmViewModel(container) }
        }
    }
}
