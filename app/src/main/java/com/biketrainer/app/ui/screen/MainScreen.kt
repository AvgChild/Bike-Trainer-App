package com.biketrainer.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biketrainer.app.data.ble.BleManager
import com.biketrainer.app.data.ble.ConnectionState
import com.biketrainer.app.data.strava.StravaApi
import com.biketrainer.app.data.workout.WorkoutManager
import com.biketrainer.app.ui.viewmodel.MainViewModel
import com.biketrainer.app.ui.viewmodel.MainViewModelFactory
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(
            bleManager = remember { BleManager(context) },
            workoutManager = remember { WorkoutManager(context) },
            stravaApi = remember { StravaApi(context) }
        )
    )

    // Handle Strava OAuth callback
    val authCode by com.biketrainer.app.MainActivity.stravaAuthCode
    LaunchedEffect(authCode) {
        authCode?.let { code ->
            viewModel.handleStravaCallback(code)
            com.biketrainer.app.MainActivity.stravaAuthCode.value = null // Clear after handling
        }
    }
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)
    var showHistory by remember { mutableStateOf(false) }
    val lastCompletedWorkout by viewModel.lastCompletedWorkout.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bike Trainer") },
                actions = {
                    if (permissionsState.allPermissionsGranted) {
                        IconButton(onClick = { showHistory = !showHistory }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Workout History"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (permissionsState.allPermissionsGranted) {
                if (lastCompletedWorkout != null) {
                    val isStravaAuthenticated by viewModel.isStravaAuthenticated.collectAsStateWithLifecycle()
                    FinishedWorkoutScreen(
                        workout = lastCompletedWorkout!!,
                        isStravaAuthenticated = isStravaAuthenticated,
                        onConnectStrava = {
                            viewModel.initiateStravaAuth()
                        },
                        onUploadToStrava = {
                            val tcxFile = viewModel.getAllWorkouts().firstOrNull { file ->
                                file.nameWithoutExtension == lastCompletedWorkout!!.id
                            }
                            tcxFile?.let { file ->
                                viewModel.uploadToStrava(file)
                            }
                        },
                        uploadStatus = viewModel.uploadStatus.collectAsStateWithLifecycle().value,
                        onDismiss = {
                            viewModel.clearLastWorkout()
                        }
                    )
                } else if (showHistory) {
                    val workouts = remember { viewModel.getAllWorkouts() }
                    WorkoutHistoryScreen(
                        workouts = workouts,
                        onBack = { showHistory = false },
                        onShareWorkout = { file ->
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/xml"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Bike Trainer Workout")
                                    putExtra(Intent.EXTRA_TEXT, "Check out my workout!")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Workout"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onDeleteWorkout = { file ->
                            viewModel.deleteWorkout(file)
                            showHistory = false
                            showHistory = true // Refresh
                        },
                        onUploadToStrava = { file ->
                            viewModel.uploadToStrava(file)
                        }
                    )
                } else {
                    MainContent(viewModel)
                }
            } else {
                PermissionsRequestScreen(permissionsState)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsRequestScreen(permissionsState: MultiplePermissionsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Bluetooth Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This app needs Bluetooth permissions to connect to your bike trainer and heart rate monitor.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun MainContent(viewModel: MainViewModel) {
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val heartRateConnectionState by viewModel.heartRateConnectionState.collectAsStateWithLifecycle()
    val trainerConnectionState by viewModel.trainerConnectionState.collectAsStateWithLifecycle()
    val heartRateData by viewModel.heartRateData.collectAsStateWithLifecycle()
    val trainerData by viewModel.trainerData.collectAsStateWithLifecycle()

    // Only require trainer connection - HR monitor is optional
    val trainerConnected = trainerConnectionState == ConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!trainerConnected) {
            // Device scanning and connection section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scan for Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                if (isScanning) {
                                    viewModel.stopScanning()
                                } else {
                                    viewModel.startScanning()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isScanning) "Stop Scan" else "Start Scan")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ConnectionStatusRow(
                        label = "Heart Rate Monitor (optional)",
                        state = heartRateConnectionState
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ConnectionStatusRow(
                        label = "Bike Trainer (required)",
                        state = trainerConnectionState
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (scannedDevices.isNotEmpty()) {
                Text(
                    text = "Available Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scannedDevices) { scanResult ->
                        DeviceItem(
                            scanResult = scanResult,
                            onConnectAsHeartRate = { viewModel.connectToHeartRateMonitor(scanResult.device) },
                            onConnectAsTrainer = { viewModel.connectToTrainer(scanResult.device) }
                        )
                    }
                }
            } else if (isScanning) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scanning for devices...")
                    }
                }
            }
        } else {
            // Metrics display section
            MetricsDisplay(
                heartRateData = heartRateData,
                trainerData = trainerData,
                onDisconnect = { viewModel.disconnectAll() },
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun ConnectionStatusRow(label: String, state: ConnectionState) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = when (state) {
            ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
            ConnectionState.CONNECTING -> MaterialTheme.colorScheme.secondary
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
        }

        Box(
            modifier = Modifier
                .size(12.dp)
                .padding(2.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.small,
                color = color
            ) {}
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$label: ${state.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun DeviceItem(
    scanResult: ScanResult,
    onConnectAsHeartRate: () -> Unit,
    onConnectAsTrainer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = scanResult.device.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = scanResult.device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "RSSI: ${scanResult.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnectAsHeartRate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Heart Rate")
                }

                Button(
                    onClick = onConnectAsTrainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBike,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Trainer")
                }
            }
        }
    }
}

