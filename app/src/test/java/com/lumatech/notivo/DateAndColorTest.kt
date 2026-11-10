package com.lumatech.notivo

import com.lumatech.notivo.domain.model.AppFontFamily
import com.lumatech.notivo.domain.model.AppPreferences
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.util.DateUtils
import com.lumatech.notivo.util.NoteColorPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateAndColorTest {

    @Test
    fun `TaskPriority maps integer levels safely`() {
        assertEquals(TaskPriority.LOW, TaskPriority.fromLevel(0))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromLevel(1))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromLevel(2))
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromLevel(99))
    }

    @Test
    fun `NoteColorPalette provides 12 unique light and dark variants`() {
        assertEquals(12, NoteColorPalette.options.size)
        val uniqueLight = NoteColorPalette.options.map { it.lightHex }.toSet()
        val uniqueDark = NoteColorPalette.options.map { it.darkHex }.toSet()
        assertEquals(12, uniqueLight.size)
        assertEquals(12, uniqueDark.size)
    }

    @Test
    fun `DateUtils detects overdue and today timestamps accurately`() {
        val startToday = DateUtils.startOfTodayMillis()
        val endToday = DateUtils.endOfTodayMillis()
        assertTrue(DateUtils.isDueTodayOrOverdue(startToday + 1_000L))
        assertTrue(DateUtils.isDueTodayOrOverdue(endToday))
        assertFalse(DateUtils.isOverdue(startToday + 1_000L))
        assertTrue(DateUtils.isOverdue(startToday - 1_000L))
    }

    @Test
    fun `formatEditorHeaderDate includes both date and modification time`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 14, 35, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = DateUtils.formatEditorHeaderDate(calendar.timeInMillis)
        assertTrue(formatted.contains(" · 14:35"))
        assertTrue(formatted.contains("2026"))
    }

    @Test
    fun `formatCardTimestamp formats today, yesterday, current year, and prior year`() {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)

        val today = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 15)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("09:15", DateUtils.formatCardTimestamp(today.timeInMillis))

        val yesterday = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 24)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("Wczoraj 22:24", DateUtils.formatCardTimestamp(yesterday.timeInMillis))

        val isMidOctober = now.get(Calendar.MONTH) == Calendar.OCTOBER &&
            now.get(Calendar.DAY_OF_MONTH) in 10..11
        val sameYearMonth = if (isMidOctober) Calendar.MARCH else Calendar.OCTOBER
        val expectedMonthWord = if (isMidOctober) "marca" else "października"
        val sameYearDate = Calendar.getInstance().apply {
            set(currentYear, sameYearMonth, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("10 $expectedMonthWord", DateUtils.formatCardTimestamp(sameYearDate.timeInMillis))

        val previousYear = currentYear - 1
        val prevYearDate = Calendar.getInstance().apply {
            set(previousYear, Calendar.JULY, 10, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals("10 lipca, $previousYear", DateUtils.formatCardTimestamp(prevYearDate.timeInMillis))
    }

    @Test
    fun `AppPreferences font scales and default font family are valid`() {
        val defaults = AppPreferences()
        assertEquals(AppFontFamily.OUTFIT, defaults.fontFamily)
        assertEquals(0.85f, FontSizeOption.SMALL.scaleFactor, 0.001f)
        assertEquals(1.0f, FontSizeOption.MEDIUM.scaleFactor, 0.001f)
        assertEquals(1.25f, FontSizeOption.LARGE.scaleFactor, 0.001f)
    }
}
