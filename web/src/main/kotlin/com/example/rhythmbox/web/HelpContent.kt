package com.example.rhythmbox.web

import kotlinx.browser.document
import org.w3c.dom.HTMLElement

/** ヘルプ画面の中身。Web 版の機能（曲構成・鑑賞モードのみ）に合わせた簡潔な説明。 */
object HelpContent {
    private data class Section(val heading: String, val body: List<String>)

    private val sections = listOf(
        Section(
            "これは何",
            listOf(
                "BreakBox（Android アプリ）の作曲ロジックをそのまま使った、ブラウザだけで動く試作版です。",
                "手元での打ち込み編集やリアルタイム再生エンジンは持たず、「ジャンル・調・長さを決めて自動で作り、" +
                    "できた曲を聴く・MP3でダウンロードする」ところに絞ってあります。",
            ),
        ),
        Section(
            "曲構成（オート作曲）",
            listOf(
                "ジャンル・調・長さ（小節数）を選んで「生成して聴く」を押すと、1 曲まるごと自動で組み立てます。",
                "64 小節を選ぶと、Aメロ→Bメロ→サビ→Aメロ→Cメロ→まとめ、という曲としてのまとまりを意識した構成になります。",
                "できた曲はその場で再生でき、MP3 でダウンロードもできます。",
            ),
        ),
        Section(
            "鑑賞モード",
            listOf(
                "元のアプリでは、鑑賞モードは終わりなく曲を生成し続けて流しっぱなしにする機能です。" +
                    "Web 版ではリアルタイム再生エンジンを持たないので、代わりに指定した長さぶんをまとめて生成し、" +
                    "できた音声を普通の音楽プレイヤーのように再生・一時停止・シークする形にしています。",
                "起承転結（4 ブロックひとまとまり）を繰り返しながら進み、起・承・結は同じ進行、転だけ別の進行や近い調へ" +
                    "寄り道します。画面にはいま鳴っている区間（Aメロ/Bメロ/サビ/Cメロ）とコード名、リードの音の高さの" +
                    "軌跡を表示します。",
                "ジャンル・調を「おまかせ」にすると、場面が変わるたびに違う組み合わせがランダムに選ばれます。",
            ),
        ),
        Section(
            "音づくりについて",
            listOf(
                "ドラムもコードもリードもベースも、すべてその場で合成しています（録音した音を鳴らしているわけではありません）。",
                "MP3 への変換はブラウザの中だけで行われ、サーバーには何も送信されません。",
            ),
        ),
    )

    fun render() {
        val container = document.getElementById("help-content") as HTMLElement
        container.innerHTML = ""
        for (section in sections) {
            val h3 = document.createElement("h3") as HTMLElement
            h3.textContent = section.heading
            container.appendChild(h3)
            for (paragraph in section.body) {
                val p = document.createElement("p") as HTMLElement
                p.textContent = paragraph
                container.appendChild(p)
            }
        }
    }
}
