package com.pushuprpg.core.game

import com.pushuprpg.core.detect.ExerciseType

/**
 * One enemy as authored: a *rep cost*, not a pile of hit points.
 *
 * [standardRepCost] says "this should take about this many pushups for a typical beginner". The
 * actual HP is computed per player at spawn, so the design intent survives every level-up, class
 * change and balance pass.
 */
data class EnemyTemplate(
    val id: String,
    val korean: String,
    val standardRepCost: Int,
    val defense: Int = 0,
    val attack: Int = 4,
    val rageThreshold: Int = 8,
    val ultimateFraction: Float = 0.35f,
    val weakness: ExerciseType? = null,
    val resist: ExerciseType? = null,
    val isBoss: Boolean = false,
    /** Ward size as a fraction of the enemy's own HP; 0 for no ward. */
    val wardFraction: Float = 0f,
) {
    /**
     * HP here is a count of reps, so the only things that can change it are the movement and the
     * difficulty the user chose. Neither the player's level nor their measured capacity enters into
     * it any more — a tier that says 100 costs 100 for everybody, which is what makes it readable.
     */
    fun spawn(
        difficulty: Difficulty,
        exercise: ExerciseType,
    ): Enemy {
        val hp = CombatResolver.enemyMaxHp(standardRepCost, difficulty, exercise)
        // A ward is a weakness, not a wall. The movement it is weak to pays the ward's face value in
        // reps; anything else pays several times over but always gets through. Priced here rather
        // than per rep because a rep is worth exactly one and a fraction of one is not a rep.
        val wardBase = hp * wardFraction
        val ward = when {
            wardFraction <= 0f -> 0
            weakness == null || exercise == weakness -> wardBase.toInt()
            else -> (wardBase / CombatResolver.WARD_CHIP).toInt()
        }
        return Enemy(
            id = id,
            korean = korean,
            maxHp = hp,
            hp = hp,
            defense = defense,
            attack = attack,
            rageThreshold = rageThreshold,
            ultimateFraction = ultimateFraction,
            weakness = weakness,
            resist = resist,
            isBoss = isBoss,
            wardHp = ward,
            wardMaxHp = ward,
        )
    }
}

data class Dungeon(
    val index: Int,
    val korean: String,
    val floors: List<EnemyTemplate>,
    val recommendedLevel: IntRange,
) {
    val standardRepCost: Int get() = floors.sumOf { it.standardRepCost }

    /** The level the encounters are balanced around; outgrowing it is what makes them easier. */
    val referenceLevel: Int get() = recommendedLevel.first

    /** Long dungeons must checkpoint per floor or they become one impossible sitting. */
    val checkpointed: Boolean get() = standardRepCost >= 56
}

object Dungeons {

