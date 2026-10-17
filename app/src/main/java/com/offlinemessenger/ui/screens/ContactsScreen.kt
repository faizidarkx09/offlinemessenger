package com.offlinemessenger.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.components.EmptyState
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.theme.Emerald400
import com.offlinemessenger.ui.theme.InkLow
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.viewmodel.ContactsViewModel

/**
 * Known peers (trust-on-first-use). Tapping verify shows the key fingerprint
 * so the user can compare it out-of-band (read aloud, QR-style check) and
 * then mark the contact verified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onOpenChat: (String) -> Unit) {
    val locator = rememberLocator()
    val vm: ContactsViewModel = viewModel(factory = VmFactory { ContactsViewModel(locator) })
    val contacts by vm.contacts.collectAsState(initial = emptyList())
    var verifyTarget by remember { mutableStateOf<ContactEntity?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Contacts") }) }) { padding ->
        if (contacts.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Contacts,
                title = "No contacts yet",
                body = "Contacts are created automatically the first time an app peer " +
                    "is in Bluetooth range (trust-on-first-use).",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(contacts, key = { it.deviceId }) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(c.deviceId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name = c.displayName, photoB64 = c.photoB64)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.displayName, style = MaterialTheme.typography.titleMedium)
                                if (c.verified) {
                                    Icon(
                                        Icons.Filled.Verified, "verified",
                                        tint = Emerald400,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (c.verified) "Verified key" else "Tap to verify key fingerprint",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (c.verified) Emerald400 else InkMid,
                                modifier = Modifier.clickable { verifyTarget = c },
                            )
                            Text(
                                c.fingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkLow,
                            )
                        }
                        IconButton(onClick = { vm.delete(c.deviceId) }) {
                            Icon(Icons.Filled.Delete, "delete", tint = InkLow)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    verifyTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { verifyTarget = null },
            title = { Text("Verify ${c.displayName}") },
            text = {
                Column {
                    Text(
                        "Compare this fingerprint with the one shown on your contact's phone " +
                            "(Settings → My fingerprint). Only mark verified if every group matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMid,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(c.fingerprint, style = MaterialTheme.typography.titleMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.verify(c.deviceId); verifyTarget = null }) {
                    Text("Fingerprints match")
                }
            },
            dismissButton = {
                TextButton(onClick = { verifyTarget = null }) { Text("Cancel") }
            },
        )
    }
}
