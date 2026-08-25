package com.example.rhythmbox.core

/**
 * ある音が、いま鳴っているコードと調に対してどういう立場にあるか。
 *
 * ピアノロールで色を分けるために使う。どの音がコードの構成音なのかが
 * 目で分かると、外れない旋律が書きやすくなるし、コードの成り立ちも掴める。
 */
enum class NoteRole {
    /** コードの構成音。ここに置けばまず外れない。 */
    CHORD_TONE,

    /** 調の音階の音。コードの構成音ではないが、通り道として使える。 */
    SCALE_TONE,

    /** 調の外の音。使えないわけではないが、外れて聞こえやすい。 */
    OUTSIDE,
}

/**
 * [midi] が [chord] と [key] に対してどの立場か。
 * オクターブは見ないので、どの高さの C も同じ扱いになる。
 */
fun noteRole(midi: Int, chord: Chord, key: MusicKey): NoteRole {
    val pitch = midi.mod(12)
    val chordTones = chord.voicing().map { it.mod(12) }.toSet()
    return when {
        pitch in chordTones -> NoteRole.CHORD_TONE
        pitch in key.scalePitches() -> NoteRole.SCALE_TONE
        else -> NoteRole.OUTSIDE
    }
}

/**
 * [midi] がコードの中で何番目の音か（R / 3 / 5 / 7 …）。構成音でなければ null。
 *
 * ルートからの距離を度数にする。3 度や 5 度が見えると、
 * コードがどう積まれているのかが分かる。
 */
fun chordDegreeLabel(midi: Int, chord: Chord): String? {
    val fromRoot = (midi - chord.root).mod(12)
    if (fromRoot !in chord.quality.intervals.map { it.mod(12) }) return null
    return when (fromRoot) {
        0 -> "R"
        2 -> "9"
        3 -> "♭3"
        4 -> "3"
        5 -> "4"
        6 -> "♭5"
        7 -> "5"
        8 -> "♯5"
        9 -> "6"
        10 -> "7"
        11 -> "M7"
        else -> null
    }
}
