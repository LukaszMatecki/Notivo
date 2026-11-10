package com.lumatech.notivo.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Date and time formatting helpers tailored for Polish locale and reference card styles.
 */
object DateUtils {
    private val polishLocale = Locale.forLanguageTag("pl-PL")

    fun formatCardTimestamp(timestampMillis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        return when {
            isSameDay(now, target) -> {
                SimpleDateFormat("HH:mm", polishLocale).format(Date(timestampMillis))
            }
            isYesterday(now, target) -> {
                val time = SimpleDateFormat("HH:mm", polishLocale).format(Date(timestampMillis))
                "Wczoraj $time"
            }
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> {
                SimpleDateFormat("d MMMM", polishLocale).format(Date(timestampMillis))
            }
            else -> {
                SimpleDateFormat("d MMMM, yyyy", polishLocale).format(Date(timestampMillis))
            }
        }
    }

    fun formatEditorHeaderDate(timestampMillis: Long): String {
        val formatted = SimpleDateFormat("EEEE, d MMMM yyyy · HH:mm", polishLocale)
            .format(Date(timestampMillis))
        return formatted.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(polishLocale) else ch.toString()
        }
    }

    fun formatTaskDueDate(timestampMillis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return when {
            isSameDay(now, target) -> "Dzisiaj"
            isTomorrow(now, target) -> "Jutro"
            isYesterday(now, target) -> "Wczoraj"
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> {
                SimpleDateFormat("d MMM", polishLocale).format(Date(timestampMillis))
            }
            else -> {
                SimpleDateFormat("d MMMM, yyyy", polishLocale).format(Date(timestampMillis))
            }
        }
    }

    fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun isDueTodayOrOverdue(dueDateMillis: Long): Boolean {
        return dueDateMillis <= endOfTodayMillis()
    }

    fun isOverdue(dueDateMillis: Long): Boolean {
        return dueDateMillis < startOfTodayMillis()
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, target)
    }

    private fun isTomorrow(now: Calendar, target: Calendar): Boolean {
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return isSameDay(tomorrow, target)
    }
}
