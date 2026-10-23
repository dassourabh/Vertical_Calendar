package com.example

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.widget.CalendarWidgetProvider

class CalendarApplication : Application() {

    private val timeTickReceiver = object : BroadcastReceiver() {
        private var lastDay = -1
        
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_TIME_TICK || action == Intent.ACTION_USER_PRESENT) {
                val currentDay = java.time.LocalDate.now().dayOfMonth
                if (currentDay != lastDay || action == Intent.ACTION_USER_PRESENT) {
                    lastDay = currentDay
                    CalendarWidgetProvider.updateCalendarWidgets(context)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // Dynamically register dynamic time/unlock receiver (Option 2)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timeTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(timeTickReceiver, filter)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        // Run AlarmManager schedules (Option 3)
        try {
            CalendarWidgetProvider.scheduleMidnightAlarm(this)
            CalendarWidgetProvider.schedulePeriodicAlarm(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
