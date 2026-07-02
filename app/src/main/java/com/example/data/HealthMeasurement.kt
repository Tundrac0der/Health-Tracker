package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "health_measurements")
data class HealthMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "GLUCOSE", "PRESSURE", "HEART_RATE"
    val timestamp: Long,
    val value1: Double, // glucose in g/L, or systolic BP in mmHg, or heart rate in bpm
    val value2: Double? = null, // diastolic BP in mmHg (only for PRESSURE)
    val notes: String? = null
) : Serializable
