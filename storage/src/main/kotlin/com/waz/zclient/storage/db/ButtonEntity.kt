package com.waz.zclient.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity

// must be the same as ButtonDataDao in Scala
@Entity(tableName = "Buttons", primaryKeys = ["message_id", "button_id"])
data class ButtonEntity(
    @ColumnInfo(name = "message_id")
    val messageId: String,

    @ColumnInfo(name = "button_id")
    val buttonId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "ordinal")
    val ordinal: Int,

    @ColumnInfo(name = "state")
    val state: Int
)
