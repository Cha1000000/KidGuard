package ru.homelab.kidguard.core.ui.components

import androidx.annotation.DrawableRes
import ru.homelab.kidguard.R

/**
 * Набор детских аватарок (`res/drawable-nodpi/avatar_*.webp`). Индекс в этом списке — то, что
 * хранится в `children.avatar` на сервере. Порядок фиксирован: добавлять новые аватарки можно
 * только в КОНЕЦ, иначе сместятся уже назначенные детям индексы.
 */
object ChildAvatars {

    @DrawableRes
    val all: List<Int> = listOf(
        R.drawable.avatar_bear_king,        // 0
        R.drawable.avatar_gamer,            // 1
        R.drawable.avatar_astronaut_cat,    // 2
        R.drawable.avatar_street_artist,    // 3
        R.drawable.avatar_hacker,           // 4
        R.drawable.avatar_rainbow_fairy,    // 5
        R.drawable.avatar_friendly_robot,   // 6
        R.drawable.avatar_music_dj,         // 7
        R.drawable.avatar_mermaid_girl,     // 8
        R.drawable.avatar_skater,           // 9
        R.drawable.avatar_astro_naut,       // 10
        R.drawable.avatar_cool_dino,        // 11
        R.drawable.avatar_cyber_wolf,       // 12
        R.drawable.avatar_magic_unicorn,    // 13
        R.drawable.avatar_photographer,     // 14
        R.drawable.avatar_pirate_penguin,   // 15
        R.drawable.avatar_gamer_girl,       // 16
        R.drawable.avatar_super_bunny,      // 17
        R.drawable.avatar_drone_pilot,      // 18
        R.drawable.avatar_wise_owl,         // 19
        R.drawable.avatar_musician,         // 20
        R.drawable.avatar_princess_frog,    // 21
        R.drawable.avatar_code_wizard,      // 22
        R.drawable.avatar_space_alien       // 23
    )

    /** Аватарка по индексу (с защитой от выхода за границы — вернёт первую). */
    @DrawableRes
    fun resFor(index: Int): Int = all.getOrElse(index) { all.first() }

    val count: Int get() = all.size
}
