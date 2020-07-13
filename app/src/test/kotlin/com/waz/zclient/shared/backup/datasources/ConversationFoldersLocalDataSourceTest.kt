package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.ConversationFoldersJSONEntity
import com.waz.zclient.shared.backup.datasources.local.ConversationFoldersLocalDataSource
import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class ConversationFoldersLocalDataSourceTest : UnitTest() {

    private val conversationFoldersEntity = ConversationFoldersEntity(
        convId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        folderId = "1"
    )

    @Mock
    private lateinit var conversationFoldersDao: ConversationFoldersDao
    private lateinit var dataSource: ConversationFoldersLocalDataSource

    @Before
    fun setup() {
        dataSource = ConversationFoldersLocalDataSource(conversationFoldersDao)
    }

    @Test
    fun `convert a conversation folders entity to a json entity and back`() = run {
        val conversationFoldersJSONEntity = ConversationFoldersJSONEntity.from(conversationFoldersEntity)
        val result:  ConversationFoldersEntity = conversationFoldersJSONEntity.toEntity()

        result shouldEqual conversationFoldersEntity
    }

    @Test
    fun `convert a conversation folders entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(conversationFoldersEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual conversationFoldersEntity
    }
}