package com.biketrainer.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biketrainer.app.data.ble.ConnectionState
import com.biketrainer.app.ui.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bike Trainer") },
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
                MainContent(viewModel)
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

    val bothConnected = heartRateConnectionState == ConnectionState.CONNECTED &&
                       trainerConnectionState == ConnectionState.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!bothConnected) {
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
                        label = "Heart Rate Monitor",
                        state = heartRateConnectionState
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ConnectionStatusRow(
                        label = "Bike Trainer",
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
                onDisconnect = { viewModel.disconnectAll() }
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

@Composable
fun MetricsDisplay(
    heartRateData: com.biketrainer.app.data.ble.HeartRateData?,
    trainerData: com.biketrainer.app.data.ble.TrainerData?,
    onDisconnect: () -> Unit
) {
    val viewModel: MainViewModel = hiltViewModel()
    var resistanceLevel by remember { mutableStateOf(50f) }
    var targetPower by remember { mutableStateOf(150f) }
    var hasRequestedControl by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Metrics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Disconnect")
                }
            }
        }

        // Trainer Control Section
        item {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Trainer Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                        // Resistance Control
                        Text(
                            text = "Resistance Level: ${resistanceLevel.toInt()}",
                            style = MaterialTheme.typography.bodyMedium,
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Power Target Control
                        Text(
                            text = "Target Power: ${targetPower.toInt()} watts",
                            style = MaterialTheme.typography.bodyMedium,
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

        // Primary Metrics Section
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Primary Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            MetricCard(
                title = "Heart Rate",
                value = heartRateData?.heartRate?.toString() ?: "--",
                unit = "bpm",
                icon = Icons.Default.Favorite
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))

            MetricCard(
                title = "Power",
                value = trainerData?.power?.toString() ?: "--",
                unit = "watts",
                icon = Icons.Default.DirectionsBike
            )
        }

        // Speed and Cadence Row
        item {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Speed",
                    value = trainerData?.speed?.let { "%.1f".format(it) } ?: "--",
                    unit = "km/h",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "Cadence",
                    value = trainerData?.cadence?.let { "%.0f".format(it) } ?: "--",
                    unit = "rpm",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Additional Metrics Section
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Additional Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Avg Speed",
                    value = trainerData?.averageSpeed?.let { "%.1f".format(it) } ?: "--",
                    unit = "km/h",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "Avg Cadence",
                    value = trainerData?.averageCadence?.let { "%.0f".format(it) } ?: "--",
                    unit = "rpm",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Avg Power",
                    value = trainerData?.averagePower?.toString() ?: "--",
                    unit = "watts",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "Resistance",
                    value = trainerData?.resistanceLevel?.toString() ?: "--",
                    unit = "level",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Distance",
                    value = trainerData?.distance?.let { "%.2f".format(it / 1000.0) } ?: "--",
                    unit = "km",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "Time",
                    value = trainerData?.elapsedTime?.let {
                        val minutes = it / 60
                        val seconds = it % 60
                        "%d:%02d".format(minutes, seconds)
                    } ?: "--",
                    unit = "min",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Energy Metrics Section
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Energy & Calories",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Total Energy",
                    value = trainerData?.totalEnergy?.toString() ?: "--",
                    unit = "kcal",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "Energy/Hour",
                    value = trainerData?.energyPerHour?.toString() ?: "--",
                    unit = "kcal/h",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallMetricCard(
                    title = "Energy/Min",
                    value = trainerData?.energyPerMinute?.toString() ?: "--",
                    unit = "kcal/m",
                    modifier = Modifier.weight(1f)
                )

                SmallMetricCard(
                    title = "MET",
                    value = trainerData?.metabolicEquivalent?.let { "%.1f".format(it) } ?: "--",
                    unit = "",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 4.dp)
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
    modifier: Modifier = Modifier
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
