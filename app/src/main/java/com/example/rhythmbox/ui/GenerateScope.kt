package com.example.rhythmbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 「ランダム」を押したときに、どこまで書き換えるか。
 *
 * 全部まとめて振り直すと、気に入っている小節まで巻き添えで消える。
 * 逆に 1 小節ずつしか作れないと、まるごと作りたいときに手間がかかる。
 * どちらも要るので、押す前に範囲を選べるようにしている。
 */
enum class GenerateScope(val label: String) {
    /** いま開いている 1 小節だけ。 */
    BAR("この小節"),

    /** いま開いているパターン。 */
    PATTERN("このパターン"),

    /** 中身のあるパターンすべて。 */
    ALL("全パターン"),
}

/**
 * 範囲を選ぶチップの列。
 *
 * 長押しや隠し操作にせず、いま何が起きるのかを画面に出しておく。
 */
@Composable
fun ScopeChips(
    scopes: List<GenerateScope>,
    selected: GenerateScope,
    onSelect: (GenerateScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    OptionChips(
        label = "範囲",
        options = scopes,
        selected = selected,
        labelOf = { it.label },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * ピアノロールを長押ししたときに何をするか。
 *
 * 長押しは「音を伸ばす」に先に使っていたので、強弱を足すには
 * どちらの道具を使うかを選んでもらうしかない。隠し操作にせず、
 * 今どちらが効くのかを画面に出しておく。
 */
enum class LeadHoldMode(val label: String) {
    STRETCH("伸ばす"),
    LEVEL("強弱"),
}

/** パッドで何を鳴らすか。 */
enum class PadMode(val label: String) {
    DRUM("ドラム"),
    CHORD("コード"),
}
