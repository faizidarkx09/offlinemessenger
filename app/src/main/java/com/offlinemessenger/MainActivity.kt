package com.offlinemessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.offlinemessenger.ble.BlePermissions
import com.offlinemessenger.service.MessengerService
import com.offlinemessenger.ui.nav.AppNav
import com.offlinemessenger.ui.screens.PermissionsScreen
import com.offlinemessenger.ui.theme.OfflineMessengerTheme

class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionsGranted = result.values.all { it }
            if (permissionsGranted) startRadioService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsGranted = BlePermissions.granted(this)
        if (permissionsGranted) {
            startRadioService()
        } else {
            permissionLauncher.launch(BlePermissions.required())
        }
        setContent {
            OfflineMessengerTheme {
                if (permissionsGranted) {
                    AppNav()
                } else {
                    PermissionsScreen(
                        missing = BlePermissions.missing(this),
                        onGrant = { permissionLauncher.launch(BlePermissions.required()) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted permissions from system Settings.
        if (!permissionsGranted && BlePermissions.granted(this)) {
            permissionsGranted = true
            startRadioService()
        }
    }

    private fun startRadioService() {
        val intent = Intent(this, MessengerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
