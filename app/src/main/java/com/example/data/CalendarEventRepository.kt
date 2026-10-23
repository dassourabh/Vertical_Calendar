package com.example.data

import kotlinx.coroutines.flow.Flow

class CalendarEventRepository(
    private val dao: CalendarEventDao,
    private val orgHolidayDao: OrgHolidayDao
) {
    val allEvents: Flow<List<CalendarEvent>> = dao.getAllEvents()
    val allOrgHolidays: Flow<List<OrgHoliday>> = orgHolidayDao.getAllOrgHolidays()

    suspend fun insert(event: CalendarEvent) {
        dao.insertEvent(event)
    }

    suspend fun update(event: CalendarEvent) {
        dao.updateEvent(event)
    }

    suspend fun delete(event: CalendarEvent) {
        dao.deleteEvent(event)
    }

    suspend fun deleteById(id: Int) {
        dao.deleteEventById(id)
    }

    suspend fun insertOrgHolidays(holidays: List<OrgHoliday>) {
        orgHolidayDao.insertOrgHolidays(holidays)
    }

    suspend fun clearAllOrgHolidays() {
        orgHolidayDao.clearAllOrgHolidays()
    }
}
