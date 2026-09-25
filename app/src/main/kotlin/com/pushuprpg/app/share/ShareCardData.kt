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
        /** Seconds held in a hold such as the plank; zero for a run of counted movements. */
        val heldSeconds: Int,
        val maxCombo: Int,
        val seconds: Int,
        val rankKorean: String,
        val lifetimeReps: Int,
    ) : ShareCardData {
        /**
         * A run that only held is told in seconds, as its HUD and the result screen told it. Its
         * reps are zero by construction, and a card saying 0개 is a card nobody posts.
         */
        val inSeconds: Boolean get() = reps == 0 && heldSeconds > 0
    }
}
