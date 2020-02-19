package com.waz.zclient.storage.db.conversations.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ConversationRoleAction", primaryKeys = ["convId", "label", "action"])
data class ConversationRoleActionEntity(
    @ColumnInfo(name = "conv_id")
    val convId: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "action")
    val action: String
)
