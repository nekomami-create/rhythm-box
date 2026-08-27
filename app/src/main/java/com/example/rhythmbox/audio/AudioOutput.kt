package com.example.rhythmbox.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.core.StepTimeline

/**
 * [PlaybackEngine] が作る PCM を AudioTrack に流し込む。
 * 画面が見えている間はスレッドを回しっぱなしにして、
 * 再生ボタンやパッドを押した瞬間に音が出るようにしている。
 */
class AudioOutput(
    private val engine: PlaybackEngine,
    /** 端末が 1 回に受け取りたいフレーム数。ここに合わせて書くと余計な溜め込みが起きない。 */
    framesPerBurst: Int = DEFAULT_BURST,
) {

    /**
     * 1 回に書き込むフレーム数。小さいほど反応が速く、小さすぎると音が途切れる。
     * 端末が言ってきた単位をそのまま使うのが、いちばん短くて安全な線になる。
     */
    private val blockFrames = framesPerBurst.coerceIn(64, 1_024)

    /** 曲構成の終端に到達して自然に停止したときに呼ばれる。 */
    var onPlaybackFinished: (() -> Unit)? = null

    /**
     * 端末が実際に何を返したか。
     *
     * 遅れの原因は端末ごとに違うのに、これまで見えないまま値を推測で
     * 詰めていた。低遅延の経路をもらえたのか、バッファがどれだけ確保
     * されたのかが分からないと、次にどこを直せばいいか決められない。
     */
    data class Report(
        val sampleRate: Int,
        val blockFrames: Int,
        /** AudioTrack が確保した器の大きさ。 */
        val capacityFrames: Int,
        /** 実際に溜める量。ここが遅れの主因になる。 */
        val bufferFrames: Int,
        /** 低遅延の経路をもらえたか。断られると溜める量が増える。 */
        val lowLatency: Boolean,
        /** 描き間に合わずに音が途切れた回数。0 でなければ詰めすぎ。 */
        val underruns: Int,
    ) {
        /** 溜めているぶんが何ミリ秒か。叩いてから鳴るまでの下限の目安。 */
        val bufferMillis: Double get() = bufferFrames * 1_000.0 / sampleRate
    }

    @Volatile
    var report: Report? = null
        private set

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Synchronized
    fun resume() {
        if (running) return
        val sampleRate = engine.sampleRate
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(blockFrames * BYTES_PER_FLOAT)
        // 溜める量が、そのまま叩いてから鳴るまでの遅れになる。
        // 2 回ぶんだけ持たせて、あとは端末に任せる。
        val bufferBytes = maxOf(minBytes, blockFrames * BYTES_PER_FLOAT * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            return
        }
        // 器は大きめに確保されることがある（getMinBufferSize が端末の
        // 受け取り単位よりずっと大きい機種がある）。実際に溜める量は
        // あとから縮められるので、ここで 2 回ぶんまで詰める。
        // 断られたら戻り値が実際の値になるので、それをそのまま記録する。
        val requested = blockFrames * 2
        val accepted = runCatching { audioTrack.setBufferSizeInFrames(requested) }
            .getOrDefault(audioTrack.bufferSizeInFrames)
        track = audioTrack
        report = Report(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            capacityFrames = audioTrack.bufferCapacityInFrames,
            bufferFrames = if (accepted > 0) accepted else audioTrack.bufferSizeInFrames,
            lowLatency = audioTrack.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY,
            underruns = 0,
        )
        running = true
        // AudioTrack を作り直すと再生位置が 0 に戻るので、エンジン側の通し番号も合わせる。
        engine.resetFrameClock()
        audioTrack.play()
        thread = Thread({ renderLoop(audioTrack) }, "rhythmbox-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun pause() {
        val worker = thread
        running = false
        thread = null
        worker?.join(500)
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            it.release()
        }
        track = null
    }

    /** 鳴らしている最中か。プレビューのたびに resume() を呼ばずに済ませるために見る。 */
    val isRunning: Boolean get() = running

    /** スピーカーから実際に鳴っているフレーム。叩いた位置を測る基準にする。 */
    fun currentFrame(): Long? =
        track?.playbackHeadPosition?.toLong()?.and(0xFFFF_FFFFL)

    /** スピーカーから実際に鳴っている位置（表示を音に合わせるために使う）。 */
    fun currentPosition(): StepTimeline.Position? {
        val played = currentFrame() ?: return null
        return engine.timeline.positionAt(played)
    }

    private fun renderLoop(audioTrack: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(blockFrames)
        var wasPlaying = false
        while (running) {
            val playing = engine.render(buffer)
            val written = audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break // デバイス切断など
            if (wasPlaying && !playing) onPlaybackFinished?.invoke()
            wasPlaying = playing
            // 途切れた回数は、詰めすぎたかどうかの唯一の手掛かり。
            report?.let { current ->
                val count = runCatching { audioTrack.underrunCount }.getOrDefault(current.underruns)
                if (count != current.underruns) report = current.copy(underruns = count)
            }
        }
    }

    private companion object {
        /** 端末が何も言ってこなかったときの単位。 */
        const val DEFAULT_BURST = 192
        const val BYTES_PER_FLOAT = 4
    }
}
