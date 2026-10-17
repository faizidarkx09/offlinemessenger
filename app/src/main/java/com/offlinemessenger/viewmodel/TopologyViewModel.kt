package com.offlinemessenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlinemessenger.data.local.entity.RouteEntity
import com.offlinemessenger.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Direct neighbours come from live BLE connections (certain).
 * Multi-hop routes are learned estimates (expiry-bounded).
 * The UI must render these two categories distinctly.
 */
class TopologyViewModel(private val locator: ServiceLocator) : ViewModel() {
    private val transport = locator.bleTransport
    private val peers = locator.peerRepository
    private val repo = locator.chatRepository

    val direct = transport.connections

    private val _routes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val routes: StateFlow<List<RouteEntity>> = _routes

    private val _queued = MutableStateFlow(0)
    val queued: StateFlow<Int> = _queued

    val relayOptIn: Boolean get() = locator.secureStore.relayOptIn

    init {
        viewModelScope.launch {
            while (isActive) {
                _routes.value = peers.validRoutes()
                _queued.value = repo.relayQueueSize()
                delay(5_000)
            }
        }
    }
}
