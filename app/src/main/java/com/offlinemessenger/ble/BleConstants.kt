package com.offlinemessenger.ble

import android.os.ParcelUuid
import java.util.UUID

/**
 * BLE identifiers and radio tuning for OfflineMessenger.
 *
 * A single GATT service is exposed. Peers that run this app advertise the
 * service UUID with a short service-data header so scanners can tell
 * "app peer" apart from "unidentified BLE device" without connecting.
 */
object BleConstants {
    /** Primary GATT service for the messaging transport. */
    val SERVICE_UUID: UUID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479")

    /** Client -> server: framed packet writes. */
    val RX_CHAR_UUID: UUID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d47a")

    /** Server -> client: framed packet notifications. */
    val TX_CHAR_UUID: UUID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d47b")

    /** Standard Client Characteristic Configuration descriptor. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    val SERVICE_PARCEL_UUID: ParcelUuid = ParcelUuid(SERVICE_UUID)

    /**
     * 16-bit alias used ONLY inside BLE advertisements. A 128-bit service UUID
     * plus the 18-byte service-data header = 57 bytes, over the 31-byte
     * advertisement limit, so the stack rejects the packet. The GATT server
     * still exposes [SERVICE_UUID]; this UUID is just the discovery beacon.
     */
    val ADVERTISE_UUID: UUID = UUID.fromString("0000FD6F-0000-1000-8000-00805F9B34FB")
    val ADVERTISE_PARCEL_UUID: ParcelUuid = ParcelUuid(ADVERTISE_UUID)

    /**
     * 2-byte magic at the start of the advertise service-data payload.
     * Followed by the 16-byte device id of the advertiser.
     */
    val AD_MAGIC: ByteArray = byteArrayOf(0x4F, 0x4D) // "OM"
    const val AD_DEVICE_ID_LEN = 16

    /** Default ATT MTU before negotiation; payload chunk = mtu - 3. */
    const val DEFAULT_MTU = 23
    const val ATT_HEADER = 3

    /** How long a discovered peer stays listed without a new advertisement. */
    const val PEER_EXPIRY_MS = 30_000L

    /** Scan window: scan this long, then rest, to save battery. */
    const val SCAN_WINDOW_MS = 12_000L
    const val SCAN_REST_MS = 8_000L
}