    val ALL: List<Dungeon> = listOf(
        Dungeon(
            1, "부서진 문",
            listOf(
                EnemyTemplate("goblin_scout", "고블린 정찰병", 4, attack = 4),
                EnemyTemplate("goblin_archer", "고블린 궁수", 5, attack = 4),
                EnemyTemplate(
                    "goblin_king", "고블린 킹", 9,
                    attack = 4, rageThreshold = 8, ultimateFraction = 0.35f, isBoss = true,
                ),
            ),
            recommendedLevel = 1..2,
        ),
        Dungeon(
            2, "이끼 낀 회랑",
            listOf(
                EnemyTemplate("moss_crawler", "이끼 기어다니는 것", 5, defense = 1, attack = 5),
                EnemyTemplate("moss_crawler_2", "이끼 포식자", 6, defense = 1, attack = 5),
                EnemyTemplate("vine_warden", "덩굴 파수꾼", 7, defense = 1, attack = 5),
                EnemyTemplate(
                    "moss_lord", "이끼 군주", 8,
                    defense = 1, attack = 5, rageThreshold = 8, ultimateFraction = 0.37f, isBoss = true,
                ),
            ),
            recommendedLevel = 3..4,
        ),
        Dungeon(
            3, "석상의 방",
            listOf(
                EnemyTemplate("stone_sentry", "석상 보초", 7, defense = 3, attack = 7, weakness = ExerciseType.SQUAT),
                EnemyTemplate("stone_sentry_2", "석상 수호병", 8, defense = 3, attack = 7, weakness = ExerciseType.SQUAT),
                EnemyTemplate("gargoyle", "가고일", 9, defense = 3, attack = 7, weakness = ExerciseType.SQUAT),
                EnemyTemplate(
                    "stone_colossus", "석상 거인", 10,
                    defense = 3, attack = 7, rageThreshold = 7, ultimateFraction = 0.40f,
                    weakness = ExerciseType.SQUAT, isBoss = true,
                ),
            ),
            recommendedLevel = 5..6,
        ),
        Dungeon(
            4, "지하 수로",
            listOf(
                EnemyTemplate("sewer_rat", "시궁쥐 무리", 7, defense = 5, attack = 9),
                EnemyTemplate("slime", "점액 덩어리", 8, defense = 5, attack = 9),
                EnemyTemplate("drowned", "물에 잠긴 자", 9, defense = 5, attack = 9),
                EnemyTemplate("eel", "전기 뱀장어", 9, defense = 5, attack = 9),
                EnemyTemplate(
                    "sewer_horror", "수로의 공포", 11,
                    defense = 5, attack = 9, rageThreshold = 7, ultimateFraction = 0.42f,
                    weakness = ExerciseType.PUSHUP, isBoss = true,
                ),
            ),
            recommendedLevel = 7..8,
        ),
        Dungeon(
            5, "화염 갱도",
            listOf(
                EnemyTemplate("ember_hound", "불씨 사냥개", 9, defense = 8, attack = 12),
                EnemyTemplate("magma_worker", "용암 일꾼", 10, defense = 8, attack = 12, weakness = ExerciseType.SQUAT),
                EnemyTemplate("flame_wisp", "불꽃 도깨비", 11, defense = 8, attack = 12),
                EnemyTemplate("forge_golem", "용광로 골렘", 12, defense = 8, attack = 12, weakness = ExerciseType.PUSHUP),
                EnemyTemplate(
                    "cinder_lord", "잿불 군주", 14,
                    defense = 8, attack = 12, rageThreshold = 6, ultimateFraction = 0.45f, isBoss = true,
                ),
            ),
            recommendedLevel = 9..11,
        ),
        Dungeon(
            6, "봉인된 성소",
            listOf(
                EnemyTemplate("acolyte", "봉인 사제", 12, defense = 11, attack = 15),
                EnemyTemplate("ward_keeper", "결계 지기", 13, defense = 11, attack = 15, wardFraction = 0.30f),
                EnemyTemplate("seal_beast", "봉인수", 14, defense = 11, attack = 15),
                EnemyTemplate("choir", "성가대", 15, defense = 11, attack = 15),
                EnemyTemplate(
                    "sealed_one", "봉인된 자", 16,
                    defense = 11, attack = 15, rageThreshold = 6, ultimateFraction = 0.48f,
                    weakness = ExerciseType.PLANK, wardFraction = 0.30f, isBoss = true,
                ),
            ),
            recommendedLevel = 12..14,
        ),
        Dungeon(
            7, "얼어붙은 첨탑",
            listOf(
                EnemyTemplate("frost_wolf", "서리 늑대", 13, defense = 15, attack = 19),
                EnemyTemplate("ice_sentinel", "얼음 감시자", 14, defense = 15, attack = 19, weakness = ExerciseType.SQUAT),
                EnemyTemplate("blizzard_shade", "눈보라 망령", 15, defense = 15, attack = 19),
                EnemyTemplate("frozen_knight", "얼어붙은 기사", 15, defense = 15, attack = 19),
                EnemyTemplate("rime_drake", "서리 비룡", 15, defense = 15, attack = 19),
                EnemyTemplate(
                    "spire_warden", "첨탑의 주인", 16,
                    defense = 15, attack = 19, rageThreshold = 5, ultimateFraction = 0.51f,
                    weakness = ExerciseType.SQUAT, isBoss = true,
                ),
            ),
            recommendedLevel = 15..17,
        ),
        Dungeon(
            8, "왕좌의 홀",
            listOf(
                EnemyTemplate("royal_guard", "왕실 근위병", 16, defense = 20, attack = 24),
                EnemyTemplate("royal_guard_2", "왕실 기사단장", 17, defense = 20, attack = 24),
                EnemyTemplate("court_mage", "궁정 마법사", 18, defense = 20, attack = 24, wardFraction = 0.30f),
                EnemyTemplate("executioner", "처형인", 19, defense = 20, attack = 24),
                EnemyTemplate("champion", "왕의 챔피언", 19, defense = 20, attack = 24),
                EnemyTemplate(
                    "throne_king", "왕좌의 왕", 21,
                    defense = 20, attack = 24, rageThreshold = 5, ultimateFraction = 0.55f, isBoss = true,
                ),
            ),
            recommendedLevel = 18..20,
        ),
    )

    fun byIndex(index: Int): Dungeon? = ALL.firstOrNull { it.index == index }

    /** The free-forever dungeon. See the monetization notes: exercise itself is never paywalled. */
    val FREE_DUNGEON: Dungeon = ALL.first()
}
