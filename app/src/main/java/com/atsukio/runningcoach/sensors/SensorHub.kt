package com.atsukio.runningcoach.sensors

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
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Connects to one Bluetooth heart-rate or running speed/cadence sensor, such as a Garmin watch
 * in its Virtual Run activity, and publishes its latest readings. The chosen device is remembered
 * and reconnected automatically. Readings are only kept in memory and in local session files.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call is behind ready() or hasPermissions().
class SensorHub private constructor(private val context: Context) {

    sealed interface Status {
        data object Unavailable : Status
        data object BluetoothOff : Status
        data object Disconnected : Status
        data object Scanning : Status
        data class Connecting(val name: String) : Status
        data class Connected(val name: String, val heartRate: Boolean, val runningData: Boolean) : Status
    }

    data class FoundDevice(val address: String, val name: String)

    private val prefs = context.getSharedPreferences("sensors", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow<Status>(Status.Disconnected)
    val status = _status.asStateFlow()
    private val _stats = MutableStateFlow<LiveStats?>(null)
    val stats = _stats.asStateFlow()
    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found = _found.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var wantConnected = false
    private var deviceName = ""
    private val pendingNotifications = ArrayDeque<BluetoothGattCharacteristic>()
    private val stopScanRunnable = Runnable { stopScan() }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** The latest readings, or null if the sensor has gone quiet for a few seconds. */
    fun freshStats(now: Long = SystemClock.elapsedRealtime()): LiveStats? =
        _stats.value?.takeIf { now - it.updatedAtMs <= STALE_AFTER_MS }

    fun startScan() {
        if (!ready()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filters = listOf(GattIds.HEART_RATE_SERVICE, GattIds.RSC_SERVICE).map {
            ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build()
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _found.value = emptyList()
        scanner.stopScan(scanCallback)
        scanner.startScan(filters, settings, scanCallback)
        if (_status.value == Status.Disconnected) _status.value = Status.Scanning
        handler.removeCallbacks(stopScanRunnable)
        handler.postDelayed(stopScanRunnable, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        if (hasPermissions()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_status.value == Status.Scanning) _status.value = Status.Disconnected
    }

    fun connect(device: FoundDevice) {
        stopScan()
        prefs.edit {
            putString(KEY_ADDRESS, device.address)
            putString(KEY_NAME, device.name)
        }
        open(device.address, device.name, autoConnect = false)
    }

    /** Reconnects to the remembered device, if there is one and nothing is connected. */
    fun reconnectSaved() {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        if (gatt != null || !ready()) return
        open(address, prefs.getString(KEY_NAME, null) ?: "Sensor", autoConnect = true)
    }

    fun disconnect(forget: Boolean) {
        wantConnected = false
        if (hasPermissions()) {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _stats.value = null
        if (forget) {
            prefs.edit {
                remove(KEY_ADDRESS)
                remove(KEY_NAME)
            }
        }
        _status.value = Status.Disconnected
    }

    private fun ready(): Boolean {
        if (!hasPermissions()) return false
        val bluetooth = adapter
        if (bluetooth == null) {
            _status.value = Status.Unavailable
            return false
        }
        if (!bluetooth.isEnabled) {
            _status.value = Status.BluetoothOff
            return false
        }
        if (_status.value == Status.BluetoothOff || _status.value == Status.Unavailable) {
            _status.value = Status.Disconnected
        }
        return true
    }

    private fun open(address: String, name: String, autoConnect: Boolean) {
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        gatt?.close()
        wantConnected = true
        deviceName = name
        _status.value = Status.Connecting(name)
        gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: "Unnamed sensor"
            val device = FoundDevice(result.device.address, name)
            _found.update { list ->
                if (list.any { it.address == device.address }) list else list + device
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _stats.value = null
                    g.close()
                    if (wantConnected && g == gatt) {
                        // Keep trying in the background, e.g. while the watch's activity is restarted.
                        val address = g.device.address
                        handler.post { if (wantConnected) open(address, deviceName, autoConnect = true) }
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val heartRate = g.getService(GattIds.HEART_RATE_SERVICE)?.getCharacteristic(GattIds.HEART_RATE_MEASUREMENT)
            val running = g.getService(GattIds.RSC_SERVICE)?.getCharacteristic(GattIds.RSC_MEASUREMENT)
            _status.value = Status.Connected(deviceName, heartRate != null, running != null)
            pendingNotifications.clear()
            pendingNotifications.addAll(listOfNotNull(heartRate, running))
            enableNextNotification(g)
        }

        // GATT allows one write at a time, so notifications are switched on one after another.
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            enableNextNotification(g)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            handle(c.uuid, value)
        }

        @Deprecated("Called instead of the ByteArray overload before Android 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) handle(c.uuid, c.value ?: return)
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNextNotification(g: BluetoothGatt) {
        val characteristic = pendingNotifications.removeFirstOrNull() ?: return
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(GattIds.CLIENT_CONFIG) ?: return enableNextNotification(g)
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value)
        } else {
            descriptor.value = value
            g.writeDescriptor(descriptor)
        }
    }

    private fun handle(uuid: UUID, value: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        val current = _stats.value ?: LiveStats()
        _stats.value = when (uuid) {
            GattIds.HEART_RATE_MEASUREMENT -> {
                val heartRate = GattParsers.heartRate(value) ?: return
                current.copy(heartRate = heartRate, updatedAtMs = now)
            }
            GattIds.RSC_MEASUREMENT -> {
                val rsc = GattParsers.rsc(value) ?: return
                current.copy(speedMps = rsc.speedMps, cadenceSpm = rsc.cadenceSpm, updatedAtMs = now)
            }
            else -> return
        }
    }

    companion object {
        const val STALE_AFTER_MS = 5_000L
        private const val SCAN_TIMEOUT_MS = 20_000L
        private const val KEY_ADDRESS = "device_address"
        private const val KEY_NAME = "device_name"

        @Volatile
        private var instance: SensorHub? = null

        fun get(context: Context): SensorHub = instance ?: synchronized(this) {
            instance ?: SensorHub(context.applicationContext).also { instance = it }
        }

        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
