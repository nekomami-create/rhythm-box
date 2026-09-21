package com.example.rhythmbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.Appreciation
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.NoteRole
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SongBuilder
import com.example.rhythmbox.core.midiName

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
                "ジャンルは始めたら変わりませんが、調とテンポは場面ごとに移り変わります。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val appreciation = state.appreciation
        if (state.appreciating && appreciation != null) {
            AppreciationStatusCard(appreciation, state.appreciationChord)
            // テンポだけは聴きながら自分で動かせる（ジャンルは変えない）。
            AppreciationTempoRow(bpm = appreciation.bpm, onBpmChange = viewModel::setAppreciationBpm)
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
            drumPulse = state.appreciationDrumPulse,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }

    if (pickerOpen) {
        GenreDialog(
            title = "鑑賞モードを始める",
            confirmLabel = "始める",
            note = "最初のジャンルだけ選べます。始めたらジャンルは変わりません。" +
                "調とテンポはおまかせで決まり、しばらく流すと場面ごと移り変わっていきます。",
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

/** テンポの行。ジャンル・調は変えず、テンポだけ聴きながら自分で動かせる。 */
@Composable
private fun AppreciationTempoRow(bpm: Int, onBpmChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onBpmChange(bpm - 1) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "テンポを下げる", modifier = Modifier.size(18.dp))
        }
        Slider(
            value = bpm.toFloat(),
            onValueChange = { onBpmChange(it.toInt()) },
            valueRange = Song.MIN_BPM.toFloat()..Song.MAX_BPM.toFloat(),
            modifier = Modifier.weight(1f).height(SLIDER_HEIGHT),
        )
        IconButton(onClick = { onBpmChange(bpm + 1) }, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "テンポを上げる", modifier = Modifier.size(18.dp))
        }
        Text(
            text = "$bpm BPM",
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.End,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** [LeadTrailVisualizer] が横に敷く軌跡の長さ。RhythmViewModel の同名の値と揃えてある。 */
private const val TRAIL_SPAN = 200

/** リードの実際の音域に近いところだけを縦の範囲にする（C3〜C6）。 */
private const val TRAIL_MIN_MIDI = 48
private const val TRAIL_MAX_MIDI = 84

/** 「今」の点を右はじからどれだけ内側に置くか。右いっぱいだと詰まって見えるため。 */
private const val NOW_INSET_FRACTION = 0.8f

/**
 * いま鳴っているリードの高さの軌跡を線で描く部品。
 *
 * [trail] を渡せば描く、それだけの純粋な表示部品にしてある。曲や鑑賞モード
 * そのものへの依存は持たないので、あとから通常再生の画面でも使い回せる。
 * 休符（null）のところは線をつながず、そこだけ途切れさせる。線は、その音が
 * コードの構成音か・音階の中か・外かで色を変える（[NoteRole]、ピアノロールと
 * 同じ考え方）。[drumPulse] が立つと、キック・スネアに合わせて「今」の点の
 * まわりが脈打つ。
 */
@Composable
private fun LeadTrailVisualizer(
    trail: List<LeadTrailPoint?>,
    drumPulse: Float,
    modifier: Modifier = Modifier,
) {
    val chordToneColor = MaterialTheme.colorScheme.tertiary
    val scaleToneColor = MaterialTheme.colorScheme.primary
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nowColor = MaterialTheme.colorScheme.secondary
    val drumColor = MaterialTheme.colorScheme.error

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (trail.isEmpty()) return@Canvas
            val span = (TRAIL_SPAN - 1).coerceAtLeast(1)
            // 溜まった件数ぶんだけ、右端（＝今）に寄せて描く。件数が増えるにつれて
            // 左からせり出してくる、流れていく見た目になる。「今」の点そのものは
            // 右いっぱいではなく、少し内側（NOW_INSET_FRACTION）に置く。
            val startIndex = (TRAIL_SPAN - trail.size).coerceAtLeast(0)
            fun xAt(index: Int) = size.width * NOW_INSET_FRACTION * (startIndex + index) / span
            fun yAt(midi: Int): Float {
                val t = (midi - TRAIL_MIN_MIDI).toFloat() / (TRAIL_MAX_MIDI - TRAIL_MIN_MIDI)
                return size.height * (1f - t.coerceIn(0f, 1f))
            }
            fun colorFor(role: NoteRole) = when (role) {
                NoteRole.CHORD_TONE -> chordToneColor
                NoteRole.SCALE_TONE -> scaleToneColor
                NoteRole.OUTSIDE -> outsideColor
            }

            var previous: Offset? = null
            trail.forEachIndexed { index, point ->
                if (point == null) {
                    previous = null
                    return@forEachIndexed
                }
                val here = Offset(xAt(index), yAt(point.midi))
                previous?.let { from ->
                    drawLine(
                        color = colorFor(point.role),
                        start = from,
                        end = here,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
                previous = here
            }

            val lastPoint = trail.lastOrNull()
            if (lastPoint != null) {
                val center = Offset(xAt(trail.lastIndex), yAt(lastPoint.midi))
                // ドラム（キック・スネア）の脈動。減衰につれて広がりながら薄くなる。
                if (drumPulse > 0.02f) {
                    drawCircle(
                        color = drumColor.copy(alpha = drumPulse * 0.5f),
                        radius = 9f + drumPulse * 30f,
                        center = center,
                    )
                }
                drawCircle(color = nowColor, radius = 9f, center = center)
            }
        }

        val lastMidi = trail.lastOrNull()?.midi
        NoteChip(
            label = lastMidi?.let(::midiName) ?: "・・・",
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
    }
}

/** いま鳴っている音名を出すだけの小さなチップ。 */
@Composable
private fun NoteChip(label: String, modifier: Modifier = Modifier) {
    OptionChip(label = label, selected = true, onClick = {}, modifier = modifier)
}
