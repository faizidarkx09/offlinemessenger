package com.offlinemessenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.offlinemessenger.data.local.dao.ContactDao
import com.offlinemessenger.data.local.dao.MessageDao
import com.offlinemessenger.data.local.dao.RelayQueueDao
import com.offlinemessenger.data.local.dao.RouteDao
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.RelayQueueEntity
import com.offlinemessenger.data.local.entity.RouteEntity

@Database(
    entities = [MessageEntity::class, ContactEntity::class, RelayQueueEntity::class, RouteEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun relayQueueDao(): RelayQueueDao
    abstract fun routeDao(): RouteDao
}
