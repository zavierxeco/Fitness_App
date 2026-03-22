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
    private val bluetoothAdapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var scanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var scanCallback: ScanCallback? = null

    val isConnected = MutableStateFlow(false)
    val bpm = MutableStateFlow(0)
    val discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val isScanning = MutableStateFlow(false)

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
        scanCallback?.let {
            scanner?.stopScan(it)
        }
        isScanning.value = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        stopScan()
        gatt = device.connectGatt(appContext, false, object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                isConnected.value = newState == BluetoothProfile.STATE_CONNECTED
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
                if (char.value[0].toInt() == 30) {
                    bpm.value = char.value[1].toInt()
                }
            }
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected.value = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startBPM() {
        val command = ByteArray(16)
        command[0] = 30
        command[1] = 3
        command[15] = command.take(15).sumOf { it.toInt() }.toByte()
        writeChar?.value = command
        gatt?.writeCharacteristic(writeChar)
    }
}

@Composable
fun TrackingScreen() {
    val context = LocalContext.current
    val manager = remember { SimpleRingManager(context) }
    val connected by manager.isConnected.collectAsStateWithLifecycle()
    val bpm by manager.bpm.collectAsStateWithLifecycle()
    val discoveredDevices by manager.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by manager.isScanning.collectAsStateWithLifecycle()
    var showDeviceList by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // BPM Display
        Text(
            text = if (bpm > 0) "$bpm" else "--",
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold,
            color = if (bpm > 0) Color.Red else Color.Gray
        )
        Text("BPM", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(32.dp))

        if (connected) {
            // Connected UI
            Button(onClick = { manager.disconnect() }) {
                Text("Disconnect")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { manager.startBPM() }) {
                Text("Start BPM")
            }
        } else {
            // Scan UI
            Button(
                onClick = {
                    if (isScanning) {
                        manager.stopScan()
                    } else {
                        manager.startScan()
                        showDeviceList = true
                    }
                }
            ) {
                Text(if (isScanning) "Stop Scanning" else "Scan for Rings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show discovered devices
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
                        onClick = { manager.connect(device) }
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
                        }
                    }
                }
            } else if (showDeviceList && !isScanning) {
                Text("No rings found. Make sure your ring is awake and try again.")
            }
        }
    }
}