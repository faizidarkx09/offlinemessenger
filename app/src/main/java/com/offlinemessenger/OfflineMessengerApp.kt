package com.offlinemessenger

import android.app.Application
import com.offlinemessenger.di.ServiceLocator

/**
 * Application entry point. Builds the [ServiceLocator] graph once so every
 * component (BLE, crypto, database, repositories) shares one instance.
 */
class OfflineMessengerApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)
    }
}
