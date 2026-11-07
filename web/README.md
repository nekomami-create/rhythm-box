# BreakBox Web（試作）

BreakBox（Android アプリ）の作曲ロジックをブラウザだけで動かす試作版です。
GitHub Pages のような静的ホスティングだけで配信できます（サーバー側の処理は一切ありません）。

## スコープ（意図的に絞ってあります）

フィージビリティ検証の結果、まずは次の構成に絞っています。

- **含む**: 曲構成（オート作曲）／鑑賞モード（まとめて生成して再生）／ヘルプ
- **含まない**: パターン・パッド・リード画面での手打ち編集、保存・ライブラリ管理、
  リアルタイム再生エンジン（Web Audio API への移植はしていない）

鑑賞モードは本来「終わりなく生成し続ける」機能ですが、Web 版はリアルタイム
エンジンを持たないため、指定した長さぶんをまとめて生成し、ブラウザの
普通の `<audio>` 要素で再生する形にしています。

## 仕組み

- `:core` の Kotlin ソースをそのまま `commonMain` に取り込んで使っています
  （`build.gradle.kts` の `kotlin.srcDir("../core/src/main/kotlin")`）。
  `core/build.gradle.kts` 自体は変えていないので、Android アプリ（`:app`）の
  ビルドには影響しません。
- 単一ターゲットの `kotlin("js")` ではなく `kotlin("multiplatform")` + `js()` に
  してあります。core 側で使っている `kotlin.concurrent.Volatile`
  （`@OptionalExpectation`）が、真の共通ソースセット経由でないと解決できない
  ためです。
- 音声合成（ドラム・コード・リード・ベース）はすべて `:core` の procedural
  synthesis（`DrumSynth`/`ToneSynth`）をそのまま使っています。録音したサンプル
  ファイルは一切使っていません（Android アプリ側も同じ作りです）。
- MP3 書き出しは [lamejs](https://github.com/zhuker/lamejs) を使っています。
  ただし npm パッケージの `package.json` の `"main"` は未バンドルのソース
  （`src/js/index.js`）を指しており、`require`/webpack 経由で読み込むと
  未定義のグローバル参照で壊れます。配布物に同梱されている正式なバンドル
  （`lame.min.js`）だけが単体で動く作りなので、`src/main/resources/lame.min.js`
  に静的ファイルとして同梱し、`index.html` で `<script>` タグ読み込みして
  グローバルの `lamejs` を使っています（npm 依存にはしていません）。
- 鑑賞モードは場面が変わるたびにテンポが変わりますが、`OfflineRenderer`
  （core）は曲全体で固定のテンポを前提にしています。そのため鑑賞モード専用に
  `AppreciationRenderer.kt`（web 側）を書き、ブロックの境目でだけ
  `PlaybackEngine.config` を差し替えながら描いています
  （`PlaybackEngine` 自体はブロックをまたいで止めずに呼べば、途中で設定を
  差し替えても再生位置が続きから進む作りになっています）。

## ローカルで動かす

Android ビルド環境（Android SDK）が無い環境でも、`:web` 単体の動作確認は
別の Gradle プロジェクトを用意してソースディレクトリだけ指す、という形で
確認できます（このリポジトリの `build.gradle.kts` はルートで Android Gradle
Plugin を読み込むため、SDK が無い環境では `:web` だけを指定しても
ルートの評価で失敗します）。Android SDK ごと揃った環境（CI など）では
ふつうに

```bash
./gradlew :web:jsBrowserDevelopmentRun   # 開発サーバーで確認
./gradlew :web:jsBrowserDistribution     # 配布用の静的ファイル一式を build/dist/js/productionExecutable に生成
```

で動きます。

## 既知の制限

- テンポが場面ごとに変わる前提の計算は近似です（各ブロックの BPM を積み上げて
  小節の開始時刻を出していますが、スイング・残響の尾などは厳密には含めていません）。
- MP3 エンコードはブラウザの中で行うため、長い曲（10 分など）では数秒〜十数秒
  かかることがあります。
- ビジュアライザは元の `LeadTrailVisualizer`（Android 版）の簡易版です
  （グローの演出や太鼓の脈動などは省いてあります）。
