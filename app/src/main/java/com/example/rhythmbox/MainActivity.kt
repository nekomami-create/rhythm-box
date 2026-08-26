package com.example.rhythmbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.rhythmbox.ui.RhythmBoxRoot
import com.example.rhythmbox.ui.RhythmViewModel
import com.example.rhythmbox.ui.theme.RhythmBoxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RhythmViewModel by viewModels {
        RhythmViewModel.factory((application as RhythmBoxApp).container)
    }

    /**
     * 通知の許可。断られても音は鳴り続ける（前面サービス自体は動く）が、
     * 通知が出ないぶん、画面を見ずに止める手立てが無くなる。
     */
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            RhythmBoxTheme {
                RhythmBoxRoot(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        // 鳴っている間は止めない（前面サービスがプロセスを保つ）。
        // 止まっているときだけ音声スレッドを畳んで、編集内容を確実に書き出す。
        viewModel.onScreenPaused()
        super.onPause()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted == PackageManager.PERMISSION_GRANTED) return
        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
