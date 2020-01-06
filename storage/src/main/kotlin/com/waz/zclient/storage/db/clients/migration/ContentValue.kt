package com.waz.zclient.storage.db.clients.migration

sealed class ContentValue {
    data class StringValue(val value: String): ContentValue()
    data class LongValue(val value: Long): ContentValue()
    data class DoubleValue(val value: Double): ContentValue()
}
