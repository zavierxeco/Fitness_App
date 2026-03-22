package com.example.basicnav

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.delay
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*

@Suppress("DEPRECATION")
class SimpleRingManager(private val appContext: Context) {
    val battery = MutableStateFlow(0)
    val isCharging = MutableStateFlow(false)
    private val bluetoothAdapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    val isConnected = MutableStateFlow(false)
    val bpm = MutableStateFlow(0)
    val discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val isScanning = MutableStateFlow(false)
    val isConnecting = MutableStateFlow(false)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getBattery() {
        //DebugLogger.addLog("getBattery() called")
        val command = ByteArray(16)
        command[0] = 3
        command[15] = 3  // CRC = 3 (since commandId=3, all data=0)
        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        DebugLogger.addLog("startScan() called")  // ADD THIS
        if (!hasPermission()) {
            DebugLogger.addLog("ERROR: Missing permissions for scan")  // ADD THIS
            return
        }

        discoveredDevices.value = emptyList()
        isScanning.value = true

        scanCallback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: ""

                // Look for QRing, R09, Colmi, or Q Ring
                if (name.contains("QRing", ignoreCase = true) ||
                    name.contains("R09", ignoreCase = true) ||
                    name.contains("Colmi", ignoreCase = true) ||
                    name.contains("Q Ring", ignoreCase = true)) {
                    val currentList = discoveredDevices.value.toMutableList()
                    if (!currentList.any { it.address == device.address }) {
                        currentList.add(device)
                        discoveredDevices.value = currentList
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning.value = false
            }
        }

        scanner?.startScan(scanCallback)
    }

    fun stopScan() {
        DebugLogger.addLog("stopscan() called")  // ADD THIS
        scanCallback?.let {
            scanner?.stopScan(it)
        }
        isScanning.value = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        if (isConnecting.value) {
            DebugLogger.addLog("connect() ignored - already connecting")
            return
        }
        DebugLogger.addLog("connect() called")  // ADD THIS
        isConnecting.value = true  // Show connecting message
        stopScan()
        gatt = device.connectGatt(appContext, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                isConnected.value = newState == BluetoothProfile.STATE_CONNECTED
                isConnecting.value = false  // Hide connecting message
                if (isConnected.value) gatt.discoverServices()
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e"))
                writeChar = service?.getCharacteristic(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))
                val notifyChar = service?.getCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))

                notifyChar?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
                val data = char.value
                DebugLogger.addLog("Data received: ${data.joinToString { it.toString() }}")

                if (data.isEmpty()) return

                val rawCommandId = data[0].toInt()
                val commandId = rawCommandId and 0x7F  // Remove error flag
                val hasError = (rawCommandId and 0x80) != 0  // Check error flag

                DebugLogger.addLog("Command ID: $commandId, Has Error: $hasError")

                if (commandId == 30) {
                    if (hasError) {
                        DebugLogger.addLog("Ring error: Not ready for BPM. Make sure ring is on finger and try again.")
                    } else {
//                        val bpmValue = data[1].toInt()
//                        if (bpmValue in 1..250) {
//                            bpm.value = bpmValue
//                            DebugLogger.addLog("BPM: $bpmValue")
//                        }
                    }
                    val bpmValue = data[1].toInt()
                    if (bpmValue in 1..250) {
                        bpm.value = bpmValue
                        DebugLogger.addLog("BPM: $bpmValue")
                    }
                }
                if (commandId == 3) {
                    val batteryPercent = data[1].toInt()
                    val charging = data[2].toInt() == 1
                    battery.value = batteryPercent
                    isCharging.value = charging
                    //DebugLogger.addLog("Battery: $batteryPercent%, Charging: $charging")
                }
            }
        })
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun enableHeartRateSettings() {
        val command = ByteArray(16)
        command[0] = 22   // Command ID
        command[1] = 1    // Action: Write
        command[2] = 1    // isEnabled: Yes
        command[3] = 1    // Interval: 5 minutes

        // Calculate CRC
        var sum = 0
        for (i in 0 until 15) {
            sum += command[i].toInt() and 0xFF
        }
        command[15] = (sum and 0xFF).toByte()

        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }

    fun enableHeartRate() {
        val command = ByteArray(16)
        command[0] = 105  // Data Request command
        command[1] = 6    // DataType.RealtimeHeartRate = 6
        command[2] = 1    // DataAction.Start = 1
        command[15] = 112
        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        DebugLogger.addLog("disconnect() called")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected.value = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startBPM() {
        DebugLogger.addLog("startBPM() called")
        val command = ByteArray(16)
        command[0] = 30  // Command ID for Realtime Heart Rate
        command[1] = 3   // Type = 3 (as per app)
        // bytes 2-14 are unused (already 0)
        command[15] = 33

        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }
}

