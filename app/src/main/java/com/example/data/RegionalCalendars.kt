package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RegionalCalendars {

    val BENGALI_MONTHS = listOf(
        "Boishakh", "Joishtho", "Asharh", "Srabon", "Bhadra", 
        "Ashwin", "Kartik", "Agrahayan", "Poush", "Magh", 
        "Falgun", "Choitro"
    )

    fun getBengaliDate(
        date: LocalDate, 
        offsetDays: Int = 0, 
        customOverrides: Map<String, String> = emptyMap()
    ): String {
        val dateKey = date.toString()
        if (customOverrides.containsKey(dateKey) && !customOverrides[dateKey].isNullOrBlank()) {
            return customOverrides[dateKey]!!
        }

        val effectiveDate = if (offsetDays != 0) date.plusDays(offsetDays.toLong()) else date
        val year = effectiveDate.year
        val pohelaBoishakhThisYear = LocalDate.of(year, 4, 15) // West Bengal, India: Pohela Boishakh starts on April 15
        val startOfBengaliYear: LocalDate
        val bengaliYear: Int
        
        if (effectiveDate.isBefore(pohelaBoishakhThisYear)) {
            startOfBengaliYear = LocalDate.of(year - 1, 4, 15)
            bengaliYear = year - 594
        } else {
            startOfBengaliYear = pohelaBoishakhThisYear
            bengaliYear = year - 593
        }
        
        val daysBetween = ChronoUnit.DAYS.between(startOfBengaliYear, effectiveDate)
        var dayOfYear = daysBetween.toInt() + 1 // 1-indexed

        // West Bengal (Surya Siddhanta / Panjika) solar month lengths:
        // Boishakh: 31 days (Apr 15 - May 15)
        // Joishtho: 31 days (May 16 - Jun 15)
        // Asharh: 32 days (Jun 16 - Jul 17)
        // Srabon: 32 days (Jul 18 - Aug 18)
        // Bhadra: 31 days (Aug 19 - Sep 18)
        // Ashwin: 30 days (Sep 19 - Oct 18)
        // Kartik: 30 days (Oct 19 - Nov 17)
        // Agrahayan: 30 days (Nov 18 - Dec 17)
        // Poush: 30 days (Dec 18 - Jan 16)
        // Magh: 30 days (Jan 17 - Feb 15)
        // Falgun: 29 days (30 in leap year) (Feb 16 - Mar 16)
        // Choitro: 29 days (Mar 17 - Apr 14)
        val hasFeb29InBengaliYear = LocalDate.of(startOfBengaliYear.year + 1, 1, 1).isLeapYear
        val monthLengths = listOf(
            31, 31, 32, 32, 31, 30, 30, 30, 30, 30, 
            if (hasFeb29InBengaliYear) 30 else 29, 
            29
        )

        var currentMonthIdx = 0
        while (currentMonthIdx < 12 && dayOfYear > monthLengths[currentMonthIdx]) {
            dayOfYear -= monthLengths[currentMonthIdx]
            currentMonthIdx++
        }
        
        val finalMonthIdx = currentMonthIdx.coerceAtMost(11)
        val finalDay = dayOfYear.coerceAtLeast(1)
        
        return "$finalDay ${BENGALI_MONTHS[finalMonthIdx]}, $bengaliYear BS"
    }

    fun parseBengaliDateComponents(
        date: LocalDate,
        offsetDays: Int = 0,
        customOverrides: Map<String, String> = emptyMap()
    ): Triple<Int, String, Int> {
        val dateStr = getBengaliDate(date, offsetDays, customOverrides)
        try {
            // Expected format: "13 Bhadra, 1433 BS"
            val parts = dateStr.replace(",", "").split(" ")
            val day = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val month = parts.getOrNull(1) ?: "Boishakh"
            val year = parts.getOrNull(2)?.toIntOrNull() ?: (date.year - 593)
            return Triple(day, month, year)
        } catch (e: Exception) {
            return Triple(1, "Boishakh", date.year - 593)
        }
    }

    fun getTeluguDate(date: LocalDate): String {
        val epochDay = date.toEpochDay()
        val daysSinceRef = epochDay - 6.8576
        var lunarAge = daysSinceRef % 29.530588853
        if (lunarAge < 0) {
            lunarAge += 29.530588853
        }
        
        val tithiVal = ((lunarAge / 29.530588853) * 30.0).toInt() + 1
        val tithiNumber = tithiVal.coerceIn(1, 30)
        
        val paksha: String
        val tithiName: String
        val relativeTithi = if (tithiNumber <= 15) {
            paksha = "Shukla"
            tithiNumber
        } else {
            paksha = "Krishna"
            tithiNumber - 15
        }
        
        tithiName = when (relativeTithi) {
            1 -> "Pratipada"
            2 -> "Dwitiya"
            3 -> "Tritiya"
            4 -> "Chaturthi"
            5 -> "Panchami"
            6 -> "Shashti"
            7 -> "Saptami"
            8 -> "Ashtami"
            9 -> "Navami"
            10 -> "Dashami"
            11 -> "Ekadashi"
            12 -> "Dwadashi"
            13 -> "Trayodashi"
            14 -> "Chaturdashi"
            15 -> if (tithiNumber <= 15) "Pournami" else "Amavasya"
            else -> "Pratipada"
        }

        val precedingNewMoon = date.minusDays(lunarAge.toLong())
        val monthValue = precedingNewMoon.monthValue
        val dayValue = precedingNewMoon.dayOfMonth
        
        val monthName = when {
            (monthValue == 3 && dayValue >= 14) || (monthValue == 4 && dayValue <= 12) -> "Chaitra"
            (monthValue == 4 && dayValue >= 13) || (monthValue == 5 && dayValue <= 13) -> "Vaishakha"
            (monthValue == 5 && dayValue >= 14) || (monthValue == 6 && dayValue <= 13) -> "Jyeshtha"
            (monthValue == 6 && dayValue >= 14) || (monthValue == 7 && dayValue <= 15) -> "Ashadha"
            (monthValue == 7 && dayValue >= 16) || (monthValue == 8 && dayValue <= 15) -> "Shravana"
            (monthValue == 8 && dayValue >= 16) || (monthValue == 9 && dayValue <= 15) -> "Bhadrapada"
            (monthValue == 9 && dayValue >= 16) || (monthValue == 10 && dayValue <= 16) -> "Ashvina"
            (monthValue == 10 && dayValue >= 17) || (monthValue == 11 && dayValue <= 15) -> "Kartika"
            (monthValue == 11 && dayValue >= 16) || (monthValue == 12 && dayValue <= 15) -> "Margashira"
            (monthValue == 12 && dayValue >= 16) || (monthValue == 1 && dayValue <= 14) -> "Pushya"
            (monthValue == 1 && dayValue >= 15) || (monthValue == 2 && dayValue <= 12) -> "Magha"
            (monthValue == 2 && dayValue >= 13) || (monthValue == 3 && dayValue <= 13) -> "Phalguna"
            else -> "Chaitra"
        }

        val shakaYear = if (monthValue < 3 || (monthValue == 3 && dayValue < 15)) {
            date.year - 79
        } else {
            date.year - 78
        }

        return "$monthName $paksha $tithiName, $shakaYear Shaka"
    }
}
