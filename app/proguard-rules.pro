# Keep LazySodium / JNA native bindings
-keep class com.goterl.lazysodium.** { *; }
-keep class com.sun.jna.** { *; }
# Keep Room entities/DAOs
-keep class com.offlinemessenger.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase
