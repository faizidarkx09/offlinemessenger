package com.offlinemessenger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.offlinemessenger.ui.theme.InkMid

/**
 * Shown until Bluetooth runtime permissions are granted. The app cannot
 * function without them — there is no internet fallback by design.
 */
@Composable
fun PermissionsScreen(missing: List<String>, onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Bluetooth,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text("Bluetooth access needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "OfflineMessenger talks to nearby phones over Bluetooth Low Energy — " +
                "no internet, no servers. It needs Bluetooth permissions to discover " +
                "peers and exchange messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMid,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Still missing: ${missing.joinToString { it.substringAfterLast('.') }}",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Grant Bluetooth permissions") }
    }
}
