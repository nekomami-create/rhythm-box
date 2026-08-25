package com.example.rhythmbox.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * ボタンが 3 つ横に並ぶ行の余白。
 * 既定（左右 24dp）のままだと 1 つぶんの幅に絵と字が入りきらず、字が折り返してしまう。
 */
val TIGHT_BUTTON_PADDING = PaddingValues(horizontal = 8.dp, vertical = 6.dp)

/** スライダーの高さ。既定は 48dp あって縦に場所を取りすぎるので詰める。 */
val SLIDER_HEIGHT = 30.dp
