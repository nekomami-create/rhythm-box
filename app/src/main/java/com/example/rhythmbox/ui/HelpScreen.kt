package com.example.rhythmbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rhythmbox.BuildConfig

/** ヘルプ 1 項目。[body] の行頭が「・」なら箇条書きとして少し詰めて表示する。 */
private data class HelpSection(val title: String, val body: List<String>)

private val helpSections = listOf(
    HelpSection(
        "BreakBox とは",
        listOf(
            "16 ステップのステップシーケンサーです。ドラムを打ち込み、コード（和音）・ベース・旋律を重ねて、" +
                "パターンを並べて曲に組み立てます。",
            "音源ファイルは持っていません。すべての音をアプリの中で作っているので、素材のダウンロードは要りません。",
        ),
    ),
    HelpSection(
        "画面は 3 つ",
        listOf(
            "・パターン … ドラム・コード・ベースを打ち込むグリッド",
            "・リード … 旋律を置くピアノロール",
            "・曲構成 … パターンを並べて曲にする場所",
            "編集した内容は自動で保存されます。曲の新規作成や切り替えは右上の ⋮ から。",
        ),
    ),
    HelpSection(
        "鳴らし方は 3 通り",
        listOf(
            "・パターン再生（▶）… 選んでいるパターン 1 小節をずっと繰り返します。打ち込みながら聴く用です",
            "・チェーン再生（▶ チェーン A→B→C）… 中身のあるパターンを 1 小節ずつ順に流します。" +
                "曲構成を作らなくても、作ったパターンを繋げて聴けます",
            "・曲の再生（曲構成タブの ▶）… 曲構成を通しで再生します",
            "右上のループボタンは、チェーンと曲の再生に効きます（オフにすると 1 回流して止まります）。" +
                "パターン 1 小節の再生は、性質上いつでもループです。",
            "今どの範囲を回しているかは、再生ボタンの下に文字で出ます。",
        ),
    ),
    HelpSection(
        "打ち込み方",
        listOf(
            "マスをタップすると音が置かれます。もう一度タップで消えます。",
            "左端のトラック名を押すと、その音だけを試聴できます。隣のスピーカーの絵はミュートです。",
            "下の 2 行（CHD / BAS）はコードとベースです。この 2 行は「いつ鳴らすか」だけを決めていて、" +
                "音の高さはその小節のコードから決まります。だからコードを変えるだけで、同じ打ち込みが違う響きになります。",
        ),
    ),
    HelpSection(
        "コードの決め方",
        listOf(
            "パターン画面の「コード」ボタンで、そのパターンを単体で鳴らすときの和音を決めます。",
            "曲構成では、各行の下に小節ぶんのコードボタンが並びます。押すとその小節の和音を変えられます。",
            "コードを選ぶ画面の上に「つぎに合うコード」が出ます。曲に出てくるコードから調（キー）を推定して、" +
                "前後の小節に馴染むものを順に並べています。",
            "候補に付いている I・IV・V・vi は度数の表記です。I が中心の和音、V は I に帰りたくなる和音、" +
                "IV はその手前によく置かれる和音、と考えると選びやすくなります。",
        ),
    ),
    HelpSection(
        "旋律（リード）",
        listOf(
            "リードタブのピアノロールで、縦が音の高さ、横が 16 ステップです。1 ステップにつき 1 音まで。",
            "同じマスをもう一度押すと消えます。左端の鍵盤を押すと、その音だけ試聴できます。",
            "音を長く伸ばすには、伸ばしたいところまでのマスを長押しします。音の頭から長押ししたところまでが " +
                "1 つの音として繋がり、少し細い帯で表示されます。音の上を長押しすると元の長さに戻ります。",
            "伸ばした音は小節をまたいでも切れません。最長 4 小節まで伸ばせます。",
            "何も伸ばしていない音の長さは、次の音まで（最長 1 拍）です。",
            "「ランダム」を押すと、その小節のコードと曲の調に沿って旋律を作ります。" +
                "拍の頭はコードの構成音に置き、隣り合う音へ動くことを優先するので、極端に外れた音にはなりません。",
            "ピアノロールの上に「1 2 3 4」と並ぶのは、同じパターンを繰り返したときの何回目かです。" +
                "ドラムは 4 小節とも同じでかまいませんが、旋律は下のコードが変わるたびに変えないと和音から外れます。" +
                "＋ で小節を増やし、番号を押して切り替えて書きます（番号の下にその小節のコードが出ます）。",
            "曲構成で 4 小節使っているパターンなら、「ランダム」を押すだけで 4 小節ぶんまとめて作ります。",
            "生成した旋律も、次の音まで 1 拍以上あくところは自動で伸ばします。",
        ),
    ),
    HelpSection(
        "自動で作らせる",
        listOf(
            "・パターン画面の「ランダム」… リズムを生成します。8ビート / 4つ打ち / ブレイクビーツ / " +
                "ヒップホップ / ラテンから選べます（おまかせも可）",
            "・リード画面の「ランダム」… 旋律を生成します",
            "・曲構成の「起承転結」… 全小節のコードを、起承転結の流れで埋めます",
            "・曲構成の「終わり」… 最後の 2 / 4 / 8 小節だけを終止形（結）に差し替えます。" +
                "途中まで気に入っているけれど終わり方が決まらない、というときに使えます",
            "どれも直前の状態を覚えているので、気に入らなければ「戻す」で 1 つ前に復帰できます。",
            "リズムの生成でリードは変わりません。書いた旋律が消える心配はありません。",
        ),
    ),
    HelpSection(
        "オート作曲",
        listOf(
            "右上の ⋮ →「オート作曲」で、曲をまるごと作ります。",
            "ジャンル（またはおまかせ）と長さを選ぶだけです。長さは 4 小節から 64 小節まで、4 小節きざみで選べます。",
            "4 小節を 1 つのまとまりとして、まとまりごとにドラムのパターンが変わります。" +
                "使うパターンは A〜D までで、16 小節を超えるとまた A から使い回します（曲としての繰り返しになります）。" +
                "E 以降は手で書く用に空けてあります。",
            "旋律も小節ごとに作ります。ドラムは 4 小節同じでも、旋律は下のコードに合わせて毎回変わります。",
            "コード進行は定番の型をそのまま並べるので、主和音で終わらせたいときは曲構成の「終わり」を押してください。",
            "気に入らなければ「戻す」で 1 つ前に復帰できます。",
        ),
    ),
    HelpSection(
        "ジャンルから作る",
        listOf(
            "右上の ⋮ →「ジャンルから作る」で、ロック / J-POP / バラード / シティポップ / ダンス を選べます。",
            "ジャンルらしさはドラムだけでは出ません。テンポ・コード進行・リズム・旋律をまとめて設定するので、" +
                "選ぶだけで一気にそれらしくなります。",
            "・ロック … 132〜152 BPM、8ビート、I-V-vi-IV",
            "・J-POP … 118〜138 BPM、王道進行（IV-V-iii-vi）・小室進行（vi-IV-V-I）・カノン進行",
            "・バラード … 62〜80 BPM、隙間を空けたリズム、I-vi-IV-V",
            "・シティポップ … 96〜116 BPM、16 分の細かい刻み、丸サ進行（IVM7-III7-vi7）",
            "・ダンス … 122〜130 BPM、4 つ打ち、vi-IV-I-V",
            "どこまで書き換えるかはチェックで選べます。旋律は既定でオフなので、" +
                "書いたメロディが不意に消えることはありません。気に入らなければ「戻す」で 1 つ前に復帰できます。",
            "コード進行は定番の型をそのまま並べます（起承転結のように主和音で終わらせたい場合は、" +
                "曲構成の「終わり」を押してください）。",
        ),
    ),
    HelpSection(
        "起承転結について",
        listOf(
            "「起承転結」を押すと、曲を 4 つに区切って役割を持たせたコード進行を作ります。",
            "・起 … 主和音から始めて、その曲の調をはっきりさせます",
            "・承 … 起を受けて広げます。IV や ii などに寄ります",
            "・転 … 主和音から離れて変化をつけます。ここが平坦だと曲が一本調子になります",
            "・結 … 主和音へ帰って終わります",
            "終わり方の型（終止形）は、最後の 1 つ前が必ず V（ドミナント）になるようにしてあります。" +
                "V から主和音へ動く形が、いちばん「終わった」と感じられるためです。",
            "2 小節なら V - I か IV - I、4 小節なら I - IV - V - I や vi - ii - V - I といった型から選ばれます。" +
                "マイナーキーのときは V をメジャーにして（和声的短音階）、終わった感じを強めています。",
        ),
    ),
    HelpSection(
        "曲構成の作り方",
        listOf(
            "「パターンを追加」でブロックを足し、そのブロックで何小節繰り返すかを ＋ − で決めます。",
            "↑ ↓ で順番を入れ替え、ゴミ箱で削除します。左のアルファベットを押すとパターンを差し替えられます。",
            "繰り返し数を増やすと、小節ごとのコード枠も一緒に増えます。",
        ),
    ),
    HelpSection(
        "音量とテンポ",
        listOf(
            "テンポは 40〜240 BPM。スライダーのほか ＋ − で 1 ずつ動かせます。",
            "スピーカーの絵の横がマスター音量、その右のつまみのボタンがミキサーです。" +
                "ミキサーではドラム 8 音色とコード・ベース・リードの音量とミュートを個別に調整できます。",
        ),
    ),
    HelpSection(
        "音声で書き出す",
        listOf(
            "右上の ⋮ →「音声を書き出す (M4A)」で、作った曲を音声ファイルにできます。",
            "・曲構成をそのまま … 曲構成に並べたとおりに 1 回ぶん",
            "・チェーンを繰り返し … A→B→C… を指定した回数ぶん",
            "・このパターンを繰り返し … 選んでいるパターンを指定した回数ぶん",
            "保存先はファイル選択画面で決めます（端末のどこにでも保存できます）。" +
                "書き出した音は、アプリで聴いている音とまったく同じものです。",
            "ミュートしているトラックは入りません。書き出したいのに音が入っていないときは、ミキサーを確認してください。",
        ),
    ),
    HelpSection(
        "保存とデータ",
        listOf(
            "編集は自動保存されます。曲は何曲でも作れます（右上の ⋮ → 新しい曲 / この曲を複製 / 保存した曲を開く）。",
            "新しい APK を上書きインストールしても、保存した曲は残ります（アンインストールは不要です）。",
        ),
    ),
    HelpSection(
        "音の作りについて",
        listOf(
            "キックはサイン波のピッチを一気に落として作り、ハイハットは 6 本の矩形波を非整数倍で重ねています。" +
                "クローズドハットはオープンハットを止めます（実機と同じ挙動）。",
            "コードはオルガン風、ベースは丸い音、リードは少し尖った音にしてあります。" +
                "音が重なって歪まないよう、出力の直前で軽く圧縮しています。",
        ),
    ),
)

@Composable
fun HelpScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(helpSections) { section ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    section.body.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "BreakBox ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
