package com.pushuprpg.app.share

/**
 * What a share card says.
 *
 * Modelled rather than passed as loose numbers because the card is the only part of this app a
 * non-user ever sees, and a card missing a field renders a hole in an image someone is about to
 * post. Everything the renderer needs is here or the type does not compile.
 */
sealed interface ShareCardData {

    /** 고냥이 지켜줘. The card that has to travel. */
    data class Survival(
        val score: Int,
        val best: Int,
        val reps: Int,
        val seconds: Int,
    ) : ShareCardData

    /** A dungeon run, cleared or not. Losses share too — that is the whole promise of the mode. */
    data class Dungeon(
        val dungeonName: String,
        val cleared: Boolean,
        val reps: Int,
        val maxCombo: Int,
        val seconds: Int,
        val rankKorean: String,
        val lifetimeReps: Int,
    ) : ShareCardData
}
