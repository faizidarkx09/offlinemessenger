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
import androidx.compose.material.icons.filled.Share
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
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.ConnectionDot
import com.offlinemessenger.ui.components.EmptyState
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.shortId
import com.offlinemessenger.ui.theme.AmberWarn
import com.offlinemessenger.ui.theme.Charcoal800
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.TopologyViewModel

/**
 * Network view with an honest split: DIRECT neighbours are live, verified
 * BLE links; ROUTES are estimates learned from neighbours' advertisements
 * and expire after 2 minutes without refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen() {
    val locator = rememberLocator()
    val vm: TopologyViewModel = viewModel(factory = VmFactory { TopologyViewModel(locator) })
    val direct by vm.direct.collectAsState(initial = emptyList())
    val routes by vm.routes.collectAsState(initial = emptyList())
    val queued by vm.queued.collectAsState(initial = 0)

    Scaffold(topBar = { TopAppBar(title = { Text("Network") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Charcoal800),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mesh status", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Relay mode: ${if (vm.relayOptIn) "ON — you forward others' messages" else "OFF — you only send/receive your own"}",
                            style = MaterialTheme.typography.bodyMedium, color = InkMid,
                        )
                        Text(
                            "Store-and-forward queue: $queued message(s) waiting for a route",
                            style = MaterialTheme.typography.bodyMedium, color = InkMid,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionDot(connected = true)
                    Spacer(Modifier.width(8.dp))
                    Text("Direct neighbours — live BLE links", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (direct.isEmpty()) {
                item {
                    Text(
                        "None. Direct links appear when a peer connects.",
                        style = MaterialTheme.typography.bodyMedium, color = InkLow,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(direct, key = { it.deviceId.toString() }) { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConnectionDot(connected = true)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                shortId(c.deviceId.toString()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                if (c.clientSide) "link opened by you" else "link opened by peer",
                                style = MaterialTheme.typography.bodySmall, color = InkLow,
                            )
                        }
                        Text("1 hop", style = MaterialTheme.typography.bodySmall, color = Emerald400)
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionDot(connected = false)
                    Spacer(Modifier.width(8.dp))
                    Text("Known routes — estimates, not live links", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Learned from neighbours' advertisements; expire after 2 min without refresh.",
                    style = MaterialTheme.typography.bodySmall, color = InkLow,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (routes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Share,
                        title = "No routes known",
                        body = "Multi-hop routes appear once neighbours advertise them. " +
                            "This only works between devices in overlapping BLE range — " +
                            "not over kilometres.",
                        modifier = Modifier.height(300.dp),
                    )
                }
            } else {
                items(routes, key = { it.destId }) { r ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Charcoal800),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("→ ${shortId(r.destId)}", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "via ${shortId(r.nextHopId)}",
                                    style = MaterialTheme.typography.bodySmall, color = InkMid,
                                )
                            }
                            Text(
                                "${r.hops} hops",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AmberWarn,
                            )
                        }
                    }
                }
            }
        }
    }
}
