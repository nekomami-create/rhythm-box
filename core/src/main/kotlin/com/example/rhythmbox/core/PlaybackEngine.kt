package com.example.rhythmbox.core

import kotlin.math.abs
import kotlin.math.floor

/** 音声スレッドが 1 ブロックごとに読む設定のスナップショット（不変）。 */
data class EngineConfig(
    val plan: PlaybackPlan,
    val bpm: Int = Song.DEFAULT_BPM,
    val masterVolume: Float = 0.75f,
    val trackVolumes: List<Float> = List(VOICE_COUNT) { 0.7f },
    val mutes: List<Boolean> = List(VOICE_COUNT) { false },
    val loop: Boolean = true,
)

/**
 * ステップシーケンサ本体。Android には依存せず、
 * 「PCM バッファを埋める」ことだけを行うので JVM の単体テストで検証できる。
 *
 * [render] は音声スレッドから、それ以外（[config] の更新や [start] / [stop]）は
 * UI スレッドから呼ばれる想定。受け渡しは @Volatile な不変オブジェクトで行う。
 */
class PlaybackEngine(
    val sampleRate: Int,
    private val voiceSamples: List<FloatArray>,
    private val maxPolyphony: Int = 24,
) {
    /** 音色ごとのチョークグループ。同じ番号の音は打ち直しで前の音を止める。 */
    private val chokeGroups = IntArray(VOICE_COUNT) { it }.also {
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
    }

    /** 単発で音を鳴らす（パッドを叩いたときのプレビュー用）。 */
    fun trigger(voice: Int) {
        if (voice in 0 until VOICE_COUNT) triggerVoice(voice)
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
            renderTails(out, i, chunk, cfg)
            framePosition += chunk
            i += chunk
        }
        return isPlaying
    }

    /** 鳴っている音（減衰中のものを含む）をミックスする。 */
    private fun renderTails(out: FloatArray, offset: Int, count: Int, cfg: EngineConfig) {
        val master = cfg.masterVolume
        for (slot in 0 until maxPolyphony) {
            val voice = slotVoice[slot]
            if (voice < 0) continue
            val sample = voiceSamples[voice]
            val gain = master * cfg.trackVolumes.getOrElse(voice) { 0.7f } *
                if (cfg.mutes.getOrElse(voice) { false }) 0f else 1f
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
        for (j in offset until offset + count) {
            out[j] = limit(out[j])
        }
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
                return
            }
            absoluteStep = 0L
            bar = 0
        }
        val step = (absoluteStep % STEPS_PER_BAR).toInt()
        val pattern = plan.patternAt(bar)
        for (voice in 0 until VOICE_COUNT) {
            if (pattern.isOn(voice, step)) triggerVoice(voice)
        }
        timeline.record(frame = nextStepFrame.toLong(), bar = bar, step = step)
        absoluteStep++
        nextStepFrame += framesPerStep(cfg.bpm)
    }

    private fun triggerVoice(voice: Int) {
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

    /** 先頭のステップから鳴らし直す。フレーム数の通し番号は保ったままにする。 */
    private fun rewind() {
        java.util.Arrays.fill(slotVoice, -1)
        java.util.Arrays.fill(slotPos, 0)
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
        absoluteStep = 0L
        framePosition = 0L
        nextStepFrame = 0.0
        timeline.clear()
    }

    fun framesPerStep(bpm: Int): Double = sampleRate * secondsPerStep(bpm)

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100

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
