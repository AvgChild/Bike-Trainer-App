package com.biketrainer.app.data.workout

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WorkoutSample(
    val timestamp: Long,  // Unix timestamp in milliseconds
    val heartRate: Int? = null,
    val power: Int? = null,
    val speed: Double? = null,  // km/h
    val cadence: Double? = null,  // rpm
    val distance: Double? = null  // meters
)

data class Workout(
    val id: String,
    val startTime: Long,  // Unix timestamp in milliseconds
    val endTime: Long,  // Unix timestamp in milliseconds
    val samples: List<WorkoutSample>,
    val totalDistance: Double,  // meters
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val averagePower: Int?,
    val maxPower: Int?,
    val averageCadence: Double?,
    val calories: Int?
) {
    val durationSeconds: Long
        get() = (endTime - startTime) / 1000

    val durationMinutes: Double
        get() = durationSeconds / 60.0

    fun toTCX(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"))

        val startTimeStr = formatter.format(Instant.ofEpochMilli(startTime))

        val trackpoints = samples.joinToString("\n") { sample ->
            val time = formatter.format(Instant.ofEpochMilli(sample.timestamp))
            val hr = sample.heartRate?.let {
                """
                <HeartRateBpm>
                  <Value>$it</Value>
                </HeartRateBpm>
                """.trimIndent()
            } ?: ""

            val power = sample.power?.let {
                """
                <Extensions>
                  <TPX xmlns="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
                    <Watts>$it</Watts>
                  </TPX>
                </Extensions>
                """.trimIndent()
            } ?: ""

            val cadence = sample.cadence?.toInt()?.let {
                "<Cadence>$it</Cadence>"
            } ?: ""

            val distance = sample.distance?.let {
                "<DistanceMeters>$it</DistanceMeters>"
            } ?: ""

            """
            <Trackpoint>
              <Time>$time</Time>
              $distance
              $hr
              $cadence
              $power
            </Trackpoint>
            """.trimIndent()
        }

        return """
<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
  <Activities>
    <Activity Sport="Biking">
      <Id>$startTimeStr</Id>
      <Lap StartTime="$startTimeStr">
        <TotalTimeSeconds>$durationSeconds</TotalTimeSeconds>
        <DistanceMeters>$totalDistance</DistanceMeters>
        <Calories>${calories ?: 0}</Calories>
        <Intensity>Active</Intensity>
        <TriggerMethod>Manual</TriggerMethod>
        <Track>
$trackpoints
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>
        """.trimIndent()
    }
}
