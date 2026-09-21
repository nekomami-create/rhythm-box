package com.example.rhythmbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.core.Appreciation
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.SongBuilder

/**
 * 鑑賞モード。終わりなく新しい小節を作って流し続ける、聴くだけの画面。
 *
 * 開いている曲・パターン・曲構成は一切触らない（[Appreciation.Stream] が
 * 内部で持つ、使い捨ての流れ）。ここに来ても他の画面の内容は変わらないし、
 * 他の画面に戻っても鑑賞モードの再生はそのまま続く。
 */
@Composable
fun AppreciationScreen(state: RhythmUiState, viewModel: RhythmViewModel) {
    // 始めるときだけジャンルを選べる。調・テンポはおまかせで決まる。
    var pickerOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "リズム・コード進行・リードを揃えたまま、終わりなく新しい小節を作って" +
                "流し続けます。曲構成やパターンには触れない、聴くだけの再生です。" +
                "しばらくすると場面（ジャンル・調・テンポ）ごと移り変わっていきます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val appreciation = state.appreciation
        if (state.appreciating && appreciation != null) {
            AppreciationStatusCard(appreciation, state.appreciationChord)
        }

        OutlinedButton(
            onClick = {
                if (state.appreciating) viewModel.stopAppreciating() else pickerOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.appreciating) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (state.appreciating) "止める" else "始める")
        }
    }

    if (pickerOpen) {
        GenreDialog(
            title = "鑑賞モードを始める",
            confirmLabel = "始める",
            note = "最初のジャンルだけ選べます。調とテンポはおまかせで決まり、" +
                "しばらく流すと場面ごと移り変わっていきます。",
            showOptions = false,
            allowRandom = true,
            onApply = { genre, scene, _, _ ->
                viewModel.startAppreciating(genre, null, scene)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun AppreciationStatusCard(status: Appreciation.Status, chord: Chord?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = chord?.name ?: "・・・",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = status.genre.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${status.key.name} ・ ${status.bpm} BPM",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "この場面になって ${status.blocksIntoEra} ブロック目" +
                    "（${status.blocksIntoEra * SongBuilder.BLOCK} 小節ぶん）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
