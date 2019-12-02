package com.wire.roomdb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(KeyValue::class), version = 1)
abstract class UserDatabase : RoomDatabase()
