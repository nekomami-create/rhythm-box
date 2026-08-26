package com.example.rhythmbox.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.rhythmbox.MainActivity
import com.example.rhythmbox.R
import com.example.rhythmbox.RhythmBoxApp

/**
 * 再生中だけ動く前面サービス。音そのものは持っていない。
 *
 * 音は AudioOutput のスレッドが鳴らしていて、これはそのプロセスを
 * 画面が消えても畳ませないためだけに居る。通知を出すのは義務であると同時に、
 * 画面を見ずに止められる場所でもある。
 */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            keepAlive()?.onStopRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )
        // 落とされたら鳴り直すのではなく、黙って終わってほしい。
        return START_NOT_STICKY
    }

    /** タスク一覧からアプリを消されたら、鳴りっぱなしにしない。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        keepAlive()?.onStopRequested?.invoke()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun keepAlive(): KeepAlive? = (application as? RhythmBoxApp)?.container?.keepAlive

    private fun buildNotification(title: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, PlaybackService::class.java).setAction(ACTION_STOP)
        val stop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_playing)
            .setContentTitle(title)
            .setContentText(getString(R.string.playing_notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.playing_notification_stop), stop)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playing_channel_name),
            // 音を出しているだけの通知なので、音も振動も出さない。
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            description = getString(R.string.playing_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        private const val ACTION_STOP = "com.example.rhythmbox.STOP"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
