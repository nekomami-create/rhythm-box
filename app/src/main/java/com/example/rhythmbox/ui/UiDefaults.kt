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

/**
 * 「戻す」ボタンの文字。あと何段さかのぼれるかを出す。
 * 押せる回数が分からないと、どこまで戻したのか見失う。
 */
fun undoLabel(depth: Int): String = if (depth > 1) "戻す $depth" else "戻す"
