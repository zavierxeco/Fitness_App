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
//import androidx.compose.material3.value
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

data class SleepRecord(
    val date: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val sleepTime: String,
    val sleepQuality: Int,
    val durationMinutes: Int,
    val deepSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0
)

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
    val sleepData = MutableStateFlow<SleepRecord?>(null)
    val sleepHistory = MutableStateFlow<List<SleepRecord>>(emptyList())
    val spo2 = MutableStateFlow(0)

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

    private var last105Time = 0L
    private var lastDataArray = intArrayOf()
    private var lastResponseCount = 0

    suspend fun waitFor105Response(timeoutMs: Long = 10000): Boolean {
        val startTime = System.currentTimeMillis()
        val startCount = lastResponseCount
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (lastResponseCount > startCount) {
                // New response received, check if it has non-zero at index 3
                if (lastDataArray.isNotEmpty() &&
                    lastDataArray[0] == 105 &&
                    lastDataArray.size > 3 &&
                    lastDataArray[3] != 0) {
                    return true
                }
            }
            delay(100)
        }
        return false
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
                val rawData = char.value
                val data = rawData.map { it.toInt() and 0xFF }.toIntArray()
                DebugLogger.addLog("Data received: ${data.joinToString { it.toString() }}")

                if (data.isEmpty()) return

                val commandId = data[0]
                //val commandId = rawCommandId and 0x7F  // Remove error flag
                //val hasError = (rawCommandId and 0x80) != 0  // Check error flag

                //DebugLogger.addLog("Command ID: $commandId, Has Error: $hasError")

                if (commandId == 3) {
                    val batteryPercent = data[1].toInt()
                    val charging = data[2].toInt() == 1
                    battery.value = batteryPercent
                    isCharging.value = charging
                    //DebugLogger.addLog("Battery: $batteryPercent%, Charging: $charging")
                }

                if (commandId == 105) {
                    last105Time = System.currentTimeMillis()  // Add this line
                    val dataType = data[1].toInt()
                    lastDataArray = data  // Store for wait function
                    lastResponseCount++

                    when (dataType) {
                        6 -> {  // Heart Rate (realtime)
                            val bpmValue = data[3]
                            if (bpmValue in 30..200) {
                                bpm.value = bpmValue
                                DebugLogger.addLog("BPM: $bpmValue")
                            }
                        }
                        3 -> {  // SpO2 (from Flutter code: HeartSpO2StressSubtype.spO2)
                            val spo2Value = data[3]
                            if (spo2Value in 70..100) {
                                spo2.value = spo2Value
                                DebugLogger.addLog("SpO2: $spo2Value%")
                            }
                        }
                        2 -> {  // Stress (from Flutter code: HeartSpO2StressSubtype.stress)
                            val stressValue = data[3]
                            if (stressValue in 0..100) {
                                // stress.value = stressValue (if you add stress)
                                DebugLogger.addLog("Stress: $stressValue")
                            }
                        }
                    }
                }

                if (commandId == 0xBC) {  // Sleep data response
                    val subType = data[1].toInt()
                    DebugLogger.addLog("Sleep data response, subtype: $subType")

                    if (subType == 0x27) {
                        // Parse sleep record
                        val year = 2000 + data[2].toInt()
                        val month = data[3].toInt()
                        val day = data[4].toInt()
                        val sleepHour = data[5].toInt()
                        val sleepMinute = data[6].toInt()
                        val wakeHour = data[7].toInt()
                        val wakeMinute = data[8].toInt()
                        val quality = data[9].toInt()  // 0-100
                        val duration = data[10].toInt()  // Minutes

                        val sleepTime = String.format("%02d:%02d", sleepHour, sleepMinute)
                        val wakeTime = String.format("%02d:%02d", wakeHour, wakeMinute)

                        val record = SleepRecord(
                            date = "$year-$month-$day",
                            year = year,
                            month = month,
                            day = day,
                            sleepTime = sleepTime,
                            sleepQuality = quality,
                            durationMinutes = duration
                        )

                        sleepData.value = record

                        // Add to history
                        val currentHistory = sleepHistory.value.toMutableList()
                        currentHistory.add(0, record)  // Add to front
                        if (currentHistory.size > 7) currentHistory.removeAt(currentHistory.size - 1)  // Keep last 7
                        sleepHistory.value = currentHistory

                        DebugLogger.addLog("Sleep: $year-$month-$day, Sleep: $sleepTime, Wake: $wakeTime, Quality: $quality, Duration: ${duration}min")
                    }
                }
            }
        })
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
    fun getBattery() {
        //DebugLogger.addLog("getBattery() called")
        val command = ByteArray(16)
        command[0] = 3
        command[15] = 3  // CRC = 3 (since commandId=3, all data=0)
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
    fun measureSpO2() {
        DebugLogger.addLog("measureSpO2() called")
        val command = ByteArray(16)
        command[0] = 105  // Data Request
        command[1] = 3    // DataType = SpO2 (from Flutter: requestSpO2 is 6903)
        command[2] = 1    // Start measurement
        command[15] = 107 // CRC (105+1+1=107)
        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestDeviceNotify() {
        DebugLogger.addLog("requestDeviceNotify() called - testing ID 115")
        val command = ByteArray(16)
        command[0] = 115  // Device Notify command

        // Set dataType = 0 to request all? Or specific type
        // According to documentation, this might be a request for device notifications
        command[1] = 5    // Request all notifications

        // Calculate CRC
        var sum = 0
        for (i in 0 until 15) {
            sum += command[i].toInt() and 0xFF
        }
        command[15] = (sum and 0xFF).toByte()

        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun syncHistoricalSleep() {
        DebugLogger.addLog("syncHistoricalSleep() called - ID BC27")
        val command = ByteArray(16)
        command[0] = 0xBC.toByte()  // 188 decimal
        command[1] = 0x27.toByte()  // 39 decimal
        // Bytes 2-14 unused
        // Calculate CRC
        var sum = 0
        for (i in 0 until 15) {
            sum += command[i].toInt() and 0xFF
        }
        command[15] = (sum and 0xFF).toByte()
        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }
}

@Composable
fun BatterySection(manager: SimpleRingManager) {
    val battery by manager.battery.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (battery) {
                in 0..15 -> Color(0x33FF0000)
                in 16..50 -> Color(0x33FFA500)
                else -> Color(0x3300FF00)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Battery Level", fontSize = 16.sp, color = Color.Gray)
                Text(
                    text = "$battery%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (battery) {
                        in 0..15 -> Color.Red
                        in 16..50 -> Color(0xFFFFA500)
                        else -> Color(0xFF4CAF50)
                    }
                )
            }
            Button(
                onClick = { manager.getBattery() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Text("Check Battery")
            }
        }
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

@Composable
fun SpO2Card(spo2: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x332196F3)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Blood Oxygen", fontSize = 16.sp, color = Color.Gray)
                Text(
                    text = if (spo2 > 0) "$spo2%" else "--",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (spo2) {
                        in 95..100 -> Color(0xFF4CAF50)
                        in 90..94 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
            }
            Text("💧", fontSize = 40.sp)
        }
    }
}

