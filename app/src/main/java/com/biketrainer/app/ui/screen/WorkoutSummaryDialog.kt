package com.biketrainer.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biketrainer.app.data.workout.Workout

@Composable
fun WorkoutSummaryDialog(
    workout: Workout,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onUploadToStrava: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Workout Complete!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                SummaryRow(
                    label = "Duration",
                    value = formatDuration(workout.durationSeconds)
                )

                SummaryRow(
                    label = "Distance",
                    value = "%.2f km".format(workout.totalDistance / 1000.0)
                )

                workout.averageHeartRate?.let {
                    SummaryRow(
                        label = "Avg Heart Rate",
                        value = "$it bpm"
                    )
                }

                workout.maxHeartRate?.let {
                    SummaryRow(
                        label = "Max Heart Rate",
                        value = "$it bpm"
                    )
                }

                workout.averagePower?.let {
                    SummaryRow(
                        label = "Avg Power",
                        value = "$it watts"
                    )
                }

                workout.maxPower?.let {
                    SummaryRow(
                        label = "Max Power",
                        value = "$it watts"
                    )
                }

                workout.averageCadence?.let {
                    SummaryRow(
                        label = "Avg Cadence",
                        value = "%.0f rpm".format(it)
                    )
                }

                workout.calories?.let {
                    SummaryRow(
                        label = "Calories",
                        value = "$it kcal"
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }

                Button(
                    onClick = onUploadToStrava
                ) {
                    Text("Upload to Strava")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Close")
            }
        }
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
