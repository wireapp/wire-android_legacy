package com.waz.zclient.storage.db.errors

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Errors")
data class ErrorsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "err_type", defaultValue = "")
    val errorType: String,

    @ColumnInfo(name = "users", defaultValue = "")
    val users: String,

    @ColumnInfo(name = "messages", defaultValue = "")
    val messages: String,

    @ColumnInfo(name = "conv_id")
    val conversationId: String?,

    @ColumnInfo(name = "res_code", defaultValue = "0")
    val responseCode: Int,

    @ColumnInfo(name = "res_msg", defaultValue = "")
    val responseMessage: String,

    @ColumnInfo(name = "res_label", defaultValue = "")
    val responseLabel: String,

    @ColumnInfo(name = "time", defaultValue = "0")
    val time: Int
)
