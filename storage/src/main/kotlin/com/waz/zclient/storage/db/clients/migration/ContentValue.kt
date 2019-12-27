package com.waz.zclient.storage.db.clients.migration

sealed class ContentValue {
    data class StringValue(val value: String): ContentValue()
    data class LongValue(val value: Long): ContentValue()
    data class DoubleValue(val value: Double): ContentValue()

    companion object {
        fun apply(value: String): StringValue = StringValue(value)
        fun apply(value: Long):   LongValue   = LongValue(value)
        fun apply(value: Double): DoubleValue = DoubleValue(value)
    }
}
