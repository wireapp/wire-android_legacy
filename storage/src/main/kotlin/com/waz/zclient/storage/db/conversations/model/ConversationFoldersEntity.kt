package com.waz.zclient.storage.db.conversations.model

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ConversationFolders", primaryKeys = ["conv_id", "folder_id"])
data class ConversationFoldersEntity(
    @ColumnInfo(name = "conv_id")
    val convId: String,

    @ColumnInfo(name = "folder_id")
    val folderId: String
)

