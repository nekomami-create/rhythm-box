package com.example.rhythmbox.core

import kotlin.math.roundToInt

/**
 * パッドを叩いた位置を、打ち込みのグリッドに落とす。
 *
 * 人の手は必ず前後にずれるので、そのまま置くと聴けるものにならない。
 * いちばん近いステップに寄せる（クオンタイズ）。
 */
object PadRecorder {

    /**
     * [step] が鳴ってから [offsetSteps] ステップぶん経ったところで叩いた、として
     * いちばん近いステップを返す。小節をまたいだら先頭に回り込む（1 小節のループなので）。
     */
    fun quantise(step: Int, offsetSteps: Double): Int =
        (step + offsetSteps.roundToInt()).mod(STEPS_PER_BAR)

    /**
     * 叩いた時刻からステップを求める。
     *
     * [stepFrame] は今鳴っているステップが始まったフレーム、[hitFrame] は
     * 叩いた瞬間に実際にスピーカーから出ていたフレーム。耳で聞いた音に
     * 合わせて叩くので、再生済みの位置を基準にするのが正しい。
     */
    fun stepAt(step: Int, stepFrame: Long, hitFrame: Long, framesPerStep: Double): Int {
        if (framesPerStep <= 0.0) return step
        return quantise(step, (hitFrame - stepFrame) / framesPerStep)
    }

    /** [row] の [step] に音を置いた（重ね録り用に、すでにあるものは消さない）パターン。 */
    fun record(pattern: Pattern, row: Int, step: Int, level: Pattern.Level): Pattern =
        pattern.set(row, step, true).withLevel(row, step, level)
}
