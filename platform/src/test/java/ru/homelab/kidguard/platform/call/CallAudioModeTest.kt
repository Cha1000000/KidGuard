package ru.homelab.kidguard.platform.call

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт признака звонка (план, этап 6, дефект D14): рингтон учитывается только когда
 * об этом попросили — полноэкранный замок перерыва должен уходить уже на входящем вызове.
 */
class CallAudioModeTest {

    @Test
    fun `ringing скрывает замок при includeRinging`() {
        assertTrue(isCallAudioMode(AudioManager.MODE_RINGTONE, includeRinging = true))
    }

    @Test
    fun `ringing не считается звонком без includeRinging`() {
        assertFalse(isCallAudioMode(AudioManager.MODE_RINGTONE, includeRinging = false))
    }

    @Test
    fun `разговор считается звонком независимо от includeRinging`() {
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_CALL, includeRinging = false))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_CALL, includeRinging = true))
    }

    @Test
    fun `интернет-вызов считается звонком`() {
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION, includeRinging = false))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION, includeRinging = true))
    }

    @Test
    fun `обычный режим не считается звонком`() {
        assertFalse(isCallAudioMode(AudioManager.MODE_NORMAL, includeRinging = false))
        assertFalse(isCallAudioMode(AudioManager.MODE_NORMAL, includeRinging = true))
    }

    @Test
    fun `новые и неизвестные аудио-режимы не считаются звонком`() {
        // Контракт перечисляет ровно RINGTONE/IN_CALL/IN_COMMUNICATION; всё остальное — не звонок,
        // иначе широкий предикат (например, `mode >= MODE_RINGTONE`) прятал бы замок без причины.
        assertFalse(isCallAudioMode(AudioManager.MODE_CALL_SCREENING, includeRinging = true))
        assertFalse(isCallAudioMode(AudioManager.MODE_CALL_REDIRECT, includeRinging = true))
        assertFalse(isCallAudioMode(99, includeRinging = true))
    }
}
