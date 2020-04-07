package com.waz.zclient.core.network.logger

import com.waz.zclient.core.extension.replaceValue
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

class RedactedMessageLogger(
    private val defaultLogger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) : HttpLoggingInterceptor.Logger {

    @Suppress("TooGenericExceptionCaught")
    override fun log(message: String) {
        try {
            //TODO: only searches in direct children. should we convert to recursive?
            val messageJson = JSONObject(message).apply {
                replaceValue(FIELD_PASSWORD, REDACTED_VALUE)
            }
            logMessageDefault(messageJson.toString())
        } catch (ex: Exception) {
            logMessageDefault(message)
        }
    }

    private fun logMessageDefault(message: String) = defaultLogger.log(message)

    companion object {
        private const val FIELD_PASSWORD = "password"
        private const val REDACTED_VALUE = "*hidden*"
    }
}
