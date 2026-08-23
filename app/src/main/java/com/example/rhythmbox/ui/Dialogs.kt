package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.Voice

/** トップバーのメニューから開くダイアログの種類。 */
enum class SongDialog { Rename, New, Library }

@Composable
fun SongDialogHost(
    dialog: SongDialog?,
    state: RhythmUiState,
    viewModel: RhythmViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        null -> Unit
        SongDialog.Rename -> TextInputDialog(
            title = "曲の名前を変更",
            initialValue = state.song.name,
            confirmLabel = "変更",
            onConfirm = {
                viewModel.renameSong(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SongDialog.New -> TextInputDialog(
            title = "新しい曲",
            initialValue = "新しい曲",
            confirmLabel = "作成",
            onConfirm = {
                viewModel.createSong(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SongDialog.Library -> SongLibraryDialog(
            songs = state.library,
            currentId = state.song.id,
            onSelect = {
                viewModel.selectSong(it)
                onDismiss()
            },
            onDelete = viewModel::deleteSong,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("曲名") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun SongLibraryDialog(
    songs: List<Song>,
    currentId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存した曲") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(songs, key = { it.id }) { song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect(song.id) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = song.name,
                                fontWeight = if (song.id == currentId) FontWeight.Bold else FontWeight.Normal,
                                color = if (song.id == currentId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = "${song.bpm} BPM ・ ${song.totalBars()} 小節",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onDelete(song.id) },
                            enabled = songs.size > 1,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "${song.name}を削除")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** パターン A〜H を選ばせる汎用ダイアログ。 */
@Composable
fun PatternPickerDialog(
    title: String,
    patterns: List<Pattern>,
    disabledIndex: Int? = null,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patterns.chunked(4).forEachIndexed { rowIndex, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEachIndexed { columnIndex, pattern ->
                            val index = rowIndex * 4 + columnIndex
                            val enabled = index != disabledIndex
                            Surface(
                                color = if (pattern.isEmpty()) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (pattern.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable(enabled = enabled) { onPick(index) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = pattern.name, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/** 音色ごとの音量・ミュート。 */
@Composable
fun MixerDialog(
    song: Song,
    onVolumeChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onClearVoice: (Int) -> Unit,
    onUnmuteAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ミキサー") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Voice.entries.forEachIndexed { index, voice ->
                    val track = song.tracks[index]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = voice.shortLabel,
                            modifier = Modifier.width(34.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = track.volume,
                            onValueChange = { onVolumeChange(index, it) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onToggleMute(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (track.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (track.muted) "ミュート解除" else "ミュート",
                                tint = if (track.muted) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { onClearVoice(index) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.ClearAll,
                                contentDescription = "${voice.label}の打ち込みを消す",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = onUnmuteAll) { Text("すべてのミュートを解除") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}
