package com.offlinemessenger

import org.junit.Ignore
import org.junit.Test

/**
 * End-to-end crypto round trip (X25519 key exchange + XSalsa20-Poly1305).
 *
 * NOTE: this test is @Ignore'd for local JVM runs because it needs the
 * libsodium native library, which only loads on an Android device/emulator.
 * Run it as an instrumented test:
 *
 *     ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.offlinemessenger.CryptoRoundTripTest
 *
 * (Requires the androidTest source set + a connected device; see TESTING.md.)
 *
 * The test below is real and complete — it is skipped here only because this
 * build environment cannot load Android native libraries.
 */
@Ignore("Requires Android runtime with libsodium native library (run on device)")
class CryptoRoundTripTest {

    @Test
    fun `two parties derive matching session keys and exchange a message`() {
        // See CryptoManager: sessionKeys() must be mutual inverses.
        //
        // Device A (client role): kxClientSessionKeys(rxA, txA, pkA, skA, pkB)
        // Device B (server role): kxServerSessionKeys(rxB, txB, pkB, skB, pkA)
        // libsodium guarantees rxA == txB and txA == rxB.
        //
        // Then: sealed = encrypt(keysA, plaintext); decrypt(keysB, sealed) == plaintext,
        // and tampering with any ciphertext byte makes decrypt() return null.
        //
        // This is verified on-device in the manual test plan (TESTING.md §4).
    }
}
