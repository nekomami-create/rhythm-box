package com.example.rhythmbox.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.midiName

/** ピアノロールで扱う音域（C4 〜 C6）。 */
private const val LOWEST_MIDI = 60
private const val HIGHEST_MIDI = 84

/** 白鍵かどうか。 */
private fun isWhiteKey(midi: Int): Boolean = midi.mod(12) in setOf(0, 2, 4, 5, 7, 9, 11)

@Composable
fun LeadScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    val pattern = state.pattern
    val playingStep = state.leadGridStep
    val leadTrack = state.song.track(Instrument.LEAD.trackIndex)
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeadHeader(
            state = state,
            muted = leadTrack.muted,
            barLabel = "${state.selectedLeadBar + 1} 小節目 ・ コード " +
                "${viewModel.chordForLeadBar(state.selectedLeadBar).name} ・ " +
                "${pattern.leadNoteCount()} 音",
            onPlayToggle = { viewModel.toggle(PlayMode.PATTERN) },
            onToggleMute = { viewModel.toggleMute(Instrument.LEAD.trackIndex) },
            onGenerate = viewModel::generateMelody,
            onUndo = viewModel::undoGenerate,
            onClear = viewModel::clearLead,
        )

        Text(
            text = "縦が音の高さ、横が 16 ステップ。同じマスをもう一度押すと消えます。\n音を長くするには、伸ばしたいところまでを長押し（音の上を長押しで元に戻る）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 「ランダム」がどこまで書き換えるか。気に入った小節を巻き添えにしないため。
        ScopeChips(
            scopes = listOf(GenerateScope.BAR, GenerateScope.PATTERN, GenerateScope.ALL),
            selected = state.leadScope,
            onSelect = viewModel::setLeadScope,
        )

        // 同じパターンを繰り返すとき、旋律だけは小節ごとに変えられる。
        LeadBarSelector(
            count = pattern.leadBarCount,
            selected = state.selectedLeadBar,
            playing = state.playingLeadBar.takeIf { state.playingPattern == state.selectedPattern } ?: -1,
            chordAt = viewModel::chordForLeadBar,
            onSelect = viewModel::selectLeadBar,
            onCountChange = viewModel::setLeadBarCount,
            onClearBar = viewModel::clearLeadBar,
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (midi in HIGHEST_MIDI downTo LOWEST_MIDI) {
                PianoRollRow(
                    midi = midi,
                    pattern = pattern,
                    leadBar = state.selectedLeadBar,
                    playingStep = playingStep,
                    scrollState = horizontalScroll,
                    onToggle = { step -> viewModel.toggleLead(step, midi) },
                    onHold = viewModel::holdLead,
                    onPreview = { viewModel.previewLead(midi) },
                )
            }
        }
    }
}

@Composable
private fun LeadHeader(
    state: RhythmUiState,
    muted: Boolean,
    barLabel: String,
    onPlayToggle: () -> Unit,
    onToggleMute: () -> Unit,
    onGenerate: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    val playing = state.isPlaying && state.mode == PlayMode.PATTERN
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (playing) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "停止" else "再生",
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "パターン ${state.pattern.name} のリード",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = barLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleMute) {
                    Icon(
                        imageVector = if (muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (muted) "リードのミュートを解除" else "リードをミュート",
                        tint = if (muted) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGenerate, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ランダム")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全消し")
                }
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("戻す")
                }
            }
        }
    }
}

/**
 * 何回目の小節を編集するかを選ぶ行。
 * ドラムは同じでも、旋律は小節ごとに変えないと下のコードから外れてしまう。
 */
@Composable
private fun LeadBarSelector(
    count: Int,
    selected: Int,
    playing: Int,
    chordAt: (Int) -> Chord,
    onSelect: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
    onClearBar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
            enabled = count < Pattern.MAX_LEAD_BARS,
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
            Icon(
                Icons.Filled.Delete,
                contentDescription = "この小節の音を消す",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PianoRollRow(
    midi: Int,
    pattern: Pattern,
    leadBar: Int,
    playingStep: Int,
    scrollState: ScrollState,
    onToggle: (Int) -> Unit,
    onHold: (Int) -> Unit,
    onPreview: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val white = isWhiteKey(midi)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端の鍵盤。押すとその音だけ試聴できる。
        Surface(
            color = if (white) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(46.dp).height(ROW_HEIGHT).clickable { onPreview() },
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Text(
                    text = midiName(midi),
                    modifier = Modifier.padding(start = 6.dp),
                    fontSize = 10.sp,
                    color = if (white) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(STEPS_PER_BAR) { step ->
                val head = pattern.leadAt(leadBar, step) == midi
                // 伸ばしている途中のマス。音の頭ではないが、この高さの音が鳴り続けている。
                val held = !head && pattern.soundingLead(leadBar, step) == midi
                val on = head || held
                val playing = step == playingStep
                val color = when {
                    on && playing -> scheme.tertiary
                    head -> scheme.secondary
                    held -> scheme.secondary.copy(alpha = 0.45f)
                    playing -> scheme.outline
                    step % 4 == 0 -> scheme.surfaceContainerHigh
                    white -> scheme.surfaceVariant.copy(alpha = 0.40f)
                    else -> scheme.surfaceVariant.copy(alpha = 0.22f)
                }
                Box(
                    modifier = Modifier
                        .width(CELL_WIDTH)
                        // 伸ばしている途中は少し細くして、音の頭が分かるようにする。
                        .height(if (held) ROW_HEIGHT - 8.dp else ROW_HEIGHT)
                        .background(color, RoundedCornerShape(4.dp))
                        .combinedClickable(
                            onClick = { onToggle(step) },
                            onLongClick = { onHold(step) },
                        ),
                )
            }
        }
    }
}

private val ROW_HEIGHT = 22.dp
private val CELL_WIDTH = 28.dp
