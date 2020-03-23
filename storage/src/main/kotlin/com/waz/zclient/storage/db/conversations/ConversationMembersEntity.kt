package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "ConversationMembers",
    primaryKeys = ["user_id", "conv_id"],
    indices = [
        Index(name = "ConversationMembers_conv", value = ["conv_id"]),
        Index(name = "ConversationMembers_userid", value = ["user_id"])
    ]
)
data class ConversationMembersEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "conv_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String
)
