package com.example.rhythmbox.web

import com.example.rhythmbox.core.Appreciation
import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.DrumKit
import com.example.rhythmbox.core.DrumSynth
import com.example.rhythmbox.core.Genre
import com.example.rhythmbox.core.MusicKey
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.Pattern
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.STEPS_PER_BAR
import com.example.rhythmbox.core.Scale
import com.example.rhythmbox.core.SongBuilder
import com.example.rhythmbox.core.secondsPerStep
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.url.URL
import kotlin.random.Random

/**
 * 「鑑賞モード」画面。
 *
 * 元の鑑賞モードは終わりなく生成し続けるが、Web 版はリアルタイム再生
 * エンジンを持たない（フィージビリティ検証の結果、まずはここを持たない
 * 構成にすると決めた）。代わりに、指定した長さぶんをまとめて生成し、
 * ブラウザの普通の音声プレイヤーで聴く形にしている。
 */
object AppreciationScreen {
    private val sampleRate = PlaybackEngine.DEFAULT_SAMPLE_RATE
    private val drumSamples by lazy { DrumSynth.renderAll(sampleRate) }
    private val chipDrumSamples by lazy { DrumSynth.renderAll(sampleRate, DrumKit.CHIP) }

    private var plan: PlaybackPlan? = null
    private var barStartSeconds: DoubleArray = DoubleArray(0)
    private var barSection: Array<SongBuilder.Section> = arrayOf()
    private var objectUrl: String? = null
    private var rafHandle: Int? = null

    fun init() {
        val genreSelect = byId<HTMLSelectElement>("appr-genre")
        genreSelect.setOptions(listOf("auto" to "おまかせ") + Genre.entries.map { it.name to it.label })

        val rootSelect = byId<HTMLSelectElement>("appr-key-root")
        rootSelect.setOptions(listOf("auto" to "おまかせ") + Chord.ROOT_NAMES.mapIndexed { i, n -> i.toString() to n })

        val scaleSelect = byId<HTMLSelectElement>("appr-key-scale")
        scaleSelect.setOptions(
            listOf(
                "major" to "メジャー",
                "minor" to "マイナー",
                "dorian" to "ドリアン",
                "mixolydian" to "ミクソリディアン",
            ),
        )

        val minutesSelect = byId<HTMLSelectElement>("appr-minutes")
        minutesSelect.setOptions(
            listOf("1" to "1 分", "3" to "3 分", "5" to "5 分", "10" to "10 分"),
            selectedValue = "3",
        )

        val generateButton = byId<HTMLButtonElement>("appr-generate")
        val status = byId<HTMLElement>("appr-status")
        val result = byId<HTMLElement>("appr-result")
        val audio = byId<HTMLAudioElement>("appr-audio")
        val download = byId<HTMLAnchorElement>("appr-download")
        val info = byId<HTMLElement>("appr-info")
        val canvas = byId<HTMLCanvasElement>("appr-canvas")

        generateButton.onclick = {
            generateButton.disabled = true
            status.textContent = "作っています…（数秒〜数十秒かかります）"
            result.classList.add("hidden")
            stopVisualizer()
            window.setTimeout({
                try {
                    val genre = genreSelect.value.takeUnless { it == "auto" }?.let { Genre.valueOf(it) }
                    val key = if (rootSelect.value == "auto") {
                        null
                    } else {
                        MusicKey(rootSelect.value.toInt(), scaleOf(scaleSelect.value))
                    }
                    val minutes = minutesSelect.value.toDouble()
                    val built = buildAppreciation(genre, key, minutes * 60.0)
                    plan = built.plan
                    barStartSeconds = built.barStartSeconds
                    barSection = built.barSection

                    val samples = AppreciationRenderer.render(
                        built.plan,
                        built.blocks,
                        Appreciation.BLOCK,
                        drumSamples,
                        chipDrumSamples,
                        sampleRate,
                    )
                    val pcm16 = OfflineRenderer.toPcm16(samples)
                    val mp3 = encodeMp3Stereo(pcm16, sampleRate)
                    objectUrl?.let { URL.revokeObjectURL(it) }
                    val url = blobUrl(mp3)
                    objectUrl = url
                    audio.src = url
                    download.href = url
                    result.classList.remove("hidden")
                    status.textContent = "${built.plan.barCount} 小節ぶん作りました。"
                    startVisualizer(audio, canvas, info)
                } catch (e: Throwable) {
                    status.textContent = "失敗しました: ${e.message}"
                } finally {
                    generateButton.disabled = false
                }
            }, 10)
        }
    }

    private fun scaleOf(value: String): Scale = when (value) {
        "minor" -> Scale.NATURAL_MINOR
        "dorian" -> Scale.DORIAN
        "mixolydian" -> Scale.MIXOLYDIAN
        else -> Scale.MAJOR
    }

    private class Built(
        val plan: PlaybackPlan,
        val blocks: List<AppreciationRenderer.BlockInfo>,
        val barStartSeconds: DoubleArray,
        val barSection: Array<SongBuilder.Section>,
    )

