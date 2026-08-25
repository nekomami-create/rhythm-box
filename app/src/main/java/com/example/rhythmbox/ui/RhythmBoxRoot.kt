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
import com.example.rhythmbox.core.SongBuilder

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
    // 書き出しの設定ダイアログを、音声と MIDI のどちらから開いたか。
    var exportingMidi by remember { mutableStateOf(false) }
    val createAudioFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4"),
    ) { uri ->
        if (uri != null) viewModel.exportAudio(uri, exportScope, exportRepeats)
    }
    val createMidiFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/midi"),
    ) { uri ->
        if (uri != null) viewModel.exportMidi(uri, exportScope, exportRepeats)
    }
    val createSongFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportSongFile(uri)
    }
    val openSongFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importSongFile(uri)
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
                            text = { Text("オート作曲") },
                            onClick = { menuOpen = false; songBuilderOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("ジャンルから作る") },
                            onClick = { menuOpen = false; genreOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("音声を書き出す (M4A)") },
                            onClick = { menuOpen = false; exportingMidi = false; exportSetupOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("MIDI を書き出す") },
                            onClick = { menuOpen = false; exportingMidi = true; exportSetupOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("曲をファイルに保存") },
                            onClick = { menuOpen = false; createSongFile.launch(viewModel.suggestedSongName()) },
                        )
                        DropdownMenuItem(
                            text = { Text("曲のファイルを読み込む") },
                            onClick = {
                                menuOpen = false
                                // 端末によっては json を認識しないので、すべてのファイルから選べるようにする。
                                openSongFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
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
            title = "オート作曲",
            confirmLabel = "作る",
            note = "テンポ・コード進行・リズム・旋律を決めて、4 小節ずつのまとまりで曲を作ります。" +
                "ドラムは 4 小節ごとに変わり、旋律は小節ごとのコードに合わせて変わります。",
            showOptions = false,
            allowRandom = true,
            barChoices = SongBuilder.BAR_CHOICES,
            defaultBars = RhythmViewModel.DEFAULT_SONG_BARS,
            onApply = { genre, _, bars ->
                viewModel.generateSong(genre, bars)
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
            onApply = { genre, options, _ ->
                if (genre != null) viewModel.applyGenre(genre, options)
                genreOpen = false
            },
            onDismiss = { genreOpen = false },
        )
    }
    if (exportSetupOpen) {
        ExportDialog(
            state = state,
            midi = exportingMidi,
            lengthLabel = viewModel::exportLengthLabel,
            onExport = { scope, repeats ->
                exportScope = scope
                exportRepeats = repeats
                exportSetupOpen = false
                if (exportingMidi) {
                    createMidiFile.launch(viewModel.suggestedMidiName())
                } else {
                    createAudioFile.launch(viewModel.suggestedFileName())
                }
            },
            onDismiss = { exportSetupOpen = false },
        )
    }
    state.exportProgress?.let { ExportProgressDialog(it) }
    state.exportMessage?.let {
        ExportResultDialog(message = it, onDismiss = viewModel::dismissExportMessage)
    }
}
