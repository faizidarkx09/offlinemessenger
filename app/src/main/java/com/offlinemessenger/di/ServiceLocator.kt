package com.offlinemessenger.di

import android.content.Context
import androidx.room.Room
import com.offlinemessenger.ble.BleTransport
import com.offlinemessenger.crypto.CryptoManager
import com.offlinemessenger.crypto.SecureStore
import com.offlinemessenger.data.local.AppDatabase
import com.offlinemessenger.relay.AckTracker
import com.offlinemessenger.relay.RelayEngine
import com.offlinemessenger.repository.ChatRepository
import com.offlinemessenger.repository.PeerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph (no DI framework, keeps the build small and the
 * wiring explicit). Single instance per process, created by [OfflineMessengerApp].
 */
class ServiceLocator(private val appContext: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secureStore: SecureStore by lazy { SecureStore(appContext) }

    val cryptoManager: CryptoManager by lazy { CryptoManager(secureStore) }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "offline-messenger.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    val relayEngine: RelayEngine by lazy { RelayEngine() }

    val ackTracker: AckTracker by lazy { AckTracker() }

    val bleTransport: BleTransport by lazy {
        BleTransport(appContext, cryptoManager, secureStore, appScope)
    }

    val peerRepository: PeerRepository by lazy {
        PeerRepository(database.contactDao(), database.routeDao(), bleTransport, cryptoManager, appScope)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            appContext = appContext,
            messageDao = database.messageDao(),
            contactDao = database.contactDao(),
            relayQueueDao = database.relayQueueDao(),
            groupDao = database.groupDao(),
            conversationDao = database.conversationDao(),
            crypto = cryptoManager,
            store = secureStore,
            transport = bleTransport,
            peers = peerRepository,
            relayEngine = relayEngine,
            ackTracker = ackTracker,
            scope = appScope,
        )
    }
}
