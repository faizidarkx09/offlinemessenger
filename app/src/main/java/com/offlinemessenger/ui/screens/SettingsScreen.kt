package com.offlinemessenger.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemessenger.ui.VmFactory
import com.offlinemessenger.ui.components.Avatar
import com.offlinemessenger.ui.rememberLocator
import com.offlinemessenger.ui.shortId
import com.offlinemessenger.ui.theme.AmberWarn
import com.offlinemessenger.ui.theme.Charcoal800
import com.offlinemessenger.ui.theme.InkMid
import com.offlinemessenger.ui.theme.RedError
import com.offlinemessenger.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val locator = rememberLocator()
    val vm: SettingsViewModel = viewModel(
        factory = VmFactory { SettingsViewModel(context.applicationContext, locator) },
    )
    val displayName by vm.displayName.collectAsState()
    val relayOptIn by vm.relayOptIn.collectAsState()
    val bluetoothOn by vm.bluetoothOn.collectAsState(initial = true)
    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cropUri = uri
    }

    // Crop editor overlay.
    cropUri?.let { uri ->
        PhotoCropScreen(uri = uri, onDone = { bytes ->
            cropUri = null
            if (bytes != null) vm.savePhoto(bytes)
        })
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ---- Profile
            Text("Profile", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Charcoal800)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val photoBytes by vm.profilePhoto.collectAsState()
                    val photoB64 = remember(photoBytes) {
                        photoBytes?.let {
                            android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                        } ?: ""
                    }
                    Avatar(name = displayName, size = 56.dp, photoB64 = photoB64)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Display name", style = MaterialTheme.typography.bodyLarge)
                        Text(displayName, style = MaterialTheme.typography.bodyMedium, color = InkMid)
                        TextButton(
                            onClick = { photoPicker.launch("image/*") },
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text("Change photo") }
                    }
                    IconButton(onClick = { editingName = true }) {
                        Icon(Icons.Filled.Edit, "edit name")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- Relay mode
            Text("Relay mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Charcoal800)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Forward others' messages", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (relayOptIn) "ON" else "OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkMid,
                            )
                        }
                        Switch(checked = relayOptIn, onCheckedChange = vm::setRelayOptIn)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Battery", style = MaterialTheme.typography.bodyMedium, color = AmberWarn)
                    Text(
                        "Relaying keeps your Bluetooth radio busier and drains battery " +
                            "faster — roughly like keeping a call's radio warm. " +
                            "Leave it off when you only need your own chats.",
                        style = MaterialTheme.typography.bodySmall, color = InkMid,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Privacy", style = MaterialTheme.typography.bodyMedium, color = AmberWarn)
                    Text(
                        "Relayed messages stay end-to-end encrypted — you cannot read them. " +
                            "Relays do see routing metadata (who is messaging whom), " +
                            "which is inherent to any relay network.",
                        style = MaterialTheme.typography.bodySmall, color = InkMid,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- Identity
            Text("My identity", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Charcoal800)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device ID", style = MaterialTheme.typography.bodyMedium, color = InkMid)
                    Text(shortId(vm.deviceId) + "…", style = MaterialTheme.typography.bodyLarge)
                    Text(vm.deviceId, style = MaterialTheme.typography.bodySmall, color = InkMid)
                    Spacer(Modifier.height(12.dp))
                    Text("Key fingerprint", style = MaterialTheme.typography.bodyMedium, color = InkMid)
                    Text(vm.fingerprint, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Read this to a contact so they can verify you in Contacts.",
                        style = MaterialTheme.typography.bodySmall, color = InkMid,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- Radio
            Text("Radio", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Charcoal800)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Bluetooth: ${if (bluetoothOn) "on" else "OFF — messaging paused"}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = vm::stopRadio,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError),
                    ) {
                        Text("Stop radio & background service")
                    }
                    Text(
                        "Stops advertising, scanning and listening entirely. " +
                            "Reopen the app to start again.",
                        style = MaterialTheme.typography.bodySmall, color = InkMid,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "OfflineMessenger 1.0.0 — offline-first messaging over Bluetooth Low Energy. " +
                    "No accounts, no servers, no internet permission. Messages are " +
                    "end-to-end encrypted with libsodium (X25519 + XSalsa20-Poly1305). " +
                    "Realistic range: tens of metres, not kilometres.",
                style = MaterialTheme.typography.bodySmall, color = InkMid,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (editingName) {
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Display name") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveDisplayName(nameDraft)
                    editingName = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingName = false }) { Text("Cancel") }
            },
        )
    }
}
