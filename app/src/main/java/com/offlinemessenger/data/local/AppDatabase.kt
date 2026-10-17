package com.offlinemessenger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.offlinemessenger.data.local.dao.ContactDao
import com.offlinemessenger.data.local.dao.ConversationDao
import com.offlinemessenger.data.local.dao.GroupDao
import com.offlinemessenger.data.local.dao.MessageDao
import com.offlinemessenger.data.local.dao.RelayQueueDao
import com.offlinemessenger.data.local.dao.RouteDao
import com.offlinemessenger.data.local.entity.ContactEntity
import com.offlinemessenger.data.local.entity.ConversationEntity
import com.offlinemessenger.data.local.entity.GroupEntity
import com.offlinemessenger.data.local.entity.GroupMemberEntity
import com.offlinemessenger.data.local.entity.MessageEntity
import com.offlinemessenger.data.local.entity.RelayQueueEntity
import com.offlinemessenger.data.local.entity.RouteEntity

@Database(
    entities = [
        MessageEntity::class, ContactEntity::class, RelayQueueEntity::class,
        RouteEntity::class, GroupEntity::class, GroupMemberEntity::class,
        ConversationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun relayQueueDao(): RelayQueueDao
    abstract fun routeDao(): RouteDao
    abstract fun groupDao(): GroupDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        /** v1 -> v2: replies/reactions/forward columns + groups + conversation prefs. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToId TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToBody TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToSender TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN reactionsJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE messages ADD COLUMN forwarded INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS groups (
                        groupId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdBy TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        iAmAdmin INTEGER NOT NULL)""",
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS group_members (
                        groupId TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        publicKeyB64 TEXT NOT NULL,
                        isAdmin INTEGER NOT NULL,
                        PRIMARY KEY(groupId, deviceId),
                        FOREIGN KEY(groupId) REFERENCES groups(groupId) ON DELETE CASCADE)""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_group_members_groupId ON group_members(groupId)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS conversations (
                        conversationId TEXT NOT NULL PRIMARY KEY,
                        pinned INTEGER NOT NULL,
                        muted INTEGER NOT NULL)""",
                )
            }
        }
    }
}
