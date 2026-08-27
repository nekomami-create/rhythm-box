package com.example.rhythmbox.core

import kotlin.math.roundToInt

/**
 * 曲を音声ファイルに書き出すための、実時間を待たないレンダリング。
 *
 * 再生と同じ [PlaybackEngine] をそのまま使うので、書き出した音は
 * アプリで聴いている音と一致する（別経路で作り直すと必ずズレる）。
 */
object OfflineRenderer {

    /** 音の余韻を残すために、曲の終わりのあとも少しだけ描き続ける秒数。 */
    const val DEFAULT_TAIL_SECONDS = 2.0

    /** 1 回に描くフレーム数。 */
    private const val BLOCK = 4096

    /** 書き出した PCM のフレーム数（左右のペアを 1 と数える）。 */
    fun frames(samples: FloatArray): Int = samples.size / CHANNELS

    /** 書き出した PCM の長さ（秒）。 */
    fun seconds(samples: FloatArray, sampleRate: Int): Double =
        frames(samples).toDouble() / sampleRate

    /**
     * その曲を鳴らし切るのに要る余韻の秒数。
     * 残響を掛けていれば、その尾が消えるまで待つ（切ると末尾がぶつ切りになる）。
     */
    fun tailFor(song: Song, base: Double = DEFAULT_TAIL_SECONDS): Double =
        if (song.reverb > 0f) maxOf(base, song.roomSize.tailSeconds) else base

    /** [plan] を鳴らし切るのに必要なフレーム数（余韻ぶんを含む）。 */
    fun frameCount(
        plan: PlaybackPlan,
        bpm: Int,
        sampleRate: Int,
        tailSeconds: Double = DEFAULT_TAIL_SECONDS,
    ): Int {
        val steps = plan.barCount * STEPS_PER_BAR
        val body = steps * secondsPerStep(bpm) * sampleRate
        return (body + tailSeconds * sampleRate).roundToInt()
    }

    /**
     * [plan] を頭から終わりまで描いて PCM（-1.0..1.0）を返す。
     * 左右が交互に並ぶので、長さはフレーム数の [CHANNELS] 倍になる。
     * [onProgress] には 0.0〜1.0 が渡る。
     */
    fun render(
        song: Song,
        plan: PlaybackPlan,
        voiceSamples: List<FloatArray>,
        sampleRate: Int = PlaybackEngine.DEFAULT_SAMPLE_RATE,
        tailSeconds: Double = DEFAULT_TAIL_SECONDS,
        /** チップ音源のドラム。渡さなければ標準のものを使い回す。 */
        chipVoiceSamples: List<FloatArray> = voiceSamples,
        onProgress: ((Float) -> Unit)? = null,
    ): FloatArray {
        if (plan.isEmpty) return FloatArray(0)

        val total = frameCount(plan, song.bpm, sampleRate, tailFor(song, tailSeconds))
        val output = FloatArray(total * CHANNELS)
        val engine = PlaybackEngine(sampleRate, voiceSamples, chipVoiceSamples)
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
            loop = false, // 書き出しは 1 回ぶんだけ
        )
        engine.start()

        val block = FloatArray(BLOCK * CHANNELS)
        var written = 0
        while (written < total) {
            engine.render(block)
            val count = minOf(BLOCK, total - written)
            System.arraycopy(block, 0, output, written * CHANNELS, count * CHANNELS)
            written += count
            onProgress?.invoke(written.toFloat() / total)
        }
        return output
    }

    /** PCM を 16bit 整数に変換する（エンコーダに渡す形）。 */
    fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { index ->
        (samples[index].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
    }
}
