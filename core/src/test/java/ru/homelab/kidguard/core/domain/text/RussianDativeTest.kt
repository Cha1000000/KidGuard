package ru.homelab.kidguard.core.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Склонение имён экстренных контактов: «Мама» → «маме» для фразы «Позвонить маме».
 * Полноты правил русского языка не требуется — родителю показан предпросмотр, — но типовые
 * обращения и уже готовые формы обязаны отрабатывать без сюрпризов.
 */
class RussianDativeTest {

    @Test
    fun `обращения к родне склоняются и становятся строчными`() {
        assertEquals("маме", RussianDative.of("Мама"))
        assertEquals("папе", RussianDative.of("Папа"))
        assertEquals("бабушке", RussianDative.of("Бабушка"))
        assertEquals("дедушке", RussianDative.of("Дедушка"))
        assertEquals("тёте", RussianDative.of("Тётя"))
        assertEquals("дяде", RussianDative.of("Дядя"))
    }

    @Test
    fun `имя собственное склоняется, но регистр сохраняется`() {
        assertEquals("Оле", RussianDative.of("Оля"))
        assertEquals("Саше", RussianDative.of("Саша"))
        assertEquals("Андрею", RussianDative.of("Андрей"))
        assertEquals("Игорю", RussianDative.of("Игорь"))
        assertEquals("Ивану", RussianDative.of("Иван"))
    }

    @Test
    fun `окончание -ия даёт -ии, а не -ие`() {
        assertEquals("Марии", RussianDative.of("Мария"))
        assertEquals("Ксении", RussianDative.of("Ксения"))
    }

    @Test
    fun `составное обращение склоняется по словам`() {
        assertEquals("тёте Оле", RussianDative.of("Тётя Оля"))
        assertEquals("бабе Вале", RussianDative.of("Баба Валя"))
    }

    @Test
    fun `уже готовая форма дательного не портится`() {
        // Родитель увидел кривой предпросмотр и ввёл нужную форму сам — она должна пережить
        // повторное применение правил.
        assertEquals("маме", RussianDative.of("маме"))
        assertEquals("Оле", RussianDative.of("Оле"))
        assertEquals("Марии", RussianDative.of("Марии"))
    }

    @Test
    fun `склонение идемпотентно для типовых обращений`() {
        listOf("Мама", "Папа", "Оля", "Андрей", "Мария").forEach { name ->
            val once = RussianDative.of(name)
            assertEquals("повторное склонение «$name» изменило результат", once, RussianDative.of(once))
        }
    }

    @Test
    fun `пустое имя и пробелы не ломают склонение`() {
        assertEquals("", RussianDative.of(""))
        assertEquals("", RussianDative.of("   "))
        assertEquals("маме", RussianDative.of("  Мама  "))
        assertEquals("тёте Оле", RussianDative.of("Тётя   Оля"))
    }

    @Test
    fun `несклоняемое имя возвращается как есть`() {
        assertEquals("Отто", RussianDative.of("Отто"))
    }
}
