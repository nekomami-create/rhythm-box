package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.ChordQuality
import com.example.rhythmbox.core.ChordSuggestion
import com.example.rhythmbox.core.Instrument
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

/**
 * ルート音と種類を選んでコードを決めるダイアログ。選ぶたびに試聴できる。
 * [suggestions] には「この流れなら次はこれ」というおすすめが入る。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordPickerDialog(
    title: String,
    current: Chord,
    suggestions: List<ChordSuggestion> = emptyList(),
    keyName: String? = null,
    /** 前後のコード。「Am → ? → F」のように、何に挟まれているかを見せる。 */
    neighbours: Pair<Chord?, Chord?> = null to null,
    onPreview: (Chord) -> Unit,
    onPick: (Chord) -> Unit,
    onDismiss: () -> Unit,
) {
    var root by remember { mutableStateOf(current.root.mod(12)) }
    var quality by remember { mutableStateOf(current.quality) }
    val chord = Chord(root, quality)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = chord.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (suggestions.isNotEmpty()) {
                    val (before, after) = neighbours
                    val heading = when {
                        before != null && after != null -> "${before.name} と ${after.name} の間に合うコード"
                        before != null -> "${before.name} のつぎに合うコード"
                        after != null -> "${after.name} の前に合うコード"
                        else -> "このキーでよく使うコード"
                    }
                    Text(
                        text = heading + if (keyName != null) "（$keyName）" else "",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            val picked = suggestion.chord.root.mod(12) == root &&
                                suggestion.chord.quality == quality
                            Surface(
                                color = if (picked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                },
                                contentColor = if (picked) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(40.dp).clickable {
                                    root = suggestion.chord.root.mod(12)
                                    quality = suggestion.chord.quality
                                    onPreview(suggestion.chord)
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = suggestion.chord.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = suggestion.degree,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Text("ルート音", style = MaterialTheme.typography.labelMedium)
                Chord.ROOT_NAMES.chunked(4).forEachIndexed { rowIndex, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEachIndexed { columnIndex, name ->
                            val value = rowIndex * 4 + columnIndex
                            PickerChip(
                                label = name,
                                selected = value == root,
                                width = 58.dp,
                                onClick = {
                                    root = value
                                    onPreview(Chord(value, quality))
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text("種類", style = MaterialTheme.typography.labelMedium)
                ChordQuality.entries.chunked(4).forEach { qualities ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        qualities.forEach { value ->
                            PickerChip(
                                label = value.suffix.ifEmpty { "maj" },
                                selected = value == quality,
                                width = 58.dp,
                                onClick = {
                                    quality = value
                                    onPreview(Chord(root, value))
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chord) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(width).height(40.dp).clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/** トラックごとの音量・ミュート（ドラム 8 音色 + コード / ベース / リード）。 */
@Composable
fun MixerDialog(
    song: Song,
    onVolumeChange: (Int, Float) -> Unit,
    onToggleMute: (Int) -> Unit,
    onUnmuteAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val channels = buildList {
        Voice.entries.forEachIndexed { index, voice -> add(index to voice.shortLabel) }
        Instrument.entries.forEach { add(it.trackIndex to it.shortLabel) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ミキサー") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                channels.forEach { (track, label) ->
                    val setting = song.track(track)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            modifier = Modifier.width(42.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = setting.volume,
                            onValueChange = { onVolumeChange(track, it) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onToggleMute(track) }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (setting.muted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (setting.muted) "ミュート解除" else "ミュート",
                                tint = if (setting.muted) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
