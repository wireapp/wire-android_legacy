package com.waz.zclient.core.network.logger

import com.google.gson.JsonParser
import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import okhttp3.logging.HttpLoggingInterceptor
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.verify

class RedactedMessageLoggerTest : UnitTest() {

    @Mock
    private lateinit var defaultLogger: HttpLoggingInterceptor.Logger

    @Captor
    private lateinit var messageCaptor: ArgumentCaptor<String>

    private lateinit var redactedMessageLogger: RedactedMessageLogger

    @Before
    fun setUp() {
        redactedMessageLogger = RedactedMessageLogger(defaultLogger)
    }

    @Test
    fun `given a message in non-json format, when log is called, then logs the message as it is`() {
        redactedMessageLogger.log(NON_JSON_MESSAGE)

        verify(defaultLogger).log(capture(messageCaptor))
        messageCaptor.value shouldBe NON_JSON_MESSAGE
    }

    @Test
    fun `given a message with password in it, when log is called, then hides password value`() {
        redactedMessageLogger.log(messageWithPassword("123456"))

        verify(defaultLogger).log(capture(messageCaptor))
        assertContentsEqual(messageCaptor.value, messageWithPassword(HIDDEN_VALUE))
    }

    @Test
    fun `given a message without any field to hide in it, when log is called, then logs message directly`() {
        redactedMessageLogger.log(MESSAGE_WITHOUT_HIDDEN_KEY)

        verify(defaultLogger).log(capture(messageCaptor))
        assertContentsEqual(messageCaptor.value, MESSAGE_WITHOUT_HIDDEN_KEY)
    }

    private fun assertContentsEqual(result: String, expected: String) {
        val parser = JsonParser()
        assert(parser.parse(result) == parser.parse(expected))
    }

    companion object {
        private const val NON_JSON_MESSAGE = "qwertyui123! xx5"
        private const val HIDDEN_VALUE = "*hidden*"
        private const val MESSAGE_WITHOUT_HIDDEN_KEY = """
            {
                "key" : "value"
            }
        """

        private fun messageWithPassword(value: String) = """
            {
                "key" : "value",
                "password" : "$value"
            }
        """
    }
}
