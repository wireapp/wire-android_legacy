package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.MessagesJSONEntity
import com.waz.zclient.shared.backup.datasources.local.MessagesLocalDataSource
import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class MessagesLocalDataSourceTest : UnitTest() {

    private val messagesEntity = MessagesEntity(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        conversationId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        messageType = "ConnectRequest",
        userId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        content = "[{\"type\":\"TextEmojiOnly\",\"content\":\" \"}]",
        protos = ByteArray(256) { it.toByte() },
        time = 0,
        firstMessage = false,
        members = null,
        recipient = "2f9e89c9-78a7-477d-8def-fbd7ca3846b5",
        email = null,
        name = "",
        messageState = "SENT",
        contentSize = 1,
        localTime = 0,
        editTime = 0,
        ephemeral = null,
        expiryTime = null,
        expired = false,
        duration = null,
        quote = null,
        quoteValidity = 0,
        forceReadReceipts = null,
        assetId = null
    )

    @Mock
    private lateinit var messagesDao: MessagesDao
    private lateinit var dataSource: MessagesLocalDataSource

    @Before
    fun setup() {
        dataSource = MessagesLocalDataSource(messagesDao)
    }

    @Test
    fun `serialize and deserialize the message's protos`(): Unit {
        val ints: IntArray? = BackupLocalDataSource.toIntArray(messagesEntity.protos)
        val result: ByteArray = BackupLocalDataSource.toByteArray(ints)!!

        result.size shouldEqual messagesEntity.protos?.size
        for (i in IntRange(0, result.size - 1)) {
            result[i] shouldEqual messagesEntity.protos?.get(i)
        }
    }

    @Test
    fun `two json entities made from one messages entity should be equal`(): Unit {
        val one = MessagesJSONEntity.from(messagesEntity)
        val two = MessagesJSONEntity.from(messagesEntity)

        // tests overriden hashCode and equals
        one shouldEqual two
    }

    @Test
    fun `convert a messages entity to a json entity and back`() = run {
        val messagesJSONEntity = MessagesJSONEntity.from(messagesEntity)
        val result: MessagesEntity = messagesJSONEntity.toEntity()

        result.id shouldEqual messagesEntity.id
    }

    @Test
    fun `convert an messages entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(messagesEntity)
        val result = dataSource.deserialize(jsonStr)

        result.id shouldEqual messagesEntity.id
    }
}