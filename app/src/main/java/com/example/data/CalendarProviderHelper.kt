package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val dateStr: String, // format "YYYY-MM-DD"
    val startTime: String, // format "HH:MM"
    val endTime: String, // format "HH:MM"
    val calendarDisplayName: String
)

data class GoogleCalendarAccount(
    val id: Long,
    val accountName: String,
    val displayName: String,
    val isPrimary: Boolean,
    val isWritable: Boolean = true
)

object CalendarProviderHelper {

    /**
     * Queries all Google Calendar accounts on the device (Calendar ACCESS_LEVEL >= 200 matches readable accounts)
     */
    fun getGoogleCalendarAccounts(context: Context): List<GoogleCalendarAccount> {
        if (!hasCalendarPermissions(context)) return emptyList()

        val accountsList = mutableListOf<GoogleCalendarAccount>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI

        try {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                val accountNameCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val displayNameCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountTypeCol = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val isPrimaryCol = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val accessLevelCol = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                while (it.moveToNext()) {
                    if (idCol == -1) continue
                    val id = it.getLong(idCol)
                    val accountName = if (accountNameCol != -1) (it.getString(accountNameCol) ?: "") else ""
                    val displayName = if (displayNameCol != -1) (it.getString(displayNameCol) ?: "") else ""
                    val accountType = if (accountTypeCol != -1) (it.getString(accountTypeCol) ?: "") else ""
                    val isPrimary = if (isPrimaryCol != -1) (it.getInt(isPrimaryCol) == 1) else false
                    val accessLevel = if (accessLevelCol != -1) it.getInt(accessLevelCol) else 700

                    // Include all readable/writable accounts (accessLevel >= 100 matches read-only/free-busy accounts, allowing Birthdays/Holidays)
                    val isWritable = accessLevel >= 500
                    if (accountType == "com.google" && accessLevel >= 100) {
                        accountsList.add(GoogleCalendarAccount(id, accountName, displayName, isPrimary, isWritable))
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return accountsList
    }

    /**
     * Checks if the calendar permissions are granted
     */
    fun hasCalendarPermissions(context: Context): Boolean {
        return try {
            val readPermission = android.Manifest.permission.READ_CALENDAR
            val writePermission = android.Manifest.permission.WRITE_CALENDAR
            val readGranted = context.checkSelfPermission(readPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val writeGranted = context.checkSelfPermission(writePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Retrieves the primary or default writeable calendar ID from the device.
     * Prioritizes writable Google Calendars and checks access levels.
     */
    fun getPrimaryCalendarId(context: Context): Long? {
        if (!hasCalendarPermissions(context)) return null

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI

        try {
            val contentResolver: ContentResolver = context.contentResolver
            val cursor: Cursor? = contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val idColumnIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
                val isPrimaryColumnIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
                val accountTypeColumnIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val accountNameColumnIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accessLevelColumnIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                var bestCalendarId: Long? = null
                var bestScore = -1

                while (it.moveToNext()) {
                    if (idColumnIndex == -1) continue
                    val id = it.getLong(idColumnIndex)
                    val isPrimary = if (isPrimaryColumnIndex != -1) it.getInt(isPrimaryColumnIndex) == 1 else false
                    val accountType = if (accountTypeColumnIndex != -1) (it.getString(accountTypeColumnIndex) ?: "") else ""
                    val accountName = if (accountNameColumnIndex != -1) (it.getString(accountNameColumnIndex) ?: "") else ""
                    val accessLevel = if (accessLevelColumnIndex != -1) it.getInt(accessLevelColumnIndex) else 700

                    // Skip read-only calendars (access levels like read or free/busy)
                    // CAL_ACCESS_CONTRIBUTOR is 500. Writable calendars must be >= 500.
                    if (accessLevel < 500) {
                        continue
                    }

                    // Rank options to prioritize accounts that sync with Google Calendar
                    var score = 0
                    if (accountType == "com.google") {
                        score += 10
                    }
                    if (isPrimary) {
                        score += 5
                    }
                    if (accountName.endsWith("@gmail.com")) {
                        score += 2
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCalendarId = id
                    }
                }
                return bestCalendarId
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Fetches all events from the selected calendar IDs (or all calendars if none specified) on the device.
     */
    fun fetchDeviceEvents(context: Context, selectedCalendarIds: Set<Long> = emptySet()): List<DeviceCalendarEvent> {
        if (!hasCalendarPermissions(context)) return emptyList()

        val eventsList = mutableListOf<DeviceCalendarEvent>()

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        val selection = if (selectedCalendarIds.isNotEmpty()) {
            val placeholders = selectedCalendarIds.joinToString(",") { "?" }
            "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        } else {
            null
        }
        val selectionArgs = if (selectedCalendarIds.isNotEmpty()) {
            selectedCalendarIds.map { it.toString() }.toTypedArray()
        } else {
            null
        }

        try {
            val nowMillis = System.currentTimeMillis()
            // Expand occurrences spanning from 5 years in the past to 5 years in the future to capture all recurring birthdays/holidays
            val fiveYearsMs = 5L * 365 * 24 * 60 * 60 * 1000
            val startMillis = nowMillis - fiveYearsMs
            val endMillis = nowMillis + fiveYearsMs

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val uri = builder.build()

            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndex(CalendarContract.Instances._ID)
                val titleCol = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val descCol = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val startCol = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endCol = it.getColumnIndex(CalendarContract.Instances.END)
                val allDayCol = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calDisplayNameCol = it.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                while (it.moveToNext()) {
                    if (idCol == -1 || startCol == -1) continue
                    val id = it.getLong(idCol)
                    val title = if (titleCol != -1) (it.getString(titleCol) ?: "Unnamed Event") else "Unnamed Event"
                    val description = if (descCol != -1) it.getString(descCol) else null
                    val dtStart = it.getLong(startCol)
                    val dtEnd = if (endCol != -1) it.getLong(endCol) else 0L
                    val allDay = if (allDayCol != -1) (it.getInt(allDayCol) == 1) else false
                    val calendarDisplayName = if (calDisplayNameCol != -1) (it.getString(calDisplayNameCol) ?: "System Calendar") else "System Calendar"

                    // Convert dtStart/dtEnd epoch millis to local times inside correct timezone (using UTC for all-day)
                    val startInstant = java.time.Instant.ofEpochMilli(dtStart)
                    val startLDT = if (allDay) {
                        LocalDateTime.ofInstant(startInstant, ZoneId.of("UTC"))
                    } else {
                        LocalDateTime.ofInstant(startInstant, ZoneId.systemDefault())
                    }

                    val endInstant = java.time.Instant.ofEpochMilli(if (dtEnd > 0) dtEnd else dtStart)
                    val endLDT = if (allDay) {
                        LocalDateTime.ofInstant(endInstant, ZoneId.of("UTC"))
                    } else {
                        LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault())
                    }

                    val dateStr = startLDT.toLocalDate().format(dateFormatter)
                    val startTimeStr = if (allDay) "00:00" else startLDT.toLocalTime().format(timeFormatter)
                    val endTimeStr = if (allDay) "23:59" else endLDT.toLocalTime().format(timeFormatter)

                    eventsList.add(
                        DeviceCalendarEvent(
                            id = id,
                            title = title,
                            description = description,
                            dateStr = dateStr,
                            startTime = startTimeStr,
                            endTime = endTimeStr,
                            calendarDisplayName = calendarDisplayName
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return eventsList
    }

    /**
     * Inserts an event into the primary calendar.
     */
    fun insertDeviceEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String?,
        dateStr: String, // "YYYY-MM-DD"
        startTime: String, // "HH:MM"
        endTime: String // "HH:MM"
    ): Long? {
        if (!hasCalendarPermissions(context)) return null

        try {
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

            val startLDT = LocalDateTime.parse("$dateStr $startTime", dateFormatter)
            val endLDT = LocalDateTime.parse("$dateStr $endTime", dateFormatter)

            val startMillis = startLDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endLDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            return uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Deletes an event by its device event ID.
     */
    fun deleteDeviceEvent(context: Context, eventId: Long): Boolean {
        if (!hasCalendarPermissions(context)) return false

        try {
            val uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            return rowsDeleted > 0
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Updates an event in the system calendar.
     */
    fun updateDeviceEvent(
        context: Context,
        eventId: Long,
        title: String,
        description: String?,
        dateStr: String,
        startTime: String,
        endTime: String
    ): Boolean {
        if (!hasCalendarPermissions(context)) return false

        try {
            val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

            val startLDT = LocalDateTime.parse("$dateStr $startTime", dateFormatter)
            val endLDT = LocalDateTime.parse("$dateStr $endTime", dateFormatter)

            val startMillis = startLDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endLDT.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, description)
            }

            val uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            return rowsUpdated > 0
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }
}
