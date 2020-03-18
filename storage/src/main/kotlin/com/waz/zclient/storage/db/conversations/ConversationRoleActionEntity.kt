package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ConversationRoleAction",
    primaryKeys = ["label", "action", "conv_id"],
    indices = [Index(name = "ConversationRoleAction_convid", value = ["conv_id"])]
)
data class ConversationRoleActionEntity(
    @ColumnInfo(name = "conv_id")
    val convId: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "action")
    val action: String
)
