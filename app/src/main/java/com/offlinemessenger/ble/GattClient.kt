package com.offlinemessenger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import com.offlinemessenger.protocol.Packet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GATT client for one remote peer. Writes are strictly serialized through a
 * channel (the BLE stack allows a single outstanding write), each chunk
 * acknowledged by onCharacteristicWrite before the next goes out.
 */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class GattClient(
    private val context: Context,
    private val device: BluetoothDevice,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onPacketReceived(packet: ByteArray)
        fun onReady()
        fun onDisconnected()
        fun onFailed(reason: String)
    }

    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var mtu: Int = BleConstants.DEFAULT_MTU
    private var rxBuffer: ByteArray = ByteArray(0)

    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeAck: CompletableDeferred<Boolean>? = null
    @Volatile private var closed = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                close()
                listener.onDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onFailed("service discovery failed: $status")
                disconnect()
                return
            }
            val service = gatt.getService(BleConstants.SERVICE_UUID)
            val rx = service?.getCharacteristic(BleConstants.RX_CHAR_UUID)
            val tx = service?.getCharacteristic(BleConstants.TX_CHAR_UUID)
            if (rx == null || tx == null) {
                listener.onFailed("messaging service not found")
                disconnect()
                return
            }
            rxChar = rx
            gatt.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(BleConstants.CCCD_UUID)
            if (cccd == null) {
                listener.onFailed("CCCD missing")
                disconnect()
                return
            }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.requestMtu(512)
                listener.onReady()
            } else {
                listener.onFailed("CCCD write failed: $status")
                disconnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@GattClient.mtu = mtu
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == BleConstants.TX_CHAR_UUID) {
                appendFrame(characteristic.value ?: return)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeAck?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    fun connect() {
        if (gatt != null || closed) return
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            listener.onFailed("connectGatt returned null")
            return
        }
        scope.launch {
            for (packet in writeQueue) {
                if (closed) break
                if (!writePacket(packet)) break
            }
        }
    }

    fun send(packet: ByteArray) {
        writeQueue.trySend(packet)
    }

    fun disconnect() {
        close()
        try {
            gatt?.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun close() {
        if (closed) return
        closed = true
        writeQueue.close()
        writeAck?.complete(false)
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private suspend fun writePacket(packet: ByteArray): Boolean {
        val g = gatt ?: return false
        val rx = rxChar ?: return false
        val chunkSize = mtu - BleConstants.ATT_HEADER
        val framed = ByteBuffer.allocate(4 + packet.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(packet.size).put(packet).array()
        var offset = 0
        while (offset < framed.size) {
            val end = minOf(offset + chunkSize, framed.size)
            val chunk = framed.copyOfRange(offset, end)
            rx.value = chunk
            rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val ack = CompletableDeferred<Boolean>()
            writeAck = ack
            if (!g.writeCharacteristic(rx)) {
                writeAck = null
                return false
            }
            val ok = withTimeoutOrNull(5_000) { ack.await() } ?: false
            writeAck = null
            if (!ok) return false
            offset = end
        }
        return true
    }

    private fun appendFrame(chunk: ByteArray) {
        var buffer = rxBuffer + chunk
        while (true) {
            if (buffer.size < 4) break
            val len = ByteBuffer.wrap(buffer, 0, 4).order(ByteOrder.BIG_ENDIAN).int
            if (len < 0 || len > Packet.HEADER_SIZE + Packet.MAX_PAYLOAD_SIZE) {
                buffer = ByteArray(0) // corrupt; drop
                break
            }
            if (buffer.size < 4 + len) break
            listener.onPacketReceived(buffer.copyOfRange(4, 4 + len))
            buffer = buffer.copyOfRange(4 + len, buffer.size)
        }
        rxBuffer = buffer
    }
}
