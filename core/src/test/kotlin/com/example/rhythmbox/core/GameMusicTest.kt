package com.example.rhythmbox.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ゲーム音楽らしい進行と音づくりの確認。
 *
 * ♭III / ♭VI / ♭VII はナチュラルマイナーの中の和音なので、調の外から
 * 持ち込む必要はない。音階を切り替えるだけで出てくる。
 */
class GameMusicTest {

    private val cMajor = MusicKey(0, Scale.MAJOR)

    private fun names(template: ProgressionTemplate) = template.chords(cMajor).map { it.name }

    @Test
    fun `the field progression walks the flat side of a minor key`() {
        // C マイナー: i=Cm, ♭VII=A#, ♭VI=G#
        assertEquals(listOf("Cm", "A#", "G#", "A#"), names(ProgressionTemplate.GAME_FIELD))
    }

    @Test
    fun `the boss progression climbs and lands on the tonic`() {
        assertEquals(listOf("G#", "A#", "Cm", "Cm"), names(ProgressionTemplate.GAME_BOSS))
    }

    @Test
    fun `the dungeon progression falls away from the tonic`() {
        assertEquals(listOf("Cm", "G#", "D#", "A#"), names(ProgressionTemplate.GAME_DUNGEON))
    }

    @Test
    fun `the quest progression needs dorian to get its major fourth`() {
        // ここが音階を型ごとに持たせている理由。同じ 3 度でも、ドリアンなら
        // 長三和音の IV（F）、ナチュラルマイナーなら短三和音の iv（Fm）になる。
        assertEquals(listOf("Cm", "D#", "F", "A#"), names(ProgressionTemplate.GAME_QUEST))

        val asMinor = ProgressionTemplate.GAME_QUEST.copy(scale = Scale.NATURAL_MINOR)
        assertEquals("音階を取り違えると 4 度が短三和音になる", "Fm", names(asMinor)[2])
    }

    @Test
    fun `a template keeps the tonic and only swaps the scale`() {
        val key = MusicKey(7, Scale.MAJOR) // G メジャー
        val applied = ProgressionTemplate.GAME_FIELD.keyFor(key)

        assertEquals("主音はそのまま", 7, applied.tonic)
        assertEquals(Scale.NATURAL_MINOR, applied.scale)
        assertEquals("Gm", ProgressionTemplate.GAME_FIELD.chords(key).first().name)
    }

    @Test
    fun `templates that do not name a scale are left alone`() {
        // 前からある型は音階を指定していないので、曲の調のまま解決する。
        for (template in listOf(
            ProgressionTemplate.POP_PUNK,
            ProgressionTemplate.ROYAL_ROAD,
            ProgressionTemplate.KOMURO,
            ProgressionTemplate.CANON,
            ProgressionTemplate.FIFTIES,
            ProgressionTemplate.CITY,
            ProgressionTemplate.DANCE_LOOP,
            ProgressionTemplate.TWO_FIVE_ONE,
        )) {
            assertEquals("${template.name} は音階を決めない", cMajor, template.keyFor(cMajor))
        }
        assertEquals(listOf("C", "G", "Am", "F"), names(ProgressionTemplate.POP_PUNK))
    }

    @Test
    fun `every game progression stays inside the scale it names`() {
        for (template in listOf(
            ProgressionTemplate.GAME_FIELD,
            ProgressionTemplate.GAME_BOSS,
            ProgressionTemplate.GAME_DUNGEON,
            ProgressionTemplate.GAME_QUEST,
            ProgressionTemplate.GAME_TOWN,
            ProgressionTemplate.GAME_CAVERN,
        )) {
            val key = template.keyFor(cMajor)
            val allowed = key.scalePitches().toSet()
            for (chord in template.chords(cMajor)) {
                for (note in chord.quality.intervals.map { (chord.root + it).mod(12) }) {
                    assertTrue("${template.name} の ${chord.name} に調の外の音", note in allowed)
                }
            }
        }
    }

    // --- リズム -------------------------------------------------------------

    @Test
    fun `the chip rhythm drives the bass on every eighth`() {
        // ゲーム音楽の推進力はベースの連打から来ている。ここは揺らさず必ず置く。
        val pattern = PatternGenerator.generate(RhythmStyle.CHIP_DRIVE, Random(7))
        for (step in 0 until STEPS_PER_BAR step 2) {
            assertTrue("$step にベースが無い", pattern.isOn(ROW_BASS, step))
        }
    }

    @Test
    fun `the chip rhythm keeps a kick on every beat`() {
        val pattern = PatternGenerator.generate(RhythmStyle.CHIP_DRIVE, Random(11))
        for (step in listOf(0, 4, 8, 12)) {
            assertTrue("$step にキックが無い", pattern.isOn(Voice.KICK.ordinal, step))
        }
    }

    // --- ジャンル -----------------------------------------------------------

    @Test
    fun `only the game genre asks for the chip sound`() {
        for (genre in Genre.entries) {
            assertEquals("${genre.label}", genre == Genre.GAME, genre.chip)
        }
    }

    @Test
    fun `the game genre is the only one with scenes to choose from`() {
        assertEquals(GameScene.entries, Genre.GAME.scenes)
        for (genre in Genre.entries - Genre.GAME) {
            assertTrue("${genre.label} に場面がある", genre.scenes.isEmpty())
        }
    }

