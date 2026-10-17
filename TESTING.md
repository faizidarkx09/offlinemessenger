# OfflineMessenger — Manual Test Guide

All radio tests need **physical Android devices** (API 26+). The Android
emulator cannot do real BLE between instances. Use the debug APK:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Automated checks first:

```bash
./gradlew :app:testDebugUnitTest
# expect: PacketTest, RoutingTest, DuplicateSuppressionTest,
#         AckTrackerTest, ExpiryTest, DatabaseTest — all green
# CryptoRoundTripTest is skipped on JVM (needs device; see §4)
```

---

## 1. Two devices — direct messaging (core path)

**Setup:** Install on phone A and phone B. Bluetooth ON, grant permissions,
keep the app in the foreground. Put the phones ~2 m apart.

- [ ] **Discovery:** On both phones, Nearby tab shows the other as an *app peer*
      (not under "Other Bluetooth devices") within ~20 s.
- [ ] **Link:** Within ~30 s the peer shows "linked" with a green dot.
      (Deterministic rule: the device with the smaller id opens the GATT link.)
- [ ] **Contact creation:** Contacts tab on each phone now lists the other
      (trust-on-first-use). Fingerprints are visible.
- [ ] **Send A→B:** Open the chat, send "hello". A shows ✓ (sent), then ✓✓
      **only after** B receives. B shows the message + increments unread.
- [ ] **Reply B→A:** works symmetrically; read state clears the badge.
- [ ] **Kill test:** Force-stop the app on B, send from A → message stays ⏳/✓
      (never ✓✓), then **FAILED** after ~30 s (3 attempts × 10 s timeout).
- [ ] **Bluetooth off:** Toggle Bluetooth off on B → A shows "Bluetooth is off"
      banner on Nearby; toggle back on → link re-establishes automatically.
- [ ] **Corrupt packets:** Nearby shows a "malformed packets ignored" counter
      only if corruption actually occurs (normally 0 — it proves the validation
      path is wired, not that corruption happened).
- [ ] **Permissions:** Fresh install → Permissions screen explains *why* before
      the system dialog. Deny → app stays on the Permissions screen (no crash,
      no fake "connected" state).

## 2. Security checks (2 devices)

- [ ] **Fingerprint verify:** Settings → read "My fingerprint" on A aloud;
      Contacts → tap A's entry on B → "Verify" → fingerprints match → ✓ verified.
- [ ] **Key rotation:** Reinstall the app on A (new keypair). B's contact for A
      must lose its verified badge (TOFU: key changed → re-verify).
- [ ] **Tamper test (advanced):** Requires a debug build with logging — confirm
      that a bit-flipped ciphertext is dropped (decrypt returns null), the
      message never appears, and no ACK is sent.
- [ ] **No-plaintext-on-relay:** With relay ON (see §3), confirm relayed
      traffic on the middle phone never creates a readable message row —
      only the store-and-forward queue counter moves.

## 3. Three devices — multi-hop relay (EXPERIMENTAL)

> This path is implemented and unit-tested but **not yet validated on
> hardware**. Run this section and report what breaks — that is the point.

**Setup:** A, B, C in a line. A↔B in range, B↔C in range, **A↔C out of range**
(use distance or a RF-blocking barrier; verify on Nearby tabs that A never
lists C as an app peer).

- [ ] Enable **Relay mode** in Settings on **B only**.
- [ ] Wait ~60 s (route adverts broadcast every 30 s).
- [ ] **Topology on A:** Network tab should show B as a *direct neighbour*
      (green, "1 hop") and C as a *known route* ("via B", amber "2 hops",
      explicitly labelled an estimate).
- [ ] **A→C message:** Send from A to C's contact. Expected: A shows ✓, then
      ✓✓ once C's ACK travels C→B→A. Check B's Network tab: queue counter
      briefly > 0 if B stored before forwarding.
- [ ] **TTL bound:** With relay ON everywhere, confirm a message never
      circulates forever: kill C, send A→C, watch B's queue — the entry must
      **expire** (max 24 h; for the test, confirm attempts stop and the row is
      eventually pruned rather than retried forever).
- [ ] **Duplicate storm:** Have A and C both in range of B, send one message
      A→C. B must forward it exactly once (dupe cache) — C receives one copy.
- [ ] **Relay OFF:** Disable relay on B, repeat A→C → message must stay
      PENDING/FAILED on A (B drops it). This proves opt-in is enforced.

## 4. Five devices — stress (EXPERIMENTAL)

Arrange a loose chain/star so no single phone sees all others.

- [ ] All 5 discover their direct neighbours; no duplicate GATT links
      (exactly one link per pair — check the "link opened by you/peer" labels).
- [ ] 3-hop delivery: ends of the chain exchange messages with relay ON on
      middle nodes; ACKs return; ✓✓ appears.
- [ ] Route expiry: power off a middle node for 3+ minutes; neighbours' Network
      tabs must drop its routes (2-min TTL) and stop trying that next hop.
- [ ] Rate limiting: burst-send 50 messages at once; the radio must stay
      responsive (≤20 forwards/sec/peer) and all messages eventually arrive
      exactly once.
- [ ] Battery note: with relay ON on all 5 for an hour, record battery drop
      vs. relay OFF — this calibrates the honest numbers for the Settings copy.

## 5. Crypto on-device test

`CryptoRoundTripTest` is `@Ignore`d on JVM. To run it on hardware, move it to
`app/src/androidTest/java/com/offlinemessenger/` and run:

```bash
./gradlew :app:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.offlinemessenger.CryptoRoundTripTest
```

It asserts: mutual `crypto_kx` session keys match across roles, encrypt→decrypt
round-trips, and any tampered byte fails authentication.

## 6. What "done" looks like

- §1 fully green on 2 phones → **direct messaging shippable.**
- §3 green on 3 phones → multi-hop graduates from experimental.
- §4 green on 5 phones → relay claims in README can be strengthened.
- Until then: do not demo or promise multi-hop/kilometre behaviour.
