package ru.homelab.kidguard.core.domain.text

/**
 * Склонение имени контакта в дательный падеж: родитель пишет «Мама», ребёнок на ночном замке
 * видит «Позвонить маме».
 *
 * Правила русского языка на все случаи не натянуть — задача и не в этом. Родителю показывается
 * живой предпросмотр результата, и если склонение вышло кривым, он вводит имя сразу в нужной
 * форме. Поэтому здесь важнее **предсказуемость**, чем полнота: слово, которое под правила не
 * подходит, возвращается как есть, а уже готовая форма («маме») правилами не портится.
 */
object RussianDative {

    /**
     * Дательный падеж имени: «Мама» → «маме», «Тётя Оля» → «тёте Оле», «Дед» → «деду».
     * Каждое слово склоняется отдельно — это покрывает составные обращения.
     */
    fun of(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.split(WHITESPACE).joinToString(" ") { declineWord(it) }
    }

    private fun declineWord(word: String): String {
        if (word.isEmpty()) return word
        // Нерусские имена («Alina», «Max») склонять нечем: правила ниже приняли бы латинскую
        // согласную за русскую и выдали «Alinaу».
        if (word.none { it in CYRILLIC }) return word
        val lowered = if (word.lowercase() in COMMON_NOUNS) word.lowercase() else word
        val stem = lowered.dropLast(1)

        return when {
            // Мария → Марии, Ксения → Ксении (проверяем до общего «-я»).
            lowered.length > 2 && lowered.endsWith("ия", ignoreCase = true) -> "${lowered.dropLast(1)}и"
            // мама → маме, папа → папе, бабушка → бабушке, Саша → Саше.
            lowered.endsWith("а", ignoreCase = true) -> "${stem}е"
            // Оля → Оле, тётя → тёте, дядя → дяде.
            lowered.endsWith("я", ignoreCase = true) -> "${stem}е"
            // Андрей → Андрею, Сергей → Сергею.
            lowered.endsWith("й", ignoreCase = true) -> "${stem}ю"
            // Игорь → Игорю.
            lowered.endsWith("ь", ignoreCase = true) -> "${stem}ю"
            // Уже дательный падеж («маме», «Оле») или несклоняемое («Отто») — не трогаем:
            // иначе живой предпросмотр ломал бы то, что родитель ввёл правильно вручную.
            lowered.last().lowercaseChar() in UNCHANGED_ENDINGS -> lowered
            // Дед → деду, Иван → Ивану — на согласную.
            lowered.last().isLetter() -> "${lowered}у"
            else -> lowered
        }
    }

    private val WHITESPACE = Regex("\\s+")

    /** Диапазон кириллицы: слово без единой такой буквы правилам склонения не подчиняется. */
    private val CYRILLIC = ('а'..'я') + ('А'..'Я') + listOf('ё', 'Ё')

    /**
     * Обращения к родне: их родитель пишет с большой буквы («Мама»), а во фразе «Позвонить маме»
     * они должны быть строчными. Имена собственные регистр сохраняют — «Позвонить Оле».
     */
    private val COMMON_NOUNS = setOf(
        "мама", "мамочка", "мамуля", "папа", "папочка", "папуля", "батя",
        "бабушка", "баба", "дедушка", "деда", "дед",
        "тётя", "тетя", "дядя", "брат", "сестра", "няня", "родители"
    )

    /** Окончания, при которых слово уже не склоняется (в т.ч. готовая форма дательного). */
    private val UNCHANGED_ENDINGS = setOf('е', 'и', 'о', 'у', 'ы', 'э', 'ю')
}
