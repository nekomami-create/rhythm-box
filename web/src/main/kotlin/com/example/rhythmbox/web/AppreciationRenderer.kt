package com.example.rhythmbox.web

import com.example.rhythmbox.core.CHANNELS
import com.example.rhythmbox.core.ChordStyle
import com.example.rhythmbox.core.DrumKit
import com.example.rhythmbox.core.EngineConfig
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.SoundSet
import com.example.rhythmbox.core.ToneSynth
import com.example.rhythmbox.core.secondsPerStep

/**
 * 鑑賞モード（[com.example.rhythmbox.core.Appreciation.Stream]）を音声にする。
 *
 * [OfflineRenderer] は曲（[com.example.rhythmbox.core.Song]）を前提にしていて、
 * 曲の間ずっと同じ BPM・音色という決め打ちがある。鑑賞モードは場面が
 * 変わるたびにテンポも音色も変わるので、そのままは使えない。
 *
 * [PlaybackEngine.render] はブロックをまたいで止めずに呼べば、途中で
 * [PlaybackEngine.config] を差し替えても再生位置（ステップの数え上げ）が
 * 続きから進むようにできている（[PlaybackEngine.start] で 1 度だけ巻き戻る）。
 * ここではその性質を使って、ブロックの境目でだけ設定を差し替えながら描く。
 */
object AppreciationRenderer {

    /** 1 ブロック（[com.example.rhythmbox.core.Appreciation.BLOCK] 小節）ぶんの設定。 */
    data class BlockInfo(
        val bpm: Int,
        val chip: Boolean,
        val leadVoice: ToneSynth.LeadVoice,
        val bassStyle: com.example.rhythmbox.core.BassStyle,
    )

    fun render(
        plan: PlaybackPlan,
        blocks: List<BlockInfo>,
        barsPerBlock: Int,
        drumSamples: List<FloatArray>,
        chipDrumSamples: List<FloatArray>,
        sampleRate: Int,
        tailSeconds: Double = OfflineRenderer.DEFAULT_TAIL_SECONDS,
    ): FloatArray {
        if (blocks.isEmpty() || plan.isEmpty) return FloatArray(0)

        val frameCounts = blocks.map { info ->
            (barsPerBlock * STEPS_PER_BAR * secondsPerStep(info.bpm) * sampleRate).let { kotlin.math.round(it).toInt() }
        }
        val bodyFrames = frameCounts.sum()
        val tailFrames = kotlin.math.round(tailSeconds * sampleRate).toInt()
        val totalFrames = bodyFrames + tailFrames
        val output = FloatArray(totalFrames * CHANNELS)

        val engine = PlaybackEngine(sampleRate, drumSamples, chipDrumSamples)
        engine.config = engineConfigFor(plan, blocks.first())
        engine.start()

        var written = 0
        blocks.forEachIndexed { index, info ->
            engine.config = engineConfigFor(plan, info)
            val frames = frameCounts[index]
            val chunk = FloatArray(frames * CHANNELS)
            engine.render(chunk)
            chunk.copyInto(output, written * CHANNELS)
            written += frames
        }
        val remaining = totalFrames - written
        if (remaining > 0) {
            val chunk = FloatArray(remaining * CHANNELS)
            engine.render(chunk)
            chunk.copyInto(output, written * CHANNELS)
        }
        return output
    }

    private fun engineConfigFor(plan: PlaybackPlan, info: BlockInfo): EngineConfig = EngineConfig(
        plan = plan,
        bpm = info.bpm,
        // 鑑賞モードの生実装（RhythmViewModel.pushAppreciationPlan）と同じ決め打ち。
        chordStyle = ChordStyle.BLOCK,
        leadVoice = if (info.chip) info.leadVoice else ToneSynth.LeadVoice.SQUARE,
        drumKit = if (info.chip) DrumKit.CHIP else DrumKit.NORMAL,
        soundSet = if (info.chip) SoundSet.CHIP else SoundSet.NORMAL,
        bassStyle = info.bassStyle,
        loop = false,
    )
}
