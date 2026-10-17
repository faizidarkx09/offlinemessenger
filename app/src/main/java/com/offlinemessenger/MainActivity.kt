package com.offlinemessenger

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
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
import com.offlinemessenger.ui.screens.CrashReportScreen
import com.offlinemessenger.ui.screens.PermissionsScreen
import com.offlinemessenger.ui.screens.ProfileSetupScreen
import com.offlinemessenger.ui.theme.OfflineMessengerTheme

class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)
    private var profileDone by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionsGranted = result.values.all { it }
            if (permissionsGranted) {
                startRadioService()
                ensureBluetoothOn()
            }
        }

    /** System dialog asking the user to enable Bluetooth; the transport
     * auto-starts the radio when it observes STATE_ON. */
    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Diagnostic: if the last run crashed, show the report instead of
        // running the normal flow (which would crash again immediately).
        val report = OfflineMessengerApp.readCrashReport(application as OfflineMessengerApp)
        if (report != null) {
            setContent {
                OfflineMessengerTheme {
                    CrashReportScreen(
                        report = report,
                        onClear = {
                            OfflineMessengerApp.clearCrashReport(application as OfflineMessengerApp)
                            recreate()
                        },
                    )
                }
            }
            return
        }
        permissionsGranted = BlePermissions.granted(this)
        profileDone = (application as OfflineMessengerApp).locator.secureStore.isProfileSetupDone()
        if (permissionsGranted) {
            startRadioService()
            ensureBluetoothOn()
        } else {
            permissionLauncher.launch(BlePermissions.required())
        }
        setContent {
            OfflineMessengerTheme {
                when {
                    !profileDone -> ProfileSetupScreen(onDone = { profileDone = true })
                    permissionsGranted -> AppNav()
                    else -> PermissionsScreen(
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
        if (permissionsGranted) ensureBluetoothOn()
    }

    private fun startRadioService() {
        val intent = Intent(this, MessengerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    /** Prompts to enable Bluetooth if it is off (messaging needs the radio). */
    private fun ensureBluetoothOn() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = manager.adapter
        if (adapter != null && !adapter.isEnabled) {
            try {
                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {
            }
        }
    }
}
