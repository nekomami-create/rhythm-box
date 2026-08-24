package com.example.rhythmbox.audio

import android.content.Context
import android.net.Uri
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 曲を M4A (AAC) ファイルとして書き出す。
 *
 * 端末によっては保存先（SAF の Uri）に直接書けないことがあるため、
 * いったんキャッシュに書いてからコピーする。
 */
class AudioExporter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    data class Result(val seconds: Double, val bytes: Long)

    suspend fun export(
        song: Song,
        plan: PlaybackPlan,
        voiceSamples: List<FloatArray>,
        sampleRate: Int,
        destination: Uri,
        onProgress: (Float) -> Unit,
    ): Result = withContext(dispatcher) {
        val samples = OfflineRenderer.render(song, plan, voiceSamples, sampleRate) {
            onProgress(it * RENDER_SHARE)
        }
        check(samples.isNotEmpty()) { "書き出す小節がありません" }

        val temp = File(context.cacheDir, "export-${System.currentTimeMillis()}.m4a")
        try {
            AacEncoder.encodeToM4a(OfflineRenderer.toPcm16(samples), sampleRate, temp) {
                onProgress(RENDER_SHARE + it * ENCODE_SHARE)
            }
            val stream = context.contentResolver.openOutputStream(destination)
                ?: error("保存先を開けませんでした")
            stream.use { output -> temp.inputStream().use { it.copyTo(output) } }
            onProgress(1f)
            Result(seconds = samples.size.toDouble() / sampleRate, bytes = temp.length())
        } finally {
            temp.delete()
        }
    }

    private companion object {
        // 進捗表示の内訳（合成 → 圧縮 → 保存）。
        const val RENDER_SHARE = 0.55f
        const val ENCODE_SHARE = 0.40f
    }
}
