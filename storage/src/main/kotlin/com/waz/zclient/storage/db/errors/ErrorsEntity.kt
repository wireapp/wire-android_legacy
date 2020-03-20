package com.waz.zclient.storage.db.errors

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Errors")
data class ErrorsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "err_type")
    val errorType: String,

    @ColumnInfo(name = "users")
    val users: String,

    @ColumnInfo(name = "messages")
    val messages: String,

    @ColumnInfo(name = "conv_id")
    val conversationId: String?,

    @ColumnInfo(name = "res_code")
    val responseCode: Int,

    @ColumnInfo(name = "res_msg")
    val responseMessage: String,

    @ColumnInfo(name = "res_label")
    val responseLabel: String,

    @ColumnInfo(name = "time")
    val time: Int
)
