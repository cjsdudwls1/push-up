package com.pushuprpg.app.ui

/**
 * Route names.
 *
 * Plain strings rather than a sealed hierarchy: there are a dozen destinations and two of them take
 * an argument, so the type ceremony would cost more than it saves.
 */
object Routes {
    const val BOOT = "boot"
    const val ONBOARDING = "onboarding"
    const val CLASS_PICK = "class_pick"

    /** The same picker after onboarding, reached from the hub or settings. Pops back when done. */
    const val CLASS_CHANGE = "class_change"
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val DUNGEON_SELECT = "dungeon_select"
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
    private const val SURVIVAL_BASE = "survival"
    const val SURVIVAL = "$SURVIVAL_BASE/{tutorial}"
    const val ARG_TUTORIAL = "tutorial"

    fun survival(tutorial: Boolean = false) = "$SURVIVAL_BASE/$tutorial"

    const val ARG_DUNGEON_INDEX = "dungeonIndex"

    /**
     * Choosing the movement sits on its own destination rather than inside the battle screen.
     *
     * Every way into a dungeon — the home shortcut, the dungeon list, retry, and next-dungeon from
     * the clear screen — goes through here, so there is no path that starts a run without a choice.
     * Keeping it off the battle screen also means the camera is not live while somebody reads a
     * safety warning.
     */
    private const val EXERCISE_PICK_BASE = "exercise_pick"
    const val EXERCISE_PICK = "$EXERCISE_PICK_BASE/{dungeonIndex}"

    fun exercisePick(dungeonIndex: Int) = "$EXERCISE_PICK_BASE/$dungeonIndex"

    private const val BATTLE_BASE = "battle"
    const val BATTLE = "$BATTLE_BASE/{dungeonIndex}"

    fun battle(dungeonIndex: Int) = "$BATTLE_BASE/$dungeonIndex"

    private const val RESULT_BASE = "result"
    const val RESULT = "$RESULT_BASE/{dungeonIndex}"

    fun result(dungeonIndex: Int) = "$RESULT_BASE/$dungeonIndex"

    /**
     * The run, which is dark whatever the user picked for the menus.
     *
     * Each of these destinations wraps its screen in `AlwaysDark`; this set is what tells the
     * system bars the same thing, since they sit outside any one screen. A new camera destination
     * needs both.
     */
    val ALWAYS_DARK = setOf(BATTLE, RESULT, SURVIVAL)
}
