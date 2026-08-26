package com.example.rhythmbox.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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

/**
 * 再生位置が右のほうまで来たら、先が見えるように横へ送る。
 *
 * ステップが画面に入りきらない幅だと、再生位置は右端に貼り付いたまま
 * 見えなくなってしまう。見えている幅の残りが [KEEP_AHEAD] を切ったところで
 * 送り、ループで先頭に戻ったら左へ引き戻す。
 *
 * [enabled] は画面上部の追従スイッチ。切っているときに勝手に動くと、
 * 自分でスクロールして見ている場所から引き剥がされることになる。
 */
@Composable
fun FollowPlayhead(
    scroll: ScrollState,
    step: Int,
    cellWidth: Dp,
    gap: Dp,
    enabled: Boolean,
) {
    val density = LocalDensity.current
    LaunchedEffect(step, enabled, scroll.maxValue, scroll.viewportSize) {
        // 全部が一度に見えている（スクロールの余地が無い）なら何もしない。
        if (!enabled || step < 0 || scroll.maxValue <= 0) return@LaunchedEffect
        val viewport = scroll.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val pitch = with(density) { (cellWidth + gap).toPx() }
        val left = step * pitch
        val right = left + with(density) { cellWidth.toPx() }
        val target = when {
            // 右へ進んで、見えている幅の残り 3 割まで来た。
            right > scroll.value + viewport * KEEP_AHEAD -> left - viewport * (1f - KEEP_AHEAD)
            // 先頭側へ飛んだ（ループした）。
            left < scroll.value -> left
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(target.toInt().coerceIn(0, scroll.maxValue))
    }
}

/** 再生位置がここまで来たら送る。見えている幅の 7 割（残り 3 割）。 */
private const val KEEP_AHEAD = 0.7f
