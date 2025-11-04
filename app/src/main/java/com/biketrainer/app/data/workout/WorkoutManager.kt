package com.biketrainer.app.data.workout

import android.content.Context
import android.util.Log
import com.biketrainer.app.data.ble.HeartRateData
import com.biketrainer.app.data.ble.TrainerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class WorkoutManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "WorkoutManager"
        private const val WORKOUTS_DIR = "workouts"
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentWorkoutDuration = MutableStateFlow(0L)
    val currentWorkoutDuration: StateFlow<Long> = _currentWorkoutDuration.asStateFlow()

    private var currentWorkoutId: String? = null
    private var workoutStartTime: Long = 0
    private val workoutSamples = mutableListOf<WorkoutSample>()
    private var cumulativeDistance: Double = 0.0
    private var lastSampleTime: Long = 0

    fun startWorkout() {
        if (_isRecording.value) return

        currentWorkoutId = UUID.randomUUID().toString()
        workoutStartTime = System.currentTimeMillis()
        workoutSamples.clear()
        cumulativeDistance = 0.0
        lastSampleTime = workoutStartTime
        _isRecording.value = true
        _currentWorkoutDuration.value = 0

        Log.d(TAG, "Workout started: $currentWorkoutId")
    }

    fun recordSample(heartRateData: HeartRateData?, trainerData: TrainerData?) {
        if (!_isRecording.value) return

        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - lastSampleTime) / 1000.0

        // Update cumulative distance based on speed and time elapsed
        trainerData?.speed?.let { speedKmh ->
            val speedMs = speedKmh / 3.6  // Convert km/h to m/s
            cumulativeDistance += speedMs * elapsedSeconds
        }

        val sample = WorkoutSample(
            timestamp = currentTime,
            heartRate = heartRateData?.heartRate,
            power = trainerData?.power,
            speed = trainerData?.speed,
            cadence = trainerData?.cadence,
            distance = cumulativeDistance
        )

        workoutSamples.add(sample)
        lastSampleTime = currentTime
        _currentWorkoutDuration.value = (currentTime - workoutStartTime) / 1000

        Log.d(TAG, "Sample recorded: HR=${sample.heartRate}, Power=${sample.power}, Distance=${cumulativeDistance}m")
    }

    fun stopWorkout(): Workout? {
        if (!_isRecording.value) return null

        val endTime = System.currentTimeMillis()
        val id = currentWorkoutId ?: return null

        // Calculate statistics
        val heartRates = workoutSamples.mapNotNull { it.heartRate }
        val powers = workoutSamples.mapNotNull { it.power }
        val cadences = workoutSamples.mapNotNull { it.cadence }

        val avgHeartRate = if (heartRates.isNotEmpty()) heartRates.average().toInt() else null
        val maxHeartRate = heartRates.maxOrNull()
        val avgPower = if (powers.isNotEmpty()) powers.average().toInt() else null
        val maxPower = powers.maxOrNull()
        val avgCadence = if (cadences.isNotEmpty()) cadences.average() else null

        // Calculate calories (rough estimation: 1 calorie per watt per hour)
        val durationHours = (endTime - workoutStartTime) / 3600000.0
        val calories = avgPower?.let { (it * durationHours).toInt() }

        val workout = Workout(
            id = id,
            startTime = workoutStartTime,
            endTime = endTime,
            samples = workoutSamples.toList(),
            totalDistance = cumulativeDistance,
            averageHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            averagePower = avgPower,
            maxPower = maxPower,
            averageCadence = avgCadence,
            calories = calories
        )

        _isRecording.value = false
        _currentWorkoutDuration.value = 0

        // Save workout
        saveWorkout(workout)

        Log.d(TAG, "Workout stopped: $id, Distance: ${cumulativeDistance}m, Duration: ${workout.durationMinutes}min")

        return workout
    }

    private fun saveWorkout(workout: Workout) {
        try {
            val workoutsDir = File(context.filesDir, WORKOUTS_DIR)
            if (!workoutsDir.exists()) {
                workoutsDir.mkdirs()
            }

            val tcxFile = File(workoutsDir, "${workout.id}.tcx")
            tcxFile.writeText(workout.toTCX())

            Log.d(TAG, "Workout saved: ${tcxFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save workout", e)
        }
    }

    fun getAllWorkouts(): List<File> {
        val workoutsDir = File(context.filesDir, WORKOUTS_DIR)
        if (!workoutsDir.exists()) return emptyList()

        return workoutsDir.listFiles()?.filter { it.extension == "tcx" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun exportWorkout(workoutFile: File): File {
        return workoutFile
    }

    fun deleteWorkout(workoutFile: File): Boolean {
        return workoutFile.delete()
    }
}
