package com.waz.zclient.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity

// must be the same as ButtonDataDao in Scala
@Entity(tableName = "Buttons", primaryKeys = ["message_id", "button_id"])
data class ButtonEntity(
    @ColumnInfo(name = "message_id", defaultValue = "")
    val messageId: String,

    @ColumnInfo(name = "button_id", defaultValue = "")
    val buttonId: String,

    @ColumnInfo(name = "title", defaultValue = "")
    val title: String,

    @ColumnInfo(name = "ordinal", defaultValue = "0")
    val ordinal: Int,

    @ColumnInfo(name = "state", defaultValue = "0")
    val state: Int
)
