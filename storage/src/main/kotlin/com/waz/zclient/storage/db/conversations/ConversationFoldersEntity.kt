package com.waz.zclient.storage.db.conversations

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "ConversationFolders", primaryKeys = ["conv_id", "folder_id"])
data class ConversationFoldersEntity(
    @ColumnInfo(name = "conv_id", defaultValue = "")
    val convId: String,

    @ColumnInfo(name = "folder_id", defaultValue = "")
    val folderId: String
)
