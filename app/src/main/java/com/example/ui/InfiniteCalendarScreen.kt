package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Constants for the virtual infinite scrolling list
private const val START_INDEX = 5000
private const val TOTAL_ITEMS = 10000

// Rich Material 3 event category color palette
private val EVENT_COLORS = listOf(
    "#5C6BC0" to "Lavender Blue",
    "#EC407A" to "Rose Pink",
    "#26A69A" to "Emerald Green",
    "#FFA726" to "Amber Gold",
    "#26C6DA" to "Sky Cyan",
    "#AB47BC" to "Royal Purple"
)

/**
 * Safely parses color hex strings from local database or native calendar content provider,
 * falling back to a elegant Material gray if parsing fails.
 */
private fun safeParseColor(hexStr: String?, defaultColor: Color = Color.LightGray): Color {
    if (hexStr.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hexStr))
    } catch (_: Throwable) {
        defaultColor
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfiniteCalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialMonth = remember { YearMonth.now() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = START_INDEX)
    
    // Check permission state on first composition
    LaunchedEffect(Unit) {
        viewModel.checkAndLoadDeviceCalendar(context)
    }
    
    // Dynamically calculate the currently centered/visible month based on first visible item index
    val visibleMonth = remember(listState.firstVisibleItemIndex) {
        val offset = listState.firstVisibleItemIndex - START_INDEX
        initialMonth.plusMonths(offset.toLong())
    }
    
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val eventsByDate by viewModel.eventsByDate.collectAsStateWithLifecycle()
    val selectedDateEvents by viewModel.selectedDateEvents.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.isSystemCalendarPermissionGranted.collectAsStateWithLifecycle()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showGoToDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var eventToEdit by remember { mutableStateOf<CalendarEvent?>(null) }
    var dismissPermissionBanner by remember { mutableStateOf(false) }
    
    // States for regional calendars (Bengali & Telugu synced with ViewModel persistence)
    val showBengaliInCells by viewModel.showBengaliInCells.collectAsStateWithLifecycle()
    val showTeluguInCells by viewModel.showTeluguInCells.collectAsStateWithLifecycle()
    val bengaliOffsetDays by viewModel.bengaliOffsetDays.collectAsStateWithLifecycle()
    val bengaliCustomOverrides by viewModel.bengaliCustomOverrides.collectAsStateWithLifecycle()
    var showRegionalDialog by remember { mutableStateOf(false) }
    var showModifyBengaliDateDialog by remember { mutableStateOf(false) }
    var dateToModifyBengali by remember { mutableStateOf(LocalDate.now()) }
    
    // States for making the Event Viewer adjustable and toggleable
    var showEventsPanel by remember { mutableStateOf(false) }
    var panelHeightDp by remember { mutableStateOf(300.dp) }
    
    // Modern ActivityResult permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val readOk = perms[android.Manifest.permission.READ_CALENDAR] == true
        val writeOk = perms[android.Manifest.permission.WRITE_CALENDAR] == true
        if (readOk && writeOk) {
            viewModel.checkAndLoadDeviceCalendar(context)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAndParseHolidayPdf(context, uri)
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + visibleMonth.year,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Small compact unique button for Regional Calendars as requested, displaying dynamic look resembling Google Calendar-style with ॐ symbol
                    IconButton(
                        onClick = { showRegionalDialog = true },
                        modifier = Modifier.testTag("regional_calendars_button")
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Top colored header bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    // Centered Om Symbol
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ॐ",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Go to button to choose direct date (Icon only as requested)
                    IconButton(
                        onClick = { showGoToDialog = true },
                        modifier = Modifier.testTag("go_to_date_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Select a specific date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))

                    // Google Account and Sync central button
                    IconButton(
                        onClick = { showAccountDialog = true },
                        modifier = Modifier.testTag("select_sync_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google Accounts & Sync Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Jump to Today button (Icon only as requested, dynamically displaying today's date in a Google Calendar-style icon)
                    IconButton(
                        onClick = {
                            viewModel.selectDate(LocalDate.now())
                            coroutineScope.launch {
                                listState.animateScrollToItem(START_INDEX)
                            }
                        },
                        modifier = Modifier.testTag("today_button")
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Top colored header bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    // Centered dynamic day-of-month text
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = LocalDate.now().dayOfMonth.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                lineHeight = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEventsPanel = !showEventsPanel },
                containerColor = if (showEventsPanel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (showEventsPanel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 12.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = if (showEventsPanel) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (showEventsPanel) "Hide Event Viewer" else "Show Event Viewer"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .then(
                    if (showEventsPanel) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            showEventsPanel = false
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            // Permission request banner card if disabled and not manually dismissed
            if (!permissionGranted && !dismissPermissionBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync Info",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sync Device Calendars?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(
                                onClick = { dismissPermissionBanner = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Grant permission to fully load, display, and schedule events securely on your Android device's native calendars.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_CALENDAR,
                                        android.Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Enable integration", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Static, sticky Weekday labels bar
            WeekdayHeaderRow()
            
            // Layout is split: Calendar Infinite Scrolling Months in the upper part,
            // Selected Day's events in the lower part for exceptional ergonomic usage.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(TOTAL_ITEMS) { index ->
                        val offset = index - START_INDEX
                        val month = initialMonth.plusMonths(offset.toLong())
                        
                        MonthSection(
                            yearMonth = month,
                            selectedDate = selectedDate,
                            eventsByDate = eventsByDate,
                            onDayClick = { day ->
                                viewModel.selectDate(day.date)
                                if (showEventsPanel) {
                                    showEventsPanel = false
                                }
                            },
                            onDayDoubleClick = { day ->
                                viewModel.selectDate(day.date)
                                showEventsPanel = true
                            },
                            showBengaliInCells = showBengaliInCells,
                            showTeluguInCells = showTeluguInCells,
                            bengaliOffsetDays = bengaliOffsetDays,
                            bengaliCustomOverrides = bengaliCustomOverrides
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            
            // Events companion list panel
            AnimatedVisibility(
                visible = showEventsPanel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                EventsBottomPanel(
                    selectedDate = selectedDate,
                    events = selectedDateEvents,
                    onEditEvent = { event -> eventToEdit = event },
                    onDeleteEvent = { event -> viewModel.deleteEvent(context, event) },
                    onAddEvent = { showAddDialog = true },
                    currentHeight = panelHeightDp,
                    onHeightChange = { newHeight -> panelHeightDp = newHeight },
                    bengaliOffsetDays = bengaliOffsetDays,
                    bengaliCustomOverrides = bengaliCustomOverrides,
                    onModifyBengaliDate = { date ->
                        dateToModifyBengali = date
                        showModifyBengaliDateDialog = true
                    },
                    modifier = Modifier
                        .height(panelHeightDp)
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            // Eat clicks inside the panel so they don't propagate to the outer Column click listener
                        }
                )
            }
        }
    }
    
    // Dialog for inserting new events
    if (showAddDialog) {
        AddEditEventDialog(
            viewModel = viewModel,
            selectedDate = selectedDate,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, desc, dateStr, startTime, endTime, colorHex, syncToDevice, isCountdown, fromDate ->
                viewModel.addEvent(
                    context = context,
                    title = title,
                    description = desc,
                    dateStr = dateStr,
                    startTime = startTime,
                    endTime = endTime,
                    colorHex = colorHex,
                    syncToDevice = syncToDevice,
                    isCountdown = isCountdown,
                    fromDate = fromDate
                )
                showAddDialog = false
            }
        )
    }
    
    // Dialog for editing existing events
    eventToEdit?.let { event ->
        AddEditEventDialog(
            viewModel = viewModel,
            event = event,
            selectedDate = try { LocalDate.parse(event.date) } catch (e: Exception) { LocalDate.now() },
            onDismiss = { eventToEdit = null },
            onConfirm = { title, desc, dateStr, startTime, endTime, colorHex, syncToDevice, isCountdown, fromDate ->
                viewModel.updateEvent(
                    context = context,
                    event = event.copy(
                        title = title,
                        description = desc,
                        date = dateStr,
                        startTime = startTime,
                        endTime = endTime,
                        colorHex = colorHex,
                        isCountdown = isCountdown,
                        fromDate = fromDate
                    ),
                    syncToDevice = syncToDevice
                )
                eventToEdit = null
            }
        )
    }

    if (showGoToDialog) {
        val today = remember { LocalDate.now() }
        val initialSelectedMillis = remember(selectedDate) {
            selectedDate.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedMillis
        )
        val selectedMillis = datePickerState.selectedDateMillis
        val targetDate = remember(selectedMillis) {
            if (selectedMillis != null) {
                java.time.Instant.ofEpochMilli(selectedMillis)
                    .atZone(java.time.ZoneId.of("UTC"))
                    .toLocalDate()
            } else {
                selectedDate
            }
        }
        val daysDiff = remember(targetDate, today) {
            java.time.temporal.ChronoUnit.DAYS.between(today, targetDate)
        }
        val daysText = when {
            daysDiff == 0L -> "Today"
            daysDiff == 1L -> "Tomorrow (+1d)"
            daysDiff == -1L -> "Yesterday (-1d)"
            daysDiff > 0L -> "+$daysDiff days from today"
            else -> "${daysDiff} days ago"
        }

        DatePickerDialog(
            onDismissRequest = { showGoToDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.selectDate(targetDate)
                        val targetYearMonth = YearMonth.from(targetDate)
                        val monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(initialMonth, targetYearMonth).toInt()
                        val targetIndex = (START_INDEX + monthsBetween).coerceIn(0, TOTAL_ITEMS - 1)
                        coroutineScope.launch {
                            listState.animateScrollToItem(targetIndex)
                        }
                        showGoToDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Go to Date",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = daysText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                },
                headline = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showAccountDialog) {
        val availableAccounts by viewModel.availableGoogleAccounts.collectAsStateWithLifecycle()
        val selectedCalendarIds by viewModel.selectedCalendarIds.collectAsStateWithLifecycle()

        Dialog(onDismissRequest = { showAccountDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header Row with Title and Refresh Sync Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Calendar Sync Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Sync button is now housed elegantly within this dialog
                        IconButton(
                            onClick = {
                                if (permissionGranted) {
                                    viewModel.checkAndLoadDeviceCalendar(context)
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_CALENDAR,
                                            android.Manifest.permission.WRITE_CALENDAR
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (permissionGranted) Icons.Default.Refresh else Icons.Default.Settings,
                                contentDescription = if (permissionGranted) "Sync Now" else "Enable Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (availableAccounts.isNotEmpty()) {
                        Text(
                            text = "Select one or more Google accounts to synchronize. Birthdays, holidays, and active calendar events from all checked accounts will be synced automatically:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            items(availableAccounts) { account ->
                                val isSelected = selectedCalendarIds.contains(account.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.toggleGoogleAccount(context, account.id)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.accountName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (account.displayName.isNotEmpty()) {
                                            Text(
                                                text = account.displayName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (account.isPrimary) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = "PRIMARY",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (!permissionGranted) "Permission Required" else "No Sync Accounts Found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!permissionGranted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (!permissionGranted) {
                                        "To sync with Google or local device calendars, please grant access permissions."
                                    } else {
                                        "Using fallback writable local system calendar. To choose specific accounts, verify they are active under system settings."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                if (!permissionGranted) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.READ_CALENDAR,
                                                    android.Manifest.permission.WRITE_CALENDAR
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("dialog_grant_permission_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Grant Calendar Access")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Organization Holidays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val showOrgHolidays by viewModel.showOrgHolidays.collectAsStateWithLifecycle()
                    val isPdfParsing by viewModel.isPdfParsing.collectAsStateWithLifecycle()
                    val pdfParsingError by viewModel.pdfParsingError.collectAsStateWithLifecycle()
                    val orgHolidaysList by viewModel.orgHolidays.collectAsStateWithLifecycle()
                    val savedApiKey by viewModel.userGeminiApiKey.collectAsStateWithLifecycle()
                    var apiKeyDraft by remember(savedApiKey) { mutableStateOf(savedApiKey) }
                    var isEditingApiKey by remember { mutableStateOf(savedApiKey.isEmpty()) }
                    var showRawApiKey by remember { mutableStateOf(false) }

                    Text(
                        text = "Upload a PDF with your organization's list of holidays. Enter your personal Gemini API key below (saved privately on your device) to parse and map holidays onto your calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // In-App User Gemini API Key Configuration
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gemini API Key",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (savedApiKey.isNotEmpty()) "Saved on device" else "Not configured",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (savedApiKey.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (savedApiKey.isNotEmpty() && !isEditingApiKey) {
                                val maskedTail = if (savedApiKey.length > 4) savedApiKey.takeLast(4) else "••••"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "••••••••••••$maskedTail",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                apiKeyDraft = savedApiKey
                                                isEditingApiKey = true
                                            },
                                            modifier = Modifier.testTag("edit_gemini_api_key_button"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit API Key",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Change", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = {
                                                viewModel.clearUserGeminiApiKey()
                                                apiKeyDraft = ""
                                                isEditingApiKey = true
                                            },
                                            modifier = Modifier.testTag("clear_gemini_api_key_button"),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove API Key",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = apiKeyDraft,
                                    onValueChange = { apiKeyDraft = it },
                                    placeholder = { Text("Paste Gemini API key (AIza...)", fontSize = 12.sp) },
                                    singleLine = true,
                                    visualTransformation = if (showRawApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        TextButton(
                                            onClick = { showRawApiKey = !showRawApiKey },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                text = if (showRawApiKey) "Hide" else "Show",
                                                fontSize = 11.sp
                                            )
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("gemini_api_key_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (savedApiKey.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                apiKeyDraft = savedApiKey
                                                isEditingApiKey = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Cancel", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.setUserGeminiApiKey(apiKeyDraft)
                                            isEditingApiKey = false
                                        },
                                        enabled = apiKeyDraft.trim().isNotEmpty(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("save_gemini_api_key_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Save API Key",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Key", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (showOrgHolidays) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable {
                                viewModel.setShowOrgHolidays(!showOrgHolidays)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = showOrgHolidays,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Organization Holidays",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (orgHolidaysList.isNotEmpty()) {
                                    "${orgHolidaysList.size} holidays loaded"
                                } else {
                                    "No organization holidays uploaded yet"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isPdfParsing) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gemini is parsing holiday PDF...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val candidateKey = if (isEditingApiKey && apiKeyDraft.trim().isNotEmpty()) {
                                        viewModel.setUserGeminiApiKey(apiKeyDraft.trim())
                                        isEditingApiKey = false
                                        apiKeyDraft.trim()
                                    } else {
                                        savedApiKey.trim()
                                    }
                                    if (candidateKey.isEmpty()) {
                                        isEditingApiKey = true
                                        viewModel.setPdfParsingError("Please enter your Gemini API Key above before uploading a PDF.")
                                    } else {
                                        pdfPickerLauncher.launch("application/pdf")
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("upload_pdf_holidays_button"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Upload PDF",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload PDF", fontSize = 13.sp)
                            }

                            if (orgHolidaysList.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearOrgHolidays(context)
                                    },
                                    modifier = Modifier.weight(1f).testTag("clear_pdf_holidays_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear All", fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    pdfParsingError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAccountDialog = false }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (showRegionalDialog) {
        Dialog(onDismissRequest = { showRegionalDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Regional Calendars Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Enable or disable overlays for Bengali and Telugu (luni-solar) calendars directly on the main date cells. Additionally, view the full local dates for any selected day inside your events panel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Bengali switch row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Bengali Calendar (B)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "West Bengal (Surya Siddhanta) Bangabda calculation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = showBengaliInCells,
                            onCheckedChange = { viewModel.setShowBengaliInCells(it) },
                            modifier = Modifier.testTag("bengali_calendar_switch")
                        )
                    }

                    // Bengali Calibration and Modify section
                    if (showBengaliInCells) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Bengali Date Calibration",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = when {
                                                bengaliCustomOverrides.isNotEmpty() -> "${bengaliCustomOverrides.size} custom override(s) active"
                                                bengaliOffsetDays != 0 -> "Shift: ${if (bengaliOffsetDays > 0) "+$bengaliOffsetDays" else "$bengaliOffsetDays"} day(s)"
                                                else -> "Default astronomical alignment"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    OutlinedButton(
                                        onClick = {
                                            dateToModifyBengali = selectedDate
                                            showModifyBengaliDateDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp).testTag("modify_bengali_date_settings_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Modify", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Telugu switch row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.secondary, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Telugu Calendar (T)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Shaka Amanta lunar tithi cycle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = showTeluguInCells,
                            onCheckedChange = { viewModel.setShowTeluguInCells(it) },
                            modifier = Modifier.testTag("telugu_calendar_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRegionalDialog = false }) {
                            Text("Save & Close")
                        }
                    }
                }
            }
        }
    }

    if (showModifyBengaliDateDialog) {
        ModifyBengaliDateDialog(
            targetDate = dateToModifyBengali,
            bengaliOffsetDays = bengaliOffsetDays,
            bengaliCustomOverrides = bengaliCustomOverrides,
            onSetCustomDate = { date, customStr -> viewModel.setBengaliCustomDate(date, customStr) },
            onClearCustomDate = { date -> viewModel.clearBengaliCustomDate(date) },
            onSetOffsetDays = { offset -> viewModel.setBengaliOffsetDays(offset) },
            onResetAll = { viewModel.resetAllBengaliModifications() },
            onDismiss = { showModifyBengaliDateDialog = false }
        )
    }
}

@Composable
fun WeekdayHeaderRow() {
    val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Week number header label on the left side
        Text(
            text = "WK",
            modifier = Modifier.weight(0.7f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        
        weekdays.forEach { dayName ->
            Text(
                text = dayName.uppercase(),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MonthSection(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    eventsByDate: Map<String, List<CalendarEvent>>,
    onDayClick: (CalendarDay) -> Unit,
    onDayDoubleClick: (CalendarDay) -> Unit,
    showBengaliInCells: Boolean,
    showTeluguInCells: Boolean,
    bengaliOffsetDays: Int = 0,
    bengaliCustomOverrides: Map<String, String> = emptyMap()
) {
    val days = remember(yearMonth) { CalendarHelper.getFullGridForMonth(yearMonth) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Subtle internal month label to clarify boundaries when scrolling fast
        Text(
            text = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + yearMonth.year,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp, top = 4.dp)
        )
        
        // 6 complete rows (weeks) of the month, optimized to exclude completely empty rows
        val weeks = days.chunked(7).filter { week -> week.any { it.isCurrentMonth } }
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Determine the week number of the year
                val representativeDate = week.firstOrNull { it.isCurrentMonth }?.date ?: week.first().date
                val weekNumber = try {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    representativeDate.get(weekFields.weekOfYear())
                } catch (e: Exception) {
                    try {
                        representativeDate.get(java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR)
                    } catch (ex: Exception) {
                        1
                    }
                }
                val isRowEmpty = week.all { !it.isCurrentMonth }
                
                Box(
                    modifier = Modifier.weight(0.7f),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isRowEmpty) {
                        Text(
                            text = weekNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f)) {
                        val dateStr = day.date.toString()
                        val dayEvents = eventsByDate[dateStr] ?: emptyList()
                        
                        DayCell(
                            day = day,
                            isSelected = day.date == selectedDate,
                            events = dayEvents,
                            onClick = { onDayClick(day) },
                            onDoubleClick = { onDayDoubleClick(day) },
                            showBengaliInCells = showBengaliInCells,
                            showTeluguInCells = showTeluguInCells,
                            bengaliOffsetDays = bengaliOffsetDays,
                            bengaliCustomOverrides = bengaliCustomOverrides
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    showBengaliInCells: Boolean,
    showTeluguInCells: Boolean,
    bengaliOffsetDays: Int = 0,
    bengaliCustomOverrides: Map<String, String> = emptyMap()
) {
    if (!day.isCurrentMonth) {
        // Return blank space to keep layout aligned but with no dates shown for previous/next month
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp)
        )
        return
    }

    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Circle selection frame / badge (26dp for perfect fit and space management)
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    day.isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        // Display secondary region initials if turned on
        val subTextParts = remember(day.date, showBengaliInCells, showTeluguInCells, bengaliOffsetDays, bengaliCustomOverrides) {
            val parts = mutableListOf<String>()
            if (showBengaliInCells) {
                val bDate = RegionalCalendars.getBengaliDate(day.date, bengaliOffsetDays, bengaliCustomOverrides)
                val bDay = bDate.split(" ").firstOrNull() ?: ""
                parts.add("B$bDay")
            }
            if (showTeluguInCells) {
                val epochDay = day.date.toEpochDay()
                val daysSinceRef = epochDay - 6.8576
                var lunarAge = daysSinceRef % 29.530588853
                if (lunarAge < 0) lunarAge += 29.530588853
                val tithiVal = ((lunarAge / 29.530588853) * 30.0).toInt() + 1
                val tithiNum = if (tithiVal <= 15) tithiVal else tithiVal - 15
                parts.add("T$tithiNum")
            }
            parts.joinToString("•")
        }
        
        if (subTextParts.isNotEmpty()) {
            Text(
                text = subTextParts,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 7.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        // Event indicator dots (super clear, 5dp size so they are explicitly visible, filtering out countdown events)
        Row(
            modifier = Modifier.height(6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            events.filter { !it.isCountdown }.take(4).forEach { event ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 0.75.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(safeParseColor(event.colorHex))
                )
            }
        }
    }
}

@Composable
fun EventsBottomPanel(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    onAddEvent: () -> Unit,
    currentHeight: androidx.compose.ui.unit.Dp,
    onHeightChange: (androidx.compose.ui.unit.Dp) -> Unit,
    bengaliOffsetDays: Int = 0,
    bengaliCustomOverrides: Map<String, String> = emptyMap(),
    onModifyBengaliDate: (LocalDate) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateDisplay = remember(selectedDate) {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
        selectedDate.format(formatter)
    }
    
    val heightState = rememberUpdatedState(currentHeight)
    val onHeightChangeState = rememberUpdatedState(onHeightChange)

    Card(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag-handle bar at top: Tap to cycle height-states, Double-tap to reset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val nextHeight = when (heightState.value) {
                                    180.dp -> 300.dp
                                    300.dp -> 520.dp
                                    else -> 180.dp
                                }
                                onHeightChangeState.value(nextHeight)
                            },
                            onDoubleTap = {
                                onHeightChangeState.value(300.dp) // Reset to standard Medium height
                            }
                        )
                    }
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap to cycle height • Double-tap to reset",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 10.dp)
            ) {
                // Header for events view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Events",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Small/Medium/Large Segmented size selection chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                                .padding(2.dp)
                        ) {
                            listOf(180.dp to "S", 300.dp to "M", 520.dp to "L").forEach { (heightVal, label) ->
                                val isSelected = currentHeight == heightVal
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { onHeightChange(heightVal) }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        FilledTonalButton(
                            onClick = onAddEvent,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Calendar Event",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = dateDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                
                // Regional calendars elegant display sub-header card
                val bEngaliDateStr = remember(selectedDate, bengaliOffsetDays, bengaliCustomOverrides) { 
                    RegionalCalendars.getBengaliDate(selectedDate, bengaliOffsetDays, bengaliCustomOverrides) 
                }
                val tEluguDateStr = remember(selectedDate) { RegionalCalendars.getTeluguDate(selectedDate) }
                val isCustomDate = bengaliCustomOverrides.containsKey(selectedDate.toString())
                
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onModifyBengaliDate(selectedDate) }
                                .padding(vertical = 1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bengali: $bEngaliDateStr" + if (isCustomDate) " (Custom)" else if (bengaliOffsetDays != 0) " (${if (bengaliOffsetDays > 0) "+$bengaliOffsetDays" else "$bengaliOffsetDays"}d)" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Modify Bengali Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.secondary, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Telugu: $tEluguDateStr",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ॐ",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (events.isEmpty()) {
                    // Beautiful empty state as specified in Design Guidelines
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No events",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No events scheduled for this day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "Tap the + button to schedule something new.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 60.dp) // Leave safety padding for FAB
                    ) {
                        items(events, key = { it.id }) { event ->
                            val itemColor = safeParseColor(event.colorHex)
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Color accent strip
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(itemColor)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(12.dp))
                                    
                                    // Event information
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = event.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (event.isCountdown) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "COUNTDOWN",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontSize = 8.sp,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                            if (event.deviceEventId != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "SYSTEM",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontSize = 8.sp,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        if (event.isCountdown) {
                                            val target = try { LocalDate.parse(event.date) } catch (e: Exception) { selectedDate }
                                            val start = try { 
                                                event.fromDate?.let { LocalDate.parse(it) } ?: target 
                                            } catch (e: Exception) { 
                                                target 
                                            }
                                            
                                            val progressDays = maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(start, selectedDate))
                                            val remainingDays = maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(selectedDate, target))
                                            
                                            val progressWeeks = progressDays / 7
                                            val progressRemainingDays = progressDays % 7
                                            
                                            val remainingWeeks = remainingDays / 7
                                            val remainingRemainingDays = remainingDays % 7
                                            
                                            Column {
                                                Text(
                                                    text = "Progress: $progressWeeks weeks, $progressRemainingDays days ($progressDays days)",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Remaining: $remainingWeeks weeks, $remainingRemainingDays days ($remainingDays days)",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "${event.startTime} - ${event.endTime}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        if (!event.description.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = event.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    
                                    // Event operations buttons
                                    Row {
                                        IconButton(
                                            onClick = { onEditEvent(event) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit event",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteEvent(event) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete event",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Dialog for adding/editing calendar events with System Calendar synchronization capabilities.
 */
@Composable
fun AddEditEventDialog(
    viewModel: CalendarViewModel,
    event: CalendarEvent? = null,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (title: String, desc: String, dateStr: String, startTime: String, endTime: String, colorHex: String, syncToDevice: Boolean, isCountdown: Boolean, fromDate: String?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var colorHex by remember { mutableStateOf(event?.colorHex ?: EVENT_COLORS[0].first) }
    var selectedEventDate by remember { mutableStateOf(selectedDate) }
    
    var isCountdownSelected by remember { mutableStateOf(event?.isCountdown ?: false) }
    var selectedFromDate by remember {
        mutableStateOf(
            if (event?.fromDate != null) {
                try { LocalDate.parse(event.fromDate) } catch (e: Exception) { selectedDate }
            } else {
                selectedDate
            }
        )
    }
    
    // Time control parameters (separated into discrete custom integers for robust adjustment)
    val startParts = remember(event) { event?.startTime?.split(":") ?: emptyList() }
    val endParts = remember(event) { event?.endTime?.split(":") ?: emptyList() }
    
    var startHour by remember { mutableStateOf(if (startParts.size >= 2) startParts[0].toIntOrNull() ?: 9 else 9) }
    var startMin by remember { mutableStateOf(if (startParts.size >= 2) startParts[1].toIntOrNull() ?: 0 else 0) }
    var endHour by remember { mutableStateOf(if (endParts.size >= 2) endParts[0].toIntOrNull() ?: 10 else 10) }
    var endMin by remember { mutableStateOf(if (endParts.size >= 2) endParts[1].toIntOrNull() ?: 0 else 0) }
    
    var syncToDevice by remember { mutableStateOf(event?.deviceEventId != null) }
    val permissionGranted by viewModel.isSystemCalendarPermissionGranted.collectAsStateWithLifecycle()
    
    var titleError by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (event == null) "Schedule Event" else "Edit Event",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close dialog",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Countdown/Schedule selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isCountdownSelected = true },
                        modifier = Modifier.weight(1f).testTag("countdown_selector_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCountdownSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isCountdownSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Countdown")
                    }
                    Button(
                        onClick = { isCountdownSelected = false },
                        modifier = Modifier.weight(1f).testTag("schedule_selector_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCountdownSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isCountdownSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Schedule")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Event Title input
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Event Name") },
                    placeholder = { Text("e.g. Marketing Sync") },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (titleError) {
                    Text(
                        text = "Title cannot be empty!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Event Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Detail (Optional)") },
                    placeholder = { Text("Add call link, room number, or notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Interactive Date Picker Trigger
                Text(
                    text = "Event Date",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable {
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    selectedEventDate = LocalDate.of(year, month + 1, dayOfMonth)
                                },
                                selectedEventDate.year,
                                selectedEventDate.monthValue - 1,
                                selectedEventDate.dayOfMonth
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Event Date Selector",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedEventDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap to choose event date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dropdown Indicator",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                if (isCountdownSelected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "From Date",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        selectedFromDate = LocalDate.of(year, month + 1, dayOfMonth)
                                    },
                                    selectedFromDate.year,
                                    selectedFromDate.monthValue - 1,
                                    selectedFromDate.dayOfMonth
                                ).show()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "From Date Selector",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFromDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to choose starting date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown Indicator",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Interactive tactile time builders
                    Text(
                        text = "Time Period",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Row for Start Time increment/decrement controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Starts", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { startHour = if (startHour == 0) 23 else startHour - 1 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", startHour),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { startHour = if (startHour == 23) 0 else startHour + 1 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            
                            Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 2.dp))
                            
                            IconButton(
                                onClick = { startMin = if (startMin == 0) 45 else startMin - 15 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", startMin),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { startMin = if (startMin == 45) 0 else startMin + 15 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Row for End Time increment/decrement controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ends", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { endHour = if (endHour == 0) 23 else endHour - 1 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", endHour),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    endHour = if (endHour == 23) 0 else endHour + 1
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            
                            Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 2.dp))
                            
                            IconButton(
                                onClick = { endMin = if (endMin == 0) 45 else endMin - 15 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", endMin),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { endMin = if (endMin == 45) 0 else endMin + 15 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                }
                
                // Color Tag selector
                Text(
                    text = "Category Tone",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    EVENT_COLORS.forEach { (colorString, details) ->
                        val itemColor = safeParseColor(colorString)
                        val isColorSelected = colorHex == colorString
                        
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(itemColor)
                                .clickable { colorHex = colorString }
                                .padding(2.dp)
                        ) {
                            if (isColorSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(
                                            BorderStroke(2.5.dp, Color.White),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
                
                // Device Calendar Sync Checkbox Integration
                if (permissionGranted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { syncToDevice = !syncToDevice }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Sync with System Calendar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Write directly to default on-device account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Checkbox(
                            checked = syncToDevice,
                            onCheckedChange = { syncToDevice = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Enable Calendar permission in app settings to sync events to your system calendar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        lineHeight = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                            } else {
                                // Auto-re-arrange start/end time if end time is mathematically earlier
                                var verifiedEndHour = endHour
                                var verifiedEndMin = endMin
                                if (endHour < startHour || (endHour == startHour && endMin <= startMin)) {
                                    verifiedEndHour = if (startHour == 23) 23 else startHour + 1
                                    verifiedEndMin = startMin
                                }
                                
                                val formattedStart = String.format(Locale.US, "%02d:%02d", startHour, startMin)
                                val formattedEnd = String.format(Locale.US, "%02d:%02d", verifiedEndHour, verifiedEndMin)
                                
                                onConfirm(
                                    title.trim(),
                                    description.trim(),
                                    selectedEventDate.toString(),
                                    formattedStart,
                                    formattedEnd,
                                    colorHex,
                                    syncToDevice,
                                    isCountdownSelected,
                                    if (isCountdownSelected) selectedFromDate.toString() else null
                                )
                            }
                        },
                        modifier = Modifier.testTag("add_button")
                    ) {
                        Text(if (event == null) "Add" else "Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifyBengaliDateDialog(
    targetDate: LocalDate,
    bengaliOffsetDays: Int,
    bengaliCustomOverrides: Map<String, String>,
    onSetCustomDate: (LocalDate, String) -> Unit,
    onClearCustomDate: (LocalDate) -> Unit,
    onSetOffsetDays: (Int) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val gregDateFormatted = remember(targetDate) {
        targetDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))
    }
    
    val (initDay, initMonth, initYear) = remember(targetDate, bengaliOffsetDays, bengaliCustomOverrides) {
        RegionalCalendars.parseBengaliDateComponents(targetDate, bengaliOffsetDays, bengaliCustomOverrides)
    }
    
    var dayVal by remember(targetDate, initDay) { mutableIntStateOf(initDay) }
    var monthVal by remember(targetDate, initMonth) { mutableStateOf(initMonth) }
    var yearVal by remember(targetDate, initYear) { mutableIntStateOf(initYear) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    
    val isCustomForDate = bengaliCustomOverrides.containsKey(targetDate.toString())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Title and Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Modify Bengali Date",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = gregDateFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs between Custom Date for This Day vs Global Offset
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; actionFeedback = null },
                        text = { Text("Specific Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; actionFeedback = null },
                        text = { Text("Global Offset (All)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Specific Date Modification Tab
                    Text(
                        text = "Set exact Bengali date for this specific day:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Month Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { monthMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Bengali Month",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = monthVal,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Month"
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = monthMenuExpanded,
                            onDismissRequest = { monthMenuExpanded = false }
                        ) {
                            RegionalCalendars.BENGALI_MONTHS.forEach { monthName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = monthName,
                                            fontWeight = if (monthName == monthVal) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        monthVal = monthName
                                        monthMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day and Year Steppers in Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Day Stepper
                        OutlinedCard(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Day (1-32)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { if (dayVal > 1) dayVal-- },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = dayVal.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { if (dayVal < 32) dayVal++ },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Day", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Year Stepper
                        OutlinedCard(
                            modifier = Modifier.weight(1.1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Bangabda Year",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { yearVal-- },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text("−", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "$yearVal BS",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { yearVal++ },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase Year", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Date Preview:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$dayVal $monthVal, $yearVal BS",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Save and Clear Override Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCustomForDate) {
                            OutlinedButton(
                                onClick = {
                                    onClearCustomDate(targetDate)
                                    val (d, m, y) = RegionalCalendars.parseBengaliDateComponents(targetDate, bengaliOffsetDays, emptyMap())
                                    dayVal = d
                                    monthVal = m
                                    yearVal = y
                                    actionFeedback = "Custom date removed for this day"
                                },
                                modifier = Modifier.weight(1f).testTag("clear_bengali_override_btn")
                            ) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Button(
                            onClick = {
                                val customStr = "$dayVal $monthVal, $yearVal BS"
                                onSetCustomDate(targetDate, customStr)
                                actionFeedback = "Saved: $customStr"
                            },
                            modifier = Modifier.weight(if (isCustomForDate) 1.5f else 1f).testTag("save_bengali_custom_date_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply for this Day", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    // Global Calibration / Offset Tab
                    Text(
                        text = "Shift all Bengali dates across the calendar to match your regional Panjika almanac (Gupta Press, PM Bagchi, etc.):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Offset Stepper
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Global Day Offset",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { onSetOffsetDays(bengaliOffsetDays - 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("−", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = if (bengaliOffsetDays == 0) "0 (Default alignment)" else "${if (bengaliOffsetDays > 0) "+$bengaliOffsetDays" else "$bengaliOffsetDays"} day(s)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bengaliOffsetDays == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { onSetOffsetDays(bengaliOffsetDays + 1) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Shift Forward")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Calibration Presets
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-2 to "-2d", -1 to "-1d", 0 to "0 (Norm)", 1 to "+1d", 2 to "+2d").forEach { (offset, label) ->
                            val isSelected = bengaliOffsetDays == offset
                            FilledTonalButton(
                                onClick = { onSetOffsetDays(offset) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                colors = if (isSelected) ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Action Feedback Snackbar/Text
                if (actionFeedback != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = actionFeedback!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onResetAll()
                            val (d, m, y) = RegionalCalendars.parseBengaliDateComponents(targetDate, 0, emptyMap())
                            dayVal = d
                            monthVal = m
                            yearVal = y
                            actionFeedback = "All modifications reset to defaults"
                        }
                    ) {
                        Text("Reset All", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }

                    Button(onClick = onDismiss, modifier = Modifier.testTag("done_bengali_modify_btn")) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
