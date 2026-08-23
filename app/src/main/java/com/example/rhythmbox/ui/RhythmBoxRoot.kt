package com.example.rhythmbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Screen(val label: String) {
    SEQUENCER("パターン"),
    LEAD("リード"),
    SONG("曲構成"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmBoxRoot(viewModel: RhythmViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.SEQUENCER) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<SongDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.song.name.ifEmpty { "リズムボックス" }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                actions = {
                    // 曲構成を最後まで再生したあと頭に戻るかどうか。どの画面からでも切り替えられる。
                    FilledIconToggleButton(
                        checked = state.loopSong,
                        onCheckedChange = viewModel::setLoopSong,
                    ) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = if (state.loopSong) {
                                "曲全体のループを解除"
                            } else {
                                "曲全体をループ再生"
                            },
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "曲メニュー")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("曲の名前を変更") },
                            onClick = { menuOpen = false; dialog = SongDialog.Rename },
                        )
                        DropdownMenuItem(
                            text = { Text("新しい曲") },
                            onClick = { menuOpen = false; dialog = SongDialog.New },
                        )
                        DropdownMenuItem(
                            text = { Text("この曲を複製") },
                            onClick = { menuOpen = false; viewModel.duplicateSong() },
                        )
                        DropdownMenuItem(
                            text = { Text("保存した曲を開く") },
                            onClick = { menuOpen = false; dialog = SongDialog.Library },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = screen.ordinal) {
                Screen.entries.forEach { entry ->
                    Tab(
                        selected = screen == entry,
                        onClick = { screen = entry },
                        text = { Text(entry.label) },
                    )
                }
            }
            if (!state.ready) return@Column
            when (screen) {
                Screen.SEQUENCER -> SequencerScreen(state, viewModel)
                Screen.LEAD -> LeadScreen(state, viewModel)
                Screen.SONG -> SongScreen(state, viewModel)
            }
        }
    }

    SongDialogHost(
        dialog = dialog,
        state = state,
        viewModel = viewModel,
        onDismiss = { dialog = null },
    )
}
