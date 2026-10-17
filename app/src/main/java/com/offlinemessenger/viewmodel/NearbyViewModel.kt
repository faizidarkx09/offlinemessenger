package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import com.offlinemessenger.ble.model.DiscoveredPeer
import com.offlinemessenger.di.ServiceLocator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NearbyViewModel(locator: ServiceLocator) : ViewModel() {
    private val transport = locator.bleTransport

    val appPeers: Flow<List<DiscoveredPeer>> =
        transport.discoveredPeers.map { list -> list.filter { it.isAppPeer } }

    val unknownDevices: Flow<List<DiscoveredPeer>> =
        transport.discoveredPeers.map { list -> list.filterNot { it.isAppPeer } }

    val connections = transport.connections
    val scanning = transport.scanning
    val bluetoothOn = transport.bluetoothOn
    val corruptFrames = transport.corruptFrames

    fun isConnected(deviceId: String): Boolean =
        connections.value.any { it.deviceId.toString() == deviceId }
}
