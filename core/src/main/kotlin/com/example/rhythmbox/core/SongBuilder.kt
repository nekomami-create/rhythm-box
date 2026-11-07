package com.example.rhythmbox.core

import kotlin.random.Random

/**
 * ジャンルを 1 つ選んで、曲をまるごと組み立てる（オート作曲）。
 *
 * 4 小節を 1 ブロックとして、ブロックごとにパターンを割り当てる。
 * ドラム・コード・ベースは 4 小節同じものを繰り返すが、旋律だけは小節ごとに作る。
 * 下のコードが変わるのに旋律が同じままだと、和音から外れるうえに単調になるため。
 *
 * ブロックの並びは、思いつきの使い回しではなく「曲としてのまとまり」
 * （Aメロ→Bメロ→サビ→Aメロ→Cメロ→まとめ）を意識して割り付ける
 * （[sections] 参照）。幕が足りないくらい短い曲は今までどおりの単純な並びに
 * 落ちる。
 */
object SongBuilder {

    /** 1 ブロックの小節数。 */
    const val BLOCK = 4

    /** 作れる小節数の下限と上限。 */
    const val MIN_BARS = 4
    const val MAX_BARS = 64

    /**
     * 使うパターンの数の上限。
     * A〜D までにとどめて、E 以降は手で書く用に空けておく。
     */
    const val MAX_PATTERNS = 4

    /** 前半に使うパターン。 */
    const val FIRST_PATTERN = 0

    /** 後半に使うパターン。 */
    const val SECOND_PATTERN = 1

    /** 選べる小節数（4, 8, 12 … 64）。 */
    val BAR_CHOICES: List<Int> = (MIN_BARS..MAX_BARS step BLOCK).toList()

    /** 曲としての役割（幕）。 */
    enum class Section(val label: String) {
        A_MELODY("Aメロ"),
        B_MELODY("Bメロ"),
        CHORUS("サビ"),
        C_MELODY("Cメロ"),
        OUTRO("まとめ"),
    }

    /** [section] を [blocks] ブロック（[BLOCK] 小節単位）ぶん、パターン [patternIndex] で鳴らす、という 1 幕。 */
    data class SectionSlot(val section: Section, val patternIndex: Int, val blocks: Int)

    /**
     * 曲としての「型」。Aメロ→Bメロ→サビ→Aメロ→Cメロ→まとめ、の 6 幕。
     * 重みの合計は 16（＝64 小節ぶん、[MAX_BARS] を [BLOCK] で割った数）にしてある。
     *
     * パターン番号（[MAX_PATTERNS] を超えない 4 つまで）は幕をまたいで使い回す。
     * まとめはサビと同じパターン番号にしてあり、サビをそのまま繰り返して
     * 終える、よくある終わり方になる。2 回目のAメロも 1 回目と同じ
     * パターン番号（＝同じ演奏）に戻ってくる。
     */
    private val SECTION_TEMPLATE = listOf(
        SectionSlot(Section.A_MELODY, patternIndex = 0, blocks = 3),
        SectionSlot(Section.B_MELODY, patternIndex = 1, blocks = 2),
        SectionSlot(Section.CHORUS, patternIndex = 2, blocks = 3),
        SectionSlot(Section.A_MELODY, patternIndex = 0, blocks = 3),
        SectionSlot(Section.C_MELODY, patternIndex = 3, blocks = 3),
        SectionSlot(Section.OUTRO, patternIndex = 2, blocks = 2),
    )

    /**
     * [bars] 小節を、[SECTION_TEMPLATE] の重みに合わせて幕ごとのブロック数に配分する。
     *
     * 幕を全部組めるだけのブロック数（[SECTION_TEMPLATE] の幕の数）に満たない
     * 短い曲は、後ろ（まとめ側）の幕から間引いて 1 ブロックずつ当てる
     * （曲としてのまとまりを持たせるには短すぎるので、今までどおりの
     * 素直な並びに落とす）。
     */
    fun sections(bars: Int): List<SectionSlot> {
        val totalBlocks = normalizeBars(bars) / BLOCK
        if (totalBlocks < SECTION_TEMPLATE.size) {
            return SECTION_TEMPLATE.take(totalBlocks).map { it.copy(blocks = 1) }
        }
        val totalWeight = SECTION_TEMPLATE.sumOf { it.blocks }
        val raw = SECTION_TEMPLATE.map { it.blocks.toDouble() * totalBlocks / totalWeight }
        val counts = raw.map { it.toInt().coerceAtLeast(1) }.toMutableList()
        // 端数は、切り捨てた量が大きい幕から順に 1 ブロックずつ足していく（最大剰余法）。
        var remainder = totalBlocks - counts.sum()
        val byFraction = raw.indices.sortedByDescending { raw[it] - counts[it] }
        var i = 0
        while (remainder > 0) {
            counts[byFraction[i % byFraction.size]]++
            remainder--
            i++
        }
        return SECTION_TEMPLATE.mapIndexed { index, slot -> slot.copy(blocks = counts[index]) }
    }

    /** [bars] 小節ぶんに敷き詰めたときの、ブロックごとに使うパターンの番号（[sections] を展開したもの）。 */
    fun patternLayout(bars: Int): List<Int> =
        sections(bars).flatMap { slot -> List(slot.blocks) { slot.patternIndex } }

