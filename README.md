# Bike Trainer App

An Android application for connecting to Wahoo Kicker Core bike trainers and Wahoo Tickr heart rate monitors via Bluetooth Low Energy (BLE).

## Features

### Device Connectivity
- Scan for BLE devices (heart rate monitors and bike trainers)
- Connect to Wahoo Tickr heart rate monitor
- Connect to Wahoo Kicker Core bike trainer

### Real-time Metrics Display
- **Primary Metrics:**
  - Heart rate (bpm)
  - Instantaneous power (watts)
  - Instantaneous speed (km/h)
  - Instantaneous cadence (rpm)

- **Additional Metrics:**
  - Average speed (km/h)
  - Average cadence (rpm)
  - Average power (watts)
  - Current resistance level
  - Total distance (km)
  - Elapsed time (mm:ss)

- **Energy & Calories:**
  - Total energy expended (kcal)
  - Energy per hour (kcal/h)
  - Energy per minute (kcal/m)
  - Metabolic Equivalent (MET)

### Trainer Control
- **Request Control** - Obtain control authority over the trainer
- **Resistance Control** - Adjust resistance level (0-100) in real-time via slider
- **Power Target Control** - Set target power output (0-500 watts) via slider
- **Reset Trainer** - Reset trainer to default settings

### Workout Recording & Export
- **Start/Stop Workout** - Record your training sessions with one tap
- **Real-time Duration Tracking** - See workout timer update every second
- **Automatic Data Collection** - Records all metrics every second during workout:
  - Heart rate throughout session
  - Power output
  - Speed and cadence
  - Distance traveled
- **TCX File Export** - Saves workouts in TCX format (Training Center XML)
- **Strava Compatible** - TCX files can be uploaded directly to Strava
- **Local Storage** - Workouts saved to device for later upload
- **Workout Statistics** - Calculates:
  - Total distance
  - Average & maximum heart rate
  - Average & maximum power
  - Average cadence
  - Estimated calories burned

## Technical Details

### Architecture

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Manual (ViewModelFactory pattern)
- **Async Operations**: Kotlin Coroutines & StateFlow
- **Data Persistence**: Local file storage (TCX format)

### BLE Protocols

- **Heart Rate Monitor**: Implements the standard Heart Rate Service (HRS) - UUID 0x180D
  - Heart Rate Measurement characteristic (UUID 0x2A37)

- **Bike Trainer**: Implements the Fitness Machine Service (FTMS) - UUID 0x1826
  - Indoor Bike Data characteristic (UUID 0x2AD2) - Receives all metrics
  - Fitness Machine Control Point characteristic (UUID 0x2AD9) - Sends control commands
  - Supports full FTMS specification including:
    - Speed, cadence, power, distance, resistance level
    - Energy expenditure and metabolic data
    - Elapsed time tracking
    - Resistance and power target control

## Requirements

- Android device with API level 26 (Android 8.0) or higher
- Bluetooth Low Energy (BLE) support
- Bluetooth permissions (automatically requested at runtime)

## Setup Instructions

### Prerequisites