enum class TrainerControlMode {
    RESISTANCE,
    POWER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsDisplay(
    heartRateData: com.biketrainer.app.data.ble.HeartRateData?,
    trainerData: com.biketrainer.app.data.ble.TrainerData?,
    onDisconnect: () -> Unit,
    viewModel: MainViewModel
) {
    var resistanceLevel by remember { mutableStateOf(50f) }
    var targetPower by remember { mutableStateOf(150f) }
    var hasRequestedControl by remember { mutableStateOf(false) }
    var controlMode by remember { mutableStateOf(TrainerControlMode.RESISTANCE) }
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val workoutDuration by viewModel.currentWorkoutDuration.collectAsStateWithLifecycle()
    val uploadStatus by viewModel.uploadStatus.collectAsStateWithLifecycle()
    val liveStats by viewModel.liveStats.collectAsStateWithLifecycle()
    val isStravaAuthenticated by viewModel.isStravaAuthenticated.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isRecording) 12.dp else 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Metrics",
                    style = if (isRecording) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (isRecording) {
                    // Show Stop button when recording
                    Button(
                        onClick = { viewModel.stopWorkout() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Stop", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // Show Disconnect button when not recording
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Disconnect", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Workout timer when recording
        if (isRecording) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                val minutes = workoutDuration / 60
                val seconds = workoutDuration % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // Workout Recording Section - Only show when not recording
        if (!isRecording) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Workout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Button(
                                onClick = { viewModel.startWorkout() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Start Workout", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Upload Status Banner - Hide when recording to save space
        uploadStatus?.let { status ->
            if (!isRecording) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (status.contains("syncing", ignoreCase = true)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Strava Connection Section - Only show when not recording
        if (!isRecording) {
            item {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isStravaAuthenticated) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Strava Connection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isStravaAuthenticated) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (isStravaAuthenticated) {
                                        "Connected - workouts will auto-upload"
                                    } else {
                                        "Connect to automatically sync workouts"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isStravaAuthenticated) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                )
                            }

                            if (isStravaAuthenticated) {
                                OutlinedButton(
                                    onClick = { viewModel.disconnectStrava() },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Disconnect")
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.initiateStravaAuth() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Trainer Control Section - Compact when recording
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(if (isRecording) 10.dp else 16.dp)
                ) {
                    if (!isRecording) {
                        Text(
                            text = "Trainer Control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (!hasRequestedControl) {
                        Button(
                            onClick = {
                                viewModel.requestTrainerControl()
                                hasRequestedControl = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Request Trainer Control")
                        }
                    } else {
                        // Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (isRecording) 4.dp else 8.dp)
                        ) {
                            FilterChip(
                                selected = controlMode == TrainerControlMode.RESISTANCE,
                                onClick = { controlMode = TrainerControlMode.RESISTANCE },
                                label = { Text("Resistance", style = if (isRecording) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = controlMode == TrainerControlMode.POWER,
                                onClick = { controlMode = TrainerControlMode.POWER },
                                label = { Text("ERG Mode", style = if (isRecording) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isRecording) 6.dp else 12.dp))

                        when (controlMode) {
                            TrainerControlMode.RESISTANCE -> {
                                if (!isRecording) {
                                    Text(
                                        text = "Manual resistance control - you control power by pedaling harder or softer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Text(
                                    text = "Resistance Level: ${resistanceLevel.toInt()}",
                                    style = if (isRecording) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )

                                Slider(
                                    value = resistanceLevel,
                                    onValueChange = { resistanceLevel = it },
                                    valueRange = 0f..100f,
                                    onValueChangeFinished = {
                                        viewModel.setTargetResistanceLevel(resistanceLevel.toInt())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            TrainerControlMode.POWER -> {
                                if (!isRecording) {
                                    Text(
                                        text = "ERG mode - trainer auto-adjusts resistance to maintain your target wattage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Text(
                                    text = "Target Power: ${targetPower.toInt()} watts",
                                    style = if (isRecording) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )

                                Slider(
                                    value = targetPower,
                                    onValueChange = { targetPower = it },
                                    valueRange = 0f..500f,
                                    onValueChangeFinished = {
                                        viewModel.setTargetPower(targetPower.toInt())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (!isRecording) {
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.resetTrainer() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Text("Reset Trainer")
                            }
                        }
                    }
                }
            }
        }

        // Primary Metrics Section
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 16.dp))

            Text(
                text = "Primary Metrics",
                style = if (isRecording) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            MetricCard(
                title = if (heartRateData == null) "Heart Rate (no monitor)" else "Heart Rate",
                value = heartRateData?.heartRate?.toString() ?: "--",
                unit = "bpm",
                icon = Icons.Default.Favorite,
                isCompact = isRecording
            )
        }

        // HR Monitor Reconnect Button - Hide when recording to save space
        if (heartRateData == null && viewModel.hasLastHeartRateDevice() && !isRecording) {
            item {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.reconnectHeartRateMonitor() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reconnect HR Monitor")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 6.dp else 12.dp))

            MetricCard(
                title = "Power",
                value = trainerData?.power?.toString() ?: "--",
                unit = "watts",
                icon = Icons.Default.DirectionsBike,
                isCompact = isRecording
            )
        }

        // Speed and Cadence Row
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 6.dp else 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Speed",
                    value = trainerData?.speed?.let { "%.1f".format(it * 0.621371) } ?: "--",
                    unit = "mph",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Cadence",
                    value = trainerData?.cadence?.let { "%.0f".format(it) } ?: "--",
                    unit = "rpm",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        // Additional Metrics Section
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 16.dp))

            Text(
                text = "Additional Metrics",
                style = if (isRecording) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Avg Speed",
                    value = if (isRecording) {
                        liveStats.averageSpeed?.let { "%.1f".format(it * 0.621371) } ?: "--"
                    } else {
                        trainerData?.averageSpeed?.let { "%.1f".format(it * 0.621371) } ?: "--"
                    },
                    unit = "mph",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Avg Cadence",
                    value = if (isRecording) {
                        liveStats.averageCadence?.let { "%.0f".format(it) } ?: "--"
                    } else {
                        trainerData?.averageCadence?.let { "%.0f".format(it) } ?: "--"
                    },
                    unit = "rpm",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Avg Power",
                    value = if (isRecording) {
                        liveStats.averagePower?.toString() ?: "--"
                    } else {
                        trainerData?.averagePower?.toString() ?: "--"
                    },
                    unit = "watts",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Resistance",
                    value = trainerData?.resistanceLevel?.toString() ?: "--",
                    unit = "level",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Distance",
                    value = if (isRecording) {
                        "%.2f".format(liveStats.distance / 1000.0 * 0.621371)
                    } else {
                        trainerData?.distance?.let { "%.2f".format(it / 1000.0 * 0.621371) } ?: "--"
                    },
                    unit = "mi",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Time",
                    value = if (isRecording) {
                        val minutes = liveStats.elapsedTime / 60
                        val seconds = liveStats.elapsedTime % 60
                        "%d:%02d".format(minutes, seconds)
                    } else {
                        trainerData?.elapsedTime?.let {
                            val minutes = it / 60
                            val seconds = it % 60
                            "%d:%02d".format(minutes, seconds)
                        } ?: "--"
                    },
                    unit = "min",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        // Energy Metrics Section
        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 16.dp))

            Text(
                text = "Energy & Calories",
                style = if (isRecording) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Total Calories",
                    value = if (isRecording) {
                        liveStats.calories?.toString() ?: "--"
                    } else {
                        trainerData?.totalEnergy?.toString() ?: "--"
                    },
                    unit = "kcal",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Avg HR",
                    value = if (isRecording) {
                        liveStats.averageHeartRate?.toString() ?: "--"
                    } else {
                        "--"
                    },
                    unit = "bpm",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 4.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isRecording) 8.dp else 12.dp)
            ) {
                SmallMetricCard(
                    title = "Max Power",
                    value = if (isRecording) {
                        liveStats.maxPower?.toString() ?: "--"
                    } else {
                        "--"
                    },
                    unit = "watts",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )

                SmallMetricCard(
                    title = "Max HR",
                    value = if (isRecording) {
                        liveStats.maxHeartRate?.toString() ?: "--"
                    } else {
                        "--"
                    },
                    unit = "bpm",
                    modifier = Modifier.weight(1f),
                    isCompact = isRecording
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(if (isRecording) 8.dp else 24.dp))
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCompact: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 12.dp else 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (isCompact) 32.dp else 48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.width(if (isCompact) 12.dp else 24.dp))

            Column {
                Text(
                    text = title,
                    style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        style = if (isCompact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(if (isCompact) 4.dp else 8.dp))

                    Text(
                        text = unit,
                        style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = if (isCompact) 2.dp else 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SmallMetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompact) 10.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = if (isCompact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.width(if (isCompact) 2.dp else 4.dp))

                Text(
                    text = unit,
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = if (isCompact) 2.dp else 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedWorkoutScreen(
    workout: com.biketrainer.app.data.workout.Workout,
    isStravaAuthenticated: Boolean,
    onConnectStrava: () -> Unit,
    onUploadToStrava: () -> Unit,
    uploadStatus: String?,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Complete") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Great job!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                val minutes = workout.durationSeconds / 60
                val seconds = workout.durationSeconds % 60
                Text(
                    text = String.format("Duration: %02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Workout Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Heart Rate Summary
            if (workout.averageHeartRate != null || workout.maxHeartRate != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Heart Rate",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                workout.averageHeartRate?.let { avgHr ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Average",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "$avgHr bpm",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                workout.maxHeartRate?.let { maxHr ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Maximum",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "$maxHr bpm",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Power Summary
            if (workout.averagePower != null || workout.maxPower != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Power",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                workout.averagePower?.let { avgPower ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Average",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "$avgPower W",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                workout.maxPower?.let { maxPower ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Maximum",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "$maxPower W",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Additional Metrics Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Distance
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Distance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.2f mi".format(workout.totalDistance / 1000 * 0.621371),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    // Calories
                    workout.calories?.let { calories ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Calories",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$calories",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Cadence (if available)
            workout.averageCadence?.let { avgCadence ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Average Cadence:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "%.0f rpm".format(avgCadence),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Strava Upload Status
            uploadStatus?.let { status ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (status.contains("success", ignoreCase = true)) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else if (status.contains("fail", ignoreCase = true) || status.contains("error", ignoreCase = true)) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (status.contains("uploading", ignoreCase = true)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } else if (status.contains("success", ignoreCase = true)) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Action Buttons
            item {
                if (!isStravaAuthenticated) {
                    // Show "Connect to Strava" button
                    Button(
                        onClick = onConnectStrava,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect to Strava")
                    }
                } else if (uploadStatus == null || uploadStatus.contains("failed", ignoreCase = true)) {
                    // Show "Upload to Strava" button if authenticated and not uploaded/failed
                    Button(
                        onClick = onUploadToStrava,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload to Strava")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
