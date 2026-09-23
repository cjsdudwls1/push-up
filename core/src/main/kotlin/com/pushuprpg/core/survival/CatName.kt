package com.pushuprpg.core.survival

/**
 * The cat's name, as the user typed it, made safe to show and to put in a sentence.
 *
 * Korean attaches a particle to a name, and which one depends on how the name ends: 치즈**가**,
 * 호박**이**. A line that hardcodes either reads wrong for half of all names, so the particle is
 * chosen here, from the name, rather than written into the string.
 */
object CatName {

    /** Long enough for 삼색이 and 고등어네 막내; short enough to fit a speech bubble's name tag. */
    const val MAX_LENGTH = 8

    /** One line, no leading or trailing space, at most [MAX_LENGTH] characters. Blank stays blank. */
    fun clean(input: String): String =
        input.replace(Regex("\\s+"), " ").trimStart().take(MAX_LENGTH)

    /** [clean], and also without trailing space — for storing, once the user has finished typing. */
    fun finished(input: String): String = clean(input).trim()

    /** 이 after a final consonant, 가 otherwise — and 가 for anything that is not a Hangul syllable. */
    fun subjectParticle(name: String): String = if (endsInConsonant(name)) "이" else "가"

    /** 을 or 를, by the same rule. */
    fun objectParticle(name: String): String = if (endsInConsonant(name)) "을" else "를"

    private fun endsInConsonant(name: String): Boolean {
        val last = name.trimEnd().lastOrNull() ?: return false
        if (last !in '가'..'힣') return false
        return (last - '가') % 28 != 0
    }
}
