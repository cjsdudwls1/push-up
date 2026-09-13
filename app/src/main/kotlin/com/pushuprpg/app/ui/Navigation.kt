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
    const val PERMISSION = "permission"
    const val HOME = "home"
    const val DUNGEON_SELECT = "dungeon_select"
    const val RECORDS = "records"
    const val SETTINGS = "settings"
    const val PAYWALL = "paywall"
    const val SURVIVAL = "survival"

    private const val BATTLE_BASE = "battle"
    const val BATTLE = "$BATTLE_BASE/{dungeonIndex}"
    const val ARG_DUNGEON_INDEX = "dungeonIndex"

    fun battle(dungeonIndex: Int) = "$BATTLE_BASE/$dungeonIndex"

    private const val RESULT_BASE = "result"
    const val RESULT = "$RESULT_BASE/{dungeonIndex}"

    fun result(dungeonIndex: Int) = "$RESULT_BASE/$dungeonIndex"
}
