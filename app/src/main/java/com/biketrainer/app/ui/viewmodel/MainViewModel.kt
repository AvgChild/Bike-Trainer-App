package com.biketrainer.app.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biketrainer.app.data.ble.BleManager
import com.biketrainer.app.data.ble.ConnectionState
import com.biketrainer.app.data.ble.HeartRateData
import com.biketrainer.app.data.ble.TrainerData
import com.biketrainer.app.data.strava.StravaApi
import com.biketrainer.app.data.strava.UploadResult
import com.biketrainer.app.data.workout.Workout
import com.biketrainer.app.data.workout.WorkoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(
    private val bleManager: BleManager,
    private val workoutManager: WorkoutManager,
    private val stravaApi: StravaApi
) : ViewModel() {

    val isScanning: StateFlow<Boolean> = bleManager.isScanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scannedDevices = bleManager.scannedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heartRateConnectionState: StateFlow<ConnectionState> = bleManager.heartRateConnectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val trainerConnectionState: StateFlow<ConnectionState> = bleManager.trainerConnectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val heartRateData: StateFlow<HeartRateData?> = bleManager.heartRateData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val trainerData: StateFlow<TrainerData?> = bleManager.trainerData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isRecording: StateFlow<Boolean> = workoutManager.isRecording
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentWorkoutDuration: StateFlow<Long> = workoutManager.currentWorkoutDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    private val _lastCompletedWorkout = MutableStateFlow<Workout?>(null)
    val lastCompletedWorkout: StateFlow<Workout?> = _lastCompletedWorkout.asStateFlow()

    fun startScanning() {
        bleManager.startScanning()
    }

    fun stopScanning() {
        bleManager.stopScanning()
    }

    fun connectToHeartRateMonitor(device: BluetoothDevice) {
        bleManager.connectToHeartRateMonitor(device)
        bleManager.stopScanning()
    }

    fun connectToTrainer(device: BluetoothDevice) {
        bleManager.connectToTrainer(device)
        bleManager.stopScanning()
    }

    fun requestTrainerControl() {
        bleManager.requestTrainerControl()
    }

    fun setTargetResistanceLevel(resistanceLevel: Int) {
        bleManager.setTargetResistanceLevel(resistanceLevel)
    }

    fun setTargetPower(power: Int) {
        bleManager.setTargetPower(power)
    }

    fun resetTrainer() {
        bleManager.resetTrainer()
    }

    fun disconnectAll() {
        bleManager.disconnectAll()
    }

    fun startWorkout() {
        workoutManager.startWorkout()
        startWorkoutRecording()
    }

    fun stopWorkout(): Workout? {
        val workout = workoutManager.stopWorkout()
        _lastCompletedWorkout.value = workout
        return workout
    }

    fun clearLastWorkout() {
        _lastCompletedWorkout.value = null
    }

    private fun startWorkoutRecording() {
        viewModelScope.launch {
            while (isActive && workoutManager.isRecording.value) {
                // Record a sample every second
                workoutManager.recordSample(
                    heartRateData = heartRateData.value,
                    trainerData = trainerData.value
                )
                delay(1000)
            }
        }
    }

    fun getAllWorkouts(): List<File> {
        return workoutManager.getAllWorkouts()
    }

    fun exportWorkout(workoutFile: File): File {
        return workoutManager.exportWorkout(workoutFile)
    }

    fun deleteWorkout(workoutFile: File): Boolean {
        return workoutManager.deleteWorkout(workoutFile)
    }

    override fun onCleared() {
        super.onCleared()
        if (isRecording.value) {
            stopWorkout()
        }
        bleManager.disconnectAll()
    }
}

class MainViewModelFactory(
    private val bleManager: BleManager,
    private val workoutManager: WorkoutManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(bleManager, workoutManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
