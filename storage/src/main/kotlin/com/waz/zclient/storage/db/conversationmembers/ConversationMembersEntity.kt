package com.waz.zclient.storage.db.conversationmembers

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ConversationMembers",
    primaryKeys = ["user_id", "conv_id"],
    indices = [Index(value = ["user_id", "conv_id"])]
)
data class ConversationMembersEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "conv_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String
)
