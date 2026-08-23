package com.example.rhythmbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.rhythmbox.ui.RhythmBoxRoot
import com.example.rhythmbox.ui.RhythmViewModel
import com.example.rhythmbox.ui.theme.RhythmBoxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RhythmViewModel by viewModels {
        RhythmViewModel.factory((application as RhythmBoxApp).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        // 画面を離れたら音を止め、編集内容を確実に書き出す。
        viewModel.onScreenPaused()
        super.onPause()
    }
}
