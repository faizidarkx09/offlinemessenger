package com.offlinemessenger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** BLE advertising state for the UI. */
enum class AdvertiseState { IDLE, ADVERTISING, FAILED, UNSUPPORTED }

/**
 * Advertises our GATT service with a service-data header
 * (magic + our device id) so peers recognise us as an app peer.
 */
@SuppressLint("MissingPermission")
class BleAdvertiser(private val context: Context) {

    private val _state = MutableStateFlow(AdvertiseState.IDLE)
    val state: StateFlow<AdvertiseState> = _state

    var lastError: String? = null
        private set

    private var advertiser = bluetoothAdvertiser()

    private fun bluetoothAdvertiser() =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.bluetoothLeAdvertiser

    private val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _state.value = AdvertiseState.ADVERTISING
        }

        override fun onStartFailure(errorCode: Int) {
            lastError = "advertise failed: $errorCode"
            _state.value = AdvertiseState.FAILED
        }
    }

    fun start(deviceId: UUID) {
        val adv = advertiser ?: bluetoothAdvertiser()
        if (adv == null || !isMultipleAdvertisementSupported()) {
            _state.value = AdvertiseState.UNSUPPORTED
            return
        }
        advertiser = adv
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val serviceData = BleConstants.AD_MAGIC + deviceId.toBytes()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), serviceData)
            .build()
        try {
            adv.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            lastError = e.message
            _state.value = AdvertiseState.FAILED
        }
    }

    fun stop() {
        try {
            advertiser?.stopAdvertising(callback)
        } catch (_: Exception) {
        }
        _state.value = AdvertiseState.IDLE
    }

    private fun isMultipleAdvertisementSupported(): Boolean {
        val adapter: BluetoothAdapter? =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        return adapter?.isMultipleAdvertisementSupported == true
    }
}
