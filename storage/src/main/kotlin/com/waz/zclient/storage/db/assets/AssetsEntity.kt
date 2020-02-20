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
    var token: String?,

    @ColumnInfo(name = "name")
    var name: String?,

    @ColumnInfo(name = "encryption")
    var encryption: String?,

    @ColumnInfo(name = "mime")
    var mime: String?,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    var sha: ByteArray?,

    @ColumnInfo(name = "size")
    var size: Int?,

    @ColumnInfo(name = "source")
    var source: String?,

    @ColumnInfo(name = "preview")
    var preview: String?,

    @ColumnInfo(name = "details")
    var details: String?,

    @ColumnInfo(name = "conversation_id")
    var conversationId: String?
)
