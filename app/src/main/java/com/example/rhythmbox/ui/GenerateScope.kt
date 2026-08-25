package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「ランダム」を押したときに、どこまで書き換えるか。
 *
 * 全部まとめて振り直すと、気に入っている小節まで巻き添えで消える。
 * 逆に 1 小節ずつしか作れないと、まるごと作りたいときに手間がかかる。
 * どちらも要るので、押す前に範囲を選べるようにしている。
 */
enum class GenerateScope(val label: String) {
    /** いま開いている 1 小節だけ。 */
    BAR("この小節"),

    /** いま開いているパターン。 */
    PATTERN("このパターン"),

    /** 中身のあるパターンすべて。 */
    ALL("全パターン"),
}

/**
 * 範囲を選ぶチップの列。
 *
 * 長押しや隠し操作にせず、いま何が起きるのかを画面に出しておく。
 */
@Composable
fun ScopeChips(
    scopes: List<GenerateScope>,
    selected: GenerateScope,
    onSelect: (GenerateScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "範囲",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        scopes.forEach { scope ->
            val on = scope == selected
            Surface(
                color = if (on) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (on) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp).clickable { onSelect(scope) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = scope.label,
                        modifier = Modifier.padding(horizontal = 10.dp),
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
