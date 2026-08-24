package com.example.rhythmbox.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * PCM を AAC に圧縮して M4A ファイルに書き出す。
 * Android 標準の [MediaCodec] と [MediaMuxer] だけを使うので、外部ライブラリは要らない。
 */
object AacEncoder {

    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val TIMEOUT_US = 10_000L
    const val DEFAULT_BIT_RATE = 128_000

    /**
     * [pcm]（16bit モノラル）を [output] に書き出す。
     * [onProgress] には 0.0〜1.0 が渡る。
     */
    fun encodeToM4a(
        pcm: ShortArray,
        sampleRate: Int,
        output: File,
        bitRate: Int = DEFAULT_BIT_RATE,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        require(pcm.isNotEmpty()) { "書き出す音がありません" }

        val format = MediaFormat.createAudioFormat(MIME, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        var fed = 0 // エンコーダに渡し終えたサンプル数
        var endOfInput = false
        val info = MediaCodec.BufferInfo()

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            while (true) {
                if (!endOfInput) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        buffer.clear()
                        val capacity = buffer.capacity() / Short.SIZE_BYTES
                        val count = minOf(capacity, pcm.size - fed)
                        val presentationUs = fed * 1_000_000L / sampleRate
                        if (count > 0) {
                            buffer.order(ByteOrder.nativeOrder())
                                .asShortBuffer()
                                .put(pcm, fed, count)
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                count * Short.SIZE_BYTES,
                                presentationUs,
                                0,
                            )
                            fed += count
                            onProgress?.invoke(fed.toFloat() / pcm.size)
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                presentationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            endOfInput = true
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // 実際の出力フォーマットが決まってから muxer を開始する。
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outputIndex)!!
                        // 先頭に来る設定データはファイルには書かない（muxer が持つ）。
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxerStarted) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            onProgress?.invoke(1f)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    private const val MAX_INPUT_SIZE = 64 * 1024
}
