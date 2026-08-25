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
class AudioOutput(private val engine: PlaybackEngine) {

    /** 曲構成の終端に到達して自然に停止したときに呼ばれる。 */
    var onPlaybackFinished: (() -> Unit)? = null

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
        ).coerceAtLeast(BLOCK_FRAMES * BYTES_PER_FLOAT)
        val bufferBytes = maxOf(minBytes, BLOCK_FRAMES * BYTES_PER_FLOAT * 4)

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
        track = audioTrack
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
        val buffer = FloatArray(BLOCK_FRAMES)
        var wasPlaying = false
        while (running) {
            val playing = engine.render(buffer)
            val written = audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break // デバイス切断など
            if (wasPlaying && !playing) onPlaybackFinished?.invoke()
            wasPlaying = playing
        }
    }

    private companion object {
        /** 1 回に書き込むフレーム数。小さいほど反応が速く、小さすぎると音が途切れる。 */
        const val BLOCK_FRAMES = 256
        const val BYTES_PER_FLOAT = 4
    }
}
