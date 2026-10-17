package com.offlinemessenger.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.ble.model.DiscoveredPeer
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.EmptyState
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.shortId
import com.offlinemessenger.ui.theme.IOSBlack
import com.offlinemessenger.ui.theme.IOSBlue
import com.offlinemessenger.ui.theme.IOSGray6
import com.offlinemessenger.ui.theme.IOSGreen
import com.offlinemessenger.ui.theme.IOSOrange
import com.offlinemessenger.ui.theme.IOSSecondary
import com.offlinemessenger.ui.theme.IOSTertiary
import com.offlinemessenger.ui.theme.IOSText
import com.offlinemessenger.viewmodel.NearbyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen() {
    val locator = rememberLocator()
    val vm: NearbyViewModel = viewModel(factory = VmFactory { NearbyViewModel(locator) })
    val appPeers by vm.appPeers.collectAsState(initial = emptyList())
    val unknown by vm.unknownDevices.collectAsState(initial = emptyList())
    val scanning by vm.scanning.collectAsState(initial = false)
    val bluetoothOn by vm.bluetoothOn.collectAsState(initial = true)
    val contacts by vm.contacts.collectAsState(initial = emptyList())
    val requested by vm.requested.collectAsState()
    val contactById = remember(contacts) { contacts.associateBy { it.deviceId } }
    var connectingId by remember { mutableStateOf<String?>(null) }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }

    Scaffold(containerColor = IOSBlack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // iOS large title
                Text(
                    "Nearby",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = IOSText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!bluetoothOn) "Bluetooth is off"
                    else if (scanning) "Scanning for peers…"
                    else "Scan paused (battery saver)",
                    fontSize = 15.sp,
                    color = IOSSecondary,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (!bluetoothOn) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = IOSGray6),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Text(
                            "Bluetooth is off — turn it on to discover peers.",
                            color = IOSOrange,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            if (appPeers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Radar,
                        title = "No app peers nearby",
                        body = "Install this app on a second phone too, keep both apps " +
                            "open with Bluetooth on, and hold them within 10–60 m — " +
                            "the other phone will appear here. Tap it to connect instantly.",
                        modifier = Modifier.height(260.dp),
                    )
                }
            } else {
                items(appPeers, key = { it.address }) { peer ->
                    val idStr = peer.deviceId.toString()
                    val connected = vm.isConnected(idStr)
                    val contact = contactById[idStr]
                    // Profile name from HELLO (stable), else the Bluetooth name.
                    val showName = contact?.displayName ?: peer.name ?: "Peer ${shortId(idStr)}"
                    if (connected && connectingId == idStr) connectingId = null
                    PeerCard(
                        peer = peer,
                        showName = showName,
                        photoB64 = contact?.photoB64 ?: "",
                        connected = connected,
                        connecting = connectingId == idStr,
                        requestSent = requested.contains(idStr),
                        onTap = {
                            if (!connected && connectingId == null && !requested.contains(idStr)) {
                                connectingId = idStr
                                vm.connect(peer)
                                // Clear the spinner after a while; the dot shows real state.
                                scope.launch {
                                    delay(30_000)
                                    if (connectingId == idStr) connectingId = null
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (unknown.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Other Bluetooth devices", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = IOSText)
                    Text(
                        "Not running this app — shown for transparency only.",
                        fontSize = 13.sp,
                        color = IOSTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(unknown, key = { it.address }) { peer ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = IOSGray6),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(name = peer.name ?: "?", size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(peer.name ?: "Unknown device", fontSize = 16.sp, color = IOSText)
                                Text(peer.address, fontSize = 13.sp, color = IOSTertiary)
                            }
                            Text("${peer.rssi} dBm", fontSize = 13.sp, color = IOSTertiary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** iOS-style tappable peer card. Tap = connect + send chat request. */
@Composable
private fun PeerCard(
    peer: DiscoveredPeer,
    showName: String,
    photoB64: String,
    connected: Boolean,
    connecting: Boolean,
    requestSent: Boolean,
    onTap: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = IOSGray6),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = showName, size = 48.dp, photoB64 = photoB64)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    showName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IOSText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${peer.rssi} dBm · ${shortId(peer.deviceId.toString())}",
                    fontSize = 13.sp,
                    color = IOSTertiary,
                )
            }
            when {
                connected && requestSent -> {
                    Pill("Chat requested", IOSBlue)
                }
                connected -> {
                    Pill("Connected", IOSGreen)
                }
                connecting || requestSent -> {
                    if (requestSent) Pill("Request sent ✓", IOSBlue)
                    else CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = IOSBlue,
                        strokeWidth = 2.5.dp,
                    )
                }
                else -> {
                    Surface(color = IOSBlue, shape = RoundedCornerShape(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Bluetooth, null,
                                tint = IOSText,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Connect", color = IOSText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
