package com.example.rhythmbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.rhythmbox.core.DRUM_COUNT
import com.example.rhythmbox.core.ROW_BASS
import com.example.rhythmbox.core.ROW_CHORD
import com.example.rhythmbox.core.Voice

/** パッド 1 枚。どの行を鳴らすかと、画面に出す名前。 */
private data class Pad(val row: Int, val label: String, val caption: String)

/**
 * 3 列 x 4 段のドラムパッド。
 *
 * 鳴らせる音は 10 種類（ドラム 8 + コード + ベース）なので、
 * 上 3 段にドラム 8 とコード、いちばん下にベースを幅いっぱいで置く。
 */
private val PADS: List<Pad> = buildList {
    Voice.entries.forEach { add(Pad(it.ordinal, it.shortLabel, it.label)) }
    add(Pad(ROW_CHORD, "CHD", "コード"))
}

@Composable
fun PadScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PadTransport(state, viewModel)

        Text(
            text = if (state.padRecording) {
                "叩いた音がパターン ${state.pattern.name} に入ります。近いステップに寄せて置きます。"
            } else {
                "パッドを叩くと音が出ます。録音を押すと、叩いたものが打ち込みに入ります。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.padRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // 上 3 段（ドラム 8 + コード）。
        PADS.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { pad ->
                    PadButton(
                        pad = pad,
                        playing = state.playingStep >= 0 && state.pattern.isOn(pad.row, state.playingStep),
                        modifier = Modifier.weight(1f),
                        onHit = { viewModel.padHit(pad.row) },
                    )
                }
            }
        }
        // いちばん下の段はベースだけ。幅いっぱいに置いて押しやすくする。
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            PadButton(
                pad = Pad(ROW_BASS, "BAS", "ベース"),
                playing = state.playingStep >= 0 && state.pattern.isOn(ROW_BASS, state.playingStep),
                modifier = Modifier.weight(1f),
                onHit = { viewModel.padHit(ROW_BASS) },
            )
        }
    }
}

@Composable
private fun PadTransport(state: RhythmUiState, viewModel: RhythmViewModel) {
    val playing = state.isPlaying
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { viewModel.toggle(PlayMode.PATTERN) },
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (playing) "停止" else "再生", maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::togglePadRecording,
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(
                    Icons.Filled.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state.padRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.padRecording) "録音中" else "録音", maxLines = 1)
            }
            OutlinedButton(
                onClick = viewModel::undoGenerate,
                enabled = state.canUndo,
                modifier = Modifier.weight(1f),
                contentPadding = TIGHT_BUTTON_PADDING,
            ) {
                Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(undoLabel(state.undoDepth), maxLines = 1)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = state.pattern.name,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // 叩く前に切り替えておくと、そのあとの音が強く入る。
            ToggleChip(
                label = "強く置く",
                on = state.padAccent,
                onClick = viewModel::togglePadAccent,
            )
            // 拍の目印。曲には入らない。
            ToggleChip(
                label = "メトロノーム",
                on = state.metronome,
                onClick = viewModel::toggleMetronome,
            )
            Spacer(Modifier.weight(1f))
            // つまみと並ぶ行なので、字を入れると幅が足りずに折り返す。絵だけにする。
            IconButton(onClick = viewModel::clearPadTake, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.ClearAll,
                    contentDescription = "打ち込みを消す",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** 入り切りの小さなつまみ。押す前に今どうなっているかが分かるよう、色と太字で出す。 */
@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (on) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (on) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PadButton(
    pad: Pad,
    playing: Boolean,
    modifier: Modifier,
    onHit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 押した瞬間だけ光らせる。少し残してから消すと、叩いた手応えになる。
    var hits by remember { mutableIntStateOf(0) }
    var lit by remember { mutableStateOf(false) }
    LaunchedEffect(hits) {
        if (hits == 0) return@LaunchedEffect
        lit = true
        delay(FLASH_MS)
        lit = false
    }
    val melodic = pad.row >= DRUM_COUNT
    val color = when {
        lit -> scheme.tertiary
        playing -> if (melodic) scheme.secondary else scheme.primary
        melodic -> scheme.secondaryContainer
        else -> scheme.surfaceContainerHigh
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        // clickable は指を離したときに鳴る。叩いた瞬間に音が出ないと演奏にならないので、
        // 押し下がりを直接拾う。
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    hits++
                    onHit()
                },
            )
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pad.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = pad.caption,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 叩いたパッドが光っている時間。 */
private const val FLASH_MS = 120L
