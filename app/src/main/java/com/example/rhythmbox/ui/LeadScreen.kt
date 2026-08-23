package com.example.rhythmbox.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val playingStep = if (state.isPlaying && state.mode == PlayMode.PATTERN) state.playingStep else -1
    val leadTrack = state.song.track(Instrument.LEAD.trackIndex)
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeadHeader(
            state = state,
            muted = leadTrack.muted,
            onPlayToggle = { viewModel.toggle(PlayMode.PATTERN) },
            onToggleMute = { viewModel.toggleMute(Instrument.LEAD.trackIndex) },
            onClear = viewModel::clearLead,
        )

        Text(
            text = "縦が音の高さ、横が 16 ステップ。同じマスをもう一度押すと消えます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (midi in HIGHEST_MIDI downTo LOWEST_MIDI) {
                PianoRollRow(
                    midi = midi,
                    pattern = pattern,
                    playingStep = playingStep,
                    scrollState = horizontalScroll,
                    onToggle = { step -> viewModel.toggleLead(step, midi) },
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
    onPlayToggle: () -> Unit,
    onToggleMute: () -> Unit,
    onClear: () -> Unit,
) {
    val playing = state.isPlaying && state.mode == PlayMode.PATTERN
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
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
                    text = "コード ${state.patternChord.name} ・ ${state.pattern.lead.count { it != Pattern.REST }} 音",
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
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("全消し")
            }
        }
    }
}

@Composable
private fun PianoRollRow(
    midi: Int,
    pattern: Pattern,
    playingStep: Int,
    scrollState: ScrollState,
    onToggle: (Int) -> Unit,
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
                val on = pattern.leadAt(step) == midi
                val playing = step == playingStep
                val color = when {
                    on && playing -> scheme.tertiary
                    on -> scheme.secondary
                    playing -> scheme.outline
                    step % 4 == 0 -> scheme.surfaceContainerHigh
                    white -> scheme.surfaceVariant.copy(alpha = 0.40f)
                    else -> scheme.surfaceVariant.copy(alpha = 0.22f)
                }
                Box(
                    modifier = Modifier
                        .width(CELL_WIDTH)
                        .height(ROW_HEIGHT)
                        .background(color, RoundedCornerShape(4.dp))
                        .clickable { onToggle(step) },
                )
            }
        }
    }
}

private val ROW_HEIGHT = 22.dp
private val CELL_WIDTH = 28.dp
