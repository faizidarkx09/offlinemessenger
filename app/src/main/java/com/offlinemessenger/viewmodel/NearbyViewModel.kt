package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.ble.model.DiscoveredPeer
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.di.ServiceLocator
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class NearbyViewModel(locator: ServiceLocator) : ViewModel() {
    private val transport = locator.bleTransport
    private val peers = locator.peerRepository
    private val chatRepo = locator.chatRepository

    val appPeers: Flow<List<DiscoveredPeer>> =
        transport.discoveredPeers.map { list -> list.filter { it.isAppPeer } }

    val unknownDevices: Flow<List<DiscoveredPeer>> =
        transport.discoveredPeers.map { list -> list.filterNot { it.isAppPeer } }

    val connections = transport.connections
    val scanning = transport.scanning
    val bluetoothOn = transport.bluetoothOn
    val corruptFrames = transport.corruptFrames

    /** Known contacts — for showing profile names/photos in Nearby. */
    val contacts: Flow<List<ContactEntity>> = peers.contacts

    /** Peer ids we already sent a chat request to (UI badge). */
    private val _requested = MutableStateFlow<Set<String>>(emptySet())
    val requested: StateFlow<Set<String>> = _requested

    fun isConnected(deviceId: String): Boolean =
        connections.value.any { it.deviceId.toString() == deviceId }

    /**
     * User tapped a peer: connect right now, then send a chat request.
     * The other side gets a notification; on Accept the chat opens.
     */
    fun connect(peer: DiscoveredPeer) {
        val id = peer.deviceId ?: return
        transport.connectNow(id, peer.device)
        viewModelScope.launch {
            // Wait for the GATT link…
            val linked = withTimeoutOrNull(15_000) {
                transport.connections.first { list -> list.any { it.deviceId == id } }
            } ?: return@launch
            // …then for the HELLO/key exchange (contact appears)…
            withTimeoutOrNull(12_000) {
                peers.contacts.first { list -> list.any { it.deviceId == id.toString() } }
            } ?: return@launch
            delay(400)
            // …then send the request.
            if (chatRepo.sendConnectionRequest(id)) {
                _requested.value = _requested.value + id.toString()
            }
        }
    }
}
