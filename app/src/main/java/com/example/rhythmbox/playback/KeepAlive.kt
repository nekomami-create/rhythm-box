package com.example.rhythmbox.playback

import android.app.Application
import android.content.Intent
import android.os.Build

/**
 * 再生中だけ前面サービスを立てて、画面を消してもアプリごと止められないようにする。
 *
 * Android は画面が消えたアプリのプロセスを都合のいいときに凍結・破棄する。
 * 音を出し続けるには「これは音楽の再生中です」と申告する必要があり、
 * その申告の手段が通知つきの前面サービスになっている。
 *
 * ViewModel から Android の API を触らずに済むよう、ここに閉じ込めている。
 */
class KeepAlive(private val application: Application) {

    /**
     * 通知の「停止」を押されたときに呼ばれる。実際の停止処理は ViewModel が差し込む。
     * サービスは音を持っていないので、自分では止められない。
     */
    @Volatile
    var onStopRequested: (() -> Unit)? = null

    @Volatile
    private var running = false

    /** 再生を始めたときに呼ぶ。[title] は通知に出す曲名。 */
    @Synchronized
    fun start(title: String) {
        val intent = Intent(application, PlaybackService::class.java)
            .putExtra(PlaybackService.EXTRA_TITLE, title)
        // 立て直しても通知が二重にならないので、曲名の更新も同じ道でよい。
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                application.startForegroundService(intent)
            } else {
                application.startService(intent)
            }
        }.onSuccess { running = true }
    }

    /** 止めたときに呼ぶ。通知も一緒に消える。 */
    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        runCatching { application.stopService(Intent(application, PlaybackService::class.java)) }
    }
}
