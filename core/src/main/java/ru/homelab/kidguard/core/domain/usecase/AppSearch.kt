package ru.homelab.kidguard.core.domain.usecase

/**
 * Подходит ли приложение под строку поиска в родительских списках — по названию ИЛИ по имени пакета.
 *
 * Раньше искали только по названию, и служебное приложение нельзя было найти по тому, как его знает
 * родитель из диагностики: движок мини-окон HiOS `com.transsion.thunderback` в списке называется
 * «Плавающие окна», и поиск «thunderback» не находил ничего (17.09.2026). Пустая строка подходит всем.
 */
fun matchesAppQuery(label: String, packageName: String, query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.isEmpty() ||
        label.contains(trimmed, ignoreCase = true) ||
        packageName.contains(trimmed, ignoreCase = true)
}
