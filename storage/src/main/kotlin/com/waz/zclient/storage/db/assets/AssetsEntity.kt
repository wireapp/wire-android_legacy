package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Assets2")
data class AssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "token")
    val token: String?,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "encryption")
    val encryption: String,

    @ColumnInfo(name = "mime")
    val mime: String,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    val sha: ByteArray,

    @ColumnInfo(name = "size")
    val size: Int,

    @ColumnInfo(name = "source")
    val source: String?,

    @ColumnInfo(name = "preview")
    val preview: String?,

    @ColumnInfo(name = "details")
    val details: String,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String?
)
