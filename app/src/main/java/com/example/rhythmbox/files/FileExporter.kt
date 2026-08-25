package com.example.rhythmbox.files

import android.content.Context
import android.net.Uri

/**
 * バイト列やテキストを、利用者が選んだ保存先（SAF の Uri）に書く。
 *
 * MIDI と曲データはどちらも「作ったものを 1 ファイル書くだけ」なので、
 * 音声の書き出しのような進捗表示や一時ファイルは要らない。
 */
class FileExporter(private val context: Context) {

    fun write(destination: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("保存先を開けませんでした")
        stream.use { it.write(bytes) }
    }

    fun readText(source: Uri): String {
        val stream = context.contentResolver.openInputStream(source)
            ?: error("ファイルを開けませんでした")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
