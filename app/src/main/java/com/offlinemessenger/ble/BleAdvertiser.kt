package com.offlinemessenger.ble

import android.annotation.SuppressLint
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
        if (adv == null) {
            _state.value = AdvertiseState.UNSUPPORTED
            return
        }
        // NOTE: no isMultipleAdvertisementSupported() gate — it is overly
        // conservative on some phones. If the controller truly can't advertise,
        // the stack reports it via onStartFailure and we surface FAILED.
        advertiser = adv
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val serviceData = BleConstants.AD_MAGIC + deviceId.toBytes()
        // 16-bit UUID keeps the packet small:
        // flags(3) + 16-bit uuid(4) + service data(2+2+18=22) = 29 bytes <= 31.
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(BleConstants.ADVERTISE_PARCEL_UUID)
            .addServiceData(BleConstants.ADVERTISE_PARCEL_UUID, serviceData)
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
}
