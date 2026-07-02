package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "medication_reminders")
data class MedicationReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicationName: String,
    val dosage: String,
    val scheduleType: String, // "DAILY", "WEEKLY", "MONTHLY"
    val hour: Int,
    val minute: Int,
    val reminderDate: Long? = null,
    val isActive: Boolean = true
) : Serializable
