package com.example.rhythmbox.web

import org.khronos.webgl.Int16Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set

/**
 * lamejs の npm パッケージ（1.2.1）は package.json の "main" が
 * ビルド前の未バンドルソース（src/js/index.js）を指しており、
 * require/webpack 経由で読み込むと未定義のグローバル参照
 * （MPEGMode 等）でそのまま壊れる。配布物に同梱されている
 * 正式なバンドル（lame.min.js）だけが単体で動く作りなので、
 * そちらを静的ファイルとして resources に置き、index.html で
 * <script> 読み込みしてグローバルの lamejs を使う
 * （npm 依存にはしない。Mp3Test.kt で実ブラウザ動作を確認済み）。
 */
external object lamejs {
    class Mp3Encoder(channels: Int, sampleRate: Int, kbps: Int) {
        fun encodeBuffer(left: Int16Array, right: Int16Array): Int8Array
        fun flush(): Int8Array
    }
}

/** 16bit PCM（L/R 交互）を MP3 にエンコードする。 */
fun encodeMp3Stereo(interleaved: ShortArray, sampleRate: Int, kbps: Int = 128): ByteArray {
    val frames = interleaved.size / 2
    val left = Int16Array(frames)
    val right = Int16Array(frames)
    for (i in 0 until frames) {
        left[i] = interleaved[i * 2]
        right[i] = interleaved[i * 2 + 1]
    }
    val encoder = lamejs.Mp3Encoder(2, sampleRate, kbps)
    val chunks = mutableListOf<Int8Array>()
    val blockSize = 1152
    var i = 0
    while (i < frames) {
        val end = minOf(i + blockSize, frames)
        val buf = encoder.encodeBuffer(left.subarray(i, end), right.subarray(i, end))
        if (buf.length > 0) chunks += buf
        i = end
    }
    val tail = encoder.flush()
    if (tail.length > 0) chunks += tail

    val totalLength = chunks.sumOf { it.length }
    val out = ByteArray(totalLength)
    var offset = 0
    for (chunk in chunks) {
        for (j in 0 until chunk.length) {
            out[offset + j] = chunk[j]
        }
        offset += chunk.length
    }
    return out
}
