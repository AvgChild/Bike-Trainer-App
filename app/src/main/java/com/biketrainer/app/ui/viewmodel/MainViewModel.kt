package com.biketrainer.app.ui.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.biketrainer.app.data.ble.BleManager
import com.biketrainer.app.data.ble.ConnectionState
import com.biketrainer.app.data.ble.HeartRateData
import com.biketrainer.app.data.ble.TrainerData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val bleManager: BleManager
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

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnectAll()
    }
}

class MainViewModelFactory(
    private val bleManager: BleManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(bleManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
