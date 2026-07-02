package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    // Measurements CRUD
    @Query("SELECT * FROM health_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<HealthMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: HealthMeasurement): Long

    @Delete
    suspend fun deleteMeasurement(measurement: HealthMeasurement)

    @Query("DELETE FROM health_measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: Int)

    // Reminders CRUD
    @Query("SELECT * FROM medication_reminders ORDER BY id DESC")
    fun getAllReminders(): Flow<List<MedicationReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: MedicationReminder): Long

    @Update
    suspend fun updateReminder(reminder: MedicationReminder)

    @Delete
    suspend fun deleteReminder(reminder: MedicationReminder)

    @Query("DELETE FROM medication_reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Int)
}