    @Test
    fun `every scene brings its own tempo, progressions and lead`() {
        // 場面ごとに速さも明暗もまるで違うので、1 つには畳めない。
        val recipes = GameScene.entries.map { it.recipe() }
        assertTrue("どの場面もチップ音源", recipes.all { it.chip })
        assertTrue("速さが場面ごとに違う", recipes.map { it.bpmRange }.distinct().size == recipes.size)
        assertTrue("進行が場面ごとに違う", recipes.map { it.progressions }.distinct().size == recipes.size)
        // 戦闘は歩く場面より速く、洞窟はいちばん遅い。
        assertTrue(GameScene.BOSS.recipe().bpmRange.first > GameScene.FIELD.recipe().bpmRange.first)
        assertTrue(GameScene.DUNGEON.recipe().bpmRange.first < GameScene.TOWN.recipe().bpmRange.first)
    }

    @Test
    fun `the town progression stays bright while still using a flat seventh`() {
        // ミクソリディアンなので、長三和音の I のまま ♭VII が入る。
        assertEquals(listOf("C", "A#", "F", "C"), names(ProgressionTemplate.GAME_TOWN))
    }

    @Test
    fun `the cavern progression leans on the flat second`() {
        // フリジアンの ♭II（半音上の長三和音）が独特の緊張を作る。
        // ♭VII のほうはフリジアンでは短三和音になる。
        assertEquals(listOf("Cm", "C#", "A#m", "Cm"), names(ProgressionTemplate.GAME_CAVERN))
    }

    // --- 音源 ---------------------------------------------------------------

    @Test
    fun `the chip sound set changes the chord and bass but nothing else`() {
        val chordChip = ToneSynth.timbre(Instrument.CHORD, set = SoundSet.CHIP)
        val bassChip = ToneSynth.timbre(Instrument.BASS, set = SoundSet.CHIP)

        // 細いパルスはアルペジオで回すと刺さるので、コードは丸い矩形波にしてある。
        assertEquals(ToneSynth.Waveform.Pulse(0.5f), chordChip.wave)
        assertEquals(ToneSynth.Waveform.ChipTriangle, bassChip.wave)
        // リードは 1 音ずつ選ぶものなので、音源の切り替えでは動かない。
        assertEquals(
            ToneSynth.timbre(Instrument.LEAD),
            ToneSynth.timbre(Instrument.LEAD, set = SoundSet.CHIP),
        )
    }

    @Test
    fun `the normal sound set is untouched`() {
        for (instrument in Instrument.entries) {
            assertEquals(
                ToneSynth.timbre(instrument),
                ToneSynth.timbre(instrument, set = SoundSet.NORMAL),
            )
        }
        assertNotEquals(
            ToneSynth.timbre(Instrument.CHORD),
            ToneSynth.timbre(Instrument.CHORD, set = SoundSet.CHIP),
        )
    }

    @Test
    fun `the sound set is remembered with the song`() {
        val saved = Song(id = "s", name = "曲").copy(soundSet = SoundSet.CHIP)
        val restored = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(saved), "s")))!!
            .songs
            .first()
        assertEquals(SoundSet.CHIP, restored.soundSet)
    }

    @Test
    fun `every scene drives its rhythm with the chip pattern`() {
        for (scene in GameScene.entries) {
            assertEquals(listOf(RhythmStyle.CHIP_DRIVE), scene.recipe().rhythms)
        }
    }

    @Test
    fun `each scene picks a lead voice that suits it`() {
        // どれもパルス波。細さで場面の軽さ・重さを付け分けている。
        for (scene in GameScene.entries) {
            val wave = ToneSynth.timbre(Instrument.LEAD, scene.recipe().leadVoice).wave
            assertTrue("${scene.label} がパルス波でない", wave is ToneSynth.Waveform.Pulse)
        }
        assertEquals(ToneSynth.LeadVoice.PULSE_12, GameScene.TOWN.recipe().leadVoice)
        assertEquals(ToneSynth.LeadVoice.PULSE_50, GameScene.DUNGEON.recipe().leadVoice)
    }

    @Test
    fun `the arpeggio speed is remembered with the song`() {
        val saved = Song(id = "s", name = "曲").copy(arpeggioSpeed = ArpeggioSpeed.SLOW)
        val restored = SongCodec.decode(SongCodec.encode(SongLibrary(listOf(saved), "s")))!!
            .songs
            .first()
        assertEquals(ArpeggioSpeed.SLOW, restored.arpeggioSpeed)
    }

    @Test
    fun `a song saved before the speed could be chosen opens on the gentler default`() {
        val json = """
            {"songs": [{"id": "old", "name": "前の形"}], "currentId": "old"}
        """.trimIndent()
        assertEquals(ArpeggioSpeed.NORMAL, SongCodec.decode(json)!!.current()!!.arpeggioSpeed)
    }

    @Test
    fun `a song saved before the chip sound existed opens on the normal one`() {
        val json = """
            {"songs": [{"id": "old", "name": "前の形"}], "currentId": "old"}
        """.trimIndent()
        assertEquals(SoundSet.NORMAL, SongCodec.decode(json)!!.current()!!.soundSet)
    }
}
