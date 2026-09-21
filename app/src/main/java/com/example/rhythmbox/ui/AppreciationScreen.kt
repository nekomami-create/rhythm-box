package com.example.rhythmbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
        // 「今」は真ん中あたり。左は鳴った音、右はこれから鳴る音（薄く）。
        LeadTrailVisualizer(
            past = state.appreciationLeadTrail,
            future = state.appreciationLeadFuture,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = status.genre.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                // いま鳴っているのが起承転結のどこか（Aメロ/Bメロ/サビ/Cメロ）。
                OptionChip(label = status.section.label, selected = true, onClick = {})
            }
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

/** リードの実際の音域に近いところだけを縦の範囲にする（C3〜C6）。 */
private const val TRAIL_MIN_MIDI = 48
private const val TRAIL_MAX_MIDI = 84

/**
 * 未来側（[LeadTrailVisualizer] の [future]）を薄くする濃さ。まだ鳴って
 * いない・確定というより見立てであることが、見た目からも伝わるように。
 */
private const val FUTURE_ALPHA = 0.35f

/**
 * 左はじに寄るところ（過去の軌跡から溜まりを外れる直前）を、幅に対する
 * 割合でさっと薄くする。急に消えるより自然で、かつ長々とは残らない。
 */
private const val LEFT_FADE_FRACTION = 0.06f

/** 線の下にもう一段太く薄い線を重ねて、ネオンのような滲みを作る。 */
private const val GLOW_STROKE_WIDTH = 16f
private const val GLOW_ALPHA_FACTOR = 0.28f

/** オクターブごとの目安線（C の高さ）の濃さ。飾りなので薄く。 */
private const val OCTAVE_GRID_ALPHA = 0.10f

/** 「今」を貫く縦のビームの濃さ。プレイヘッドらしさを添える飾り。 */
private const val NOW_BEAM_ALPHA = 0.18f

/** 「今」の点の常時の淡い光暈（ドラムの脈動とは別に、常に薄く光らせる）。 */
private const val NOW_GLOW_RADIUS = 22f
private const val NOW_GLOW_ALPHA = 0.25f

private val CHIP_PADDING = 3.dp
private val CHIP_CORNER_RADIUS = 6.dp

/** 音名チップどうしが詰まりすぎないための最小間隔。 */
private val CHIP_MIN_SPACING = 32.dp

/**
 * いま鳴っているリードの高さの軌跡を線で描く部品。
 *
 * [past]・[future] を渡せば描く、それだけの純粋な表示部品にしてある。曲や
 * 鑑賞モードそのものへの依存は持たないので、あとから通常再生の画面でも
 * 使い回せる。「今」（[past] の最後＝[future] の直前）はだいたい画面の
 * 真ん中に来る（[past]・[future] を同じ件数だけ渡す前提）。休符（null）の
 * ところは線をつながず、そこだけ途切れさせる。線は、その音がコードの
 * 構成音か・音階の中か・外かで色を変える（[NoteRole]、ピアノロールと
 * 同じ考え方）。音名のチップは、鳴った音（過去〜今）にだけ、音が変わる
 * ところごとに添える。まだ鳴っていない（[future] 側）の音は文字を出さず
 * 線だけ薄く見せる（先読みで文字がちらつくのを避けるため）。左はじに寄る
 * ところは軌跡ごと素早くフェードアウトする。[drumPulse] が立つと、
 * キック・スネアに合わせて「今」の点のまわりが脈打つ。
 */
