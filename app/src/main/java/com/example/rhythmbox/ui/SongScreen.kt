package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.ArrangementStep
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.formatDuration

/** どの小節のコードを編集しているか。 */
private data class ChordTarget(val stepIndex: Int, val barInBlock: Int)

@Composable
fun SongScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    var addOpen by remember { mutableStateOf(false) }
    var editingPattern by remember { mutableStateOf<Int?>(null) }
    var editingChord by remember { mutableStateOf<ChordTarget?>(null) }
    val song = state.song
    // 各ブロックが曲の何小節目から始まるか。再生位置の表示に使う。
    val startBars = song.arrangement.runningFold(0) { acc, step -> acc + step.repeat }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SongTransport(
            state = state,
            onPlayToggle = { viewModel.toggle(PlayMode.SONG) },
            onLoopChange = viewModel::setLoopSong,
        )

        if (song.arrangement.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "パターンを並べて曲を組み立てます。\n" +
                        "下の「パターンを追加」から始めてください。\n" +
                        "小節ごとのコードは各行の下に並ぶボタンで変えられます。",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(song.arrangement) { index, step ->
                val playingBarInBlock = if (
                    state.isPlaying && state.mode == PlayMode.SONG &&
                    state.playingBar >= startBars[index] &&
                    state.playingBar < startBars[index] + step.repeat
                ) {
                    state.playingBar - startBars[index]
                } else {
                    -1
                }
                ArrangementRow(
                    order = index + 1,
                    step = step,
                    song = song,
                    playingBarInBlock = playingBarInBlock,
                    canMoveUp = index > 0,
                    canMoveDown = index < song.arrangement.lastIndex,
                    onPatternClick = { editingPattern = index },
                    onChordClick = { bar -> editingChord = ChordTarget(index, bar) },
                    onRepeatChange = { viewModel.setArrangementRepeat(index, it) },
                    onMove = { viewModel.moveArrangementStep(index, it) },
                    onRemove = { viewModel.removeArrangementStep(index) },
                )
            }
        }

        Button(onClick = { addOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("パターンを追加")
        }
    }

    if (addOpen) {
        PatternPickerDialog(
            title = "追加するパターン",
            patterns = song.patterns,
            onPick = {
                viewModel.addArrangementStep(it)
                addOpen = false
            },
            onDismiss = { addOpen = false },
        )
    }
    editingPattern?.let { index ->
        PatternPickerDialog(
            title = "${index + 1} 番目のパターン",
            patterns = song.patterns,
            onPick = {
                viewModel.setArrangementPattern(index, it)
                editingPattern = null
            },
            onDismiss = { editingPattern = null },
        )
    }
    editingChord?.let { target ->
        val step = song.arrangement.getOrNull(target.stepIndex)
        if (step == null) {
            editingChord = null
        } else {
            ChordPickerDialog(
                title = "${target.stepIndex + 1}-${target.barInBlock + 1} 小節目",
                current = step.chordAt(target.barInBlock, song.patternChord(step.patternIndex)),
                onPreview = viewModel::previewChord,
                onPick = {
                    viewModel.setArrangementChord(target.stepIndex, target.barInBlock, it)
                    editingChord = null
                },
                onDismiss = { editingChord = null },
            )
        }
    }
}

@Composable
private fun SongTransport(
    state: RhythmUiState,
    onPlayToggle: () -> Unit,
    onLoopChange: (Boolean) -> Unit,
) {
    val song = state.song
    val playing = state.isPlaying && state.mode == PlayMode.SONG
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onPlayToggle,
                enabled = song.arrangement.isNotEmpty(),
                modifier = Modifier.size(52.dp),
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
                    contentDescription = if (playing) "停止" else "曲を再生",
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${song.totalBars()} 小節 ・ ${formatDuration(song.totalSeconds())}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${song.bpm} BPM" + if (playing) " ・ ${state.playingBar + 1} 小節目" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ループ", style = MaterialTheme.typography.labelSmall)
                Switch(checked = state.loopSong, onCheckedChange = onLoopChange)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArrangementRow(
    order: Int,
    step: ArrangementStep,
    song: Song,
    playingBarInBlock: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPatternClick: () -> Unit,
    onChordClick: (Int) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val fallback = song.patternChord(step.patternIndex)
    Surface(
        color = if (playingBarInBlock >= 0) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$order",
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onPatternClick,
                    modifier = Modifier.width(56.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(song.pattern(step.patternIndex).name, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { onRepeatChange(step.repeat - 1) },
                    enabled = step.repeat > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = "繰り返しを減らす",
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(modifier = Modifier.width(46.dp), contentAlignment = Alignment.Center) {
                    Text("${step.repeat} 小節", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { onRepeatChange(step.repeat + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "繰り返しを増やす",
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "上へ",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "下へ",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "削除", modifier = Modifier.size(18.dp))
                }
            }
            // 小節ごとのコード。押すと変更できる。
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(step.repeat) { bar ->
                    val chord = step.chordAt(bar, fallback)
                    val playing = bar == playingBarInBlock
                    Surface(
                        color = if (playing) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
                        },
                        contentColor = if (playing) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp).clickable { onChordClick(bar) },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = chord.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
