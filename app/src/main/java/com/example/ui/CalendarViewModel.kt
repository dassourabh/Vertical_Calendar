package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Dynamic mapping of system calendar events to App's CalendarEvent model on-the-fly
fun DeviceCalendarEvent.toCalendarEvent(): CalendarEvent {
    // Generate a collision-safe, strictly negative Int ID using positive bits hash to keep it completely distinct from real SQLite positive Int IDs.
    val posHash = (this.id.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)
    val negativeId = -posHash
    return CalendarEvent(
        id = negativeId,
        title = this.title,
        description = this.description ?: "Imported from System Calendar",
        date = this.dateStr,
        startTime = this.startTime,
        endTime = this.endTime,
        colorHex = "#AB47BC", // Royal Purple accent tone representing imported system events
        deviceEventId = this.id
    )
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CalendarDatabase.getDatabase(application)
    private val repository = CalendarEventRepository(database.calendarEventDao(), database.orgHolidayDao())
    private val sharedPrefs = application.getSharedPreferences("calendar_sync_prefs", Context.MODE_PRIVATE)

    // Regional Calendars preferences (persisted across app relaunches, default to true)
    private val _showBengaliInCells = MutableStateFlow(sharedPrefs.getBoolean("show_bengali_in_cells", true))
    val showBengaliInCells: StateFlow<Boolean> = _showBengaliInCells.asStateFlow()

    private val _showTeluguInCells = MutableStateFlow(sharedPrefs.getBoolean("show_telugu_in_cells", true))
    val showTeluguInCells: StateFlow<Boolean> = _showTeluguInCells.asStateFlow()

    // Bengali Date Customization & Calibration (Offset and per-date overrides)
    private val _bengaliOffsetDays = MutableStateFlow(sharedPrefs.getInt("bengali_offset_days", 0))
    val bengaliOffsetDays: StateFlow<Int> = _bengaliOffsetDays.asStateFlow()

    private val _bengaliCustomOverrides = MutableStateFlow(loadBengaliOverrides())
    val bengaliCustomOverrides: StateFlow<Map<String, String>> = _bengaliCustomOverrides.asStateFlow()

    private fun loadBengaliOverrides(): Map<String, String> {
        val raw = sharedPrefs.getString("bengali_custom_overrides", null) ?: return emptyMap()
        return try {
            val json = org.json.JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = json.getString(k)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun persistBengaliOverrides(map: Map<String, String>) {
        val json = org.json.JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        sharedPrefs.edit().putString("bengali_custom_overrides", json.toString()).apply()
        _bengaliCustomOverrides.value = map
    }

    fun setBengaliOffsetDays(offset: Int) {
        sharedPrefs.edit().putInt("bengali_offset_days", offset).apply()
        _bengaliOffsetDays.value = offset
    }

    fun setBengaliCustomDate(date: LocalDate, customDateStr: String) {
        val current = _bengaliCustomOverrides.value.toMutableMap()
        current[date.toString()] = customDateStr
        persistBengaliOverrides(current)
    }

    fun clearBengaliCustomDate(date: LocalDate) {
        val current = _bengaliCustomOverrides.value.toMutableMap()
        current.remove(date.toString())
        persistBengaliOverrides(current)
    }

    fun resetAllBengaliModifications() {
        sharedPrefs.edit()
            .remove("bengali_offset_days")
            .remove("bengali_custom_overrides")
            .apply()
        _bengaliOffsetDays.value = 0
        _bengaliCustomOverrides.value = emptyMap()
    }

    fun setShowBengaliInCells(show: Boolean) {
        sharedPrefs.edit().putBoolean("show_bengali_in_cells", show).apply()
        _showBengaliInCells.value = show
    }

    fun setShowTeluguInCells(show: Boolean) {
        sharedPrefs.edit().putBoolean("show_telugu_in_cells", show).apply()
        _showTeluguInCells.value = show
    }

    // Organization Holidays toggle preference (persisted, default to true)
    private val _showOrgHolidays = MutableStateFlow(sharedPrefs.getBoolean("show_org_holidays", true))
    val showOrgHolidays: StateFlow<Boolean> = _showOrgHolidays.asStateFlow()

    fun setShowOrgHolidays(show: Boolean) {
        sharedPrefs.edit().putBoolean("show_org_holidays", show).apply()
        _showOrgHolidays.value = show
    }

    // PDF upload and extraction states
    private val _isPdfParsing = MutableStateFlow(false)
    val isPdfParsing: StateFlow<Boolean> = _isPdfParsing.asStateFlow()

    private val _pdfParsingError = MutableStateFlow<String?>(null)
    val pdfParsingError: StateFlow<String?> = _pdfParsingError.asStateFlow()

    fun uploadAndParseHolidayPdf(context: Context, pdfUri: android.net.Uri) {
        viewModelScope.launch {
            _isPdfParsing.value = true
            _pdfParsingError.value = null
            try {
                val base64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    } ?: throw IllegalArgumentException("Could not open file stream")
                }

                val parsed = GeminiPdfParser.parseHolidayPdf(base64)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.clearAllOrgHolidays()
                    val entities = parsed.map { OrgHoliday(name = it.name, date = it.date) }
                    repository.insertOrgHolidays(entities)
                }

                // Auto-enable organizational holidays display when a new list is uploaded
                setShowOrgHolidays(true)
                com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(context)
            } catch (e: Throwable) {
                e.printStackTrace()
                _pdfParsingError.value = e.message ?: "Failed parsing holiday PDF"
            } finally {
                _isPdfParsing.value = false
            }
        }
    }

    fun clearOrgHolidays(context: Context) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.clearAllOrgHolidays()
            }
            com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(context)
        }
    }

    val orgHolidays: StateFlow<List<OrgHoliday>> = repository.allOrgHolidays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Tracks selected target Google Calendar IDs for reading
    private val _selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedCalendarIds: StateFlow<Set<Long>> = _selectedCalendarIds.asStateFlow()

    // Contains all discovered Google Accounts on device
    private val _availableGoogleAccounts = MutableStateFlow<List<GoogleCalendarAccount>>(emptyList())
    val availableGoogleAccounts: StateFlow<List<GoogleCalendarAccount>> = _availableGoogleAccounts.asStateFlow()

    // Backward-compatible single ID representation for writing events (first writable among selected, or fallback)
    val selectedCalendarId: StateFlow<Long?> = combine(_selectedCalendarIds, _availableGoogleAccounts) { ids, accounts ->
        val writableActive = accounts.firstOrNull { it.id in ids && it.isWritable }
        writableActive?.id ?: accounts.firstOrNull { it.isWritable }?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // Tracks current calendar permissions granted state
    val isSystemCalendarPermissionGranted = MutableStateFlow(false)
    
    // Tracks the currently selected date. Defaults to today's date.
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Holds fetched events from the physical system calendar Provider
    private val _deviceEvents = MutableStateFlow<List<DeviceCalendarEvent>>(emptyList())
    val deviceEvents: StateFlow<List<DeviceCalendarEvent>> = _deviceEvents.asStateFlow()

    // Map of all merged events indexed by date string "YYYY-MM-DD"
    val eventsByDate: StateFlow<Map<String, List<CalendarEvent>>>
    
    init {
        // Combine Flow: Combines local Room DB events, dynamic loaded System/Device events, and custom organization holidays, filtering out duplicates
        val combinedEvents = combine(
            repository.allEvents,
            _deviceEvents,
            _showOrgHolidays,
            repository.allOrgHolidays
        ) { dbEvents, deviceEventsList, showHolidays, orgHolidaysList ->
            val localDeviceEventIds = dbEvents.mapNotNull { it.deviceEventId }.toSet()
            
            val virtualDeviceEvents = deviceEventsList
                .filter { it.id !in localDeviceEventIds }
                .map { it.toCalendarEvent() }

            val virtualOrgHolidays = if (showHolidays) {
                orgHolidaysList.map { holiday ->
                    CalendarEvent(
                        id = -1000000 - holiday.id, // collision-safe ID in negative space
                        title = "Holiday: ${holiday.name}",
                        description = "Organization Holiday",
                        date = holiday.date,
                        startTime = "00:00",
                        endTime = "23:59",
                        colorHex = "#FF5722", // Distinct orange color representing the organization holiday
                        deviceEventId = null
                    )
                }
            } else {
                emptyList()
            }
            
            val mergedList = dbEvents + virtualDeviceEvents + virtualOrgHolidays
            mergedList.distinctBy { it.id }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)

        eventsByDate = combinedEvents
            .map { list ->
                list.groupBy { it.date }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )
            
        // Trigger initial check for calendar permissions and loading on start
        checkAndLoadDeviceCalendar(application)
        try {
            com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(application)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    // Combined live list of events for the selected date
    val selectedDateEvents: StateFlow<List<CalendarEvent>> = combine(
        selectedDate,
        repository.allEvents,
        _deviceEvents,
        _showOrgHolidays,
        repository.allOrgHolidays
    ) { date, dbEvents, deviceEventsList, showHolidays, orgHolidaysList ->
        val dateStr = date.toString()
        val localDeviceEventIds = dbEvents.mapNotNull { it.deviceEventId }.toSet()
        
        val virtualDeviceEvents = deviceEventsList
            .filter { it.id !in localDeviceEventIds }
            .map { it.toCalendarEvent() }
            
        val virtualOrgHolidays = if (showHolidays) {
            orgHolidaysList.map { holiday ->
                CalendarEvent(
                    id = -1000000 - holiday.id,
                    title = "Holiday: ${holiday.name}",
                    description = "Organization Holiday",
                    date = holiday.date,
                    startTime = "00:00",
                    endTime = "23:59",
                    colorHex = "#FF5722",
                    deviceEventId = null
                )
            }
        } else {
            emptyList()
        }
            
        val allMerged = dbEvents + virtualDeviceEvents + virtualOrgHolidays
        allMerged.filter { event ->
            if (event.isCountdown) {
                try {
                    val targetDate = LocalDate.parse(event.date)
                    val fromDateVal = event.fromDate?.let { java.time.LocalDate.parse(it) }
                    if (fromDateVal != null) {
                        !date.isBefore(fromDateVal) && !date.isAfter(targetDate)
                    } else {
                        !date.isAfter(targetDate)
                    }
                } catch (e: Exception) {
                    false
                }
            } else {
                event.date == dateStr
            }
        }.distinctBy { it.id }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun getSavedCalendarIds(): Set<Long> {
        val savedStr = sharedPrefs.getString("selected_sync_calendar_ids", "") ?: ""
        if (savedStr.isEmpty()) return emptySet()
        return savedStr.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun saveCalendarIds(ids: Set<Long>) {
        sharedPrefs.edit().putString("selected_sync_calendar_ids", ids.joinToString(",")).apply()
        _selectedCalendarIds.value = ids
    }

    /**
     * Tries to check/load physical device calendar events if permission is given.
     */
    fun checkAndLoadDeviceCalendar(context: Context) {
        viewModelScope.launch {
            try {
                val hasPermission = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CalendarProviderHelper.hasCalendarPermissions(context)
                }
                isSystemCalendarPermissionGranted.value = hasPermission
                
                if (hasPermission) {
                    val googleAccounts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        CalendarProviderHelper.getGoogleCalendarAccounts(context)
                    }
                    _availableGoogleAccounts.value = googleAccounts

                    var selectedIds = getSavedCalendarIds()
                    if (selectedIds.isEmpty() && !sharedPrefs.contains("selected_sync_calendar_ids")) {
                        // Default to selecting all discovered Google calendars (including holidays/birthdays if they exist)
                        selectedIds = googleAccounts.map { it.id }.toSet()
                        // If no Google calendars exist at all, fall back to default primary calendar ID
                        if (selectedIds.isEmpty()) {
                            val primaryId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                CalendarProviderHelper.getPrimaryCalendarId(context)
                            }
                            if (primaryId != null) {
                                selectedIds = setOf(primaryId)
                            }
                        }
                        saveCalendarIds(selectedIds)
                    } else {
                        _selectedCalendarIds.value = selectedIds
                    }

                    val events = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        CalendarProviderHelper.fetchDeviceEvents(context, _selectedCalendarIds.value)
                    }
                    _deviceEvents.value = events
                } else {
                    _deviceEvents.value = emptyList()
                    _availableGoogleAccounts.value = emptyList()
                    _selectedCalendarIds.value = emptySet()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                isSystemCalendarPermissionGranted.value = false
                _deviceEvents.value = emptyList()
                _availableGoogleAccounts.value = emptyList()
                _selectedCalendarIds.value = emptySet()
            }
        }
    }

    /**
     * Toggles the sync status of a given Google calendar account
     */
    fun toggleGoogleAccount(context: Context, calendarId: Long) {
        viewModelScope.launch {
            val current = _selectedCalendarIds.value.toMutableSet()
            if (current.contains(calendarId)) {
                current.remove(calendarId)
            } else {
                current.add(calendarId)
            }
            saveCalendarIds(current)
            checkAndLoadDeviceCalendar(context)
        }
    }

    fun addEvent(
        context: Context,
        title: String,
        description: String?,
        dateStr: String,
        startTime: String,
        endTime: String,
        colorHex: String,
        syncToDevice: Boolean,
        isCountdown: Boolean = false,
        fromDate: String? = null
    ) {
        viewModelScope.launch {
            try {
                var deviceId: Long? = null
                
                if (syncToDevice) {
                    deviceId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (CalendarProviderHelper.hasCalendarPermissions(context)) {
                             val calendarId = selectedCalendarId.value ?: CalendarProviderHelper.getPrimaryCalendarId(context)
                             if (calendarId != null) {
                                 CalendarProviderHelper.insertDeviceEvent(
                                     context = context,
                                     calendarId = calendarId,
                                     title = title,
                                     description = description,
                                     dateStr = dateStr,
                                     startTime = startTime,
                                     endTime = endTime
                                 )
                             } else null
                        } else null
                    }
                }
                
                val event = CalendarEvent(
                    title = title,
                    description = description,
                    date = dateStr,
                    startTime = startTime,
                    endTime = endTime,
                    colorHex = colorHex,
                    deviceEventId = deviceId,
                    isCountdown = isCountdown,
                    fromDate = fromDate
                )
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    repository.insert(event)
                }
                
                // Update home screen widgets
                com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(context)
                
                // Force reload to update UI immediately
                checkAndLoadDeviceCalendar(context)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun updateEvent(context: Context, event: CalendarEvent, syncToDevice: Boolean) {
        viewModelScope.launch {
            try {
                var updatedDeviceEventId = event.deviceEventId
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (syncToDevice) {
                        if (CalendarProviderHelper.hasCalendarPermissions(context)) {
                            val calendarId = selectedCalendarId.value ?: CalendarProviderHelper.getPrimaryCalendarId(context)
                            if (calendarId != null) {
                                if (event.deviceEventId != null) {
                                    // Update existing system calendar event
                                    CalendarProviderHelper.updateDeviceEvent(
                                        context = context,
                                        eventId = event.deviceEventId,
                                        title = event.title,
                                        description = event.description,
                                        dateStr = event.date,
                                        startTime = event.startTime,
                                        endTime = event.endTime
                                    )
                                } else {
                                    // Insert new system calendar event
                                    updatedDeviceEventId = CalendarProviderHelper.insertDeviceEvent(
                                        context = context,
                                        calendarId = calendarId,
                                        title = event.title,
                                        description = event.description,
                                        dateStr = event.date,
                                        startTime = event.startTime,
                                        endTime = event.endTime
                                    )
                                }
                            }
                        }
                    } else if (event.deviceEventId != null) {
                        // Remove existing connection from physical calendar
                        if (CalendarProviderHelper.hasCalendarPermissions(context)) {
                            CalendarProviderHelper.deleteDeviceEvent(context, event.deviceEventId)
                        }
                        updatedDeviceEventId = null
                    }
                }
                
                if (event.id > 0) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.update(event.copy(deviceEventId = updatedDeviceEventId))
                    }
                }
                
                // Update home screen widgets
                com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(context)
                
                checkAndLoadDeviceCalendar(context)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun deleteEvent(context: Context, event: CalendarEvent) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Delete locally if stored in SQLite DB
                    if (event.id > 0) {
                        repository.delete(event)
                    }
                    
                    // Delete physically from device calendar Provider
                    if (event.deviceEventId != null && CalendarProviderHelper.hasCalendarPermissions(context)) {
                        CalendarProviderHelper.deleteDeviceEvent(context, event.deviceEventId)
                    }
                }
                
                // Update home screen widgets
                com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(context)
                
                checkAndLoadDeviceCalendar(context)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
