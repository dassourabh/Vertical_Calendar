package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.CalendarViewModel
import com.example.ui.InfiniteCalendarScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val viewModel = ViewModelProvider(this)[CalendarViewModel::class.java]
    
    // Force refresh active widgets and reset AlarmManager schedules on app launch (Fail-safe)
    try {
        com.example.widget.CalendarWidgetProvider.updateCalendarWidgets(this)
        com.example.widget.CalendarWidgetProvider.scheduleMidnightAlarm(this)
        com.example.widget.CalendarWidgetProvider.schedulePeriodicAlarm(this)
    } catch (e: Throwable) {
        e.printStackTrace()
    }
    
    setContent {
      MyApplicationTheme {
        InfiniteCalendarScreen(viewModel = viewModel)
      }
    }
  }
}
