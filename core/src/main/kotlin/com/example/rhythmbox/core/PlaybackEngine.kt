package com.example.rhythmbox.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/** 出力のチャンネル数。左右が交互に並ぶ（L R L R …）。 */
const val CHANNELS = 2

/**
 * 定位から左チャンネルの音量を出す。
 *
 * 中央を 1.0 にしてある。ここを 0.707（等パワー）にすると、
 * ステレオにしただけで曲全体が小さくなり、これまで計算で決めてきた
 * トラックごとの音量を全部取り直すことになる。中央を 1.0 に置けば、
 * 何も振っていない曲はモノラルだったころとそのまま同じ音になる
 * （既定の曲 75 万フレームで実測して、違ったのは 6 フレーム、
 * それも float の刻み 1 つぶん＝約 -144 dB だった）。
 * 端に振り切ったぶんだけ音が小さくなるが、それは振った側の判断でいい。
 */
fun panLeft(pan: Float): Float {
    val position = pan.coerceIn(-1f, 1f)
    return if (position <= 0f) 1f else 1f - position
}

/** 定位から右チャンネルの音量を出す。[panLeft] の裏返し。 */
fun panRight(pan: Float): Float {
    val position = pan.coerceIn(-1f, 1f)
    return if (position >= 0f) 1f else 1f + position
}

/** 音声スレッドが 1 ブロックごとに読む設定のスナップショット（不変）。 */
data class EngineConfig(
    val plan: PlaybackPlan,
    val bpm: Int = Song.DEFAULT_BPM,
    val masterVolume: Float = 0.75f,
    val trackVolumes: List<Float> = List(TRACK_COUNT) { 0.7f },
    val mutes: List<Boolean> = List(TRACK_COUNT) { false },
    /** トラックごとの左右の位置。-1 が左端、0 が中央、1 が右端。 */
    val trackPans: List<Float> = List(TRACK_COUNT) { 0f },
    /** トラックごとの音の伸び（サステイン）。音程のある 3 トラックだけで効く。 */
    val holds: List<Float> = List(TRACK_COUNT) { ToneSynth.DEFAULT_HOLD },
    /** ハネ具合。0 でまっすぐ。 */
    val swing: Float = 0f,
    /** コード行の弾き方。 */
    val chordStyle: ChordStyle = ChordStyle.BLOCK,
    /** リードの音色。 */
    val leadVoice: ToneSynth.LeadVoice = ToneSynth.LeadVoice.SQUARE,
    /** リードの揺れ（ビブラート）。0 で揺らさない。 */
    val leadVibrato: Float = 0f,
    /** ドラムの音の作り方。 */
    val drumKit: DrumKit = DrumKit.NORMAL,
    /** コードとベースの音の作り方。 */
    val soundSet: SoundSet = SoundSet.NORMAL,
    /** 高速アルペジオで音を進める速さ。 */
    val arpeggioSpeed: ArpeggioSpeed = ArpeggioSpeed.NORMAL,
    /** 残響の量。0 で掛けない。 */
    val reverb: Float = 0f,
    /** 残響の広さ。 */
    val roomSize: RoomSize = RoomSize.MEDIUM,
    /** 和音の組み立て方。 */
    val chordVoicing: ChordVoicing = ChordVoicing.PLAIN,
    /** ベースの動き方。 */
    val bassStyle: BassStyle = BassStyle.ROOT,
    /**
     * メトロノームを鳴らすか。
     * 叩くときの目印なので曲の一部ではない。書き出しでは常に切っておく。
     */
    val metronome: Boolean = false,
    val loop: Boolean = true,
)

/**
 * ステップシーケンサ本体。Android には依存せず、
 * 「PCM バッファを埋める」ことだけを行うので JVM の単体テストで検証できる。
 *
 * ドラムは作り置きした波形の再生、コード / ベース / リードは
 * その場で倍音を合成する発振器（[ToneVoice]）で鳴らす。
 *
 * [render] は音声スレッドから、それ以外（[config] の更新や [start] / [stop]）は
 * UI スレッドから呼ばれる想定。受け渡しは @Volatile な不変オブジェクトで行う。
 */
