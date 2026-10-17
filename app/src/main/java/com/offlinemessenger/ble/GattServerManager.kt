package com.offlinemessenger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import com.offlinemessenger.protocol.Packet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * The local GATT server: peers connect to us and write framed packets to the
 * RX characteristic; we push packets back via TX notifications.
 *
 * Framing: 4-byte big-endian length prefix + packet bytes. Lengths outside
 * [0, HEADER+MAX_PAYLOAD] reset the per-device buffer and count as corrupt.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION") // value-based APIs work on all supported API levels
class GattServerManager(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPacketReceived(fromAddress: String, packet: ByteArray)
        fun onDeviceConnected(address: String, device: BluetoothDevice)
        fun onDeviceDisconnected(address: String)
        fun onCorruptFrame(address: String)
    }

    private var server: BluetoothGattServer? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private val devices = ConcurrentHashMap<String, BluetoothDevice>()
    private val rxBuffers = ConcurrentHashMap<String, ByteArray>()
    private val mtus = ConcurrentHashMap<String, Int>()

    val maxFrameBytes: Int = 4 + Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                devices[address] = device
                mtus[address] = BleConstants.DEFAULT_MTU
                listener.onDeviceConnected(address, device)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                devices.remove(address)
                rxBuffers.remove(address)
                mtus.remove(address)
                listener.onDeviceDisconnected(address)
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == BleConstants.RX_CHAR_UUID) {
                appendFrame(device.address, value)
            }
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mtus[device.address] = mtu
        }
    }

    fun start(): Boolean {
        if (server != null) return true
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val gattServer = manager.openGattServer(context, callback) ?: return false

        val rx = BluetoothGattCharacteristic(
            BleConstants.RX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val tx = BluetoothGattCharacteristic(
            BleConstants.TX_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        tx.addDescriptor(
            BluetoothGattDescriptor(
                BleConstants.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(rx)
        service.addCharacteristic(tx)
        if (!gattServer.addService(service)) {
            gattServer.close()
            return false
        }
        server = gattServer
        txChar = tx
        return true
    }

    fun stop() {
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        txChar = null
        devices.clear()
        rxBuffers.clear()
        mtus.clear()
    }

    /** Sends one framed packet to the connected device at [address]. */
    fun send(address: String, packet: ByteArray): Boolean {
        val srv = server ?: return false
        val device = devices[address] ?: return false
        val char = txChar ?: return false
        val chunkSize = (mtus[address] ?: BleConstants.DEFAULT_MTU) - BleConstants.ATT_HEADER
        val framed = ByteBuffer.allocate(4 + packet.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(packet.size).put(packet).array()
        var offset = 0
        while (offset < framed.size) {
            val end = minOf(offset + chunkSize, framed.size)
            char.value = framed.copyOfRange(offset, end)
            if (!srv.notifyCharacteristicChanged(device, char, false)) return false
            offset = end
        }
        return true
    }

    private fun appendFrame(address: String, chunk: ByteArray) {
        val current = rxBuffers[address] ?: ByteArray(0)
        var buffer = current + chunk
        while (true) {
            if (buffer.size < 4) break
            val len = ByteBuffer.wrap(buffer, 0, 4).order(ByteOrder.BIG_ENDIAN).int
            if (len < 0 || len > Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE) {
                rxBuffers.remove(address)
                listener.onCorruptFrame(address)
                return
            }
            if (buffer.size < 4 + len) break
            val packet = buffer.copyOfRange(4, 4 + len)
            buffer = buffer.copyOfRange(4 + len, buffer.size)
            listener.onPacketReceived(address, packet)
        }
        if (buffer.isEmpty()) rxBuffers.remove(address) else rxBuffers[address] = buffer
        // Defensive cap: never let a misbehaving peer grow the buffer unbounded.
        if (buffer.size > maxFrameBytes) {
            rxBuffers.remove(address)
            listener.onCorruptFrame(address)
        }
    }
}
