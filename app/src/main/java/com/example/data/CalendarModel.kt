package com.example.data

import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

object CalendarHelper {
    /**
     * Generates a flat list of exactly 42 CalendarDays (6 rows * 7 columns) for a given YearMonth.
     * Starts on Sunday by default.
     */
    fun getFullGridForMonth(yearMonth: YearMonth): List<CalendarDay> {
        val firstDayOfMonth = yearMonth.atDay(1)
        
        // Day of week of first day: 1 = Monday, ..., 7 = Sunday
        val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value
        
        // Sunday-start index: Sun = 0, Mon = 1, Tue = 2, Wed = 3, Thu = 4, Fri = 5, Sat = 6
        val firstDayIndex = if (dayOfWeekValue == 7) 0 else dayOfWeekValue
        
        val gridStartDate = firstDayOfMonth.minusDays(firstDayIndex.toLong())
        val today = LocalDate.now()
        
        val days = ArrayList<CalendarDay>(42)
        for (i in 0 until 42) {
            val date = gridStartDate.plusDays(i.toLong())
            days.add(
                CalendarDay(
                    date = date,
                    isCurrentMonth = YearMonth.from(date) == yearMonth,
                    isToday = date == today
                )
            )
        }
        return days
    }
}
