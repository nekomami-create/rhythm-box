package com.example.rhythmbox

import android.app.Application
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

    val sampleRate: Int = PlaybackEngine.DEFAULT_SAMPLE_RATE

    /** ドラムの波形。再生と書き出しで同じものを使う。 */
    val drumSamples: List<FloatArray> by lazy { DrumSynth.renderAll(sampleRate) }

    val engine: PlaybackEngine by lazy { PlaybackEngine(sampleRate, drumSamples) }

    val audioOutput: AudioOutput by lazy { AudioOutput(engine) }

    val audioExporter: AudioExporter by lazy { AudioExporter(application) }

    val fileExporter: FileExporter by lazy { FileExporter(application) }

    val songRepository: SongRepository by lazy {
        SongRepository(File(application.filesDir, "songs.json"), scope)
    }
}

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
