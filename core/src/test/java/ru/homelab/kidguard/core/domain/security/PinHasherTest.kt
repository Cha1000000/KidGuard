package ru.homelab.kidguard.core.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `один и тот же pin и соль дают стабильный хеш`() {
        val salt = PinHasher.generateSalt()
        val hash1 = PinHasher.hash("1234", salt)
        val hash2 = PinHasher.hash("1234", salt)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `verify верного pin - true`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("4321", salt)
        assertTrue(PinHasher.verify("4321", salt, hash))
    }

    @Test
    fun `verify неверного pin - false`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("4321", salt)
        assertFalse(PinHasher.verify("0000", salt, hash))
    }

    @Test
    fun `разные соли дают разные хеши для одного pin`() {
        val saltA = PinHasher.generateSalt()
        val saltB = PinHasher.generateSalt()
        assertNotEquals(saltA, saltB)
        assertNotEquals(PinHasher.hash("1111", saltA), PinHasher.hash("1111", saltB))
    }

    @Test
    fun `generateSalt возвращает разные значения при повторных вызовах`() {
        val salts = (1..10).map { PinHasher.generateSalt() }
        assertEquals(salts.size, salts.toSet().size)
    }
}
