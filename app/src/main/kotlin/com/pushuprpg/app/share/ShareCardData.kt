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
        /**
         * The movement the session was played with, as the app names it: 푸쉬업. The card's chip,
         * since the best score beside it is that movement's, and the app's name is on the card
         * already.
         */
        val movement: String,
        val score: Int,
        val best: Int,
        val reps: Int,
        val seconds: Int,
    ) : ShareCardData
}
