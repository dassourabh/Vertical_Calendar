package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String?,
    val date: String, // format "YYYY-MM-DD"
    val startTime: String, // format "HH:MM"
    val endTime: String, // format "HH:MM"
    val colorHex: String, // visual color indicator
    val deviceEventId: Long? = null,
    val isCountdown: Boolean = false,
    val fromDate: String? = null
)
