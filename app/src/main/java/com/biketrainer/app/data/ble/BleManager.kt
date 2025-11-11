package com.biketrainer.app.data.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "BleManager"

        // Standard BLE Service UUIDs
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        val FITNESS_MACHINE_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
        val INDOOR_BIKE_DATA_CHAR_UUID: UUID = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb")
        val TRAINING_STATUS_CHAR_UUID: UUID = UUID.fromString("00002ad3-0000-1000-8000-00805f9b34fb")
        val FITNESS_MACHINE_FEATURE_CHAR_UUID: UUID = UUID.fromString("00002acc-0000-1000-8000-00805f9b34fb")
        val FITNESS_MACHINE_CONTROL_POINT_CHAR_UUID: UUID = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb")

        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // FTMS Control Point Op Codes
        private const val FTMS_REQUEST_CONTROL = 0x00.toByte()
        private const val FTMS_RESET = 0x01.toByte()
        private const val FTMS_SET_TARGET_RESISTANCE_LEVEL = 0x04.toByte()
        private const val FTMS_SET_TARGET_POWER = 0x05.toByte()
        private const val FTMS_START_RESUME = 0x07.toByte()
        private const val FTMS_STOP_PAUSE = 0x08.toByte()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var heartRateGatt: BluetoothGatt? = null
    private var trainerGatt: BluetoothGatt? = null
    private var lastHeartRateDevice: BluetoothDevice? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScanResult>>(emptyList())
    val scannedDevices: StateFlow<List<ScanResult>> = _scannedDevices.asStateFlow()

    private val _heartRateConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val heartRateConnectionState: StateFlow<ConnectionState> = _heartRateConnectionState.asStateFlow()

    private val _trainerConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val trainerConnectionState: StateFlow<ConnectionState> = _trainerConnectionState.asStateFlow()

    private val _heartRateData = MutableStateFlow<HeartRateData?>(null)
    val heartRateData: StateFlow<HeartRateData?> = _heartRateData.asStateFlow()

    private val _trainerData = MutableStateFlow<TrainerData?>(null)
    val trainerData: StateFlow<TrainerData?> = _trainerData.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return
        }

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build(),
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(FITNESS_MACHINE_SERVICE_UUID))
                .build()
        )

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _scannedDevices.value = emptyList()
        bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
        _isScanning.value = true
        Log.d(TAG, "Started BLE scanning")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!hasBluetoothPermissions()) return

        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Log.d(TAG, "Stopped BLE scanning")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val currentDevices = _scannedDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.device.address == result.device.address }

            if (existingIndex != -1) {
                currentDevices[existingIndex] = result
            } else {
                currentDevices.add(result)
            }

            _scannedDevices.value = currentDevices
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHeartRateMonitor(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) return

        lastHeartRateDevice = device
        _heartRateConnectionState.value = ConnectionState.CONNECTING
        heartRateGatt = device.connectGatt(context, false, heartRateGattCallback)
    }

    @SuppressLint("MissingPermission")
    fun reconnectHeartRateMonitor() {
        lastHeartRateDevice?.let { device ->
            if (!hasBluetoothPermissions()) return

            // Disconnect if already connected
            heartRateGatt?.disconnect()
            heartRateGatt?.close()

            _heartRateConnectionState.value = ConnectionState.CONNECTING
            heartRateGatt = device.connectGatt(context, false, heartRateGattCallback)
            Log.d(TAG, "Reconnecting to heart rate monitor: ${device.address}")
        } ?: run {
            Log.w(TAG, "No previous heart rate device to reconnect to")
        }
    }

    fun hasLastHeartRateDevice(): Boolean = lastHeartRateDevice != null

    @SuppressLint("MissingPermission")
    fun connectToTrainer(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) return

        _trainerConnectionState.value = ConnectionState.CONNECTING
        trainerGatt = device.connectGatt(context, false, trainerGattCallback)
    }

    private val heartRateGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to heart rate monitor")
                    _heartRateConnectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from heart rate monitor")
                    _heartRateConnectionState.value = ConnectionState.DISCONNECTED
                    _heartRateData.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)

                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.let { desc ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val heartRate = parseHeartRate(value)
                _heartRateData.value = heartRate
            }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                onCharacteristicChanged(gatt, characteristic, characteristic.value)
            }
        }
    }

    private val trainerGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to trainer")
                    _trainerConnectionState.value = ConnectionState.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from trainer")
                    _trainerConnectionState.value = ConnectionState.DISCONNECTED
                    _trainerData.value = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(FITNESS_MACHINE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(INDOOR_BIKE_DATA_CHAR_UUID)

                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.let { desc ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(desc)
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == INDOOR_BIKE_DATA_CHAR_UUID) {
                val trainerData = parseIndoorBikeData(value)
                _trainerData.value = trainerData
            }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                onCharacteristicChanged(gatt, characteristic, characteristic.value)
            }
        }
    }

    private fun parseHeartRate(data: ByteArray): HeartRateData {
        val flag = data[0].toInt()
        val format = flag and 0x01
        val heartRate = if (format == 0) {
            data[1].toInt() and 0xFF
        } else {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        }

        return HeartRateData(heartRate = heartRate)
    }

    private fun parseIndoorBikeData(data: ByteArray): TrainerData {
        var index = 0

        // Read flags (2 bytes)
        val flags = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
        index += 2

        Log.d(TAG, "Indoor Bike Data - Flags: 0x${flags.toString(16)}, Data size: ${data.size}, Raw bytes: ${data.joinToString(" ") { "%02X".format(it) }}")

        var speed: Double? = null
        var averageSpeed: Double? = null
        var cadence: Double? = null
        var averageCadence: Double? = null
        var distance: Int? = null
        var resistanceLevel: Int? = null
        var power: Int? = null
        var averagePower: Int? = null
        var totalEnergy: Int? = null
        var energyPerHour: Int? = null
        var energyPerMinute: Int? = null
        var heartRate: Int? = null
        var metabolicEquivalent: Double? = null
        var elapsedTime: Int? = null
        var remainingTime: Int? = null

        // Parse based on flags
        // Bit 0: Instantaneous Speed present (uint16, resolution 0.01 km/h)
        if (flags and 0x0001 != 0) {
            if (index + 1 < data.size) {
                speed = ((data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)) * 0.01
                index += 2
            }
        }

        // Bit 1: Average Speed present (uint16, resolution 0.01 km/h)
        if (flags and 0x0002 != 0) {
            if (index + 1 < data.size) {
                averageSpeed = ((data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)) * 0.01
                index += 2
            }
        }

        // Bit 2: Instantaneous Cadence present (uint16, resolution 0.5 rpm)
        if (flags and 0x0004 != 0) {
            if (index + 1 < data.size) {
                val rawCadence = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                cadence = rawCadence * 0.5
                Log.d(TAG, "Cadence - Raw value: $rawCadence, Calculated: $cadence rpm, Bytes at index $index: ${data[index].toInt() and 0xFF}, ${data[index + 1].toInt() and 0xFF}")
                index += 2
            }
        }

        // Bit 3: Average Cadence present (uint16, resolution 0.5 rpm)
        if (flags and 0x0008 != 0) {
            if (index + 1 < data.size) {
                averageCadence = ((data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)) * 0.5
                index += 2
            }
        }

        // Bit 4: Total Distance present (uint24)
        if (flags and 0x0010 != 0) {
            if (index + 2 < data.size) {
                distance = (data[index].toInt() and 0xFF) or
                          ((data[index + 1].toInt() and 0xFF) shl 8) or
                          ((data[index + 2].toInt() and 0xFF) shl 16)
                index += 3
            }
        }

        // Bit 5: Resistance Level present (sint16)
        if (flags and 0x0020 != 0) {
            if (index + 1 < data.size) {
                resistanceLevel = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                if (resistanceLevel!! > 32767) resistanceLevel -= 65536 // Handle signed int
                index += 2
            }
        }

        // Bit 6: Instantaneous Power present (sint16, resolution 1 watt)
        if (flags and 0x0040 != 0) {
            if (index + 1 < data.size) {
                power = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                if (power!! > 32767) power -= 65536 // Handle signed int
                index += 2
            }
        }

        // Bit 7: Average Power present (sint16, resolution 1 watt)
        if (flags and 0x0080 != 0) {
            if (index + 1 < data.size) {
                averagePower = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                if (averagePower!! > 32767) averagePower -= 65536 // Handle signed int
                index += 2
            }
        }

        // Bit 8: Expended Energy present (3 uint16 values)
        if (flags and 0x0100 != 0) {
            // Total Energy (uint16, resolution 1 kcal)
            if (index + 1 < data.size) {
                totalEnergy = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                index += 2
            }
            // Energy Per Hour (uint16, resolution 1 kcal)
            if (index + 1 < data.size) {
                energyPerHour = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                index += 2
            }
            // Energy Per Minute (uint8, resolution 1 kcal)
            if (index < data.size) {
                energyPerMinute = data[index].toInt() and 0xFF
                index += 1
            }
        }

        // Bit 9: Heart Rate present (uint8, resolution 1 bpm)
        if (flags and 0x0200 != 0) {
            if (index < data.size) {
                heartRate = data[index].toInt() and 0xFF
                index += 1
            }
        }

        // Bit 10: Metabolic Equivalent present (uint8, resolution 0.1)
        if (flags and 0x0400 != 0) {
            if (index < data.size) {
                metabolicEquivalent = (data[index].toInt() and 0xFF) * 0.1
                index += 1
            }
        }

        // Bit 11: Elapsed Time present (uint16, resolution 1 second)
        if (flags and 0x0800 != 0) {
            if (index + 1 < data.size) {
                elapsedTime = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                index += 2
            }
        }

        // Bit 12: Remaining Time present (uint16, resolution 1 second)
        if (flags and 0x1000 != 0) {
            if (index + 1 < data.size) {
                remainingTime = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                index += 2
            }
        }

        return TrainerData(
            speed = speed,
            averageSpeed = averageSpeed,
            cadence = cadence,
            averageCadence = averageCadence,
            distance = distance,
            resistanceLevel = resistanceLevel,
            power = power,
            averagePower = averagePower,
            totalEnergy = totalEnergy,
            energyPerHour = energyPerHour,
            energyPerMinute = energyPerMinute,
            heartRate = heartRate,
            metabolicEquivalent = metabolicEquivalent,
            elapsedTime = elapsedTime,
            remainingTime = remainingTime
        )
    }

    @SuppressLint("MissingPermission")
    fun requestTrainerControl() {
        if (!hasBluetoothPermissions()) return

        val service = trainerGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        val controlPoint = service?.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_CHAR_UUID)

        controlPoint?.let {
            val command = byteArrayOf(FTMS_REQUEST_CONTROL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                trainerGatt?.writeCharacteristic(it, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                it.value = command
                @Suppress("DEPRECATION")
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                trainerGatt?.writeCharacteristic(it)
            }
            Log.d(TAG, "Requested trainer control")
        }
    }

    @SuppressLint("MissingPermission")
    fun setTargetResistanceLevel(resistanceLevel: Int) {
        if (!hasBluetoothPermissions()) return

        val service = trainerGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        val controlPoint = service?.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_CHAR_UUID)

        controlPoint?.let {
            // Resistance level is sent as a signed 16-bit integer with resolution 0.1
            val resistanceValue = (resistanceLevel * 10).toShort()
            val command = byteArrayOf(
                FTMS_SET_TARGET_RESISTANCE_LEVEL,
                (resistanceValue.toInt() and 0xFF).toByte(),
                ((resistanceValue.toInt() shr 8) and 0xFF).toByte()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                trainerGatt?.writeCharacteristic(it, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                it.value = command
                @Suppress("DEPRECATION")
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                trainerGatt?.writeCharacteristic(it)
            }
            Log.d(TAG, "Set target resistance level to: $resistanceLevel")
        }
    }

    @SuppressLint("MissingPermission")
    fun setTargetPower(power: Int) {
        if (!hasBluetoothPermissions()) return

        val service = trainerGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        val controlPoint = service?.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_CHAR_UUID)

        controlPoint?.let {
            // Power is sent as a signed 16-bit integer in watts
            val powerValue = power.toShort()
            val command = byteArrayOf(
                FTMS_SET_TARGET_POWER,
                (powerValue.toInt() and 0xFF).toByte(),
                ((powerValue.toInt() shr 8) and 0xFF).toByte()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                trainerGatt?.writeCharacteristic(it, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                it.value = command
                @Suppress("DEPRECATION")
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                trainerGatt?.writeCharacteristic(it)
            }
            Log.d(TAG, "Set target power to: $power watts")
        }
    }

    @SuppressLint("MissingPermission")
    fun resetTrainer() {
        if (!hasBluetoothPermissions()) return

        val service = trainerGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        val controlPoint = service?.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_CHAR_UUID)

        controlPoint?.let {
            val command = byteArrayOf(FTMS_RESET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                trainerGatt?.writeCharacteristic(it, command, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                it.value = command
                @Suppress("DEPRECATION")
                it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                trainerGatt?.writeCharacteristic(it)
            }
            Log.d(TAG, "Reset trainer")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectAll() {
        if (!hasBluetoothPermissions()) return

        heartRateGatt?.disconnect()
        heartRateGatt?.close()
        heartRateGatt = null

        trainerGatt?.disconnect()
        trainerGatt?.close()
        trainerGatt = null
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class HeartRateData(
    val heartRate: Int
)

data class TrainerData(
    val speed: Double? = null,
    val averageSpeed: Double? = null,
    val cadence: Double? = null,
    val averageCadence: Double? = null,
    val distance: Int? = null,
    val resistanceLevel: Int? = null,
    val power: Int? = null,
    val averagePower: Int? = null,
    val totalEnergy: Int? = null,
    val energyPerHour: Int? = null,
    val energyPerMinute: Int? = null,
    val heartRate: Int? = null,
    val metabolicEquivalent: Double? = null,
    val elapsedTime: Int? = null,
    val remainingTime: Int? = null
)
