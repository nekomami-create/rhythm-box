package com.example.rhythmbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhythmbox.core.CHORD_SLOTS
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.Instrument
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.RhythmStyle
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.Voice
import com.example.rhythmbox.core.chordSlotOf

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
    /** コードを置こうとしている枠（-1 なら開いていない）。 */
    var chordSlot by remember { mutableStateOf(-1) }
    var copyTargetOpen by remember { mutableStateOf(false) }

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
            onChainToggle = { viewModel.toggle(PlayMode.CHAIN) },
            onBpmChange = viewModel::setBpm,
            onVolumeChange = viewModel::setMasterVolume,
            onSwingChange = viewModel::setSwing,
            onOpenMixer = { mixerOpen = true },
        )

        PatternSelector(
            patterns = state.song.patterns,
            selected = state.selectedPattern,
            playing = state.playingPattern,
            canUndo = state.canUndo,
            onSelect = viewModel::selectPattern,
            onClear = viewModel::clearPattern,
            onCopy = { copyTargetOpen = true },
            onGenerate = viewModel::generateRhythm,
            barCount = state.pattern.barCount,
            undoDepth = state.undoDepth,
            scope = state.rhythmScope,
            onScopeChange = viewModel::setRhythmScope,
            onUndo = viewModel::undoGenerate,
        )

        // パターンが 2 小節以上あるときに、どの小節を打ち込んでいるか。
        // ＋ / − がパターンの長さそのものを変える。
        PatternBarSelector(
            count = state.pattern.barCount,
            selected = state.selectedBar,
            playing = state.playingPatternBar.takeIf { state.playingPattern == state.selectedPattern } ?: -1,
            chordAt = viewModel::chordForBar,
            onSelect = viewModel::selectBar,
            onCountChange = viewModel::setBarCount,
            onClearBar = viewModel::clearBar,
            clearLabel = "この小節の打ち込みを消す",
        )

        StepGrid(
            // 打ち込みは開いている 1 小節ぶんだけを渡す。
            pattern = state.pattern.at(state.selectedBar),
            song = state.song,
            playingStep = state.gridStep,
            follow = state.following,
            onToggle = viewModel::toggleStep,
            onCycleLevel = viewModel::cycleStepLevel,
            onPreview = viewModel::previewRow,
            onToggleMute = viewModel::toggleMute,
            placedChord = { slot -> viewModel.placedChordAt(state.selectedBar, slot) },
            soundingChord = { slot ->
                viewModel.chordAtStep(state.song, state.selectedPattern, state.selectedBar, slot * 2)
            },
            onPlaceChord = { chordSlot = it },
            onClearChord = { viewModel.clearChordAt(state.selectedBar, it) },
            onClearPlacedChords = viewModel::clearPlacedChords,
            hasPlacedChords = state.pattern.hasChords,
        )
    }

    // 帯のマスを押したとき。置いてあればそれ、無ければ今鳴っているコードから始める。
    val slot = chordSlot
    if (slot >= 0) {
        val current = viewModel.placedChordAt(state.selectedBar, slot)
            ?: viewModel.chordAtStep(state.song, state.selectedPattern, state.selectedBar, slot * 2)
        // 種は開いたときに一度だけ引く。作らせたものが混ざっているので、
        // 描き直すたびに作ると、指を伸ばしている間に中身が入れ替わってしまう。
        val seeds = remember(slot) { viewModel.progressionSeeds() }
        ChordPickerDialog(
            title = "${state.selectedBar + 1} 小節目 ・ ${slot / 2 + 1} 拍目${if (slot % 2 == 1) "の裏" else ""}",
            current = current,
            suggestions = viewModel.chordSuggestions(
                viewModel.placedChordBefore(state.selectedBar, slot),
            ),
            progressions = seeds,
            onProgression = { seed, overBars ->
                if (overBars) viewModel.placeProgressionOverBars(seed) else viewModel.placeProgressionInBar(seed)
                chordSlot = -1
            },
            progressionBarSpan = viewModel::progressionBarSpan,
            keyName = viewModel.detectedKey().name,
            onPreview = viewModel::previewChord,
            onPick = {
                viewModel.placeChord(state.selectedBar, slot, it)
                chordSlot = -1
            },
            onDismiss = { chordSlot = -1 },
        )
    }

    if (mixerOpen) {
        MixerDialog(
            song = state.song,
            actions = MixerActions(
                onVolumeChange = viewModel::setTrackVolume,
                onPanChange = viewModel::setTrackPan,
                onHoldChange = viewModel::setTrackHold,
                onToggleMute = viewModel::toggleMute,
                onUnmuteAll = viewModel::unmuteAll,
                onCentreAll = viewModel::centreAll,
                onChordStyleChange = viewModel::setChordStyle,
                onChordVoicingChange = viewModel::setChordVoicing,
                onBassStyleChange = viewModel::setBassStyle,
                onArpeggioSpeedChange = viewModel::setArpeggioSpeed,
                onLeadVoiceChange = viewModel::setLeadVoice,
                onLeadVibratoChange = viewModel::setLeadVibrato,
                onDrumKitChange = viewModel::setDrumKit,
                onSoundSetChange = viewModel::setSoundSet,
                onReverbChange = viewModel::setReverb,
                onRoomSizeChange = viewModel::setRoomSize,
            ),
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
}

/** 再生ボタン・テンポ・マスター音量。 */
@Composable
private fun TransportPanel(
    state: RhythmUiState,
    onPlayToggle: () -> Unit,
    onChainToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSwingChange: (Float) -> Unit,
    onOpenMixer: () -> Unit,
) {
    val playing = state.isPlaying && state.mode == PlayMode.PATTERN
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier.size(46.dp),
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
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                // テンポは 1 行にまとめる。ラベルを別行に出すほどの情報量ではない。
                IconButton(
                    onClick = { onBpmChange(state.song.bpm - 1) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "テンポを下げる", modifier = Modifier.size(18.dp))
                }
                Slider(
                    value = state.song.bpm.toFloat(),
                    onValueChange = { onBpmChange(it.toInt()) },
                    valueRange = Song.MIN_BPM.toFloat()..Song.MAX_BPM.toFloat(),
                    modifier = Modifier.weight(1f).height(SLIDER_HEIGHT),
                )
                IconButton(
                    onClick = { onBpmChange(state.song.bpm + 1) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "テンポを上げる", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = "${state.song.bpm} BPM",
                    modifier = Modifier.width(58.dp),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "音量",
                    modifier = Modifier.size(18.dp),
                )
                Slider(
                    value = state.song.masterVolume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(SLIDER_HEIGHT),
                )
                IconButton(onClick = onOpenMixer, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Tune, contentDescription = "ミキサー", modifier = Modifier.size(20.dp))
                }
            }
            // ハネ。裏の 16 分を後ろにずらす量。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ハネ",
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = state.song.swing,
                    onValueChange = onSwingChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(SLIDER_HEIGHT),
                )
                Text(
                    text = swingLabel(state.song.swing),
                    modifier = Modifier.width(58.dp),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 今どの範囲を回しているのかを言葉で出す。ループの効き方が分かるように。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.scopeLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val chaining = state.isPlaying && state.mode == PlayMode.CHAIN
                TextButton(onClick = onChainToggle) {
                    Icon(
                        imageVector = if (chaining) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("チェーン ${state.chainLabel}")
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
    playing: Int,
    canUndo: Boolean,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onGenerate: (RhythmStyle?) -> Unit,
    /** 選んでいるパターンの長さ。範囲チップに「この小節」を出すかどうかに使う。 */
    barCount: Int,
    undoDepth: Int,
    scope: GenerateScope,
    onScopeChange: (GenerateScope) -> Unit,
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
                val isPlaying = index == playing
                Surface(
                    color = when {
                        isPlaying -> MaterialTheme.colorScheme.tertiary
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (isSelected || isPlaying) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected && isPlaying) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else if (!isSelected && !pattern.isEmpty()) {
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
            // リズムの自動生成。スタイルを選べる。
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { styleMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
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
        // 「ランダム」がどこまで書き換えるか。
        // 1 小節のパターンでは「この小節」と「このパターン」が同じなので出さない。
        ScopeChips(
            scopes = if (barCount > 1) {
                listOf(GenerateScope.BAR, GenerateScope.PATTERN, GenerateScope.ALL)
            } else {
                listOf(GenerateScope.PATTERN, GenerateScope.ALL)
            },
            selected = scope,
            onSelect = onScopeChange,
        )
        // 3 つ並ぶと 1 つぶんの幅が狭い。既定の余白のままだと絵と字がぶつかる。
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("クリア", maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("コピー", maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(undoLabel(undoDepth), maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 8 音色 + コード + ベース x 16 ステップの打ち込みグリッド。 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StepGrid(
    pattern: Pattern,
    song: Song,
    playingStep: Int,
    /** 再生位置に合わせて横へ送るか（画面上部の追従スイッチ）。 */
    follow: Boolean,
    onToggle: (Int, Int) -> Unit,
    onCycleLevel: (Int, Int) -> Unit,
    onPreview: (Int) -> Unit,
    onToggleMute: (Int) -> Unit,
    /** その枠に置いてあるコード（無ければ null）。 */
    placedChord: (Int) -> Chord?,
    /** その枠で実際に鳴っているコード。置いていなければ曲構成のもの。 */
    soundingChord: (Int) -> Chord,
    onPlaceChord: (Int) -> Unit,
    onClearChord: (Int) -> Unit,
    onClearPlacedChords: () -> Unit,
    hasPlacedChords: Boolean,
) {
    val scroll = rememberScrollState()
    val labelWidth = 78.dp
    val gap = 4.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth - labelWidth - gap
        val fitted = (available - gap * (STEPS_PER_BAR - 1)) / STEPS_PER_BAR
        val cellWidth = if (fitted < MIN_CELL) MIN_CELL else fitted
        // 16 ステップが入りきらない幅のときだけ効く（入りきるなら maxValue が 0）。
        FollowPlayhead(scroll, playingStep, cellWidth, gap, follow)

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

            // コードを置く帯。8 分音符ごとに 1 枠で、打ち込みのマス 2 つぶんの幅。
            // 打点（CHD 行）とは別物で、こちらは「何の和音か」を決める。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.width(labelWidth),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (hasPlacedChords) {
                        TextButton(
                            onClick = onClearPlacedChords,
                            contentPadding = TIGHT_BUTTON_PADDING,
                        ) {
                            Text("曲に任せる", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            text = "コード",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(gap))
                Row(
                    modifier = Modifier.horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(CHORD_SLOTS) { slot ->
                        ChordSlotCell(
                            placed = placedChord(slot),
                            sounding = soundingChord(slot),
                            playing = chordSlotOf(playingStep.coerceAtLeast(0)) == slot && playingStep >= 0,
                            onBeat = slot % 2 == 0,
                            // マス 2 つぶん＋その間の隙間。打ち込みの列とぴったり合う。
                            width = cellWidth * 2 + gap,
                            onClick = { onPlaceChord(slot) },
                            onLongClick = { onClearChord(slot) },
                        )
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
                                level = pattern.levelAt(info.row, step),
                                playing = step == playingStep,
                                onBeat = step % 4 == 0,
                                dimmed = track.muted,
                                melodic = info.melodic,
                                width = cellWidth,
                                onClick = { onToggle(info.row, step) },
                                onLongClick = { onCycleLevel(info.row, step) },
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

/**
 * コードを置く枠ひとつ。
 *
 * 置いてあれば濃い色でコード名、置いていなければ薄く「実際に鳴っているコード」を
 * 出す。押すと置ける／差し替えられる。長押しで外す。
 *
 * 置いていないほうも名前を出すのは、置く前に「いま何が鳴っているか」を
 * 見せるため。空欄にすると、コードが無いように見えてしまう。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChordSlotCell(
    placed: Chord?,
    sounding: Chord,
    playing: Boolean,
    onBeat: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when {
        placed != null && playing -> scheme.tertiary
        placed != null -> scheme.secondary
        playing -> scheme.outline
        onBeat -> scheme.surfaceContainerHigh
        else -> scheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(CELL_HEIGHT)
            .background(color = color, shape = RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (placed ?: sounding).name,
            maxLines = 1,
            fontSize = 11.sp,
            fontWeight = if (placed != null) FontWeight.Bold else FontWeight.Normal,
            color = if (placed != null) scheme.onSecondary else scheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun StepCell(
    on: Boolean,
    level: Pattern.Level,
    playing: Boolean,
    onBeat: Boolean,
    dimmed: Boolean,
    melodic: Boolean,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
    // 強さは色の濃さで見せる。弱い音は薄く、アクセントは上に線を足す。
    val shaded = when {
        !on -> color
        level == Pattern.Level.GHOST -> color.copy(alpha = 0.45f)
        else -> color
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(CELL_HEIGHT)
            .background(
                color = if (dimmed) shaded.copy(alpha = 0.35f) else shaded,
                shape = RoundedCornerShape(6.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (playing) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (on) Color.White.copy(alpha = 0.6f) else scheme.primary),
            )
        }
        if (on && level == Pattern.Level.ACCENT) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.75f)),
            )
        }
    }
}

private val MIN_CELL = 26.dp
private val CELL_HEIGHT = 34.dp

/** ハネ具合を言葉にする。三連（シャッフル）のあたりが分かるように。 */
private fun swingLabel(swing: Float): String = when {
    swing < 0.04f -> "まっすぐ"
    swing < 0.5f -> "軽く"
    swing < 0.8f -> "三連"
    else -> "強め"
}