1. Install [Android Studio](https://developer.android.com/studio) (latest version recommended)
2. Install Java Development Kit (JDK) 17 or higher
3. Enable Developer Mode on your Android device

### Building the App

1. Clone this repository:
   ```bash
   git clone <repository-url>
   cd Bike-Trainer-App
   ```

2. Open the project in Android Studio:
   - File → Open → Select the project directory

3. Sync Gradle files:
   - Android Studio should automatically prompt you to sync
   - Or click: File → Sync Project with Gradle Files

4. Build the project:
   - Build → Make Project
   - Or use the shortcut: Ctrl+F9 (Windows/Linux) or Cmd+F9 (Mac)

5. Run on your device:
   - Connect your Android device via USB
   - Enable USB debugging in Developer Options
   - Click Run → Run 'app'
   - Or use the shortcut: Shift+F10 (Windows/Linux) or Ctrl+R (Mac)

### Installing the APK

If you prefer to build and install the APK directly:

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

The APK will be located at: `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. **Grant Permissions**: On first launch, the app will request Bluetooth permissions. Grant these to proceed.

2. **Scan for Devices**:
   - Ensure your Wahoo devices are powered on and in pairing mode
   - Tap "Start Scan" to search for nearby BLE devices

3. **Connect Devices**:
   - Once devices appear in the list, tap the appropriate button:
     - "Heart Rate" button for your Wahoo Tickr
     - "Trainer" button for your Wahoo Kicker Core
   - Wait for both devices to show "Connected" status

4. **View Metrics**:
   - Once both devices are connected, the app switches to the metrics view
   - All sensor data updates in real-time
   - Scroll down to see all available metrics organized by category:
     - Primary Metrics (heart rate, power, speed, cadence)
     - Additional Metrics (averages, resistance, distance, time)
     - Energy & Calories (total energy, energy rates, MET)

5. **Record Workouts**:
   - Tap "Start Workout" to begin recording your session
   - The card will turn red and show a live timer
   - All metrics are automatically recorded every second
   - Tap "Stop Workout" when finished
   - Workout is automatically saved as a TCX file

6. **Control Trainer** (Optional):
   - Tap "Request Trainer Control" to gain control authority
   - Once control is granted, you can:
     - Adjust resistance level using the slider (0-100)
     - Set target power using the slider (0-500 watts)
     - Reset the trainer to default settings
   - The trainer will adjust in real-time as you move the sliders

7. **Upload to Strava**:
   - Workouts are saved in `/data/data/com.biketrainer.app/files/workouts/`
   - Each workout is saved as `[workout-id].tcx`
   - Transfer TCX files to your computer or use a file manager app
   - Upload to Strava via their website or app
   - Strava automatically reads heart rate, power, cadence, and distance

8. **Disconnect**:
   - Tap the "Disconnect" button to disconnect from all devices
   - This returns you to the scanning screen

## Supported Devices

### Tested Devices

- Wahoo Kicker Core (bike trainer)
- Wahoo Tickr (heart rate monitor)

### Should Also Work With

Any BLE device that implements the standard protocols:
- Heart Rate Service (0x180D) - Most BLE heart rate monitors
- Fitness Machine Service (0x1826) - Most smart bike trainers

## Troubleshooting

### Devices Not Appearing

- Ensure Bluetooth is enabled on your phone
- Ensure devices are powered on and in pairing mode
- Unpair devices from phone's Bluetooth settings if previously paired
- Restart the devices
- Close and restart the app

### Connection Fails

- Move closer to the devices (within 2-3 meters)
- Check device battery levels
- Ensure devices aren't connected to other apps/devices
- Try turning Bluetooth off and on again

### Permission Issues

- Go to Settings → Apps → Bike Trainer → Permissions
- Ensure all Bluetooth and Location permissions are granted
- On Android 12+, you need BLUETOOTH_SCAN and BLUETOOTH_CONNECT
- On Android 11 and below, you need ACCESS_FINE_LOCATION

## Project Structure

```
app/src/main/java/com/biketrainer/app/
├── BikeTrainerApplication.kt          # Application class
├── MainActivity.kt                    # Main activity
├── data/
│   ├── ble/
│   │   └── BleManager.kt              # BLE connection & protocol handling
│   └── workout/
│       ├── WorkoutModels.kt           # Workout data models & TCX export
│       └── WorkoutManager.kt          # Workout recording & file management
├── ui/
│   ├── screen/
│   │   └── MainScreen.kt              # Main UI composables
│   ├── viewmodel/
│   │   └── MainViewModel.kt           # ViewModel for state management
│   └── theme/                         # Material 3 theme files
```

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues.

## License

This project is provided as-is for educational and personal use.

## Resources

- [Android Bluetooth Low Energy Documentation](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [Bluetooth GATT Services](https://www.bluetooth.com/specifications/gatt/services/)
- [FTMS Specification](https://www.bluetooth.com/specifications/specs/fitness-machine-service-1-0/)
- [Heart Rate Service Specification](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/)
