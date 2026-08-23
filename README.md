# リズムボックス (Rhythm Box)

16 ステップ x 8 音色のステップシーケンサーで、パターンを打ち込んで曲に組み立てる Android アプリです。
音源ファイルは持たず、**ドラム音はアプリ内で合成**しています（サンプル素材の同梱・ダウンロード不要）。

## 主な機能

- **16 ステップ x 8 音色**のパターン打ち込み（BD / SD / CH / OH / CP / RS / TM / CB）
- **テンポ (40〜240 BPM)** とマスター音量、トラックごとの音量・ミュート（ミキサー）
- **パターン A〜H** の切り替え・クリア・コピー
- **曲構成**: パターンを「A を 4 小節 → B を 2 小節…」と並べて通し再生（ループ切り替えつき）
- **自動保存 + 複数曲の管理**（新規作成・複製・名前変更・切り替え・削除）
- 再生中もその場で打ち込みを変更できる（音は止まりません）
- 再生位置は実際に鳴っている音に合わせて表示（AudioTrack の再生位置を参照）
- トラック名をタップすると、その音色だけを単発で試聴

## 使い方

1. 上部の **パターン** タブでグリッドをタップして打ち込み、▶ でループ再生。
2. パターン A〜H を切り替えて、いくつかのパターンを作る（「コピー」で複製してから変えると早いです）。
3. **曲構成** タブで「パターンを追加」→ 並び順と繰り返し小節数を指定 → ▶ で通し再生。
4. 編集内容は自動保存されます。曲の切り替えや新規作成は右上の ⋮ メニューから。

## 技術スタック

- Kotlin / Jetpack Compose (Material 3)
- 音声出力: `AudioTrack`（32bit float・モノラル・ストリーミング）
- 音源: 加算合成 + ノイズ + 1 次フィルタによる自前のドラムシンセ
- 保存: kotlinx.serialization による JSON ファイル（`filesDir/songs.json`）

### モジュール構成

Android に依存する部分としない部分を分けています。`:core` は JVM 単体テストで検証できます。

```
core/   … 音源合成・シーケンサ・データモデル（純 Kotlin）
  Model.kt          パターン / 曲 / ミキサー設定
  DrumSynth.kt      8 音色のワンショット波形を合成
  PlaybackPlan.kt   「どの小節でどのパターンを鳴らすか」
  PlaybackEngine.kt ステップ発音・ミックス・ソフトリミッタ
  StepTimeline.kt   再生位置の履歴（表示を音に合わせる）
  SongCodec.kt      JSON の読み書きと値の補正

app/    … Android アプリ（Compose UI / AudioTrack / ファイル保存）
  audio/AudioOutput.kt     AudioTrack への書き込みスレッド
  data/SongRepository.kt   自動保存つきの曲リポジトリ
  ui/                      画面・ViewModel・テーマ
```

## ビルド方法

Android Studio (Ladybug 以降推奨) でプロジェクトを開くか、コマンドラインから:

```bash
./gradlew :core:test           # 音源・シーケンサの単体テスト
./gradlew testDebugUnitTest    # アプリ側の単体テスト
./gradlew assembleDebug        # デバッグ APK をビルド
```

- **compileSdk / targetSdk**: 35
- **minSdk**: 26 (Android 8.0)

Android SDK のパスは `local.properties` に `sdk.dir=/path/to/Android/sdk` として設定するか、
`ANDROID_HOME` 環境変数で指定してください（このファイルは Git 管理外）。

GitHub Actions (`.github/workflows/android.yml`) でも単体テストと APK ビルドを行い、
`rhythmbox-latest` タグの **GitHub Release** に `RhythmBox.apk` を公開します
（Actions の成果物ストレージ枠を消費しないため）。最新版はいつも同じ URL から取得できます。

## アップデート（データを維持したまま更新）

固定のデバッグ署名鍵 (`app/debug.keystore`) を使うため、**新しい APK を上書きインストールするだけ**で更新でき、
保存した曲データはそのまま残ります（アンインストール不要）。

- ⚠️ `app/debug.keystore` はデバッグ用の鍵（パスワード `android`）です。Google Play で配布する場合は別途リリース用の署名鍵が必要です。

## 音づくりのメモ

- キック: サイン波のピッチを 141Hz → 46Hz へ一気に落とし、頭にノイズのクリックを重ねる
- スネア: 186Hz / 331Hz のサイン波 + ハイパスしたノイズ
- ハイハット: 6 本の矩形波を非整数倍で重ねてハイパス（減衰時間だけ変えてクローズド／オープン）
- クラップ: 10ms 間隔のノイズバースト 3 連発 + 残響
- 同時に鳴った音が歪まないよう、出力の直前でソフトリミッタを通しています
- クローズドハットはオープンハットを止めます（実機と同じ挙動）

## ライセンス

サンプルアプリのため任意にご利用ください。
