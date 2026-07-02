package com.example.data

import kotlinx.coroutines.flow.Flow

class HealthRepository(private val healthDao: HealthDao) {
    val allMeasurements: Flow<List<HealthMeasurement>> = healthDao.getAllMeasurements()
    val allReminders: Flow<List<MedicationReminder>> = healthDao.getAllReminders()

    suspend fun insertMeasurement(measurement: HealthMeasurement): Long {
        return healthDao.insertMeasurement(measurement)
    }

    suspend fun deleteMeasurement(measurement: HealthMeasurement) {
        healthDao.deleteMeasurement(measurement)
    }

    suspend fun deleteMeasurementById(id: Int) {
        healthDao.deleteMeasurementById(id)
    }

    suspend fun insertReminder(reminder: MedicationReminder): Long {
        return healthDao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: MedicationReminder) {
        healthDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: MedicationReminder) {
        healthDao.deleteReminder(reminder)
    }

    suspend fun deleteReminderById(id: Int) {
        healthDao.deleteReminderById(id)
    }
}
