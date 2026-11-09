package ru.homelab.kidguard.platform.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.homelab.kidguard.core.domain.model.ScheduleState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Приоритет причины мягкого блока и текст времени — чистые функции, без Android. */
class BlockDecisionReasonTest {

    private val allowed = setOf("com.launcher", "ru.homelab.kidguard")
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    @Test
    fun `запрет родителя бьёт расписание`() {
        assertEquals(
            BlockReason.BLOCKED_BY_PARENT,
            blockReasonFor("com.blocked", allowed, setOf("com.blocked"), studyTimeActive = true)
        )
    }

    @Test
    fun `alwaysAllowed не считается запрещённым`() {
        assertEquals(
            BlockReason.STUDY_TIME,
            blockReasonFor("com.launcher", allowed, setOf("com.launcher"), studyTimeActive = true)
        )
    }

    @Test
    fun `учёба бьёт обычный лимит`() {
        assertEquals(
            BlockReason.STUDY_TIME,
            blockReasonFor("com.app", allowed, emptySet(), studyTimeActive = true)
        )
    }

    @Test
    fun `без учёбы - обычный лимит`() {
        assertEquals(
            BlockReason.LIMIT_EXPIRED,
            blockReasonFor("com.app", allowed, emptySet(), studyTimeActive = false)
        )
    }

    @Test
    fun `время окончания показывается только для учёбы`() {
        val study = ScheduleState.Study(LocalTime.of(14, 0))
        assertEquals("14:00", studyUntilText(study, BlockReason.STUDY_TIME, formatter))
        assertNull(studyUntilText(study, BlockReason.LIMIT_EXPIRED, formatter))
        assertNull(studyUntilText(ScheduleState.Inactive, BlockReason.STUDY_TIME, formatter))
        assertNull(
            studyUntilText(
                ScheduleState.Sleep(LocalTime.of(7, 0)),
                BlockReason.STUDY_TIME,
                formatter
            )
        )
    }
}
