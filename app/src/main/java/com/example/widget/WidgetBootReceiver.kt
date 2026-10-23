package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                // Update widget content immediately on boot/upgrade
                CalendarWidgetProvider.updateCalendarWidgets(context)
                
                // Re-schedule the AlarmManager alarms
                CalendarWidgetProvider.scheduleMidnightAlarm(context)
                CalendarWidgetProvider.schedulePeriodicAlarm(context)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
