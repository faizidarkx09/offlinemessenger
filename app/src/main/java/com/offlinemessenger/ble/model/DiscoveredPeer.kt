package com.offlinemessenger.ble.model

import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * A BLE device seen during scanning.
 * [deviceId] is non-null only for app peers (advertisement carried our magic
 * + device id). Everything else is an unidentified BLE device.
 */
data class DiscoveredPeer(
    val device: BluetoothDevice,
    val deviceId: UUID?,
    val name: String?,
    val rssi: Int,
    val lastSeenMs: Long,
) {
    val isAppPeer: Boolean get() = deviceId != null
    val address: String get() = device.address
}
