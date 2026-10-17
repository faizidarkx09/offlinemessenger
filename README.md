# OfflineMessenger

A native Android offline messaging app — WhatsApp-inspired UI, **zero internet**.
All communication runs over **Bluetooth Low Energy**: no cellular data, no SIM
service, no Firebase, no cloud backend. The manifest deliberately declares **no
`INTERNET` permission**.

## What it does

- **Nearby peer discovery** over BLE advertisements (app peers vs. unidentified
  BLE devices are listed separately).
- **Direct BLE text messaging** with framed, length-prefixed packets over GATT.
- **Local message history** in Room, with delivery states driven by **real
  acknowledgements** — a message is never shown ✓✓ before the destination ACKs.
- **Bounded multi-hop relay**: TTL-capped forwarding, duplicate suppression,
  per-peer rate limiting, learned distance-vector routes, and a persisted
  **store-and-forward** queue that only releases messages to a live peer.
- **Opt-in relay mode** with explicit battery + privacy information.
- **End-to-end encryption** with libsodium (X25519 key exchange → XSalsa20-Poly1305),
  keys in Keystore-backed storage, trust-on-first-use + fingerprint verification.
- Premium dark-charcoal / emerald Material 3 UI: Chats, Nearby, Network
  (topology), Contacts, Settings.

## Project layout

```
app/src/main/java/com/offlinemessenger/
├── MainActivity.kt / OfflineMessengerApp.kt
├── di/ServiceLocator.kt            # manual DI graph
├── ble/                            # radio: advertise, scan, GATT server+client, transport
├── protocol/                       # v1 binary packet format, HELLO, route adverts
├── crypto/                         # libsodium E2E + Keystore-backed SecureStore
├── data/local/                     # Room: messages, contacts, relay queue, routes
├── relay/                          # RelayEngine (pure Kotlin) + AckTracker
├── repository/                     # ChatRepository (orchestration), PeerRepository
├── service/MessengerService.kt     # foreground service (connectedDevice)
├── ui/                             # Compose theme, nav, screens, components
└── viewmodel/                      # MVVM view models
app/src/test/                       # unit tests (JUnit4 + Robolectric)
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with Android Studio is fine)
- Android SDK: compileSdk 34, minSdk 26
- Two or more physical Android devices with BLE for real testing
  (the emulator cannot do real BLE between instances)

## Setup & build

```bash
# 1. Open the OfflineMessenger folder in Android Studio and let it sync.
#    (First sync downloads AGP 8.5.2, Kotlin 2.0.20, and all dependencies.)

# 2. Run unit tests (no device needed for most):
./gradlew :app:testDebugUnitTest

# 3. Build the debug APK:
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# 4. Install on a connected device:
./gradlew :app:installDebug
# or: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant the Bluetooth runtime permissions on first launch, keep Bluetooth on,
and put two devices in range — they discover each other on the Nearby tab.

## Protocol (v1)

```
 0      version (0x01)      52..59  timestamp millis (BE)
 1      type                60..63  payload length (BE)
 2      ttl                 64..    payload (<= 8 KiB)
 3      flags (bit0=relayed)
 4..19  message id (UUID)
 20..35 sender id (UUID)
 36..51 destination id (UUID, all-zero = broadcast)
```

Types: `HELLO` (identity + X25519 pubkey), `MSG` (E2E ciphertext),
`ACK` (delivery acknowledgement), `ROUTE` (distance-vector advert),
`BYE` (graceful disconnect). Every packet is validated on decode
(version, type, TTL bound, size bound, exact length, ±24h freshness);
anything else is dropped and counted.

## Security model

- Reviewed crypto only: **libsodium** via LazySodium-Android. No custom
  algorithms, no hardcoded secrets.
- Long-term X25519 identity keypair per device, generated with the OS CSPRNG,
  stored in `EncryptedSharedPreferences` (Android Keystore).
- Per-peer session keys via `crypto_kx` (authenticated key exchange); roles
  are deterministic (smaller device id = client) so no extra round trip.
- Payloads: XSalsa20-Poly1305 with fresh random 24-byte nonce per message.
- Relay nodes see ciphertext + routing headers only. **Routing metadata
  (who messages whom) is visible to relays** — inherent to relaying, and the
  Settings screen says so.
- Trust-on-first-use: first HELLO creates the contact; users verify by
  comparing key fingerprints out-of-band (Contacts → verify).

## Honest limitations (please read)

1. **Not compiled in the author's environment** — this project was written
   without an Android SDK available, so `./gradlew assembleDebug` has not
   been run here. Expect the usual first-sync dependency resolution; the code
   targets stable, documented APIs (AGP 8.5.2 / Kotlin 2.0.20 / Room 2.6.1).
2. **Multi-hop relay is implemented but NOT validated on hardware.** The
   relay engine, TTL/dupe/rate-limit logic and queue are unit-tested, but
   real 3–5-device relay behaviour needs physical phones (see TESTING.md).
   Treat multi-hop as **experimental** until then.
3. **Range is tens of metres** (typical BLE), not kilometres. Any claim of
   long-range mesh coverage would require hardware (and likely different
   radios) — this app makes no such claim.
4. **Text only.** Attachment support is stubbed as a modular interface point,
   not implemented.
5. **1:1 chats only.** No group chats.
6. **First contact needs physical proximity**: E2E requires the peer's public
   key from a HELLO; you cannot message someone you've never been in BLE
   range of. This is a deliberate security property, not a bug.
7. **Background operation is best-effort.** A foreground service
   (`connectedDevice` type) keeps the radio alive, but Doze and vendor
   battery managers can still throttle BLE on some devices.
8. **No forward secrecy for stored history**: session keys derive from the
   long-term identity keypair. Compromise of a device exposes its past
   messages. This is documented, not hidden.
9. Crypto round-trip test is `@Ignore`d for JVM runs (needs the libsodium
   native lib) — run it on-device; see TESTING.md §4.

## License

MIT — do whatever you want with it, but please keep the honesty section
honest if you redistribute.
