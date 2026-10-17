package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.di.ServiceLocator
import kotlinx.coroutines.launch

class ContactsViewModel(locator: ServiceLocator) : ViewModel() {
    private val peers = locator.peerRepository

    val contacts = peers.contacts

    fun verify(deviceId: String) {
        viewModelScope.launch { peers.setVerified(deviceId) }
    }

    fun delete(deviceId: String) {
        viewModelScope.launch { peers.deleteContact(deviceId) }
    }
}
