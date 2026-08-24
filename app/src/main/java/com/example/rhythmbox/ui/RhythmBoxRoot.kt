package com.example.rhythmbox.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    HELP("ヘルプ"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmBoxRoot(viewModel: RhythmViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.SEQUENCER) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<SongDialog?>(null) }
    var exportSetupOpen by remember { mutableStateOf(false) }
    var genreOpen by remember { mutableStateOf(false) }
    var songBuilderOpen by remember { mutableStateOf(false) }
    // 保存先を選ぶ画面から戻ってきたときに使う、選んだ書き出し条件。
    var exportScope by remember { mutableStateOf(ExportScope.SONG) }
    var exportRepeats by remember { mutableStateOf(2) }
    val createAudioFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4"),
    ) { uri ->
        if (uri != null) viewModel.exportAudio(uri, exportScope, exportRepeats)
    }

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
                        DropdownMenuItem(
                            text = { Text("8 小節つくる") },
                            onClick = { menuOpen = false; songBuilderOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("ジャンルから作る") },
                            onClick = { menuOpen = false; genreOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("音声を書き出す (M4A)") },
                            onClick = { menuOpen = false; exportSetupOpen = true },
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
            // ヘルプは曲の読み込みを待たずに開ける。
            if (!state.ready && screen != Screen.HELP) return@Column
            when (screen) {
                Screen.SEQUENCER -> SequencerScreen(state, viewModel)
                Screen.LEAD -> LeadScreen(state, viewModel)
                Screen.SONG -> SongScreen(state, viewModel)
                Screen.HELP -> HelpScreen()
            }
        }
    }

    SongDialogHost(
        dialog = dialog,
        state = state,
        viewModel = viewModel,
        onDismiss = { dialog = null },
    )

    if (songBuilderOpen) {
        GenreDialog(
            title = "8 小節つくる",
            confirmLabel = "作る",
            note = "テンポ・コード進行・リズムを決めて、A を 4 小節 + B を 4 小節の曲を作ります。" +
                "旋律はそのままなので、あとからリードタブで足してください。",
            showOptions = false,
            allowRandom = true,
            onApply = { genre, _ ->
                viewModel.generateSong(genre)
                songBuilderOpen = false
            },
            onDismiss = { songBuilderOpen = false },
        )
    }
    if (genreOpen) {
        GenreDialog(
            title = "ジャンルから作る",
            confirmLabel = "当てはめる",
            note = "コード進行は定番の型をそのまま並べます。曲構成がまだ無いときは作ります。",
            showOptions = true,
            allowRandom = false,
            onApply = { genre, options ->
                if (genre != null) viewModel.applyGenre(genre, options)
                genreOpen = false
            },
            onDismiss = { genreOpen = false },
        )
    }
    if (exportSetupOpen) {
        ExportDialog(
            state = state,
            lengthLabel = viewModel::exportLengthLabel,
            onExport = { scope, repeats ->
                exportScope = scope
                exportRepeats = repeats
                exportSetupOpen = false
                createAudioFile.launch(viewModel.suggestedFileName())
            },
            onDismiss = { exportSetupOpen = false },
        )
    }
    state.exportProgress?.let { ExportProgressDialog(it) }
    state.exportMessage?.let {
        ExportResultDialog(message = it, onDismiss = viewModel::dismissExportMessage)
    }
}