// Separate BPM Display function
@Composable
fun BPMDisplay(bpm: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text =  "$bpm",
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = if (bpm > 0) Color.Red else Color.Gray
        )
        Text("BPM", fontSize = 24.sp)
    }
}

// Battery Display function
@Composable
fun BatteryDisplay(batteryLevel: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (batteryLevel) {
                in 0..15 -> Color(0xFFF44336) // Red - Critical
                in 16..50 -> Color(0xFFFF9800) // Orange - Low
                else -> Color(0xFF4CAF50) // Green - Good
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Battery",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$batteryLevel%",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TrackingScreen(ringViewModel: RingViewModel) {
    val manager = ringViewModel.manager  // Use this instead of creating new manager
    val context = LocalContext.current
    val connected by manager.isConnected.collectAsStateWithLifecycle()
    val bpm by manager.bpm.collectAsStateWithLifecycle()
    val discoveredDevices by manager.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    var showDeviceList by remember { mutableStateOf(true) }
    val isConnecting by manager.isConnecting.collectAsStateWithLifecycle()
    val battery by manager.battery.collectAsStateWithLifecycle()
    //DebugLogger.addLog("TrackingScreen() called")

    // Add this LaunchedEffect to check connection when screen appears
    LaunchedEffect(connected) {
        // If already connected, don't show scan UI
        if (connected) {
            showDeviceList = false
        }
        while(connected){
            delay(1000)
            manager.enableHeartRateSettings()
            delay(3000)
            manager.enableHeartRate()
            delay(3000)
            manager.getBattery()
            delay(2000)
            manager.startBPM()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (connected) {
            // Battery Level at the top
            BatteryDisplay(batteryLevel = battery)

            Spacer(modifier = Modifier.height(16.dp))

            // BPM Display
            BPMDisplay(bpm = bpm)

            Spacer(modifier = Modifier.height(32.dp))

            // Connected UI
            Button(onClick = { manager.disconnect() }) {
                Text("Disconnect")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { manager.startBPM() }) {
                Text("Start BPM")
            }
        } else {
            // Scan UI - separated into its own function
            ScanUI(
                isScanning = isScanning,
                isConnecting = isConnecting,
                showDeviceList = showDeviceList,
                discoveredDevices = discoveredDevices,
                onScanClick = {
                    if (isScanning) {
                        manager.stopScan()
                    } else {
                        manager.startScan()
                        showDeviceList = true
                    }
                },
                onDeviceClick = { device ->
                    manager.connect(device)
                }
            )
        }
    }
}

// Separate composable for scan UI
@Composable
fun ScanUI(
    isScanning: Boolean,
    isConnecting: Boolean,
    showDeviceList: Boolean,
    discoveredDevices: List<BluetoothDevice>,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onScanClick) {
            Text(if (isScanning) "Stop Scanning" else "Scan for Rings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showDeviceList && discoveredDevices.isNotEmpty()) {
            Text(
                text = "Found ${discoveredDevices.size} ring(s):",
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            discoveredDevices.forEach { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    onClick = { onDeviceClick(device) }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = device.name ?: "Unknown Device",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = device.address,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        if (isConnecting) {
                            Text(
                                text = "Connecting...",
                                fontSize = 12.sp,
                                color = Color.Blue
                            )
                        }
                    }
                }
            }
        } else if (showDeviceList && !isScanning) {
            Text("No rings found. Make sure your ring is awake and try again.")
        }
    }
}