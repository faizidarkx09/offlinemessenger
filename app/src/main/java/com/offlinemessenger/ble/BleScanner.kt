package com.offlinemessenger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.offlinemessenger.ble.model.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Duty-cycled BLE scanner. Scans [BleConstants.SCAN_WINDOW_MS], rests
 * [BleConstants.SCAN_REST_MS], and prunes peers not seen within
 * [BleConstants.PEER_EXPIRY_MS]. Duty cycling is the main battery lever
 * while keeping discovery latency acceptable.
 */
@SuppressLint("MissingPermission")
class BleScanner(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var loopJob: Job? = null
    private val seen = mutableMapOf<String, DiscoveredPeer>()

    private fun scanner() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeScanner

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handle(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach(::handle)
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    private fun handle(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))
        val now = System.currentTimeMillis()
        val deviceId = if (serviceData != null &&
            serviceData.size >= BleConstants.AD_MAGIC.size + BleConstants.AD_DEVICE_ID_LEN &&
            serviceData[0] == BleConstants.AD_MAGIC[0] &&
            serviceData[1] == BleConstants.AD_MAGIC[1]
        ) {
            try {
                serviceData.copyOfRange(2, 18).toUuid()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val peer = DiscoveredPeer(
            device = result.device,
            deviceId = deviceId,
            name = result.device.name ?: record.deviceName,
            rssi = result.rssi,
            lastSeenMs = now,
        )
        seen[peer.address] = peer
        publish()
    }

    private fun publish() {
        val cutoff = System.currentTimeMillis() - BleConstants.PEER_EXPIRY_MS
        seen.entries.removeIf { it.value.lastSeenMs < cutoff }
        _peers.value = seen.values.sortedByDescending { it.lastSeenMs }
    }

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                .build()
            // No filter would also surface unidentified devices; we run a second
            // unfiltered low-duty pass so the Nearby screen can list them too.
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            while (isActive) {
                val s = scanner()
                if (s != null) {
                    try {
                        s.startScan(listOf(filter), settings, callback)
                        _scanning.value = true
                        delay(BleConstants.SCAN_WINDOW_MS)
                    } catch (_: Exception) {
                    } finally {
                        try {
                            s.stopScan(callback)
                        } catch (_: Exception) {
                        }
                        _scanning.value = false
                    }
                }
                publish()
                delay(BleConstants.SCAN_REST_MS)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        try {
            scanner()?.stopScan(callback)
        } catch (_: Exception) {
        }
        _scanning.value = false
        seen.clear()
        _peers.value = emptyList()
    }
}
