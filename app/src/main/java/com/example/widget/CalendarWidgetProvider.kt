package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.CalendarDatabase
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.concurrent.thread

class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Trigger a background update for all existing widgets
        triggerUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_USER_PRESENT ||
            action == "com.example.ACTION_WIDGET_RELOAD" ||
            action == "com.example.ACTION_WIDGET_ALARM") {
            
            triggerUpdate(context)
            
            if (action == "com.example.ACTION_WIDGET_ALARM" || action == Intent.ACTION_DATE_CHANGED) {
                scheduleMidnightAlarm(context)
            }
        }
    }

    private fun triggerUpdate(context: Context) {
        val pendingResult = try { goAsync() } catch (e: Throwable) { null }
        thread {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, CalendarWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (appWidgetIds.isNotEmpty()) {
                    updateWidgetContent(context, appWidgetManager, appWidgetIds)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                try {
                    pendingResult?.finish()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateWidgetContent(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDate.now()
        val dayOfMonthStr = now.dayOfMonth.toString()
        val shortWeekDay = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())
        val shortMonth = now.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())
        val todayStr = now.toString() // "YYYY-MM-DD"

        val eventsToday = try {
            val database = CalendarDatabase.getDatabase(context)
            val dbEvents = database.calendarEventDao().getEventsForDateSync(todayStr)
            val holidays = database.orgHolidayDao().getOrgHolidaysForDateSync(todayStr)
            val mappedHolidays = holidays.map { holiday ->
                com.example.data.CalendarEvent(
                    id = -1000000 - holiday.id,
                    title = holiday.name,
                    description = "Organization Holiday",
                    date = holiday.date,
                    startTime = "00:00",
                    endTime = "23:59",
                    colorHex = "#FF5722", // Holiday color orange
                    deviceEventId = null
                )
            }
            dbEvents + mappedHolidays
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }

        val dotIds = listOf(
            R.id.widget_event_dot,
            R.id.widget_event_dot_2,
            R.id.widget_event_dot_3,
            R.id.widget_event_dot_4
        )

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.calendar_widget)
            
            // Bind texts
            views.setTextViewText(R.id.widget_header_month, shortMonth)
            views.setTextViewText(R.id.widget_day_text, shortWeekDay)
            views.setTextViewText(R.id.widget_date_text, dayOfMonthStr)
            
            // Bind event dots beautifully with their specific colors (filtering out countdown events)
            val oneTimeEvents = eventsToday.filter { !it.isCountdown }
            for (i in dotIds.indices) {
                val dotId = dotIds[i]
                if (i < oneTimeEvents.size) {
                    val event = oneTimeEvents[i]
                    views.setViewVisibility(dotId, View.VISIBLE)
                    try {
                        val colorInt = android.graphics.Color.parseColor(event.colorHex)
                        views.setInt(dotId, "setColorFilter", colorInt)
                    } catch (e: Throwable) {
                        views.setInt(dotId, "setColorFilter", android.graphics.Color.parseColor("#4CAF50"))
                    }
                } else {
                    views.setViewVisibility(dotId, View.GONE)
                }
            }

            // Register touch triggers to launch the main calendar applet
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_header, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateCalendarWidgets(context: Context) {
            val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = "com.example.ACTION_WIDGET_RELOAD"
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }

        fun scheduleMidnightAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
                val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                    action = "com.example.ACTION_WIDGET_ALARM"
                    setPackage(context.packageName)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Calculate next midnight (00:00:05)
                val calendar = java.util.Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 5)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                
                alarmManager.cancel(pendingIntent)
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        fun schedulePeriodicAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
                val intent = Intent(context, CalendarWidgetProvider::class.java).apply {
                    action = "com.example.ACTION_WIDGET_ALARM"
                    setPackage(context.packageName)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pendingIntent)
                
                // Inexact periodic updates as a safe fallback (every hour)
                val triggerAt = System.currentTimeMillis() + android.app.AlarmManager.INTERVAL_HOUR
                alarmManager.setRepeating(
                    android.app.AlarmManager.RTC,
                    triggerAt,
                    android.app.AlarmManager.INTERVAL_HOUR,
                    pendingIntent
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}