    /** [targetSeconds] ぶん（またはそれを超えるまで）ブロックを伸ばしながら生成する。 */
    private fun buildAppreciation(genre: Genre?, key: MusicKey?, targetSeconds: Double): Built {
        val stream = Appreciation.Stream(genre, key, random = Random(Random.Default.nextInt()))
        val blocks = mutableListOf<AppreciationRenderer.BlockInfo>()
        val sections = mutableListOf<SongBuilder.Section>()

        fun recordBlock() {
            val status = stream.status
            blocks += AppreciationRenderer.BlockInfo(
                bpm = status.bpm,
                chip = status.recipe.chip,
                leadVoice = status.recipe.leadVoice,
                bassStyle = status.recipe.bassStyle,
            )
            sections += status.section
        }

        // コンストラクタの init { grow() } で、すでに 1 ブロックできている。
        recordBlock()
        var totalSeconds = Appreciation.BLOCK * STEPS_PER_BAR * secondsPerStep(blocks.last().bpm)
        while (totalSeconds < targetSeconds) {
            stream.grow()
            recordBlock()
            totalSeconds += Appreciation.BLOCK * STEPS_PER_BAR * secondsPerStep(blocks.last().bpm)
        }

        val plan = stream.plan()
        val barStart = DoubleArray(plan.barCount + 1)
        val barSectionArr = Array(plan.barCount) { SongBuilder.Section.A_MELODY }
        for (bar in 0 until plan.barCount) {
            val block = bar / Appreciation.BLOCK
            val bpm = blocks[block].bpm
            barStart[bar + 1] = barStart[bar] + STEPS_PER_BAR * secondsPerStep(bpm)
            barSectionArr[bar] = sections[block]
        }
        return Built(plan, blocks, barStart, barSectionArr)
    }

    // --- ビジュアライザ（LeadTrailVisualizer の簡易版） ---------------------

    private fun stopVisualizer() {
        rafHandle?.let { window.cancelAnimationFrame(it) }
        rafHandle = null
    }

    private fun startVisualizer(audio: HTMLAudioElement, canvas: HTMLCanvasElement, info: HTMLElement) {
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        fun frame(@Suppress("UNUSED_PARAMETER") timestamp: Double) {
            draw(audio, canvas, ctx, info)
            rafHandle = window.requestAnimationFrame(::frame)
        }
        rafHandle = window.requestAnimationFrame(::frame)
    }

    /** いま再生している秒数から、小節番号とその中のステップを引く。 */
    private fun barStepAt(currentTime: Double): Pair<Int, Int>? {
        val starts = barStartSeconds
        if (starts.size < 2) return null
        var bar = 0
        // 曲の長さ（多くて数百小節）なら線形探索でも軽い。
        while (bar < starts.size - 2 && starts[bar + 1] <= currentTime) bar++
        val barDuration = starts[bar + 1] - starts[bar]
        val frac = if (barDuration > 0) ((currentTime - starts[bar]) / barDuration).coerceIn(0.0, 1.0) else 0.0
        val step = (frac * STEPS_PER_BAR).toInt().coerceIn(0, STEPS_PER_BAR - 1)
        return bar to step
    }

    private const val TRAIL_STEPS = 64
    private const val LOWEST_MIDI = 48
    private const val HIGHEST_MIDI = 84

    private fun draw(audio: HTMLAudioElement, canvas: HTMLCanvasElement, ctx: CanvasRenderingContext2D, info: HTMLElement) {
        val plan = plan ?: return
        val (bar, step) = barStepAt(audio.currentTime) ?: return

        val chord = plan.chordAt(bar, step)
        info.innerHTML = ""
        barSection.getOrNull(bar)?.let { addChip(info, it.label) }
        addChip(info, chord.name)

        val width = canvas.width.toDouble()
        val height = canvas.height.toDouble()
        ctx.fillStyle = "#11141a"
        ctx.fillRect(0.0, 0.0, width, height)

        // 直近ぶんのリードの高さを線で描く。
        var b = bar
        var s = step
        val pitches = arrayOfNulls<Int>(TRAIL_STEPS)
        for (i in TRAIL_STEPS - 1 downTo 0) {
            if (b >= 0) {
                val pattern = plan.patternAt(b)
                val patternBar = plan.patternBarAt(b)
                val midi = pattern.soundingLead(patternBar, s)
                if (Pattern.isNote(midi)) pitches[i] = midi
            }
            s--
            if (s < 0) {
                s = STEPS_PER_BAR - 1
                b--
            }
        }

        fun y(midi: Int): Double =
            height - (midi - LOWEST_MIDI).toDouble() / (HIGHEST_MIDI - LOWEST_MIDI) * height

        ctx.strokeStyle = "#6fd7a8"
        ctx.lineWidth = 2.0
        ctx.beginPath()
        var drawing = false
        for (index in 0 until TRAIL_STEPS) {
            val midi = pitches[index]
            val x = width * index / (TRAIL_STEPS - 1)
            if (midi == null) {
                drawing = false
                continue
            }
            val yy = y(midi)
            if (!drawing) {
                ctx.moveTo(x, yy)
                drawing = true
            } else {
                ctx.lineTo(x, yy)
            }
        }
        ctx.stroke()

        pitches.last()?.let { midi ->
            ctx.fillStyle = "#6fd7a8"
            ctx.beginPath()
            ctx.arc(width, y(midi), 5.0, 0.0, 2 * kotlin.math.PI)
            ctx.fill()
        }
    }

    private fun addChip(container: HTMLElement, text: String) {
        val span = document.createElement("span") as HTMLElement
        span.className = "chip"
        span.textContent = text
        container.appendChild(span)
    }
}