@Composable
private fun LeadTrailVisualizer(
    past: List<LeadTrailPoint?>,
    future: List<LeadTrailPoint?>,
    drumPulse: Float,
    modifier: Modifier = Modifier,
) {
    val chordToneColor = MaterialTheme.colorScheme.tertiary
    val scaleToneColor = MaterialTheme.colorScheme.primary
    val outsideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val nowColor = MaterialTheme.colorScheme.secondary
    val drumColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chipTextColor = MaterialTheme.colorScheme.onSurface
    val chipBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val chipTextStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val combined = past + future
        if (combined.isEmpty()) return@Canvas
        // 「今」は past の最後（future の直前）。過去・未来を同じ件数にして
        // 渡す前提なので、これがだいたい画面の真ん中に来る。
        val nowIndex = past.lastIndex
        val span = (combined.size - 1).coerceAtLeast(1)
        val leftFadeWidth = size.width * LEFT_FADE_FRACTION
        fun xAt(index: Int) = size.width * index / span
        fun yAt(midi: Int): Float {
            val t = (midi - TRAIL_MIN_MIDI).toFloat() / (TRAIL_MAX_MIDI - TRAIL_MIN_MIDI)
            return size.height * (1f - t.coerceIn(0f, 1f))
        }
        fun colorFor(role: NoteRole) = when (role) {
            NoteRole.CHORD_TONE -> chordToneColor
            NoteRole.SCALE_TONE -> scaleToneColor
            NoteRole.OUTSIDE -> outsideColor
        }
        fun alphaFor(index: Int): Float {
            // まだ鳴っていない（future 側の）ところは薄く。
            val base = if (index > nowIndex) FUTURE_ALPHA else 1f
            // 左はじへ寄るところは、溜まりから外れる前にさっと薄くする。
            val edgeFade = if (leftFadeWidth > 0f) (xAt(index) / leftFadeWidth).coerceIn(0f, 1f) else 1f
            return base * edgeFade
        }

        // 飾り: オクターブ（C）ごとの目安の横線。
        var gridMidi = TRAIL_MIN_MIDI
        while (gridMidi <= TRAIL_MAX_MIDI) {
            val y = yAt(gridMidi)
            drawLine(
                color = gridColor.copy(alpha = OCTAVE_GRID_ALPHA),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f,
            )
            gridMidi += 12
        }

        // 飾り: 「今」を貫く縦のビーム（プレイヘッド）。
        if (nowIndex >= 0) {
            val nowX = xAt(nowIndex)
            drawLine(
                color = nowColor.copy(alpha = NOW_BEAM_ALPHA),
                start = Offset(nowX, 0f),
                end = Offset(nowX, size.height),
                strokeWidth = 3f,
            )
        }

        // 軌跡の線。下にもう一段太く薄い線を重ねて、ネオンのような滲みを添える。
        var previous: Offset? = null
        combined.forEachIndexed { index, point ->
            if (point == null) {
                previous = null
                return@forEachIndexed
            }
            val here = Offset(xAt(index), yAt(point.midi))
            previous?.let { from ->
                val alpha = alphaFor(index)
                val color = colorFor(point.role)
                drawLine(
                    color = color.copy(alpha = alpha * GLOW_ALPHA_FACTOR),
                    start = from,
                    end = here,
                    strokeWidth = GLOW_STROKE_WIDTH,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = alpha),
                    start = from,
                    end = here,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }
            previous = here
        }

        if (nowIndex >= 0) {
            val lastPastPoint = past[nowIndex]
            if (lastPastPoint != null) {
                val center = Offset(xAt(nowIndex), yAt(lastPastPoint.midi))
                // 常時の淡い光暈。ドラムの脈動が乗るとさらに広がる。
                drawCircle(color = nowColor.copy(alpha = NOW_GLOW_ALPHA), radius = NOW_GLOW_RADIUS, center = center)
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

        // 音名のチップは、鳴った音（過去〜今）だけに添える。まだ鳴っていない
        // 未来側（index > nowIndex）には出さない。音が変わるところ（その音の
        // 鳴り始め）ごとに 1 つ、詰まりすぎないよう最小間隔は空けて出す。
        val paddingPx = CHIP_PADDING.toPx()
        val minSpacingPx = CHIP_MIN_SPACING.toPx()
        var lastChipX = Float.NEGATIVE_INFINITY
        for (index in 0..nowIndex) {
            val point = combined.getOrNull(index) ?: continue
            val isNoteStart = index == 0 || combined[index - 1]?.midi != point.midi
            if (!isNoteStart) continue
            val x = xAt(index)
            if (x - lastChipX < minSpacingPx) continue
            lastChipX = x
            val alpha = alphaFor(index)
            val measured = textMeasurer.measure(midiName(point.midi), style = chipTextStyle)
            val chipSize = Size(
                measured.size.width + paddingPx * 2,
                measured.size.height + paddingPx * 2,
            )
            val chipTopLeft = Offset(
                (x - chipSize.width / 2f).coerceIn(0f, (size.width - chipSize.width).coerceAtLeast(0f)),
                (yAt(point.midi) - chipSize.height - 14f).coerceAtLeast(0f),
            )
            drawRoundRect(
                color = chipBackgroundColor.copy(alpha = alpha),
                topLeft = chipTopLeft,
                size = chipSize,
                cornerRadius = CornerRadius(CHIP_CORNER_RADIUS.toPx()),
            )
            drawText(
                textLayoutResult = measured,
                color = chipTextColor.copy(alpha = alpha),
                topLeft = chipTopLeft + Offset(paddingPx, paddingPx),
            )
        }
    }
}
