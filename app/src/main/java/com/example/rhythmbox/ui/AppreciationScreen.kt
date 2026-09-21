package com.example.rhythmbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.Appreciation
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.SongBuilder

/**
 * 鑑賞モード。終わりなく新しい小節を作って流し続ける、聴くだけの画面。
 *
 * 開いている曲・パターン・曲構成は一切触らない（[Appreciation.Stream] が
 * 内部で持つ、使い捨ての流れ）。ここに来ても他の画面の内容は変わらないし、
 * 他の画面に戻っても鑑賞モードの再生はそのまま続く。
 */
@Composable
fun AppreciationScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    // 始めるときだけジャンルを選べる。調・テンポはおまかせで決まる。
    var pickerOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "リズム・コード進行・リードを揃えたまま、終わりなく新しい小節を作って" +
                "流し続けます。曲構成やパターンには触れない、聴くだけの再生です。" +
                "しばらくすると場面（ジャンル・調・テンポ）ごと移り変わっていきます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val appreciation = state.appreciation
        if (state.appreciating && appreciation != null) {
            AppreciationStatusCard(appreciation, state.appreciationChord)
        }

        OutlinedButton(
            onClick = {
                if (state.appreciating) viewModel.stopAppreciating() else pickerOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.appreciating) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.appreciating) "止める" else "始める")
        }

        // 画面の残り（だいたい下半分）に、鳴っているリードの上がり下がりを描く。
        LeadTrailVisualizer(
            trail = state.appreciationLeadTrail,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }

    if (pickerOpen) {
        GenreDialog(
            title = "鑑賞モードを始める",
            confirmLabel = "始める",
            note = "最初のジャンルだけ選べます。調とテンポはおまかせで決まり、" +
                "しばらく流すと場面ごと移り変わっていきます。",
            showOptions = false,
            allowRandom = true,
            onApply = { genre, scene, _, _ ->
                viewModel.startAppreciating(genre, null, scene)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun AppreciationStatusCard(status: Appreciation.Status, chord: Chord?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = chord?.name ?: "・・・",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status.genre.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${status.key.name} ・ ${status.bpm} BPM",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "この場面になって ${status.blocksIntoEra} ブロック目" +
                    "（${status.blocksIntoEra * SongBuilder.BLOCK} 小節ぶん）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [LeadTrailVisualizer] が横に敷く軌跡の長さ。RhythmViewModel の同名の値と揃えてある。 */
private const val TRAIL_SPAN = 200

/** リードの実際の音域に近いところだけを縦の範囲にする（C3〜C6）。 */
private const val TRAIL_MIN_MIDI = 48
private const val TRAIL_MAX_MIDI = 84

/**
 * いま鳴っているリードの高さの軌跡を線で描くだけの部品。
 *
 * [trail] を渡せば描く、それだけの純粋な表示部品にしてある。曲や鑑賞モード
 * そのものへの依存は持たないので、あとから通常再生の画面でも使い回せる。
 * 休符（null）のところは線をつながず、そこだけ途切れさせる。
 */
@Composable
private fun LeadTrailVisualizer(trail: List<Int?>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val nowColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier) {
        if (trail.isEmpty()) return@Canvas
        val span = (TRAIL_SPAN - 1).coerceAtLeast(1)
        // 溜まった件数ぶんだけ、右端（＝今）に寄せて描く。件数が増えるにつれて
        // 左からせり出してくる、流れていく見た目になる。
        val startIndex = (TRAIL_SPAN - trail.size).coerceAtLeast(0)
        fun xAt(index: Int) = size.width * (startIndex + index) / span
        fun yAt(midi: Int): Float {
            val t = (midi - TRAIL_MIN_MIDI).toFloat() / (TRAIL_MAX_MIDI - TRAIL_MIN_MIDI)
            return size.height * (1f - t.coerceIn(0f, 1f))
        }

        val path = Path()
        var drawing = false
        trail.forEachIndexed { index, pitch ->
            if (pitch == null) {
                drawing = false
                return@forEachIndexed
            }
            val point = Offset(xAt(index), yAt(pitch))
            if (drawing) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
            drawing = true
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val lastPitch = trail.lastOrNull()
        if (lastPitch != null) {
            drawCircle(
                color = nowColor,
                radius = 9f,
                center = Offset(xAt(trail.lastIndex), yAt(lastPitch)),
            )
        }
    }
}
