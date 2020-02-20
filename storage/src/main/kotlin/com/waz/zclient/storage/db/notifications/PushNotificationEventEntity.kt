package com.waz.zclient.storage.db.notifications

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "PushNotificationEvents")
data class PushNotificationEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_index")
    val eventIndex: Int,

    @ColumnInfo(name = "pushId")
    var pushId: String?,

    @ColumnInfo(name = "decrypted")
    val isDecrypted: Boolean,

    @ColumnInfo(name = "event")
    var eventJson: String?,

    @ColumnInfo(name = "plain")
    var plain: ByteArray?,

    @ColumnInfo(name = "transient")
    val isTransient: Boolean
)
