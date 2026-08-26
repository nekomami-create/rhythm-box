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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClearAll
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
import com.example.rhythmbox.core.NoteRole
import com.example.rhythmbox.core.chordDegreeLabel
import com.example.rhythmbox.core.noteRole
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
            barLabel = "${state.selectedBar + 1} 小節目 ・ コード " +
                "${viewModel.chordForBar(state.selectedBar).name} ・ " +
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

        // パターンの中の何小節目を書いているか。打ち込みの画面と同じ帯を出している。
        PatternBarSelector(
            count = pattern.barCount,
            selected = state.selectedBar,
            playing = state.playingPatternBar.takeIf { state.playingPattern == state.selectedPattern } ?: -1,
            chordAt = viewModel::chordForBar,
            onSelect = viewModel::selectBar,
            onCountChange = viewModel::setBarCount,
            onClearBar = viewModel::clearLeadBar,
            clearLabel = "この小節の音を消す",
        )

        // いま書いている小節のコード。どの音が構成音かを鍵盤の色で見せる。
        val barChord = viewModel.chordForBar(state.selectedBar)
        val key = viewModel.detectedKey()
        ChordLegend(chord = barChord, keyName = key.name)

        FollowPlayhead(horizontalScroll, playingStep, CELL_WIDTH, CELL_GAP, state.following)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            for (midi in HIGHEST_MIDI downTo LOWEST_MIDI) {
                PianoRollRow(
                    midi = midi,
                    pattern = pattern,
                    leadBar = state.selectedBar,
                    role = noteRole(midi, barChord, key),
                    degree = chordDegreeLabel(midi, barChord),
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
            // 3 つ並ぶと 1 つぶんの幅が狭い。既定の余白のままだと絵と字がぶつかる。
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onGenerate,
                    modifier = Modifier.weight(1f),
                    contentPadding = TIGHT_BUTTON_PADDING,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ランダム", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                    contentPadding = TIGHT_BUTTON_PADDING,
                ) {
                    Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全消し", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onUndo,
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f),
                    contentPadding = TIGHT_BUTTON_PADDING,
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(undoLabel(state.undoDepth), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * 何回目の小節を編集するかを選ぶ行。
 * ドラムは同じでも、旋律は小節ごとに変えないと下のコードから外れてしまう。
 */
/**
 * 色の意味を出す帯。
 *
 * 色を付けただけでは「なぜその色なのか」が伝わらないので、
 * いま何のコードで、どの色が何を意味するのかを添える。
 */
@Composable
private fun ChordLegend(chord: Chord, keyName: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = chord.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "の構成音に色。薄い行は $keyName の外の音",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PianoRollRow(
    midi: Int,
    pattern: Pattern,
    leadBar: Int,
    role: NoteRole,
    degree: String?,
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
        // コードの構成音は色を付け、度数（R / 3 / 5 …）を添える。
        // 調の外の音は薄くして、外れやすい音が目で分かるようにする。
        val keyColor = when (role) {
            NoteRole.CHORD_TONE -> scheme.tertiaryContainer
            NoteRole.SCALE_TONE -> if (white) scheme.surfaceContainerHigh else scheme.surfaceContainerLow
            NoteRole.OUTSIDE -> scheme.surfaceVariant.copy(alpha = 0.30f)
        }
        Surface(
            color = keyColor,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(46.dp).height(ROW_HEIGHT).clickable { onPreview() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp),
            ) {
                Text(
                    text = midiName(midi),
                    fontSize = 10.sp,
                    fontWeight = if (role == NoteRole.CHORD_TONE) FontWeight.Bold else FontWeight.Normal,
                    color = when (role) {
                        NoteRole.CHORD_TONE -> scheme.onTertiaryContainer
                        NoteRole.SCALE_TONE -> if (white) scheme.onSurface else scheme.onSurfaceVariant
                        NoteRole.OUTSIDE -> scheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                )
                if (degree != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = degree,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onTertiaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
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
                    // 何も置いていないマスも、コードの構成音の行だけ薄く色を敷く。
                    role == NoteRole.CHORD_TONE -> scheme.tertiary.copy(alpha = if (step % 4 == 0) 0.30f else 0.18f)
                    role == NoteRole.OUTSIDE -> scheme.surfaceVariant.copy(alpha = 0.12f)
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

/** マスの間隔。再生位置を追いかけて横へ送るときの計算にも使う。 */
private val CELL_GAP = 3.dp
