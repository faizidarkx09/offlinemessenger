package com.offlinemessenger.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.di.ServiceLocator
import com.offlinemessenger.service.MessengerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    locator: ServiceLocator,
) : ViewModel() {
    private val store = locator.secureStore
    private val crypto = locator.cryptoManager
    private val transport = locator.bleTransport

    private val _displayName = MutableStateFlow(store.displayName())
    val displayName: StateFlow<String> = _displayName

    private val _photo = MutableStateFlow(store.profilePhoto())
    val profilePhoto: StateFlow<ByteArray?> = _photo

    fun savePhoto(jpeg: ByteArray?) {
        viewModelScope.launch {
            store.setProfilePhoto(jpeg)
            _photo.value = jpeg
        }
    }

    private val _relayOptIn = MutableStateFlow(store.relayOptIn)
    val relayOptIn: StateFlow<Boolean> = _relayOptIn

    val deviceId: String = store.deviceId().toString()
    val fingerprint: String = crypto.fingerprint()
    val bluetoothOn = transport.bluetoothOn

    fun saveDisplayName(name: String) {
        val clean = name.trim().take(40).ifEmpty { return }
        viewModelScope.launch {
            store.setDisplayName(clean)
            _displayName.value = clean
        }
    }

    fun setRelayOptIn(enabled: Boolean) {
        store.relayOptIn = enabled
        _relayOptIn.value = enabled
    }

    /** Stops the radio + foreground service entirely. */
    fun stopRadio() {
        val intent = Intent(appContext, MessengerService::class.java)
            .setAction(MessengerService.ACTION_STOP)
        appContext.startService(intent)
    }
}
