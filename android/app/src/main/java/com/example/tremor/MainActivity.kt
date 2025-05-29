package com.example.tremor

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    companion object { private const val TAG = "MainActivity" }

    private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHAR_UUID_TASK: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private var bluetoothGatt: BluetoothGatt? = null
    private var taskChar: BluetoothGattCharacteristic? = null

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val bleScanner: BluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> perms.forEach { (p, g) -> Log.d(TAG, "Permission $p granted=$g") } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()

        setContent {
            var isScanning by remember { mutableStateOf(false) }
            var target by remember { mutableStateOf(Offset.Zero) }
            var touchCount by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                if (!isScanning) {
                    startScan()
                    isScanning = true
                    delay(10000)
                    stopScan()
                    isScanning = false
                }
            }

            Surface(modifier = Modifier.fillMaxSize()) {
                TouchArea(target = target,
                    onTargetUpdate = { target = it },
                    onTouchData = { offset ->
                        logTouch(target, offset, touchCount)
                        touchCount++
                    },
                    onCommand = { cmd -> writeTaskCommand(cmd) })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) return
        bluetoothGatt?.close()
    }

    private fun requestBlePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "Device found: ${'$'}{device.address}")
            if (taskChar == null) {
                stopScan(); connectDevice(device)
            }
        }
        override fun onScanFailed(code: Int) { Log.e(TAG, "Scan failed: ${'$'}code") }
    }

    private fun startScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "SCAN perm not granted"); return }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bleScanner.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "SCAN perm not granted"); return }
        bleScanner.stopScan(scanCallback)
    }

    private fun connectDevice(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "CONNECT perm not granted"); return }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) return
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected")
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                taskChar = service?.getCharacteristic(CHAR_UUID_TASK)
            }
        }
    }

    private fun writeTaskCommand(value: Byte) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "CONNECT perm not granted"); return }
        val ascii = if (value == 2.toByte()) '2'.code.toByte() else '0'.code.toByte()
        taskChar?.let {
            it.value = byteArrayOf(ascii)
            bluetoothGatt?.writeCharacteristic(it)
        } ?: Log.w(TAG, "Characteristic not ready")
    }

    private fun logTouch(target: Offset, touch: Offset, index: Int) {
        val dir = File(filesDir, "touch")
        if (!dir.exists()) dir.mkdirs()
        val date = SimpleDateFormat("yy_MM_dd", Locale.US).format(Date())
        val file = File(dir, "${'$'}date_touch${'$'}index.txt")
        val delta = touch - target
        file.writeText("absolute=${'$'}touch target=${'$'}target delta=${'$'}delta")
    }
}

@Composable
fun TouchArea(
    target: Offset,
    onTargetUpdate: (Offset) -> Unit,
    onTouchData: (Offset) -> Unit,
    onCommand: (Byte) -> Unit
) {
    var pending by remember { mutableStateOf<Byte?>(null) }

    pending?.let { cmd ->
        LaunchedEffect(cmd) { onCommand(cmd); pending = null }
    }

    BoxWithConstraints(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onSizeChanged { size ->
            val center = Offset(size.width / 2f, size.height / 2f)
            onTargetUpdate(center)
        }
        .pointerInput(Unit) {
            detectTouches(target, onTargetUpdate, onTouchData, onCmd = { pending = it })
        }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.Red,
                radius = 60f,
                center = target,
                style = Stroke(width = 5f)
            )
            drawCircle(
                color = Color.Red,
                radius = 30f,
                center = target,
                style = Stroke(width = 5f)
            )
        }
    }
}

suspend fun PointerInputScope.detectTouches(
    target: Offset,
    onTargetUpdate: (Offset) -> Unit,
    onTouchData: (Offset) -> Unit,
    onCmd: (Byte) -> Unit
) {
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown()
            onCmd(1)
            onTouchData(down.position)
            var drag = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                if (change.changedToUp()) {
                    onTouchData(change.position)
                    onCmd(3)
                    break
                }
                val move = change.positionChange()
                if (move != Offset.Zero) {
                    if (!drag && (change.position - down.position).getDistance() > 10f) {
                        drag = true
                        onCmd(2)
                    }
                    onTouchData(change.position)
                }
                change.consume()
            }
        }
    }
}