class PlaybackEngine(
    val sampleRate: Int,
    private val voiceSamples: List<FloatArray>,
    /**
     * チップ音源のドラム。渡さなければ標準のものを使い回すので、
     * キットを 1 つしか持たない呼び出し側でもそのまま動く。
     */
    private val chipVoiceSamples: List<FloatArray> = voiceSamples,
    private val maxPolyphony: Int = 24,
    private val maxTonePolyphony: Int = 12,
) {
    /** 音色ごとのチョークグループ。同じ番号の音は打ち直しで前の音を止める。 */
    private val chokeGroups = IntArray(DRUM_COUNT) { it }.also {
        // クローズドとオープンのハイハットは実機同様に排他。
        it[Voice.OPEN_HAT.ordinal] = Voice.CLOSED_HAT.ordinal
    }

    @Volatile
    var config: EngineConfig = EngineConfig(PlaybackPlan(emptyList(), emptyList()))

    @Volatile
    var isPlaying: Boolean = false
        private set

    /** 再生開始からのフレーム数（音声スレッドが進める）。 */
    @Volatile
    var framePosition: Long = 0L
        private set

    val timeline = StepTimeline()

    // --- 音声スレッドだけが触る状態 ---
    private val slotVoice = IntArray(maxPolyphony) { -1 }
    private val slotPos = DoubleArray(maxPolyphony)

    /** 発音ごとの読み出し速さ。1.0 から少しずらすと音の高さが変わる。 */
    private val slotRate = DoubleArray(maxPolyphony) { 1.0 }

    /**
     * 音色ごとに何発目か。揺らぎの表を引く番号に使う。
     *
     * 乱数ではなく通し番号にしてある。乱数だと書き出すたびに結果が変わり、
     * 同じ曲から同じファイルが出てこなくなる。頭から鳴らせば必ず同じになり、
     * それでいて続けて叩いたぶんは違う音になる。
     */
    private val hitCount = IntArray(DRUM_COUNT)

    /** ドラム 1 発ごとの強さ（アクセント / 幽霊音）。 */
    private val slotGain = FloatArray(maxPolyphony) { 1f }

    /** 残響。ミックス全体に掛けるので 1 つだけ持つ。 */
    private val reverb = Reverb(sampleRate)

    /** メトロノームのクリック。曲の音とは別に、最後に足すだけにする。 */
    private val clickDown = DrumSynth.click(sampleRate, downbeat = true)
    private val clickBeat = DrumSynth.click(sampleRate, downbeat = false)
    private var clickSample: FloatArray? = null
    private var clickPos = 0
    private val toneVoices = Array(maxTonePolyphony) { ToneVoice() }
    private var absoluteStep = 0L
    private var nextStepFrame = 0.0
    private var pendingRestart = false

    /** 先頭から再生を始める。実際のリセットは次の [render] で行われる。 */
    fun start() {
        pendingRestart = true
        isPlaying = true
    }

    fun stop() {
        isPlaying = false
        pendingRestart = true
        // 伸ばしっぱなしのコードなどが鳴り続けないよう、離鍵だけしておく。
        for (voice in toneVoices) voice.release()
    }

    /** ドラムを単発で鳴らす（パッドを叩いたときのプレビュー用）。 */
    fun trigger(voice: Int) {
        if (voice in 0 until DRUM_COUNT) triggerDrum(voice)
    }

    /**
     * コードを単発で鳴らす（コードを選んだときの試聴用）。
     *
     * 転回形は選び直さない。前の和音が無いので繋げようがなく、
     * 単発で聴くなら基本の積み方のほうが響きが分かりやすい。
     * 低いルートだけは曲の設定に合わせる（厚みは単発でも聞き分けられる）。
     */
    fun previewChord(chord: Chord) {
        val cfg = config
        val notes = if (cfg.chordVoicing.lowRoot && !cfg.chordStyle.chipArpeggio) {
            listOf(Voicing.lowRoot(chord)) + chord.voicing()
        } else {
            chord.voicing()
        }
        triggerChord(
            notes,
            (sampleRate * PREVIEW_SECONDS).toLong(),
            timbreOf(cfg, Instrument.CHORD),
        )
    }

    /** コード以外の音程楽器を単発で鳴らす。 */
    fun previewNote(instrument: Instrument, midi: Int) {
        triggerNote(instrument, midi, (sampleRate * PREVIEW_SECONDS).toLong(), timbreOf(config, instrument))
    }

    /**
     * [out] の先頭 [frames] フレームぶんを描画する。既存の内容は上書きされる。
     * [out] は左右が交互に並ぶので、長さはフレーム数の [CHANNELS] 倍いる。
     * 戻り値は再生中かどうか（曲構成の終端で false になる）。
     */
    fun render(out: FloatArray, frames: Int = out.size / CHANNELS): Boolean {
        // 停止直後は余韻を鳴らし切りたいので、リセットは再生開始時だけ行う。
        if (pendingRestart && isPlaying) {
            rewind()
            pendingRestart = false
        }
        val cfg = config
        var i = 0
        while (i < frames) {
            if (isPlaying && framePosition >= nextStepFrame) fireStep(cfg)
            // 停止中も余韻とプレビュー音を鳴らしつつ、フレーム数は進める。
            val chunk = if (isPlaying) {
                minOf(frames - i, floor(nextStepFrame - framePosition).toInt().coerceAtLeast(1))
            } else {
                frames - i
            }
            java.util.Arrays.fill(out, i * CHANNELS, (i + chunk) * CHANNELS, 0f)
            renderDrums(out, i, chunk, cfg)
            renderTones(out, i, chunk, cfg)
            // 残響は曲の音にだけ掛ける。メトロノームは目印なので、
            // 尾を引かせると裏拍が滲んで数えられなくなる。だから後で足す。
            reverb.process(out, i, chunk, cfg.reverb, cfg.roomSize)
            renderClick(out, i, chunk)
            // 抑えるのは残響を足したあと。先に抑えると、
            // 足した残響のぶんでまた膨らんで割れる。
            for (j in i until i + chunk) limitFrame(out, j * CHANNELS)
            framePosition += chunk
            i += chunk
        }
        return isPlaying
    }

    /** そのとき鳴らすドラムの波形一式。 */
    private fun kitOf(cfg: EngineConfig): List<FloatArray> =
        if (cfg.drumKit == DrumKit.CHIP) chipVoiceSamples else voiceSamples

    /** 鳴っているドラム（減衰中のものを含む）をミックスする。 */
    private fun renderDrums(out: FloatArray, offset: Int, count: Int, cfg: EngineConfig) {
        val master = cfg.masterVolume
        val kit = kitOf(cfg)
        for (slot in 0 until maxPolyphony) {
            val voice = slotVoice[slot]
            if (voice < 0) continue
            val sample = kit[voice]
            val gain = master * trackGain(cfg, voice) * slotGain[slot]
            val pan = trackPan(cfg, voice)
            val gainLeft = gain * panLeft(pan)
            val gainRight = gain * panRight(pan)
            val rate = slotRate[slot]
            var pos = slotPos[slot]
            var k = 0
            if (gain > 0f) {
                // 読み出す位置が整数で止まらないので、間を直線で埋める。
                // 埋めないと、速さをずらしたぶんがざらつきとして乗る。
                //
                // 止めるのは「最後のサンプルを過ぎたところ」。size で切ると、
                // 速さが 1 未満のときに末尾を越えた位置まで補間してしまい、
                // 波形の外に小さな尾が伸びる。size - 1 で切ると今度は最後の
                // 1 サンプルが鳴らず、1 フレームの波形は丸ごと消える。
                val last = sample.size - 1
                while (k < count && pos <= last) {
                    val index = pos.toInt()
                    val fraction = (pos - index).toFloat()
                    val here = sample[index]
                    val next = if (index + 1 < sample.size) sample[index + 1] else 0f
                    val value = here + (next - here) * fraction
                    val at = (offset + k) * CHANNELS
                    out[at] += value * gainLeft
                    out[at + 1] += value * gainRight
                    k++
                    pos += rate
                }
            }
            // ミュート中でも再生位置は進める（解除したときに続きから鳴らない）。
            pos += (count - k) * rate
            if (pos > sample.size - 1) {
                slotVoice[slot] = -1
            } else {
                slotPos[slot] = pos
            }
        }
    }

    /**
     * メトロノームを足す。
     * 音量つまみもミュートも通さない。目印なので、曲の音量をいじっても聞こえ方が変わらないほうがいい。
     */
    private fun renderClick(out: FloatArray, offset: Int, count: Int) {
        val sample = clickSample ?: return
        var pos = clickPos
        var k = 0
        while (k < count && pos < sample.size) {
            // 目印なので中央から動かさない。
            val at = (offset + k) * CHANNELS
            out[at] += sample[pos]
            out[at + 1] += sample[pos]
            k++
            pos++
        }
        if (pos >= sample.size) {
            clickSample = null
            clickPos = 0
        } else {
            clickPos = pos
        }
    }

    /** コード / ベース / リードを合成してミックスする。 */
    private fun renderTones(out: FloatArray, offset: Int, count: Int, cfg: EngineConfig) {
        val master = cfg.masterVolume
        for (voice in toneVoices) {
            val instrument = voice.instrument ?: continue
            val track = instrument.trackIndex
            val gain = master * trackGain(cfg, track)
            val pan = trackPan(cfg, track)
            voice.render(out, offset, count, gain * panLeft(pan), gain * panRight(pan))
        }
    }

    private fun trackGain(cfg: EngineConfig, track: Int): Float =
        if (cfg.mutes.getOrElse(track) { false }) 0f else cfg.trackVolumes.getOrElse(track) { 0.7f }

    private fun trackPan(cfg: EngineConfig, track: Int): Float = cfg.trackPans.getOrElse(track) { 0f }

    /**
     * その小節で鳴らす和音の音。
     *
     * 繋がりを解いた結果はプランが常に持っているので、ここでは使うかどうかを
     * 決めるだけ。設定を途中で変えてもプランを作り直さずに切り替わる。
     *
     * 低いルートは高速アルペジオのときは足さない。あれは 1 声部で和音を
     * 装う技で、音を 1 つ足すと「1 つの音色に聞こえる」効き目が崩れる。
     */
    private fun voicingFor(cfg: EngineConfig, plan: PlaybackPlan, bar: Int, chord: Chord): List<Int> {
        val notes = if (cfg.chordVoicing.smooth) plan.voicingAt(bar) else chord.voicing()
        if (!cfg.chordVoicing.lowRoot || cfg.chordStyle.chipArpeggio) return notes
        return listOf(Voicing.lowRoot(chord)) + notes
    }

    /** 次のステップを発音し、次回の発音フレームを決める。 */
    private fun fireStep(cfg: EngineConfig) {
        val plan = cfg.plan
        if (plan.isEmpty) {
            isPlaying = false
            return
        }
        var bar = (absoluteStep / STEPS_PER_BAR).toInt()
        if (bar >= plan.barCount) {
            if (!cfg.loop) {
                isPlaying = false
                for (voice in toneVoices) voice.release()
                return
            }
            absoluteStep = 0L
            bar = 0
        }
        val step = (absoluteStep % STEPS_PER_BAR).toInt()
        val pattern = plan.patternAt(bar)
        val chord = plan.chordAt(bar)
        val leadBar = plan.patternBarAt(bar)

        // 拍の頭だけ鳴らす。小節の頭は高い音にして、どこが 1 拍目か分かるようにする。
        if (cfg.metronome && step % 4 == 0) {
            clickSample = if (step == 0) clickDown else clickBeat
            clickPos = 0
        }
        for (voice in 0 until DRUM_COUNT) {
            if (pattern.isOn(voice, step)) triggerDrum(voice, pattern.levelAt(voice, step).gain)
        }
        if (pattern.isOn(ROW_CHORD, step)) {
            val timbre = timbreOf(cfg, Instrument.CHORD)
            val voicing = voicingFor(cfg, plan, bar, chord)
            // 高速アルペジオは 1 声部で鳴らし、構成音までの距離を渡して回してもらう。
            val arpeggio = if (cfg.chordStyle.chipArpeggio && voicing.isNotEmpty()) {
                IntArray(voicing.size) { voicing[it] - voicing[0] }
            } else {
                null
            }
            triggerChord(
                cfg.chordStyle.notesAt(voicing, chordHitIndex(plan, bar, step)),
                gateFrames(pattern.nextHit(ROW_CHORD, step) - step, timbre, cfg.bpm),
                timbre,
                pattern.levelAt(ROW_CHORD, step).gain,
                arpeggio,
                cfg.arpeggioSpeed.ticks,
            )
        }
        if (pattern.isOn(ROW_BASS, step)) {
            val timbre = timbreOf(cfg, Instrument.BASS)
            val nextHit = pattern.nextHit(ROW_BASS, step)
            triggerNote(
                Instrument.BASS,
                Bassline.noteAt(
                    chord = chord,
                    next = plan.nextChordAt(bar),
                    hitIndex = pattern.hitIndex(ROW_BASS, step),
                    last = nextHit >= STEPS_PER_BAR,
                    style = cfg.bassStyle,
                ),
                gateFrames(nextHit - step, timbre, cfg.bpm),
                timbre,
                pattern.levelAt(ROW_BASS, step).gain,
            )
        }
        val leadMidi = pattern.leadAt(leadBar, step)
        if (Pattern.isNote(leadMidi)) {
            val timbre = timbreOf(cfg, Instrument.LEAD)
            triggerNote(
                Instrument.LEAD,
                leadMidi,
                leadGate(cfg, bar, step, timbre),
                timbre,
                pattern.leadLevelAt(leadBar, step).gain,
            )
        }

        timeline.record(frame = nextStepFrame.toLong(), bar = bar, step = step)
        absoluteStep++
        nextStepFrame += framesPerStep(cfg.bpm) * swingFactor(cfg.swing, step)
    }

    /**
     * ハネ。表の 16 分を少し長く、裏を同じだけ短くする。
     * 2 つで元の長さに戻るので、テンポは変わらないまま裏だけが後ろにずれる。
     */
    private fun swingFactor(swing: Float, step: Int): Double {
        if (swing <= 0f) return 1.0
        val shift = swing.coerceIn(0f, 1f) * MAX_SWING_SHIFT
        return if (step % 2 == 0) 1.0 + shift else 1.0 - shift
    }

    /**
     * その小節で、このステップが何回目のコードかを数える。
     * 状態を持たずに数えるので、ループしても分散の並びがずれない。
     */
    private fun chordHitIndex(plan: PlaybackPlan, bar: Int, step: Int): Int {
        if (config.chordStyle == ChordStyle.BLOCK || config.chordStyle.chipArpeggio) return 0
        return plan.patternAt(bar).hitIndex(ROW_CHORD, step)
    }

    /**
     * リードの音を伸ばすフレーム数。タイが続くあいだは伸ばし続け、小節をまたいでも切らない。
     * タイが無い音は今までどおり「次の音まで（最長 1 拍）」で切る。
     */
    private fun leadGate(cfg: EngineConfig, bar: Int, step: Int, timbre: ToneSynth.Timbre): Long {
        val plan = cfg.plan
        var held = 1
        var cursorBar = bar
        var cursorStep = step
        while (held < MAX_LEAD_HOLD_STEPS) {
            cursorStep++
            if (cursorStep >= STEPS_PER_BAR) {
                cursorStep = 0
                cursorBar++
                if (cursorBar >= plan.barCount) break
            }
            val pattern = plan.patternAt(cursorBar)
            if (pattern.leadAt(plan.patternBarAt(cursorBar), cursorStep) != Pattern.TIE) break
            held++
        }
        if (held > 1) return (held * framesPerStep(cfg.bpm) * GATE_RATIO).toLong()
        val pattern = plan.patternAt(bar)
        return gateFrames(pattern.nextLead(plan.patternBarAt(bar), step) - step, timbre, cfg.bpm)
    }

    /** 「音の伸び」つまみを反映した、そのトラックの音色。 */
    private fun timbreOf(cfg: EngineConfig, instrument: Instrument): ToneSynth.Timbre =
        with(ToneSynth) {
            val base = timbre(instrument, cfg.leadVoice, cfg.soundSet).withHold(
                cfg.holds.getOrElse(instrument.trackIndex) { ToneSynth.DEFAULT_HOLD },
            )
            // 揺れはリードだけ。コードとベースまで揺れると土台が不安定に聞こえる。
            if (instrument == Instrument.LEAD) base.withVibrato(cfg.leadVibrato) else base
        }

    /** 次の音までの長さから、実際に音を伸ばすフレーム数を決める。 */
    private fun gateFrames(steps: Int, timbre: ToneSynth.Timbre, bpm: Int): Long {
        val limited = steps.coerceIn(1, timbre.maxGateSteps)
        return (limited * framesPerStep(bpm) * GATE_RATIO).toLong()
    }

    private fun triggerDrum(voice: Int, velocity: Float = 1f) {
        val group = chokeGroups[voice]
        var slot = -1
        var oldest = -1
        var oldestPos = -1.0
        for (s in 0 until maxPolyphony) {
            val v = slotVoice[s]
            if (v < 0) {
                if (slot < 0) slot = s
                continue
            }
            if (chokeGroups[v] == group) {
                slotVoice[s] = -1 // 同じグループの音は止める
                if (slot < 0) slot = s
            } else if (slotPos[s] > oldestPos) {
                oldestPos = slotPos[s]
                oldest = s
            }
        }
        val target = if (slot >= 0) slot else oldest
        if (target < 0) return
        // 同じ波形を毎回そのまま鳴らすと、1 サンプルまで同一の音が並ぶ。
        // 強弱を付けても波形自体は同じなので、機械的に聞こえる限界がここにあった。
        //
        // 揺らすのは高さだけにしてある。音量のばらつきはアクセントと幽霊音で
        // すでに手で付けられるので、そこに乱数を重ねると打ち消し合う。
        // 高さのほうは、ほかに表現する手立てが無い。
        slotVoice[target] = voice
        slotPos[target] = 0.0
        slotRate[target] = PITCH_JITTER[hitCount[voice].mod(PITCH_JITTER.size)]
        slotGain[target] = velocity
        hitCount[voice]++
    }

    private fun triggerChord(
        midis: List<Int>,
        gate: Long,
        timbre: ToneSynth.Timbre,
        velocity: Float = 1f,
        arpeggio: IntArray? = null,
        arpeggioTicks: Int = ToneSynth.ARPEGGIO_TICKS,
    ) {
        releaseInstrument(Instrument.CHORD)
        for (midi in midis) {
            startTone(Instrument.CHORD, midi, gate, timbre, velocity, arpeggio, arpeggioTicks)
        }
    }

    private fun triggerNote(
        instrument: Instrument,
        midi: Int,
        gate: Long,
        timbre: ToneSynth.Timbre,
        velocity: Float = 1f,
    ) {
        releaseInstrument(instrument)
        startTone(instrument, midi, gate, timbre, velocity)
    }

    /** 同じ楽器の音を離鍵する（切るのではなく減衰させるので音が途切れて聞こえない）。 */
    private fun releaseInstrument(instrument: Instrument) {
        for (voice in toneVoices) {
            if (voice.instrument == instrument) voice.release()
        }
    }

    private fun startTone(
        instrument: Instrument,
        midi: Int,
        gate: Long,
        timbre: ToneSynth.Timbre,
        velocity: Float,
        arpeggio: IntArray? = null,
        arpeggioTicks: Int = ToneSynth.ARPEGGIO_TICKS,
    ) {
        var target: ToneVoice? = null
        var quietest: ToneVoice? = null
        for (voice in toneVoices) {
            if (voice.instrument == null) {
                target = voice
                break
            }
            if (quietest == null || voice.level < quietest.level) quietest = voice
        }
        (target ?: quietest)
            ?.start(instrument, midi, gate, sampleRate, timbre, velocity, arpeggio, arpeggioTicks)
    }

    /** 先頭のステップから鳴らし直す。フレーム数の通し番号は保ったままにする。 */
    private fun rewind() {
        java.util.Arrays.fill(slotVoice, -1)
        java.util.Arrays.fill(slotPos, 0.0)
        java.util.Arrays.fill(slotRate, 1.0)
        java.util.Arrays.fill(slotGain, 1f)
        // 頭から鳴らせば必ず同じ揺らぎになるよう、通し番号も戻す。
        java.util.Arrays.fill(hitCount, 0)
        clickSample = null
        // 前に鳴らしたぶんの尾を持ち越さない（頭から鳴らせば必ず同じ音になる）。
        reverb.clear()
        for (voice in toneVoices) voice.silence()
        absoluteStep = 0L
        nextStepFrame = framePosition.toDouble()
        timeline.clear()
    }

    /**
     * フレーム数の通し番号を 0 に戻す。
     * [framePosition] は AudioTrack に書き込んだフレーム数と一致している必要があるため、
     * 出力先を作り直したとき（音声スレッドが止まっている間）にだけ呼ぶ。
     */
    fun resetFrameClock() {
        java.util.Arrays.fill(slotVoice, -1)
        java.util.Arrays.fill(slotPos, 0.0)
        java.util.Arrays.fill(slotRate, 1.0)
        java.util.Arrays.fill(slotGain, 1f)
        // 頭から鳴らせば必ず同じ揺らぎになるよう、通し番号も戻す。
        java.util.Arrays.fill(hitCount, 0)
        clickSample = null
        // 前に鳴らしたぶんの尾を持ち越さない（頭から鳴らせば必ず同じ音になる）。
        reverb.clear()
        for (voice in toneVoices) voice.silence()
        absoluteStep = 0L
        framePosition = 0L
        nextStepFrame = 0.0
        timeline.clear()
    }

    fun framesPerStep(bpm: Int): Double = sampleRate * secondsPerStep(bpm)

    /**
     * 音程を持つ 1 音ぶんの発振器。倍音を足し合わせ、ADSR で音量を動かす。
     * 音声スレッドからのみ触る。
     */
    private class ToneVoice {
        var instrument: Instrument? = null
            private set

        /** 今の音量（発音中の音を選ぶときに使う）。 */
        var level = 0f
            private set

        private var phase = 0.0
        private var phaseStep = 0.0
        private var gate = 0L
        private var stage = Stage.IDLE
        private var attackStep = 0f
        private var decayCoefficient = 0f
        private var releaseCoefficient = 0f
        private var sustain = 0f
        private var gain = 0f
        private var harmonicCount = 0
        private val harmonics = IntArray(MAX_PARTIALS)
        private val harmonicGains = FloatArray(MAX_PARTIALS)

        // 波形を直接作るときの状態。発音のたびに [start] で決まる。
        private var waveKind = WAVE_ADDITIVE
        private var duty = 0.5f
        private var baseDuty = 0.5f
        private var noiseShort = false
        private var lfsr = ToneSynth.LFSR_SEED
        private var noiseValue = 1f

        // 1/60 秒ごとに音を動かすための状態。動かすものが無ければ丸ごと飛ばす。
        private var modulated = false
        private var modulation = ToneSynth.Modulation()
        private var baseStep = 0.0
        private var tick = 0
        private var framesPerTick = 1
        private var framesToTick = 1
        private var arpCount = 0
        private var arpTicks = ToneSynth.ARPEGGIO_TICKS
        private val arpOffsets = IntArray(MAX_ARPEGGIO)

        private enum class Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

        fun start(
            instrument: Instrument,
            midi: Int,
            gateFrames: Long,
            sampleRate: Int,
            timbre: ToneSynth.Timbre,
            /** アクセント / 幽霊音の強さ。[level]（包絡線の今の値）とは別物。 */
            velocity: Float,
            /**
             * 1/60 秒ごとに回す音程のずれ（半音）。高速アルペジオに使う。
             * 渡すと 1 声部で和音に聞こえる。
             */
            arpeggio: IntArray? = null,
            /** 音を 1 つ進めるまでの刻み数。 */
            arpeggioTicks: Int = ToneSynth.ARPEGGIO_TICKS,
        ) {
            val frequency = ToneSynth.frequency(midi)
            this.instrument = instrument
            phase = 0.0
            baseStep = frequency / sampleRate
            phaseStep = baseStep
            gate = gateFrames.coerceAtLeast(1L)
            stage = Stage.ATTACK
            level = 0f
            // 直接作る波形は倍音の山が高く、そのままだと加算合成より大きく鳴る。
            gain = timbre.gain * velocity * timbre.wave.levelTrim
            sustain = timbre.sustain
            waveKind = when (val wave = timbre.wave) {
                is ToneSynth.Waveform.Pulse -> {
                    duty = wave.duty.coerceIn(0.01f, 0.99f)
                    WAVE_PULSE
                }
                is ToneSynth.Waveform.Noise -> {
                    noiseShort = wave.shortPeriod
                    // 種は 0 以外なら何でもよい。0 だと以後ずっと 0 のまま動かない。
                    lfsr = ToneSynth.LFSR_SEED
                    noiseValue = 1f
                    WAVE_NOISE
                }
                ToneSynth.Waveform.ChipTriangle -> WAVE_CHIP_TRIANGLE
                ToneSynth.Waveform.Additive -> WAVE_ADDITIVE
            }
            baseDuty = duty

            modulation = timbre.modulation
            arpTicks = arpeggioTicks.coerceAtLeast(1)
            arpCount = 0
            if (arpeggio != null) {
                val count = minOf(arpeggio.size, MAX_ARPEGGIO)
                for (i in 0 until count) arpOffsets[i] = arpeggio[i]
                arpCount = count
            }
            modulated = arpCount > 1 || modulation.active
            tick = 0
            framesPerTick = (sampleRate / ToneSynth.TICKS_PER_SECOND).toInt().coerceAtLeast(1)
            framesToTick = framesPerTick
            if (modulated) applyModulation()
            attackStep = (1.0 / (timbre.attack * sampleRate).coerceAtLeast(1.0)).toFloat()
            decayCoefficient = exp(-1.0 / (timbre.decay * sampleRate)).toFloat()
            releaseCoefficient = exp(-1.0 / (timbre.release * sampleRate)).toFloat()

            if (waveKind != WAVE_ADDITIVE) {
                // 直接作る波形は倍音表を使わない。折り返しはパルスだけ [blep] で抑える。
                harmonicCount = 0
                return
            }
            // 折り返し（エイリアス）を避けるため、ナイキスト周波数を超える倍音は捨てる。
            val nyquist = sampleRate / 2.0
            var count = 0
            var total = 0f
            for (partial in timbre.partials) {
                if (count >= MAX_PARTIALS) break
                if (frequency * partial.harmonic >= nyquist) continue
                harmonics[count] = partial.harmonic
                harmonicGains[count] = partial.gain
                total += partial.gain
                count++
            }
            harmonicCount = count
            if (total > 0f) {
                for (i in 0 until count) harmonicGains[i] /= total
            }
        }

        /** 離鍵。以降は減衰して自然に消える。 */
        fun release() {
            if (stage != Stage.IDLE) {
                stage = Stage.RELEASE
                gate = 0L
            }
        }

        /** 即座に消す（再生し直しなど）。 */
        fun silence() {
            instrument = null
            stage = Stage.IDLE
            level = 0f
        }

        fun render(out: FloatArray, offset: Int, count: Int, gainLeft: Float, gainRight: Float) {
            if (stage == Stage.IDLE) return
            val amplitudeLeft = gain * gainLeft
            val amplitudeRight = gain * gainRight
            for (k in 0 until count) {
                if (modulated && --framesToTick <= 0) {
                    framesToTick = framesPerTick
                    tick++
                    applyModulation()
                }
                advanceEnvelope()
                if (stage == Stage.IDLE) {
                    instrument = null
                    return
                }
                if (amplitudeLeft > 0f || amplitudeRight > 0f) {
                    val value = waveSample() * level
                    val at = (offset + k) * CHANNELS
                    out[at] += value * amplitudeLeft
                    out[at + 1] += value * amplitudeRight
                }
                phase += phaseStep
                if (phase >= 1.0) {
                    phase -= floor(phase)
                    // ノイズは 1 周期に 1 回だけ進める。音の高さがそのまま粗さになる。
                    if (waveKind == WAVE_NOISE) advanceNoise()
                }
            }
        }

        private fun waveSample(): Float = when (waveKind) {
            WAVE_PULSE -> ToneSynth.pulse(phase, duty, phaseStep)
            WAVE_CHIP_TRIANGLE -> ToneSynth.chipTriangle(phase)
            WAVE_NOISE -> noiseValue
            else -> additive()
        }

        private fun additive(): Float {
            var sample = 0f
            for (h in 0 until harmonicCount) {
                sample += ToneSynth.sine(phase * harmonics[h]) * harmonicGains[h]
            }
            return sample
        }

        /**
         * 1/60 秒ぶん進んだところで、音程とパルス幅を置き直す。
         *
         * 音を鳴らし直すのではなく発音中のものを動かすので、
         * 高速アルペジオでも切れ目が入らず 1 つの音として繋がって聞こえる。
         */
        private fun applyModulation() {
            var semitones = ToneSynth.semitoneOffset(modulation, tick)
            if (arpCount > 0) semitones += arpOffsets[ToneSynth.arpeggioIndex(tick, arpCount, arpTicks)]
            phaseStep = if (semitones == 0.0) baseStep else baseStep * 2.0.pow(semitones / 12.0)
            if (waveKind == WAVE_PULSE) duty = ToneSynth.dutyAt(modulation, tick, baseDuty)
        }

        /** LFSR を 1 段進める。1 周期に 1 回だけ呼ぶので、音の高さが粗さになる。 */
        private fun advanceNoise() {
            lfsr = ToneSynth.nextLfsr(lfsr, noiseShort)
            noiseValue = ToneSynth.lfsrOutput(lfsr)
        }

        private fun advanceEnvelope() {
            if (gate > 0) {
                gate--
                if (gate == 0L) stage = Stage.RELEASE
            }
            when (stage) {
                Stage.ATTACK -> {
                    level += attackStep
                    if (level >= 1f) {
                        level = 1f
                        stage = Stage.DECAY
                    }
                }
                Stage.DECAY -> {
                    level = sustain + (level - sustain) * decayCoefficient
                    if (level - sustain < 0.001f) {
                        level = sustain
                        stage = Stage.SUSTAIN
                    }
                }
                Stage.SUSTAIN -> Unit
                Stage.RELEASE -> {
                    level *= releaseCoefficient
                    if (level < 0.0005f) {
                        level = 0f
                        stage = Stage.IDLE
                    }
                }
                Stage.IDLE -> Unit
            }
        }

        private companion object {
            const val MAX_PARTIALS = 6

            const val WAVE_ADDITIVE = 0
            const val WAVE_PULSE = 1
            const val WAVE_CHIP_TRIANGLE = 2
            const val WAVE_NOISE = 3

            /** 高速アルペジオで回せる音の数。7 の和音でも足りる。 */
            const val MAX_ARPEGGIO = 8
        }
    }

    companion object {
        /**
         * 発音ごとの揺らぎ。読み出す速さ（＝音の高さ）をわずかにずらす。
         *
         * 表の長さを 7 にしてあるのは、4 拍や 8 分・16 分の刻みと約数を
         * 持たないため。4 や 8 だと拍と噛み合って、揺らぎ自体が周期として
         * 聞こえてしまう。
         */
        private val PITCH_JITTER = doubleArrayOf(
            1.000, 1.012, 0.991, 1.005, 0.982, 1.018, 0.996,
        )

        const val DEFAULT_SAMPLE_RATE = 44_100

        /** 次の音の直前で切って、同じ音が続くときも打ち直しがわかるようにする。 */
        private const val GATE_RATIO = 0.95

        /** ハネの上限。1 ステップの半分までずらせる（0.67 あたりが三連）。 */
        private const val MAX_SWING_SHIFT = 0.5

        /** タイで伸ばせる上限（4 小節）。書き間違いで延々と鳴り続けないようにする。 */
        private const val MAX_LEAD_HOLD_STEPS = STEPS_PER_BAR * 4

        /** 試聴で鳴らす長さ（秒）。 */
        private const val PREVIEW_SECONDS = 0.6

        /** ここを超えたぶんだけ滑らかに圧縮する（それ以下の音量は素通し）。 */
        private const val KNEE = 0.8f

        /**
         * 1 フレームぶんを左右まとめて抑える。
         *
         * 左右を別々に通すと、片方だけが抑えられたときに音が反対側へ
         * 寄ってしまう。大きいほうで抑える量を決めて両方に同じだけ掛ければ、
         * 定位は動かない。中央（左右が同じ値）なら [limit] を通したのと
         * 同じ値になる（割ってから掛け直すので、float の刻み 1 つぶんだけ
         * ずれることがある）。
         */
        fun limitFrame(out: FloatArray, at: Int) {
            val left = out[at]
            val right = out[at + 1]
            val peak = maxOf(abs(left), abs(right))
            if (peak <= KNEE) return
            val scale = limit(peak) / peak
            out[at] = left * scale
            out[at + 1] = right * scale
        }

        /**
         * 打点が重なったときの音割れを防ぐソフトリミッタ。
         * 出力は必ず -1.0..1.0 に収まり、KNEE 以下は元の値のまま。
         */
        fun limit(x: Float): Float {
            val magnitude = abs(x)
            if (magnitude <= KNEE) return x
            val over = (magnitude - KNEE) / (1f - KNEE)
            val compressed = KNEE + (1f - KNEE) * (over / (1f + over))
            return if (x < 0) -compressed else compressed
        }
    }
}
