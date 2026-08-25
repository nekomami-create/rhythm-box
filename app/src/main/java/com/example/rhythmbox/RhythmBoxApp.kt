package com.example.rhythmbox

import android.app.Application
import android.content.Context
import android.media.AudioManager
import com.example.rhythmbox.audio.AudioExporter
import com.example.rhythmbox.audio.AudioOutput
import com.example.rhythmbox.core.DrumSynth
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.files.FileExporter
import com.example.rhythmbox.data.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.io.File

/** 依存関係をまとめて持つだけの簡易 DI コンテナ。 */
class AppContainer(private val application: Application) {
    val scope: CoroutineScope = MainScope()

    private val audioManager =
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * 端末の音声回路が実際に動いている周波数。
     *
     * ここを決め打ちにすると（多くの端末は 48kHz なのに 44.1kHz を渡すなど）、
     * システムがリサンプラーを挟み、その時点で低遅延の経路から外れてしまう。
     */
    val sampleRate: Int = audioManager
        .getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        ?.toIntOrNull()
        ?.takeIf { it in 8_000..192_000 }
        ?: PlaybackEngine.DEFAULT_SAMPLE_RATE

    /**
     * 端末が 1 回に受け取りたいフレーム数。
     * この単位で書くと余計な溜め込みが起きず、叩いてから鳴るまでが短くなる。
     */
    val framesPerBurst: Int = audioManager
        .getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        ?.toIntOrNull()
        ?.takeIf { it in 32..2_048 }
        ?: DEFAULT_BURST

    /** ドラムの波形。再生と書き出しで同じものを使う。 */
    val drumSamples: List<FloatArray> by lazy { DrumSynth.renderAll(sampleRate) }

    val engine: PlaybackEngine by lazy { PlaybackEngine(sampleRate, drumSamples) }

    val audioOutput: AudioOutput by lazy { AudioOutput(engine, framesPerBurst) }

    val audioExporter: AudioExporter by lazy { AudioExporter(application) }

    val fileExporter: FileExporter by lazy { FileExporter(application) }

    val songRepository: SongRepository by lazy {
        SongRepository(File(application.filesDir, "songs.json"), scope)
    }
}

private const val DEFAULT_BURST = 192

class RhythmBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 音源の合成に少し時間がかかるので、起動直後に裏で作っておく。
        Thread({ container.engine }, "rhythmbox-warmup").start()
    }
}
