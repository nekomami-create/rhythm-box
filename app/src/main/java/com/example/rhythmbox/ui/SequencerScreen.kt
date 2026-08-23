package com.example.rhythmbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.RhythmStyle
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.Voice

/** グリッドの 1 行ぶんの情報。ドラムの後ろにコードとベースが並ぶ。 */
data class StepRowInfo(
    val row: Int,
    val label: String,
    val fullLabel: String,
    val trackIndex: Int,
    val melodic: Boolean,
)

val stepRows: List<StepRowInfo> = buildList {
    Voice.entries.forEachIndexed { index, voice ->
        add(StepRowInfo(index, voice.shortLabel, voice.label, index, melodic = false))
    }
    add(
        StepRowInfo(
            ROW_CHORD,
            Instrument.CHORD.shortLabel,
            Instrument.CHORD.label,
            Instrument.CHORD.trackIndex,
            melodic = true,
        ),
    )
    add(
        StepRowInfo(
            ROW_BASS,
            Instrument.BASS.shortLabel,
            Instrument.BASS.label,
            Instrument.BASS.trackIndex,
            melodic = true,
        ),
    )
}

@Composable
fun SequencerScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    var mixerOpen by remember { mutableStateOf(false) }
    var copyTargetOpen by remember { mutableStateOf(false) }
    var chordPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransportPanel(
            state = state,
            onPlayToggle = { viewModel.toggle(PlayMode.PATTERN) },
            onBpmChange = viewModel::setBpm,
            onVolumeChange = viewModel::setMasterVolume,
            onOpenMixer = { mixerOpen = true },
        )

        PatternSelector(
            patterns = state.song.patterns,
            selected = state.selectedPattern,
            chordName = state.patternChord.name,
            canUndo = state.canUndo,
            onSelect = viewModel::selectPattern,
            onClear = viewModel::clearPattern,
            onCopy = { copyTargetOpen = true },
            onChordClick = { chordPickerOpen = true },
            onGenerate = viewModel::generateRhythm,
            onUndo = viewModel::undoGenerate,
        )

        StepGrid(
            pattern = state.pattern,
            song = state.song,
            playingStep = if (state.isPlaying && state.mode == PlayMode.PATTERN) state.playingStep else -1,
            onToggle = viewModel::toggleStep,
            onPreview = viewModel::previewRow,
            onToggleMute = viewModel::toggleMute,
        )
    }

    if (mixerOpen) {
        MixerDialog(
            song = state.song,
            onVolumeChange = viewModel::setTrackVolume,
            onToggleMute = viewModel::toggleMute,
            onUnmuteAll = viewModel::unmuteAll,
            onDismiss = { mixerOpen = false },
        )
    }
    if (copyTargetOpen) {
        PatternPickerDialog(
            title = "コピー先のパターン",
            patterns = state.song.patterns,
            disabledIndex = state.selectedPattern,
            onPick = {
                viewModel.copyPatternTo(it)
                copyTargetOpen = false
            },
            onDismiss = { copyTargetOpen = false },
        )
    }
    if (chordPickerOpen) {
        ChordPickerDialog(
            title = "パターン ${state.pattern.name} のコード",
            current = state.patternChord,
            suggestions = viewModel.chordSuggestions(null),
            keyName = viewModel.detectedKey().name,
            onPreview = viewModel::previewChord,
            onPick = {
                viewModel.setPatternChord(it)
                chordPickerOpen = false
            },
            onDismiss = { chordPickerOpen = false },
        )
    }
}

/** 再生ボタン・テンポ・マスター音量。 */
@Composable
private fun TransportPanel(
    state: RhythmUiState,
    onPlayToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenMixer: () -> Unit,
) {
    val playing = state.isPlaying && state.mode == PlayMode.PATTERN
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier.size(58.dp),
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
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("テンポ", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.song.bpm} BPM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onBpmChange(state.song.bpm - 1) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "テンポを下げる")
                        }
                        Slider(
                            value = state.song.bpm.toFloat(),
                            onValueChange = { onBpmChange(it.toInt()) },
                            valueRange = Song.MIN_BPM.toFloat()..Song.MAX_BPM.toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onBpmChange(state.song.bpm + 1) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "テンポを上げる")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "音量",
                    modifier = Modifier.size(20.dp),
                )
                Slider(
                    value = state.song.masterVolume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onOpenMixer) {
                    Icon(Icons.Filled.Tune, contentDescription = "ミキサー")
                }
            }
        }
    }
}

