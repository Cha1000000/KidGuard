package ru.homelab.kidguard.core.domain.security

/** Исход проверки родительского PIN ([PinGuard]). */
sealed interface PinVerifyResult {

    /** PIN верный — пускаем. */
    object Success : PinVerifyResult

    /** PIN не задан родителем: защита не настроена — не мешаем (та же политика, что в 6.2). */
    object NoPinSet : PinVerifyResult

    /** PIN неверный, попытки в серии ещё остались. */
    data class Wrong(val attemptsLeft: Int) : PinVerifyResult

    /** Серия исчерпана — ввод заблокирован на [secondsLeft] секунд. */
    data class Blocked(val secondsLeft: Int) : PinVerifyResult
}
