package com.offlinemessenger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.crypto.SecureStore
import com.offlinemessenger.protocol.DecodedPacket
import com.offlinemessenger.protocol.HelloPayload
import com.offlinemessenger.protocol.Packet
import com.offlinemessenger.protocol.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the whole BLE radio: advertising, scanning, GATT server + clients.
 *
 * Connection rule (prevents duplicate links): when two app peers discover
 * each other, the one with the lexicographically smaller device id opens
 * the GATT client connection; the other waits for the incoming server link.
 *
 * Every established link immediately exchanges HELLO packets (device id,
 * X25519 public key, display name).
 */
@SuppressLint("MissingPermission")
class BleTransport(
    private val context: Context,
    private val crypto: CryptoManager,
    private val store: SecureStore,
    private val scope: CoroutineScope,
) {
    data class Connection(val deviceId: UUID, val address: String, val clientSide: Boolean)
    data class Incoming(val fromDeviceId: UUID?, val fromAddress: String, val packet: DecodedPacket)

    interface EventListener {
        fun onHello(deviceId: UUID, hello: HelloPayload.Hello, address: String)
        fun onPeerConnected(deviceId: UUID)
        fun onPeerDisconnected(deviceId: UUID)
    }

    var eventListener: EventListener? = null

    val myDeviceId: UUID by lazy { store.deviceId() }

    private val advertiser = BleAdvertiser(context)
    private val scanner = BleScanner(context, scope)
    private val serverListener = object : GattServerManager.Listener {
        override fun onPacketReceived(fromAddress: String, packet: ByteArray) {
            handleRaw(serverPeerIds[fromAddress], fromAddress, packet)
        }

        override fun onDeviceConnected(address: String, device: BluetoothDevice) {
            // Identity is established by the HELLO packet, not by the link.
        }

        override fun onDeviceDisconnected(address: String) {
            val id = serverPeerIds.remove(address)
            if (id != null) eventListener?.onPeerDisconnected(id)
            refreshConnections()
        }

        override fun onCorruptFrame(address: String) {
            _corruptFrames.value += 1
        }
    }
    private val server = GattServerManager(context, serverListener)

    val discoveredPeers: StateFlow<List<com.offlinemessenger.ble.model.DiscoveredPeer>> = scanner.peers
    val scanning = scanner.scanning
    val advertising = advertiser.state

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 128)
    val incoming: SharedFlow<Incoming> = _incoming

    private val _bluetoothOn = MutableStateFlow(true)
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn

    private val _corruptFrames = MutableStateFlow(0)
    val corruptFrames: StateFlow<Int> = _corruptFrames

    /** deviceId string -> client we opened */
    private val clients = ConcurrentHashMap<String, GattClient>()

    /** server-side address -> deviceId (known after HELLO) */
    private val serverPeerIds = ConcurrentHashMap<String, UUID>()

    @Volatile private var started = false

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                _bluetoothOn.value = state == BluetoothAdapter.STATE_ON
                if (state != BluetoothAdapter.STATE_ON) stopRadio()
            }
        }
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** Starts the radio. Returns false when Bluetooth is unavailable/disabled. */
    fun start(): Boolean {
        if (started) return _bluetoothOn.value
        val a = adapter()
        if (a == null || !a.isEnabled) {
            _bluetoothOn.value = false
            return false
        }
        _bluetoothOn.value = true
        context.registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        advertiser.start(myDeviceId)
        scanner.start()
        if (!server.start()) {
            stopRadio()
            return false
        }
        started = true
        scope.launch { autoConnectLoop() }
        return true
    }

    fun stop() {
        sendByeToAll()
        stopRadio()
        try {
            context.unregisterReceiver(btReceiver)
        } catch (_: Exception) {
        }
    }

    private fun stopRadio() {
        started = false
        clients.values.forEach { runCatching { it.disconnect() } }
        clients.clear()
        serverPeerIds.clear()
        server.stop()
        scanner.stop()
        advertiser.stop()
        refreshConnections()
    }

    fun connectedDeviceIds(): Set<UUID> = _connections.value.map { it.deviceId }.toSet()

    /** Sends raw encoded packet bytes to a connected peer. */
    fun sendPacket(destId: UUID, packet: ByteArray): Boolean {
        clients[destId.toString()]?.let {
            it.send(packet)
            return true
        }
        val address = serverPeerIds.entries.firstOrNull { it.value == destId }?.key
            ?: return false
        return server.send(address, packet)
    }

    // ------------------------------------------------------------------ links

    private suspend fun autoConnectLoop() {
        scanner.peers.collect { peers ->
            if (!started) return@collect
            for (peer in peers) {
                val id = peer.deviceId ?: continue
                if (id == myDeviceId) continue
                if (clients.containsKey(id.toString())) continue
                if (serverPeerIds.containsValue(id)) continue
                // Deterministic initiator: smaller id connects.
                if (myDeviceId.toString() < id.toString()) {
                    openClient(id, peer.device)
                }
            }
        }
    }

    private fun openClient(deviceId: UUID, device: BluetoothDevice) {
        val key = deviceId.toString()
        if (clients.containsKey(key)) return
        // Assigned before connect(), so the listener can safely use it.
        var clientRef: GattClient? = null
        val client = GattClient(context, device, scope, object : GattClient.Listener {
            override fun onPacketReceived(packet: ByteArray) {
                handleRaw(deviceId, device.address, packet)
            }

            override fun onReady() {
                clientRef?.send(helloPacket())
                eventListener?.onPeerConnected(deviceId)
                refreshConnections()
            }

            override fun onDisconnected() {
                clients.remove(key)
                eventListener?.onPeerDisconnected(deviceId)
                refreshConnections()
            }

            override fun onFailed(reason: String) {
                clients.remove(key)
                refreshConnections()
            }
        })
        clientRef = client
        clients[key] = client
        client.connect()
    }

    private fun handleRaw(fromDeviceId: UUID?, fromAddress: String, raw: ByteArray) {
        val packet = Packet.decode(raw)
        if (packet == null) {
            _corruptFrames.value += 1
            return
        }
        if (packet.type == PacketType.HELLO) {
            val hello = HelloPayload.decode(packet.payload) ?: run {
                _corruptFrames.value += 1
                return
            }
            if (hello.deviceId == myDeviceId) return // ignore our own echo
            serverPeerIds[fromAddress] = hello.deviceId
            eventListener?.onHello(hello.deviceId, hello, fromAddress)
            // Answer HELLO on server-side links (client side already sent its own).
            if (!clients.containsKey(hello.deviceId.toString())) {
                server.send(fromAddress, helloPacket())
            }
            refreshConnections()
            return
        }
        scope.launch { _incoming.emit(Incoming(fromDeviceId, fromAddress, packet)) }
    }

    private fun helloPacket(): ByteArray = Packet.encode(
        type = PacketType.HELLO,
        ttl = 0,
        messageId = UUID.randomUUID(),
        senderId = myDeviceId,
        destId = Packet.BROADCAST_ID,
        timestampMs = System.currentTimeMillis(),
        payload = HelloPayload.encode(myDeviceId, crypto.publicKey, store.displayName()),
    )

    private fun sendByeToAll() {
        val bye = Packet.encode(
            type = PacketType.BYE,
            ttl = 0,
            messageId = UUID.randomUUID(),
            senderId = myDeviceId,
            destId = Packet.BROADCAST_ID,
            timestampMs = System.currentTimeMillis(),
            payload = ByteArray(0),
        )
        _connections.value.forEach { sendPacket(it.deviceId, bye) }
    }

    private fun refreshConnections() {
        val list = mutableListOf<Connection>()
        clients.keys.forEach { idStr ->
            list.add(Connection(UUID.fromString(idStr), address = "", clientSide = true))
        }
        serverPeerIds.forEach { (address, id) ->
            list.add(Connection(id, address, clientSide = false))
        }
        _connections.value = list
    }
}
