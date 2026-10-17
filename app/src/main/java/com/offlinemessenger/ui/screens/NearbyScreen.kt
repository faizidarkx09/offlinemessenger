package com.offlinemessenger.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.ble.model.DiscoveredPeer
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.ConnectionDot
import com.offlinemessenger.ui.components.EmptyState
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.shortId
import com.offlinemessenger.ui.theme.AmberWarn
import com.offlinemessenger.ui.theme.Charcoal800
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.NearbyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen() {
    val locator = rememberLocator()
    val vm: NearbyViewModel = viewModel(factory = VmFactory { NearbyViewModel(locator) })
    val appPeers by vm.appPeers.collectAsState(initial = emptyList())
    val unknown by vm.unknownDevices.collectAsState(initial = emptyList())
    val scanning by vm.scanning.collectAsState(initial = false)
    val bluetoothOn by vm.bluetoothOn.collectAsState(initial = true)
    val corrupt by vm.corruptFrames.collectAsState(initial = 0)

    Scaffold(topBar = { TopAppBar(title = { Text("Nearby") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            if (!bluetoothOn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Charcoal800),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(
                            "Bluetooth is off — turn it on to discover peers.",
                            color = AmberWarn,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (scanning) "Scanning for peers…" else "Scan paused (battery saver)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMid,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("App peers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            if (appPeers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Radar,
                        title = "No app peers nearby",
                        body = "Peers running OfflineMessenger appear here automatically. " +
                            "Typical BLE range is 10–60 m indoors.",
                        modifier = Modifier.height(260.dp),
                    )
                }
            } else {
                items(appPeers, key = { it.address }) { peer ->
                    PeerRow(peer = peer, connected = vm.isConnected(peer.deviceId.toString()))
                }
            }
            if (unknown.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("Other Bluetooth devices", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unidentified — not running this app. Shown for transparency only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkLow,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(unknown, key = { it.address }) { peer ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name = peer.name ?: "?", size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.name ?: "Unknown device", style = MaterialTheme.typography.bodyLarge)
                            Text(peer.address, style = MaterialTheme.typography.bodySmall, color = InkLow)
                        }
                        Text("${peer.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = InkLow)
                    }
                }
            }
            if (corrupt > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "$corrupt malformed packets ignored (integrity protection working).",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkLow,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: DiscoveredPeer, connected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = peer.name ?: shortId(peer.deviceId.toString()))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                peer.name ?: "Peer ${shortId(peer.deviceId.toString())}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${peer.rssi} dBm · ${shortId(peer.deviceId.toString())}",
                style = MaterialTheme.typography.bodySmall,
                color = InkLow,
            )
        }
        ConnectionDot(connected = connected)
        Spacer(Modifier.width(8.dp))
        Text(
            if (connected) "linked" else "in range",
            style = MaterialTheme.typography.bodySmall,
            color = if (connected) Emerald400 else InkMid,
        )
    }
}
