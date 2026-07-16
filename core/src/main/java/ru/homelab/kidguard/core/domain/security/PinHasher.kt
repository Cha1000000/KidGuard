package ru.homelab.kidguard.core.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Хеширование родительского PIN (веха 6, шаг 6.1). Чистая функция без Android-зависимостей —
 * используется и в родительском приложении (задать PIN), и в детском (офлайн-проверка ввода).
 *
 * Сырой PIN никогда не хранится и не уходит на сервер — только соль и хеш (едут в policy-JSON,
 * как обычное скалярное поле).
 *
 * **Осознанное ограничение:** PIN — 4 цифры, то есть всего 10 000 комбинаций. Полной
 * крипто-стойкости тут не добиться в принципе (переборщик с доступом к hash+salt подберёт PIN
 * за разумное время даже с PBKDF2). Цель PBKDF2 с большим числом итераций — не сделать перебор
 * невозможным, а замедлить и затруднить ручной подбор подростком без специальных инструментов
 * (не защита от эксперта с оффлайн-доступом к базе, а защита от ребёнка, тыкающего цифры на
 * экране). Основная защита от онлайн-перебора — не крипто-стойкость хеша, а UI (задержки/блокировка
 * после нескольких неверных попыток) на шаге 6.2.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /** Случайная соль (16 байт), Base64 без переносов строк. */
    fun generateSalt(): String {
        val bytes = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Хеш PIN на заданной соли (Base64). Соль в кодировке [generateSalt]. */
    fun hash(pin: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        return Base64.getEncoder().withoutPadding().encodeToString(keyBytes)
    }

    /** Проверка введённого PIN против сохранённых соли и хеша. Сравнение constant-time. */
    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val actualHash = hash(pin, salt)
        return MessageDigest.isEqual(actualHash.toByteArray(), expectedHash.toByteArray())
    }
}
