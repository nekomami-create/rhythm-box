package com.example.rhythmbox.web

import com.example.rhythmbox.core.Chord
import com.example.rhythmbox.core.DrumKit
import com.example.rhythmbox.core.DrumSynth
import com.example.rhythmbox.core.Genre
import com.example.rhythmbox.core.MusicKey
import com.example.rhythmbox.core.OfflineRenderer
import com.example.rhythmbox.core.PlaybackEngine
import com.example.rhythmbox.core.PlaybackPlan
import com.example.rhythmbox.core.Song
import com.example.rhythmbox.core.SongBuilder
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import kotlin.random.Random

/** 「曲構成（オート作曲）」画面。ジャンル・調・長さを決めて 1 曲まるごと作る。 */
object SongScreen {
    private val sampleRate = PlaybackEngine.DEFAULT_SAMPLE_RATE
    private val drumSamples by lazy { DrumSynth.renderAll(sampleRate) }
    private val chipDrumSamples by lazy { DrumSynth.renderAll(sampleRate, DrumKit.CHIP) }

    fun init() {
        val genreSelect = byId<HTMLSelectElement>("song-genre")
        genreSelect.setOptions(Genre.entries.map { it.name to it.label })

        val rootSelect = byId<HTMLSelectElement>("song-key-root")
        rootSelect.setOptions(Chord.ROOT_NAMES.mapIndexed { i, n -> i.toString() to n })

        val scaleSelect = byId<HTMLSelectElement>("song-key-scale")
        scaleSelect.setOptions(listOf("major" to "メジャー", "minor" to "マイナー"))

        val barsSelect = byId<HTMLSelectElement>("song-bars")
        barsSelect.setOptions(
            SongBuilder.BAR_CHOICES.map { it.toString() to "$it 小節" },
            selectedValue = "32",
        )

        val generateButton = byId<HTMLButtonElement>("song-generate")
        val status = byId<HTMLElement>("song-status")
        val result = byId<HTMLElement>("song-result")
        val audio = byId<HTMLAudioElement>("song-audio")
        val download = byId<HTMLAnchorElement>("song-download")

        generateButton.onclick = {
            generateButton.disabled = true
            status.textContent = "作っています…"
            result.classList.add("hidden")
            // 直後に描画を挟まないと「作っています」が出る前に重い処理で固まって見える。
            window.setTimeout({
                try {
                    val genre = Genre.valueOf(genreSelect.value)
                    val root = rootSelect.value.toInt()
                    val minor = scaleSelect.value == "minor"
                    val key = MusicKey(root, minor)
                    val bars = barsSelect.value.toInt()
                    val recipe = genre.recipe()
                    val base = Song.newSong("web-song", genre.label, 0L)
                    val song = SongBuilder.build(base, recipe, key, bars, Random(Random.nextInt()))
                    val plan = PlaybackPlan.arrangement(song)
                    val samples = OfflineRenderer.render(
                        song,
                        plan,
                        drumSamples,
                        sampleRate,
                        chipVoiceSamples = chipDrumSamples,
                    )
                    val pcm16 = OfflineRenderer.toPcm16(samples)
                    val mp3 = encodeMp3Stereo(pcm16, sampleRate)
                    val url = blobUrl(mp3)
                    audio.src = url
                    download.href = url
                    result.classList.remove("hidden")
                    status.textContent = "${song.totalBars()} 小節・${song.bpm} BPM・${key.name} で作りました。"
                } catch (e: Throwable) {
                    status.textContent = "失敗しました: ${e.message}"
                } finally {
                    generateButton.disabled = false
                }
            }, 10)
        }
    }
}
