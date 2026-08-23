package com.example.rhythmbox.core

/**
 * 「何フレーム目にどのステップが鳴ったか」の履歴を持つリングバッファ。
 * 音声はバッファ経由でスピーカーに届くため、UI はここに実際の再生位置
 * （AudioTrack の再生済みフレーム数）を問い合わせて表示を合わせる。
 */
class StepTimeline(capacity: Int = 256) {
    private val frames = LongArray(capacity)
    private val steps = IntArray(capacity)
    private val bars = IntArray(capacity)
    private var writeIndex = 0
    @Volatile private var count = 0

    data class Position(val bar: Int, val step: Int)

    fun clear() {
        count = 0
        writeIndex = 0
    }

    fun record(frame: Long, bar: Int, step: Int) {
        frames[writeIndex] = frame
        bars[writeIndex] = bar
        steps[writeIndex] = step
        writeIndex = (writeIndex + 1) % frames.size
        if (count < frames.size) count++
    }

    /** [frame] フレーム目までに鳴り始めた最後のステップ。まだ何も鳴っていなければ null。 */
    fun positionAt(frame: Long): Position? {
        var bestFrame = Long.MIN_VALUE
        var bestIndex = -1
        val size = count
        for (i in 0 until size) {
            val f = frames[i]
            if (f in (bestFrame + 1)..frame) {
                bestFrame = f
                bestIndex = i
            }
        }
        return if (bestIndex < 0) null else Position(bars[bestIndex], steps[bestIndex])
    }
}
