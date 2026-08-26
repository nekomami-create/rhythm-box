package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.Pattern

/**
 * パターンの中の何小節目を編集するかを選ぶ帯。＋ / − がパターンの長さそのものを変える。
 *
 * 打ち込みの画面とピアノロールで同じものを出している。
 * 片方で 2 小節目を開いたのに、もう片方が 1 小節目のまま、という食い違いを避けるため
 * 選んでいる小節はアプリ全体で 1 つだけ持っている。
 */
@Composable
fun PatternBarSelector(
    count: Int,
    selected: Int,
    playing: Int,
    chordAt: (Int) -> Chord,
    onSelect: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
    onClearBar: () -> Unit,
    clearLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { bar ->
            val isSelected = bar == selected
            val isPlaying = bar == playing
            Surface(
                color = when {
                    isPlaying -> MaterialTheme.colorScheme.tertiary
                    isSelected -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSelected || isPlaying) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(42.dp).clickable { onSelect(bar) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "${bar + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = chordAt(bar).name, fontSize = 10.sp)
                }
            }
        }
        IconButton(
            onClick = { onCountChange(count + 1) },
            enabled = count < Pattern.MAX_BARS,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "小節を増やす", modifier = Modifier.size(18.dp))
        }
        IconButton(
            onClick = { onCountChange(count - 1) },
            enabled = count > 1,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "小節を減らす", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onClearBar, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = clearLabel, modifier = Modifier.size(18.dp))
        }
    }
}
