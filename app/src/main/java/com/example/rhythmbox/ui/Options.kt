package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 選択肢を出すつまみと行。
 *
 * 「選ばれているものを色と太字で示す」という同じ見た目を、音色・ドラム・弾き方・
 * アルペジオの速さ・音源・生成範囲・パッドの切り替えでそれぞれ書いていた。
 * 余白が 8dp と 10dp、字が labelSmall と 11sp で混ざっていたのもそのため。
 * ここに寄せて、設定が増えても 1 行で足せるようにする。
 */

/** 選択肢ひとつぶんのつまみ。 */
@Composable
fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(CHIP_HEIGHT).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = CHIP_PADDING),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * 見出し付きの選択肢の列。「弾き方 [和音][上へ][下へ]」のような並び。
 * [labelWidth] を渡すと見出しの幅が揃うので、縦に並べたときに端が合う。
 */
@Composable
fun <T> OptionChips(
    label: String?,
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                modifier = if (labelWidth != null) Modifier.width(labelWidth) else Modifier,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        options.forEach { option ->
            OptionChip(
                label = labelOf(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * 横幅いっぱいの選択肢の行。つまみに収まらない長さのものに使う。
 * [detail] は右側に小さく添える補足（音階の構成音、場面の説明など）。
 */
@Composable
fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** つまみの高さと左右の余白。ここだけ直せば全部の選択肢が揃う。 */
private val CHIP_HEIGHT = 28.dp
private val CHIP_PADDING = 10.dp

/**
 * ミキサーで選べる左右の位置。
 *
 * 曲には -1.0〜1.0 の数として持たせてある（後から刻みを増やしても
 * 保存の形は変わらない）が、画面では 5 段から選ぶ。細かいつまみを
 * 11 トラックぶん縦に並べると場所を食ううえ、指では狙えない。
 * ドラムを少し散らす、という使い方に必要なのはこれで足りる。
 */
enum class PanPosition(val label: String, val value: Float) {
    LEFT("左", -1f),
    HALF_LEFT("やや左", -0.5f),
    CENTRE("中央", 0f),
    HALF_RIGHT("やや右", 0.5f),
    RIGHT("右", 1f),
    ;

    companion object {
        /** いちばん近い段。刻みに乗っていない値が入っていても選べる。 */
        fun nearest(pan: Float): PanPosition =
            entries.minByOrNull { abs(it.value - pan) } ?: CENTRE
    }
}
