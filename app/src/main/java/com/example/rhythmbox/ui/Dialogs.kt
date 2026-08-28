package com.example.rhythmbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.ArpeggioSpeed
import com.example.rhythmbox.core.BassStyle
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.ChordQuality
import com.example.rhythmbox.core.ChordStyle
import com.example.rhythmbox.core.ChordSuggestion
import com.example.rhythmbox.core.ChordCruiser
import com.example.rhythmbox.core.ChordVoicing
import com.example.rhythmbox.core.DrumKit
import com.example.rhythmbox.core.GameScene
import com.example.rhythmbox.core.Genre
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.MusicKey
import com.example.rhythmbox.core.RoomSize
import com.example.rhythmbox.core.Scale
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SoundSet
import com.example.rhythmbox.core.ToneSynth
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
    /** 「おまかせ」で引くコード。null を返したら候補が無かったということ。 */
    onShuffle: (() -> Chord?)? = null,
    onPreview: (Chord) -> Unit,
    onPick: (Chord) -> Unit,
    onDismiss: () -> Unit,
) {
    var root by remember { mutableStateOf(current.root.mod(12)) }
    var quality by remember { mutableStateOf(current.quality) }
    var bass by remember { mutableStateOf(current.bass) }
    val chord = Chord(root, quality, bass)

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = heading + if (keyName != null) "（$keyName）" else "",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (onShuffle != null) {
                            // この小節だけ引き直す。押すたびに違うものが出る。
                            TextButton(onClick = {
                                onShuffle()?.let {
                                    root = it.root.mod(12)
                                    quality = it.quality
                                    onPreview(it)
                                }
                            }) {
                                Text("おまかせ")
                            }
                        }
                    }
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
                                    bass = suggestion.chord.bass
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
                                    onPreview(Chord(value, quality, bass))
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
                                    onPreview(Chord(root, value, bass))
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                // 分数コード（オンコード）。ベースだけ別の音にすると、
                // 同じ和音でも進み方の感じが変わる。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ベース音", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = bass?.let { "${chord.name}（分数コード）" } ?: "ルートのまま",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PickerChip(
                        label = "なし",
                        selected = bass == null,
                        width = 58.dp,
                        onClick = {
                            bass = null
                            onPreview(Chord(root, quality, null))
                        },
                    )
                    // ルート以外の構成音は、そのまま置いて自然に響くベース音。
                    quality.intervals.drop(1).map { (root + it).mod(12) }.distinct().take(3)
                        .forEach { value ->
                            PickerChip(
                                label = Chord.ROOT_NAMES[value],
                                selected = bass == value,
                                width = 58.dp,
                                onClick = {
                                    bass = value
                                    onPreview(Chord(root, quality, value))
                                },
                            )
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

/**
 * ミキサーが受け取る操作をひとまとめにしたもの。
 *
 * 設定が増えるたびに引数が 1 つずつ伸びて 11 個になっていた。
 * 束ねておくと、行を描く部品へそのまま渡せる。
 */
data class MixerActions(
    val onVolumeChange: (Int, Float) -> Unit,
    val onPanChange: (Int, Float) -> Unit,
    val onHoldChange: (Int, Float) -> Unit,
    val onToggleMute: (Int) -> Unit,
    val onUnmuteAll: () -> Unit,
    val onCentreAll: () -> Unit,
    val onChordStyleChange: (ChordStyle) -> Unit,
    val onChordVoicingChange: (ChordVoicing) -> Unit,
    val onBassStyleChange: (BassStyle) -> Unit,
    val onArpeggioSpeedChange: (ArpeggioSpeed) -> Unit,
    val onLeadVoiceChange: (ToneSynth.LeadVoice) -> Unit,
    val onLeadVibratoChange: (Float) -> Unit,
    val onDrumKitChange: (DrumKit) -> Unit,
    val onSoundSetChange: (SoundSet) -> Unit,
    val onReverbChange: (Float) -> Unit,
    val onRoomSizeChange: (RoomSize) -> Unit,
)

/**
 * トラックごとの音量・ミュート（ドラム 8 音色 + コード / ベース / リード）。
 *
 * 音源とドラムキットは複数のトラックにまたがる設定なのに、「コードの行」
 * 「キックの行」の中に紛れて置かれていて探せなかった。またがるものは上に
 * まとめ、行の中はその行のものだけにしてある。
 */
@Composable
fun MixerDialog(
    song: Song,
    actions: MixerActions,
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
                SoundSection(song, actions)
                Spacer(Modifier.height(8.dp))
                channels.forEach { (track, label) ->
                    TrackRow(track, label, song, actions)
                }
                Text(
                    text = "「伸び」はコード / ベース / リードの余韻の長さです。左で短く歯切れよく、右で長く伸びます。" +
                        "コードの「弾き方」を和音以外にすると、CHD 行が鳴るたびに 1 音ずつ散らして弾きます。" +
                        "「定位」は左右のどちらから鳴るかです。中央のままなら今までと同じ音で、" +
                        "ハイハットやタムを少し振ると横に広がって聞こえます。" +
                        "「残響」は曲全体に掛かります。キックとベースには掛からないので、" +
                        "上げても土台は締まったまま、上のほうだけが広がります。" +
                        "CHD の「積み方」を「なめらか」にすると、前の和音から動きの小さい形を選ぶので、" +
                        "コードが変わっても音が飛び跳ねなくなります。" +
                        "BAS の「動き」は、ルートだけを弾くか、5 度も混ぜるか、" +
                        "次のコードへ入る音まで弾くかです。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = actions.onUnmuteAll) { Text("すべてのミュートを解除") }
                TextButton(onClick = actions.onCentreAll) { Text("定位をすべて中央に戻す") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/**
 * 複数のトラックにまたがる音の作り方。
 * 音源はコードとベースの 2 つに、ドラムキットは 8 音色すべてに効く。
 */
@Composable
private fun SoundSection(song: Song, actions: MixerActions) {
    Text(
        text = "音の作り方",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    OptionChips(
        label = "音源",
        options = SoundSet.entries,
        selected = song.soundSet,
        labelOf = { it.label },
        onSelect = actions.onSoundSetChange,
        labelWidth = SETTING_LABEL_WIDTH,
    )
    OptionChips(
        label = "ドラム",
        options = DrumKit.entries,
        selected = song.drumKit,
        labelOf = { it.label },
        onSelect = actions.onDrumKitChange,
        labelWidth = SETTING_LABEL_WIDTH,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "残響",
            modifier = Modifier.width(SETTING_LABEL_WIDTH),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = song.reverb,
            onValueChange = actions.onReverbChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = reverbLabel(song.reverb),
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 掛けていないときに広さだけ出ていても選びようがない。
    if (song.reverb > 0f) {
        OptionChips(
            label = "広さ",
            options = RoomSize.entries,
            selected = song.roomSize,
            labelOf = { it.label },
            onSelect = actions.onRoomSizeChange,
            labelWidth = SETTING_LABEL_WIDTH,
        )
    }
}

/** 1 トラックぶんの行。音量とミュートは全トラック、それ以外はその行のものだけ。 */
@Composable
private fun TrackRow(track: Int, label: String, song: Song, actions: MixerActions) {
    val setting = song.track(track)
    // 音の伸びは、音程を合成している 3 トラックでしか効かない。
    val pitched = track in Instrument.entries.map { it.trackIndex }.toSet()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(SETTING_LABEL_WIDTH),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = setting.volume,
            onValueChange = { actions.onVolumeChange(track, it) },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { actions.onToggleMute(track) },
            modifier = Modifier.size(36.dp),
        ) {
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
    OptionChips(
        label = "定位",
        options = PanPosition.entries,
        selected = PanPosition.nearest(setting.pan),
        labelOf = { it.label },
        onSelect = { actions.onPanChange(track, it.value) },
        modifier = Modifier.padding(start = 8.dp),
        labelWidth = SETTING_LABEL_WIDTH,
    )
    if (pitched) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "伸び",
                modifier = Modifier.width(SETTING_LABEL_WIDTH).padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = setting.hold,
                onValueChange = { actions.onHoldChange(track, it) },
                modifier = Modifier.weight(1f),
            )
            Text(
                text = holdLabel(setting.hold),
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // コード行だけ、和音をまとめて鳴らすか 1 音ずつ散らすかを選べる。
    if (track == Instrument.CHORD.trackIndex) {
        OptionChips(
            label = "弾き方",
            options = ChordStyle.entries,
            selected = song.chordStyle,
            labelOf = { it.label },
            onSelect = actions.onChordStyleChange,
            modifier = Modifier.padding(start = 8.dp),
            labelWidth = SETTING_LABEL_WIDTH,
        )
        // 和音をどう積むか。前の和音から動きの小さい転回形を選ぶと、
        // 同じ進行でも繋がりが滑らかになる。
        OptionChips(
            label = "積み方",
            options = ChordVoicing.entries,
            selected = song.chordVoicing,
            labelOf = { it.label },
            onSelect = actions.onChordVoicingChange,
            modifier = Modifier.padding(start = 8.dp),
            labelWidth = SETTING_LABEL_WIDTH,
        )
        // 高速アルペジオのときだけ、回す速さを選べる。
        // 速いほどきらめくが、そのぶん耳に刺さりやすい。
        if (song.chordStyle.chipArpeggio) {
            OptionChips(
                label = "速さ",
                options = ArpeggioSpeed.entries,
                selected = song.arpeggioSpeed,
                labelOf = { it.label },
                onSelect = actions.onArpeggioSpeedChange,
                modifier = Modifier.padding(start = 8.dp),
                labelWidth = SETTING_LABEL_WIDTH,
            )
        }
    }
    // ベース行だけ、動き方を選べる。行に書けるのは打点だけなので、
    // どの高さを弾くかは和音と「何回目の打点か」から決まる。
    if (track == Instrument.BASS.trackIndex) {
        OptionChips(
            label = "動き",
            options = BassStyle.entries,
            selected = song.bassStyle,
            labelOf = { it.label },
            onSelect = actions.onBassStyleChange,
            modifier = Modifier.padding(start = 8.dp),
            labelWidth = SETTING_LABEL_WIDTH,
        )
    }
    // リード行だけ、揺れ（ビブラート）を掛けられる。
    // チップ音源は音量を変えられないぶん、揺らして表情を付けていた。
    if (track == Instrument.LEAD.trackIndex) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "揺れ",
                modifier = Modifier.width(SETTING_LABEL_WIDTH).padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = song.leadVibrato,
                onValueChange = actions.onLeadVibratoChange,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = vibratoLabel(song.leadVibrato),
                modifier = Modifier.width(36.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 音色は 16 種類あるので、当然 1 行には入らない。
        // 折り返しは OptionChips が面倒を見る。
        OptionChips(
            label = "音色",
            options = ToneSynth.LeadVoice.entries,
            selected = song.leadVoice,
            labelOf = { it.label },
            onSelect = actions.onLeadVoiceChange,
            modifier = Modifier.padding(start = 8.dp),
            labelWidth = SETTING_LABEL_WIDTH,
        )
    }
}

/** つまみの位置を言葉にする。真ん中が既定の音。 */
private fun vibratoLabel(amount: Float): String = when {
    amount < 0.05f -> "なし"
    amount < 0.35f -> "浅め"
    amount < 0.7f -> "標準"
    else -> "深め"
}

/** 残響のつまみの位置を言葉にする。左端が「なし」。 */
private fun reverbLabel(amount: Float): String = when {
    amount <= 0f -> "なし"
    amount < 0.3f -> "うっすら"
    amount < 0.6f -> "標準"
    amount < 0.85f -> "深め"
    else -> "たっぷり"
}

private fun holdLabel(hold: Float): String = when {
    hold < 0.2f -> "短"
    hold < 0.42f -> "やや短"
    hold <= 0.58f -> "標準"
    hold <= 0.8f -> "やや長"
    else -> "長"
}

/** 書き出す範囲と繰り返し回数を決めるダイアログ。 */
@Composable
fun ExportDialog(
    state: RhythmUiState,
    /** MIDI として書き出すか（false なら音声）。 */
    midi: Boolean = false,
    lengthLabel: (ExportScope, Int) -> String,
    onExport: (ExportScope, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasArrangement = state.song.arrangement.isNotEmpty()
    var scope by remember {
        mutableStateOf(if (hasArrangement) ExportScope.SONG else ExportScope.CHAIN)
    }
    var repeats by remember { mutableIntStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (midi) "MIDI を書き出す" else "音声を書き出す") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportScope.entries.forEach { option ->
                    val enabled = option != ExportScope.SONG || hasArrangement
                    val selected = option == scope
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { scope = option },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = option.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            val detail = when {
                                option == ExportScope.SONG && !hasArrangement -> "曲構成がまだ空です"
                                option == ExportScope.CHAIN -> state.chainLabel
                                option == ExportScope.PATTERN -> "パターン ${state.pattern.name}"
                                else -> "${state.song.totalBars()} 小節"
                            }
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (scope != ExportScope.SONG) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("繰り返し", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { repeats = (repeats - 1).coerceAtLeast(1) },
                            enabled = repeats > 1,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Remove,
                                contentDescription = "減らす",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                            Text("$repeats 回", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = { repeats = (repeats + 1).coerceAtMost(MAX_EXPORT_REPEATS) },
                            enabled = repeats < MAX_EXPORT_REPEATS,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "増やす",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Text(
                    text = "書き出し: ${lengthLabel(scope, repeats)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (midi) {
                        "標準 MIDI ファイル (.mid) で保存します。DAW に読み込んで続きを作れます。" +
                            "ドラムはチャンネル 10、コード / ベース / リードはそれぞれ別のトラックに入ります。" +
                            "強弱とハネもそのまま入ります。音そのものではないので、ミュートや音量は反映されません。"
                    } else {
                        "M4A (AAC) で保存します。ミュートしたトラックは入りません。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(scope, repeats) }) { Text("保存先を選ぶ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

/** 書き出し中の進捗。閉じられないようにしておく。 */
@Composable
fun ExportProgressDialog(progress: Float) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("書き出し中…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(progress * 100).toInt()} %",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
fun ExportResultDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音声の書き出し") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

private const val MAX_EXPORT_REPEATS = 32

/**
 * ジャンルを選んで、テンポ・コード進行・リズム・旋律をまとめて当てはめるダイアログ。
 * どこまで書き換えるかを選べるようにして、書いたものが不意に消えないようにしている。
 */
@Composable
fun GenreDialog(
    title: String,
    confirmLabel: String,
    note: String,
    /** 書き換える範囲のチェックを出すか。 */
    showOptions: Boolean,
    /** 「おまかせ」（ジャンルもランダム）を選べるようにするか。 */
    allowRandom: Boolean,
    /** 小節数を選ばせるなら、その選択肢（空なら出さない）。 */
    barChoices: List<Int> = emptyList(),
    defaultBars: Int = 8,
    onApply: (Genre?, GameScene?, GenreOptions, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var genre by remember { mutableStateOf<Genre?>(if (allowRandom) null else Genre.JPOP) }
    var scene by remember { mutableStateOf(GameScene.FIELD) }
    var options by remember { mutableStateOf(GenreOptions()) }
    var bars by remember { mutableIntStateOf(defaultBars) }
    var barMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (allowRandom) {
                    val selected = genre == null
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { genre = null },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "おまかせ",
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = "ジャンルもランダムに選びます",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Genre.entries.forEach { entry ->
                    val selected = entry == genre
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { genre = entry },
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = entry.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = "${entry.description} ・ ${entry.bpmRange.first}〜${entry.bpmRange.last} BPM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (barChoices.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("長さ", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(10.dp))
                        Box {
                            OutlinedButton(onClick = { barMenuOpen = true }) {
                                Text("$bars 小節")
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = barMenuOpen,
                                onDismissRequest = { barMenuOpen = false },
                                modifier = Modifier.heightIn(max = 320.dp),
                            ) {
                                barChoices.forEach { choice ->
                                    DropdownMenuItem(
                                        text = { Text("$choice 小節") },
                                        onClick = {
                                            bars = choice
                                            barMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ゲーム音楽は場面ごとに速さも明暗もまるで違うので、そこから選ぶ。
                val scenes = genre?.scenes.orEmpty()
                if (scenes.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text("場面", style = MaterialTheme.typography.labelMedium)
                    scenes.forEach { entry ->
                        OptionRow(
                            label = entry.label,
                            selected = entry == scene,
                            onClick = { scene = entry },
                            detail = entry.description,
                        )
                    }
                }
                if (showOptions) {
                    Spacer(Modifier.height(2.dp))
                    Text("当てはめるもの", style = MaterialTheme.typography.labelMedium)
                    GenreOptionRow("テンポ", options.tempo) { options = options.copy(tempo = it) }
                    GenreOptionRow("コード進行（曲構成）", options.chords) {
                        options = options.copy(chords = it)
                    }
                    GenreOptionRow("リズム（このパターン）", options.rhythm) {
                        options = options.copy(rhythm = it)
                    }
                    GenreOptionRow("旋律（このパターン）", options.melody) {
                        options = options.copy(melody = it)
                    }
                    // チップ音源のジャンルでだけ効く。音色・ドラム・弾き方をまとめて切り替える。
                    GenreOptionRow("音色（チップ音源のジャンルのみ）", options.sound) {
                        options = options.copy(sound = it)
                    }
                }
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(genre, scene.takeIf { genre?.scenes?.isNotEmpty() == true }, options, bars) },
                enabled = !showOptions ||
                    options.tempo || options.chords || options.rhythm || options.melody,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun GenreOptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 曲まるごとのキーを上げ下げする。 */
@Composable
fun TransposeDialog(
    keyName: String,
    onTranspose: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("キーを変える") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "いまのキー: $keyName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "コード・ベース・リードをまとめて動かします。曲の形は変わりません。" +
                        "リードが音域からはみ出すときは、曲ごとオクターブで折り返して収めます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // よく使う動かし幅だけを並べる。半音単位で刻めても迷うだけなので。
                listOf(
                    listOf(-5 to "-5", -4 to "-4", -3 to "-3", -2 to "-2", -1 to "-1"),
                    listOf(1 to "+1", 2 to "+2", 3 to "+3", 4 to "+4", 5 to "+5"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { (shift, label) ->
                            PickerChip(
                                label = label,
                                selected = false,
                                width = 54.dp,
                                onClick = { onTranspose(shift) },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PickerChip(
                        label = "1 オクターブ下",
                        selected = false,
                        width = 130.dp,
                        onClick = { onTranspose(-12) },
                    )
                    PickerChip(
                        label = "1 オクターブ上",
                        selected = false,
                        width = 130.dp,
                        onClick = { onTranspose(12) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/**
 * 調（キー）と音階を決める。
 *
 * 既定はコードからの推定。モードやペンタトニックはコードからは当てられないので、
 * 使いたい人が選ぶ形にしてある。ここを変えると、コードの候補・旋律の生成・
 * ピアノロールの色分けがまとめて追従する。
 */
@Composable
fun KeyDialog(
    current: MusicKey?,
    detected: MusicKey,
    onPick: (MusicKey?) -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = current ?: detected
    var tonic by remember { mutableIntStateOf(shown.tonic.mod(12)) }
    var scale by remember { mutableStateOf(shown.scale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("キーと音階") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (current == null) {
                        "いまは自動（コードから推定して ${detected.name}）"
                    } else {
                        "いまは指定: ${current.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ここを変えると、コードの候補・旋律の生成・リード画面の色分けがまとめて変わります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("主音", style = MaterialTheme.typography.labelMedium)
                Chord.ROOT_NAMES.chunked(4).forEachIndexed { rowIndex, names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.forEachIndexed { columnIndex, label ->
                            val value = rowIndex * 4 + columnIndex
                            PickerChip(
                                label = label,
                                selected = value == tonic,
                                width = 58.dp,
                                onClick = { tonic = value },
                            )
                        }
                    }
                }

                Text("音階", style = MaterialTheme.typography.labelMedium)
                Scale.entries.forEach { option ->
                    OptionRow(
                        label = option.label,
                        selected = option == scale,
                        onClick = { scale = option },
                        // どの音を使う音階なのかを、その主音での音名で見せる。
                        detail = option.intervals
                            .joinToString(" ") { Chord.ROOT_NAMES[(tonic + it).mod(12)] },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(MusicKey(tonic, scale)) }) { Text("この調にする") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) { Text("自動に戻す") }
        },
    )
}

/**
 * コードクルーザー。4 小節ぶんの進行だけを取り出して、聴きながら捏ねる。
 *
 * これまでコードを触る手段は「1 小節ずつ選ぶ」か「曲全体を書き換える」かしか
 * 無く、しかも決める前に進行として聴けなかった（試聴で鳴るのは和音ひとつ）。
 * ここでは差し込むまで曲を書き換えないので、何度でも試せる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordCruiserDialog(
    chords: List<Chord>,
    seedName: String,
    playing: Boolean,
    seeds: () -> List<ChordCruiser.Seed>,
    onChordClick: (Int) -> Unit,
    onSeed: (ChordCruiser.Seed) -> Unit,
    onTogglePlay: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 引き直すのは、開いたときと「別のを出す」を押したときだけ。
    // 描き直すたびに引くと、選んでいる最中に候補が入れ替わってしまう。
    var offered by remember { mutableStateOf(seeds()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("コード ${chords.size} 小節") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // いま捏ねている進行。押すとその小節を差し替えられる。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chords.forEachIndexed { bar, chord ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { onChordClick(bar) },
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 62.dp)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "${bar + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = chord.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onTogglePlay, contentPadding = TIGHT_BUTTON_PADDING) {
                        Icon(
                            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "止める" else "聴く",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (playing) "止める" else "聴く")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = seedName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "鳴らしたまま下から選べます。差し込むまで曲は変わりません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "進行を選ぶ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { offered = seeds() },
                        contentPadding = TIGHT_BUTTON_PADDING,
                    ) {
                        Text("別のを出す")
                    }
                }
                offered.forEach { seed ->
                    OptionRow(
                        label = seed.name,
                        selected = seed.name == seedName,
                        onClick = { onSeed(seed) },
                        detail = seed.chords.take(chords.size).joinToString(" ") { it.name },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("差し込む") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
