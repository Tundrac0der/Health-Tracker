package com.example.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiver.AlarmScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HealthViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs: SharedPreferences = application.getSharedPreferences("health_tracker_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = HealthRepository(database.healthDao())

    // All measurements and reminders
    val allMeasurements = repository.allMeasurements
    val allReminders = repository.allReminders

    // Filtering inputs
    val searchQuery = MutableStateFlow("")
    val selectedFilterType = MutableStateFlow("ALL") // "ALL", "GLUCOSE", "PRESSURE", "HEART_RATE"
    val startDateFilter = MutableStateFlow<Long?>(null)
    val endDateFilter = MutableStateFlow<Long?>(null)

    // Language, Theme and User profile States
    val currentLanguage = MutableStateFlow(sharedPrefs.getString("language", "en") ?: "en")
    val themeMode = MutableStateFlow(sharedPrefs.getString("theme", "light") ?: "light")
    val username = MutableStateFlow(sharedPrefs.getString("username", "Marc") ?: "Marc")

    // Reactively filtered measurements list
    val filteredMeasurements: StateFlow<List<HealthMeasurement>> = combine(
        allMeasurements,
        searchQuery,
        selectedFilterType,
        startDateFilter,
        endDateFilter
    ) { measurements, query, type, start, end ->
        measurements.filter { item ->
            val matchesType = type == "ALL" || item.type == type
            val matchesQuery = query.isEmpty() || (item.notes?.contains(query, ignoreCase = true) == true)
            
            // Normalize dates to day-start or standard comparison
            val matchesDate = (start == null || item.timestamp >= start) &&
                              (end == null || item.timestamp <= (end + 86400000L)) // Include full end day
            matchesType && matchesQuery && matchesDate
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // CRUD Health Measurements
    fun insertMeasurement(type: String, value1: Double, value2: Double?, notes: String?, timestamp: Long) {
        viewModelScope.launch {
            repository.insertMeasurement(
                HealthMeasurement(
                    type = type,
                    timestamp = timestamp,
                    value1 = value1,
                    value2 = value2,
                    notes = notes
                )
            )
        }
    }

    fun deleteMeasurement(measurement: HealthMeasurement) {
        viewModelScope.launch {
            repository.deleteMeasurement(measurement)
        }
    }

    // CRUD Medication Reminders & Alarms scheduling
    fun insertReminder(medName: String, dosage: String, scheduleType: String, hour: Int, minute: Int, reminderDate: Long? = null) {
        viewModelScope.launch {
            val reminder = MedicationReminder(
                medicationName = medName,
                dosage = dosage,
                scheduleType = scheduleType,
                hour = hour,
                minute = minute,
                reminderDate = reminderDate,
                isActive = true
            )
            val insertedId = repository.insertReminder(reminder)
            AlarmScheduler.scheduleReminder(
                context = getApplication(),
                reminderId = insertedId.toInt(),
                medicationName = medName,
                dosage = dosage,
                scheduleType = scheduleType,
                hour = hour,
                minute = minute,
                reminderDate = reminderDate
            )
        }
    }

    fun toggleReminder(reminder: MedicationReminder) {
        viewModelScope.launch {
            val updated = reminder.copy(isActive = !reminder.isActive)
            repository.updateReminder(updated)
            if (updated.isActive) {
                AlarmScheduler.scheduleReminder(
                    context = getApplication(),
                    reminderId = updated.id,
                    medicationName = updated.medicationName,
                    dosage = updated.dosage,
                    scheduleType = updated.scheduleType,
                    hour = updated.hour,
                    minute = updated.minute,
                    reminderDate = updated.reminderDate
                )
            } else {
                AlarmScheduler.cancelReminder(getApplication(), updated.id)
            }
        }
    }

    fun deleteReminder(reminder: MedicationReminder) {
        viewModelScope.launch {
            AlarmScheduler.cancelReminder(getApplication(), reminder.id)
            repository.deleteReminder(reminder)
        }
    }

    // In-app Language preferences
    fun setLanguage(lang: String) {
        sharedPrefs.edit().putString("language", lang).apply()
        currentLanguage.value = lang
    }

    // In-app Theme preference
    fun setTheme(theme: String) {
        sharedPrefs.edit().putString("theme", theme).apply()
        themeMode.value = theme
    }

    fun setUsername(name: String) {
        sharedPrefs.edit().putString("username", name).apply()
        username.value = name
    }
}