@Composable
fun SleepCard(sleepRecord: SleepRecord?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x339C27B0)
        )
    ) {
        if (sleepRecord == null) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("😴", fontSize = 32.sp)
                Text("No sleep data yet", color = Color.Gray)
                Text("Sync after waking up", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sleep", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        sleepRecord.date,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Duration and Quality
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏱️", fontSize = 24.sp)
                        Text(
                            "${sleepRecord.durationMinutes / 60}h ${sleepRecord.durationMinutes % 60}m",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Duration", fontSize = 12.sp, color = Color.Gray)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 24.sp)
                        Text(
                            "${sleepRecord.sleepQuality}%",
                            fontWeight = FontWeight.Bold,
                            color = when (sleepRecord.sleepQuality) {
                                in 80..100 -> Color(0xFF4CAF50)
                                in 60..79 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                        Text("Quality", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sleep and Wake times
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🌙 Bedtime", fontSize = 12.sp, color = Color.Gray)
                        Text(sleepRecord.sleepTime, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("☀️ Wake up", fontSize = 12.sp, color = Color.Gray)
                        Text("${(sleepRecord.durationMinutes / 60 + sleepRecord.sleepTime.substring(0,2).toInt()) % 24}:${sleepRecord.sleepTime.substring(3)}",
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SleepHistoryCard(sleepHistory: List<SleepRecord>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x339C27B0)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("📊 Sleep History", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (sleepHistory.isEmpty()) {
                Text("No sleep history yet", fontSize = 12.sp, color = Color.Gray)
            } else {
                sleepHistory.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(record.date, fontSize = 12.sp)
                        Text("${record.durationMinutes / 60}h${record.durationMinutes % 60}m", fontSize = 12.sp)
                        Text("${record.sleepQuality}%", fontSize = 12.sp,
                            color = when (record.sleepQuality) {
                                in 80..100 -> Color(0xFF4CAF50)
                                in 60..79 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackingScreen(ringViewModel: RingViewModel) {
    val manager = ringViewModel.manager  // Use this instead of creating new manager
    val context = LocalContext.current
    val connected by manager.isConnected.collectAsStateWithLifecycle()
    val discoveredDevices by manager.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    var showDeviceList by remember { mutableStateOf(true) }
    val isConnecting by manager.isConnecting.collectAsStateWithLifecycle()
    val battery by manager.battery.collectAsStateWithLifecycle()
    val bpm by manager.bpm.collectAsStateWithLifecycle()
    val spo2 by manager.spo2.collectAsStateWithLifecycle()
    val sleepRecord by manager.sleepData.collectAsStateWithLifecycle()
    val sleepHistory by manager.sleepHistory.collectAsStateWithLifecycle()
    //DebugLogger.addLog("TrackingScreen() called")

    // Add this LaunchedEffect to check connection when screen appears
    LaunchedEffect(connected) {
        // If already connected, don't show scan UI
        if (connected) {
            showDeviceList = false
        }
        delay(3000)
        manager.getBattery()
        delay(3000)
//        delay(3000)
//        //manager.requestDeviceNotify()
//        manager.measureSpO2()
//        delay(3000)
        //manager.enableHeartRate()
        //manager.syncHistoricalSleep()
        while(connected){
            delay(2000)
            manager.getBattery()
            DebugLogger.addLog("Battery: ${manager.battery.value}%")
            delay(2000)
            // 2. Get Heart Rate - wait for ID 105 response
            manager.enableHeartRate()
            DebugLogger.addLog("Waiting for BPM (ID 105)...")
            if (manager.waitFor105Response(40000)) {
                DebugLogger.addLog("BPM: ${manager.bpm.value}")
            }
            else{
                DebugLogger.addLog("BPM: skip")
            }
            // 3. Get SpO2 - wait for ID 105 response
            delay(2000)
            manager.measureSpO2()
            DebugLogger.addLog("Waiting for SpO2 (ID 105)...")
            if (manager.waitFor105Response(40000)) {
                DebugLogger.addLog("SpO2: ${manager.spo2.value}%")
            }
            else{
                DebugLogger.addLog("SpO2: skip")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (connected) {
            // Battery Section
            BatterySection(manager)
            // BPM Display
            BPMDisplay(bpm = bpm)
            SpO2Card(spo2 = spo2)  // Add this
            SleepCard(sleepRecord)
            SleepHistoryCard(sleepHistory)
            // Connected UI
            Button(onClick = { manager.disconnect() }) {
                Text("Disconnect")
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