/** A〜H のパターン切り替え、パターンのコード、クリア／コピー。 */
@Composable
private fun PatternSelector(
    patterns: List<Pattern>,
    selected: Int,
    chordName: String,
    canUndo: Boolean,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onChordClick: () -> Unit,
    onGenerate: (RhythmStyle?) -> Unit,
    onUndo: () -> Unit,
) {
    var styleMenuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            patterns.forEachIndexed { index, pattern ->
                val isSelected = index == selected
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = if (!isSelected && !pattern.isEmpty()) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier.size(width = 44.dp, height = 40.dp).clickable { onSelect(index) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            pattern.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // このパターンを単体で鳴らすときのコード。曲構成に足すときの初期値にもなる。
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(40.dp).clickable { onChordClick() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("コード", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        chordName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // リズムの自動生成。スタイルを選べる。
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { styleMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ランダム")
                }
                DropdownMenu(expanded = styleMenuOpen, onDismissRequest = { styleMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("おまかせ") },
                        onClick = {
                            styleMenuOpen = false
                            onGenerate(null)
                        },
                    )
                    RhythmStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.label) },
                            onClick = {
                                styleMenuOpen = false
                                onGenerate(style)
                            },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("クリア")
            }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("コピー")
            }
            OutlinedButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("戻す")
            }
        }
    }
}

/** 8 音色 + コード + ベース x 16 ステップの打ち込みグリッド。 */
@Composable
private fun StepGrid(
    pattern: Pattern,
    song: Song,
    playingStep: Int,
    onToggle: (Int, Int) -> Unit,
    onPreview: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
) {
    val scroll = rememberScrollState()
    val labelWidth = 78.dp
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth - labelWidth - gap
        val fitted = (available - gap * (STEPS_PER_BAR - 1)) / STEPS_PER_BAR
        val cellWidth = if (fitted < MIN_CELL) MIN_CELL else fitted

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row {
                Spacer(Modifier.width(labelWidth + gap))
                Row(
                    modifier = Modifier.horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(STEPS_PER_BAR) { step ->
                        Box(Modifier.width(cellWidth), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (step % 4 == 0) "${step / 4 + 1}" else "・",
                                fontSize = 11.sp,
                                color = if (step == playingStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            stepRows.forEach { info ->
                val track = song.track(info.trackIndex)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackLabel(
                        info = info,
                        muted = track.muted,
                        width = labelWidth,
                        onPreview = { onPreview(info.row) },
                        onToggleMute = { onToggleMute(info.trackIndex) },
                    )
                    Spacer(Modifier.width(gap))
                    Row(
                        modifier = Modifier.horizontalScroll(scroll),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        repeat(STEPS_PER_BAR) { step ->
                            StepCell(
                                on = pattern.isOn(info.row, step),
                                playing = step == playingStep,
                                onBeat = step % 4 == 0,
                                dimmed = track.muted,
                                melodic = info.melodic,
                                width = cellWidth,
                                onClick = { onToggle(info.row, step) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackLabel(
    info: StepRowInfo,
    muted: Boolean,
    width: Dp,
    onPreview: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(
        modifier = Modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (info.melodic) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).height(CELL_HEIGHT).clickable { onPreview() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = info.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (muted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        IconButton(onClick = onToggleMute, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (muted) {
                    Icons.AutoMirrored.Filled.VolumeOff
                } else {
                    Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = if (muted) {
                    "${info.fullLabel}のミュートを解除"
                } else {
                    "${info.fullLabel}をミュート"
                },
                tint = if (muted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StepCell(
    on: Boolean,
    playing: Boolean,
    onBeat: Boolean,
    dimmed: Boolean,
    melodic: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = if (melodic) scheme.secondary else scheme.primary
    val color = when {
        on && playing -> scheme.tertiary
        on -> activeColor
        playing -> scheme.outline
        onBeat -> scheme.surfaceContainerHigh
        else -> scheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(CELL_HEIGHT)
            .background(
                color = if (dimmed) color.copy(alpha = 0.35f) else color,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onClick() },
    ) {
        if (playing) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (on) Color.White.copy(alpha = 0.6f) else scheme.primary),
            )
        }
    }
}

private val MIN_CELL = 26.dp
private val CELL_HEIGHT = 34.dp
