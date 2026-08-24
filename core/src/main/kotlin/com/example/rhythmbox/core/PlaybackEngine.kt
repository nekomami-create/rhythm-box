package com.example.rhythmbox.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

/** 音声スレッドが 1 ブロックごとに読む設定のスナップショット（不変）。 */
data class EngineConfig(
    val plan: PlaybackPlan,
    val bpm: Int = Song.DEFAULT_BPM,
    val masterVolume: Float = 0.75f,
    val trackVolumes: List<Float> = List(TRACK_COUNT) { 0.7f },
    val mutes: List<Boolean> = List(TRACK_COUNT) { false },
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
    private val slotPos = IntArray(maxPolyphony)
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

    /** コードを単発で鳴らす（コードを選んだときの試聴用）。 */
    fun previewChord(chord: Chord) {
        triggerChord(chord.voicing(), (sampleRate * PREVIEW_SECONDS).toLong())
    }

    /** コード以外の音程楽器を単発で鳴らす。 */
    fun previewNote(instrument: Instrument, midi: Int) {
        triggerNote(instrument, midi, (sampleRate * PREVIEW_SECONDS).toLong())
    }

    /**
     * [out] の先頭 [frames] フレームぶんを描画する。既存の内容は上書きされる。
     * 戻り値は再生中かどうか（曲構成の終端で false になる）。
     */
    fun render(out: FloatArray, frames: Int = out.size): Boolean {
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
            java.util.Arrays.fill(out, i, i + chunk, 0f)
            renderDrums(out, i, chunk, cfg)
            renderTones(out, i, chunk, cfg)
            for (j in i until i + chunk) out[j] = limit(out[j])
            framePosition += chunk
            i += chunk
        }
        return isPlaying
    }

    /** 鳴っているドラム（減衰中のものを含む）をミックスする。 */
    private fun renderDrums(out: FloatArray, offset: Int, count: Int, cfg: EngineConfig) {
        val master = cfg.masterVolume
        for (slot in 0 until maxPolyphony) {
            val voice = slotVoice[slot]
            if (voice < 0) continue
            val sample = voiceSamples[voice]
            val gain = master * trackGain(cfg, voice)
            var pos = slotPos[slot]
            var k = 0
            if (gain > 0f) {
                while (k < count && pos < sample.size) {
                    out[offset + k] += sample[pos] * gain
                    k++
                    pos++
                }
            }
            // ミュート中でも再生位置は進める（解除したときに続きから鳴らない）。
            pos += (count - k)
            if (pos >= sample.size) {
                slotVoice[slot] = -1
            } else {
                slotPos[slot] = pos
            }
        }
    }

    /** コード / ベース / リードを合成してミックスする。 */
    private fun renderTones(out: FloatArray, offset: Int, count: Int, cfg: EngineConfig) {
        val master = cfg.masterVolume
        for (voice in toneVoices) {
            val instrument = voice.instrument ?: continue
            voice.render(out, offset, count, master * trackGain(cfg, instrument.trackIndex))
        }
    }

    private fun trackGain(cfg: EngineConfig, track: Int): Float =
        if (cfg.mutes.getOrElse(track) { false }) 0f else cfg.trackVolumes.getOrElse(track) { 0.7f }

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
        val leadBar = plan.leadBarAt(bar)

        for (voice in 0 until DRUM_COUNT) {
            if (pattern.isOn(voice, step)) triggerDrum(voice)
        }
        if (pattern.isOn(ROW_CHORD, step)) {
            triggerChord(
                chord.voicing(),
                gateFrames(pattern.nextHit(ROW_CHORD, step) - step, Instrument.CHORD, cfg.bpm),
            )
        }
        if (pattern.isOn(ROW_BASS, step)) {
            triggerNote(
                Instrument.BASS,
                chord.bassMidi(),
                gateFrames(pattern.nextHit(ROW_BASS, step) - step, Instrument.BASS, cfg.bpm),
            )
        }
        val leadMidi = pattern.leadAt(leadBar, step)
        if (Pattern.isNote(leadMidi)) {
            triggerNote(Instrument.LEAD, leadMidi, leadGate(cfg, bar, step))
        }

        timeline.record(frame = nextStepFrame.toLong(), bar = bar, step = step)
        absoluteStep++
        nextStepFrame += framesPerStep(cfg.bpm)
    }

    /**
     * リードの音を伸ばすフレーム数。タイが続くあいだは伸ばし続け、小節をまたいでも切らない。
     * タイが無い音は今までどおり「次の音まで（最長 1 拍）」で切る。
     */
    private fun leadGate(cfg: EngineConfig, bar: Int, step: Int): Long {
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
            if (pattern.leadAt(plan.leadBarAt(cursorBar), cursorStep) != Pattern.TIE) break
            held++
        }
        if (held > 1) return (held * framesPerStep(cfg.bpm) * GATE_RATIO).toLong()
        val pattern = plan.patternAt(bar)
        return gateFrames(pattern.nextLead(plan.leadBarAt(bar), step) - step, Instrument.LEAD, cfg.bpm)
    }

    /** 次の音までの長さから、実際に音を伸ばすフレーム数を決める。 */
    private fun gateFrames(steps: Int, instrument: Instrument, bpm: Int): Long {
        val limited = steps.coerceIn(1, ToneSynth.timbre(instrument).maxGateSteps)
        return (limited * framesPerStep(bpm) * GATE_RATIO).toLong()
    }

    private fun triggerDrum(voice: Int) {
        val group = chokeGroups[voice]
        var slot = -1
        var oldest = -1
        var oldestPos = -1
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
        slotVoice[target] = voice
        slotPos[target] = 0
    }

    private fun triggerChord(midis: List<Int>, gate: Long) {
        releaseInstrument(Instrument.CHORD)
        for (midi in midis) startTone(Instrument.CHORD, midi, gate)
    }

    private fun triggerNote(instrument: Instrument, midi: Int, gate: Long) {
        releaseInstrument(instrument)
        startTone(instrument, midi, gate)
    }

    /** 同じ楽器の音を離鍵する（切るのではなく減衰させるので音が途切れて聞こえない）。 */
    private fun releaseInstrument(instrument: Instrument) {
        for (voice in toneVoices) {
            if (voice.instrument == instrument) voice.release()
        }
    }

    private fun startTone(instrument: Instrument, midi: Int, gate: Long) {
        var target: ToneVoice? = null
        var quietest: ToneVoice? = null
        for (voice in toneVoices) {
            if (voice.instrument == null) {
                target = voice
                break
            }
            if (quietest == null || voice.level < quietest.level) quietest = voice
        }
        (target ?: quietest)?.start(instrument, midi, gate, sampleRate)
    }

    /** 先頭のステップから鳴らし直す。フレーム数の通し番号は保ったままにする。 */
    private fun rewind() {
        java.util.Arrays.fill(slotVoice, -1)
        java.util.Arrays.fill(slotPos, 0)
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
        java.util.Arrays.fill(slotPos, 0)
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

        private enum class Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

        fun start(instrument: Instrument, midi: Int, gateFrames: Long, sampleRate: Int) {
            val timbre = ToneSynth.timbre(instrument)
            val frequency = ToneSynth.frequency(midi)
            this.instrument = instrument
            phase = 0.0
            phaseStep = frequency / sampleRate
            gate = gateFrames.coerceAtLeast(1L)
            stage = Stage.ATTACK
            level = 0f
            gain = timbre.gain
            sustain = timbre.sustain
            attackStep = (1.0 / (timbre.attack * sampleRate).coerceAtLeast(1.0)).toFloat()
            decayCoefficient = exp(-1.0 / (timbre.decay * sampleRate)).toFloat()
            releaseCoefficient = exp(-1.0 / (timbre.release * sampleRate)).toFloat()

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

        fun render(out: FloatArray, offset: Int, count: Int, trackGain: Float) {
            if (stage == Stage.IDLE) return
            val amplitude = gain * trackGain
            for (k in 0 until count) {
                advanceEnvelope()
                if (stage == Stage.IDLE) {
                    instrument = null
                    return
                }
                if (amplitude > 0f) {
                    var sample = 0f
                    for (h in 0 until harmonicCount) {
                        sample += ToneSynth.sine(phase * harmonics[h]) * harmonicGains[h]
                    }
                    out[offset + k] += sample * level * amplitude
                }
                phase += phaseStep
                if (phase >= 1.0) phase -= floor(phase)
            }
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
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100

        /** 次の音の直前で切って、同じ音が続くときも打ち直しがわかるようにする。 */
        private const val GATE_RATIO = 0.95

        /** タイで伸ばせる上限（4 小節）。書き間違いで延々と鳴り続けないようにする。 */
        private const val MAX_LEAD_HOLD_STEPS = STEPS_PER_BAR * 4

        /** 試聴で鳴らす長さ（秒）。 */
        private const val PREVIEW_SECONDS = 0.6

        /** ここを超えたぶんだけ滑らかに圧縮する（それ以下の音量は素通し）。 */
        private const val KNEE = 0.8f

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
