package com.example.rhythmbox

import android.app.Application
import com.example.rhythmbox.audio.AudioOutput
import com.example.rhythmbox.core.DrumSynth
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.data.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.io.File

/** 依存関係をまとめて持つだけの簡易 DI コンテナ。 */
class AppContainer(application: Application) {
    val scope: CoroutineScope = MainScope()

    val engine: PlaybackEngine by lazy {
        val sampleRate = PlaybackEngine.DEFAULT_SAMPLE_RATE
        PlaybackEngine(sampleRate, DrumSynth.renderAll(sampleRate))
    }

    val audioOutput: AudioOutput by lazy { AudioOutput(engine) }

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
