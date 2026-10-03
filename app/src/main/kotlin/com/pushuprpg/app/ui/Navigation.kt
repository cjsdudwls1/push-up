package com.pushuprpg.app.ui

import com.pushuprpg.core.detect.ExerciseType

/**
 * Route names.
 *
 * Plain strings rather than a sealed hierarchy: there are a handful of destinations and one of them
 * takes arguments, so the type ceremony would cost more than it saves.
 */
object Routes {
    const val BOOT = "boot"
    const val ONBOARDING = "onboarding"
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val RECORDS = "records"
    const val SETTINGS = "settings"

    /** 고양이 꾸미기: the cat's name and coat, and what it has found. Reached from the cat on the hub. */
    const val WARDROBE = "wardrobe"

    private const val SURVIVAL_BASE = "survival"
    const val SURVIVAL = "$SURVIVAL_BASE/{tutorial}/{exercise}"
    const val ARG_TUTORIAL = "tutorial"
    const val ARG_EXERCISE = "exercise"

    /**
     * The tutorial is always pushups: it is the first set anybody does here, and a first-time user
     * has not been asked about any other movement yet.
     */
    fun survival(tutorial: Boolean = false, exercise: ExerciseType = ExerciseType.PUSHUP) =
        "$SURVIVAL_BASE/$tutorial/${exercise.name}"

    /**
     * Choosing the movement for 고양이 지켜줘, on its own destination rather than inside the run:
     * the camera is not live while somebody reads a safety note.
     */
    const val SURVIVAL_PICK = "survival_pick"

    /**
     * The run, which is dark whatever the user picked for the menus.
     *
     * The destination wraps its screen in `AlwaysDark`; this set is what tells the system bars the
     * same thing, since they sit outside any one screen. A new camera destination needs both.
     */
    val ALWAYS_DARK = setOf(SURVIVAL)
}
