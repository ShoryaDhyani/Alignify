package com.alignify.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Calendar

/**
 * Singleton manager to interface directly with Android's Health Connect API.
 * Written in Kotlin to leverage Google's official Coroutines-based SDK.
 */
class WearableDataManager private constructor(private val context: Context) {

    private val healthConnectClient: HealthConnectClient? by lazy {
        if (isHealthConnectAvailable(context)) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: WearableDataManager? = null

        @JvmStatic
        fun getInstance(context: Context): WearableDataManager {
            return instance ?: synchronized(this) {
                instance ?: WearableDataManager(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Checks if Health Connect SDK is available and the app is installed.
         */
        @JvmStatic
        fun isHealthConnectAvailable(context: Context): Boolean {
            val status = HealthConnectClient.getSdkStatus(context)
            return status == HealthConnectClient.SDK_AVAILABLE
        }

        /**
         * Returns the set of permissions required for reading wearable data.
         */
        @JvmStatic
        fun getRequiredPermissions(): Set<String> {
            return setOf(
                HealthPermission.getReadPermission(StepsRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(OxygenSaturationRecord::class),
                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                HealthPermission.getReadPermission(SleepSessionRecord::class)
            )
        }
    }

    /**
     * Checks if the app has all required Health Connect permissions.
     */
    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(getRequiredPermissions())
    }

    /**
     * Reads aggregated daily steps from Health Connect.
     */
    suspend fun readDailySteps(startTime: Instant, endTime: Instant): Long {
        val client = healthConnectClient ?: return 0L
        try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            return response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return 0L
        }
    }

    /**
     * Reads aggregated active calories burned from Health Connect.
     */
    suspend fun readActiveCalories(startTime: Instant, endTime: Instant): Double {
        val client = healthConnectClient ?: return 0.0
        try {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            return response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            e.printStackTrace()
            return 0.0
        }
    }

    /**
     * Reads heart rate records for the time range.
     */
    suspend fun readHeartRateRecords(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = client.readRecords(request)
            return response.records
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Reads SpO2 records (Oxygen Saturation) for the time range.
     */
    suspend fun readSpO2Records(startTime: Instant, endTime: Instant): List<OxygenSaturationRecord> {
        val client = healthConnectClient ?: return emptyList()
        try {
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = client.readRecords(request)
            return response.records
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Bridges Health Connect (Suspend Functions) to FitnessDataManager (Java).
     * Automatically applies the Higher-Value-Wins merge strategy.
     */
    fun syncWithFitnessDataManager(fitnessDataManager: FitnessDataManager) {
        GlobalScope.launch(Dispatchers.IO) {
            if (!hasAllPermissions()) return@launch

            val now = Instant.now()
            
            // Start of today in local time, converted to Instant
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = Instant.ofEpochMilli(calendar.timeInMillis)

            val wearableSteps = readDailySteps(startOfDay, now).toInt()
            val wearableCalories = readActiveCalories(startOfDay, now).toInt()

            // Merge Strategy: Higher-Value-Wins
            if (wearableSteps > fitnessDataManager.stepsToday) {
                fitnessDataManager.setStepsToday(wearableSteps)
            }
            if (wearableCalories > fitnessDataManager.caloriesToday) {
                fitnessDataManager.setCaloriesToday(wearableCalories)
            }

            // Sync Heart Rate
            val hrRecords = readHeartRateRecords(startOfDay, now)
            val latestHrRecord = hrRecords.maxByOrNull { it.endTime }
            val latestHr = latestHrRecord?.samples?.lastOrNull()?.beatsPerMinute?.toInt()
            if (latestHr != null && latestHr > 0) {
                fitnessDataManager.setLatestHeartRate(latestHr)
            }

            // Sync SpO2
            val spo2Records = readSpO2Records(startOfDay, now)
            val latestSpO2Record = spo2Records.maxByOrNull { it.time }
            val latestSpO2 = latestSpO2Record?.percentage?.value
            if (latestSpO2 != null && latestSpO2 > 0) {
                fitnessDataManager.setLatestSpO2(latestSpO2.toFloat())
            }
        }
    }
}
