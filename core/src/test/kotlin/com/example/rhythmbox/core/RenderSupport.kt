package com.example.rhythmbox.core

/**
 * 左チャンネルだけを [out] に取り出して描く（テスト用）。
 *
 * ほとんどのテストが見たいのは「いつ・何が・どれだけの大きさで鳴るか」で、
 * 左右のどちらに寄っているかではない。定位の既定は中央で、中央は左右とも
 * 音量 1.0 なので、片方だけ見れば以前のモノラルと同じ値が並ぶ。
 * 定位そのものは StereoTest で左右を突き合わせて確かめる。
 */
internal fun PlaybackEngine.renderLeft(out: FloatArray): Boolean {
    val stereo = FloatArray(out.size * CHANNELS)
    val playing = render(stereo)
    for (frame in out.indices) out[frame] = stereo[frame * CHANNELS]
    return playing
}