    /** 4 小節単位に丸めて、扱える範囲に収める。 */
    fun normalizeBars(bars: Int): Int =
        (bars / BLOCK * BLOCK).coerceIn(MIN_BARS, MAX_BARS)

    fun build(
        base: Song,
        recipe: GenreRecipe,
        key: MusicKey,
        bars: Int = 8,
        random: Random = Random.Default,
        /** 旋律も作るか。 */
        withMelody: Boolean = true,
    ): Song {
        val total = normalizeBars(bars)
        val slots = sections(total)
        val layout = slots.flatMap { slot -> List(slot.blocks) { slot.patternIndex } }
        // 幕を全部組めるだけの長さがある曲だけ、幕ごとに進行そのものを選び直す。
        // 短い曲は今までどおり 1 本の進行を頭から敷くだけにする。
        val structured = layout.size >= SECTION_TEMPLATE.size

        // パターン番号ごとの、その幕で敷く 1 ブロック（[BLOCK] 小節）ぶんの和音。
        // 幕の最初の出番だけで決め、同じパターン番号が後から出てきても
        // 使い回す（[getOrPut]）ので、あとで何度使い回してもコードが食い違わない。
        //
        // 旋律を作る前に済ませるのも大事で、あとから足すと旋律が元のコードの
        // 3 度を歌ってしまい、預けたはずの音とぶつかる。
        // 7th の色付けが先。あとから掛けると、sus4 にして 3 度を預けた和音に
        // また 3 度が戻ってくる（7th は 3 度の上に積む音なので）。
        val chordsByPattern = LinkedHashMap<Int, List<Chord>>()
        if (structured) {
            // 幕ごとに進行をまるごと選び直す。曲のどこを切っても同じ数本の
            // 進行を使い回すだけだと曲の中での「幅」が出ないので、パターン
            // （＝幕の役割）が初めて出てくるたびに別の進行を引き、出だしの
            // 位置もその進行の中からランダムに決める。
            for (slot in slots) {
                chordsByPattern.getOrPut(slot.patternIndex) {
                    val progression = recipe.pickProgression(random)
                    val coloured = Harmony.enrichSevenths(
                        progression.chords(key),
                        progression.keyFor(key),
                        recipe.seventhChance,
                        random,
                    )
                    val cycle = Harmony.sprinkleSus4(coloured, random)
                    val offset = random.nextInt(cycle.size)
                    List(BLOCK) { cycle[(offset + it).mod(cycle.size)] }
                }
            }
        } else {
            // 曲としてのまとまりを持たせるには短すぎる曲は、進行を 1 本だけ選び、
            // 頭から流れのまま敷く（今までどおりの振る舞い）。
            val progression = recipe.pickProgression(random)
            val coloured = Harmony.enrichSevenths(
                progression.chords(key),
                progression.keyFor(key),
                recipe.seventhChance,
                random,
            )
            val cycle = Harmony.sprinkleSus4(coloured, random)
            var barCursor = 0
            for (slot in slots) {
                val offset = barCursor % cycle.size
                chordsByPattern.getOrPut(slot.patternIndex) {
                    List(BLOCK) { cycle[(offset + it).mod(cycle.size)] }
                }
                barCursor += slot.blocks * BLOCK
            }
        }
        val chords = layout.flatMap { patternIndex -> chordsByPattern.getValue(patternIndex) }
        val style = recipe.pickRhythm(random)

        var song = base.copy(bpm = recipe.pickBpm(random), bassStyle = recipe.bassStyle)

        // 同じパターンが何ブロックかに出てくる。最初に出てくるブロックの
        // コードに合わせて作れば、以降のブロックでもコードの並びは同じになる。
        val firstBlockOf = layout.withIndex()
            .groupBy({ it.value }, { it.index })
            .mapValues { it.value.first() }

        var previousLead: List<Int>? = null
        firstBlockOf.entries.sortedBy { it.key }.forEach { (patternIndex, blockIndex) ->
            val blockChords = chords.subList(blockIndex * BLOCK, blockIndex * BLOCK + BLOCK)
            val generated = PatternGenerator.generate(style, random, song.pattern(patternIndex).name)
            // ブロックと同じ長さのパターンにして、最後の 1 小節だけ崩す。
            // 4 小節が寸分たがわず同じだと「ループ」に聞こえて「曲」にならない。
            var pattern = song.pattern(patternIndex)
                .withRhythmOf(generated)
                .withBarCount(BLOCK)
                .withRhythmAt(BLOCK - 1, PatternGenerator.fill(generated, random))
            if (withMelody) {
                // ブロックのコード 1 つにつき 1 小節ぶんの旋律を作る。
                val leads = MelodyGenerator.generateBars(
                    chords = blockChords,
                    key = key,
                    random = random,
                    density = recipe.melodyDensity,
                    previous = previousLead,
                )
                pattern = pattern.withLeads(leads)
                previousLead = leads.lastOrNull()
            }
            song = song.withPattern(patternIndex, pattern)
            // パターン単体で鳴らしたときも、そのブロックの頭の響きになるように。
            song = song.withPatternChord(patternIndex, blockChords.first())
        }

        return song.copy(
            arrangement = layout.mapIndexed { blockIndex, patternIndex ->
                ArrangementStep(
                    patternIndex = patternIndex,
                    repeat = BLOCK,
                    chords = chords.subList(blockIndex * BLOCK, blockIndex * BLOCK + BLOCK),
                )
            },
        )
    }
}
