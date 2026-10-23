package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "org_holidays")
data class OrgHoliday(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val date: String // format "YYYY-MM-DD"
)

@Dao
interface OrgHolidayDao {
    @Query("SELECT * FROM org_holidays ORDER BY date ASC")
    fun getAllOrgHolidays(): Flow<List<OrgHoliday>>

    @Query("SELECT * FROM org_holidays WHERE date = :dateStr")
    fun getOrgHolidaysForDateSync(dateStr: String): List<OrgHoliday>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrgHolidays(holidays: List<OrgHoliday>)

    @Query("DELETE FROM org_holidays")
    suspend fun clearAllOrgHolidays()
}
