package com.offlinemessenger.data.local

import androidx.room.TypeConverter
import com.offlinemessenger.data.local.entity.MessageStatus

class Converters {
    @TypeConverter
    fun statusToString(s: MessageStatus): String = s.name

    @TypeConverter
    fun stringToStatus(s: String): MessageStatus = MessageStatus.valueOf(s)
}
