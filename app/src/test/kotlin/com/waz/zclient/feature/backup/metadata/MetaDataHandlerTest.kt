package com.waz.zclient.feature.backup.metadata

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.io.file.SerializationFailure
import com.waz.zclient.framework.functional.assertRight
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.UUID

class MetaDataHandlerTest : UnitTest() {

    @Mock
    private lateinit var jsonConverter: JsonConverter<BackupMetaData>

    @Test
    fun `given the user's and app's data, when the metadata json file is created, then it consists of correct json string`() {
        val tempDir = createTempDir()
        val metaDataHandler = MetaDataHandler(jsonConverter, tempDir)

        `when`(jsonConverter.toJson(metaData)).thenReturn(metaDataJson)

        metaDataHandler.generateMetaDataFile(metaData)
            .assertRight {
                val contents = it.readText()
                assertEquals(metaDataJson, contents)
            }
    }

    @Test
    fun `given a valid metadata json, when it is read, return userId, handle, and backup version`() {
        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(metaDataJson)
        val metaDataHandler = MetaDataHandler(jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(metaDataJson)).thenReturn(metaData)

        metaDataHandler.readMetaData(metadataFile)
            .assertRight {
                assertEquals(platform, it.platform)
                assertEquals(userId, it.userId)
                assertEquals(userHandle, it.userHandle)
                assertEquals(backUpVersion, it.backUpVersion)
                assertEquals(clientId, it.clientId)
                assertEquals(version, it.version)
            }
    }

    @Test
    fun `given an invalid metadata json, when it is read, return a serialization failure`() {
        val invalidJson = "error"
        val serializationException = SerializationException("invalid json")

        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(invalidJson)

        val metaDataHandler = MetaDataHandler(jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(invalidJson)).thenThrow(serializationException)

        val res = metaDataHandler.readMetaData(metadataFile)
        assertEquals(Either.Left(SerializationFailure(serializationException)), res)
    }

    companion object {
        private const val platform = "Android"
        private val userId = UUID.randomUUID().toString()
        private val clientId = UUID.randomUUID().toString()
        private const val version = "3.54"
        private const val creationTime = "2020-09-08T10:00:00.000Z"
        private const val userHandle = "user"
        private const val backUpVersion = 0

        private val metaData = BackupMetaData(
                platform = platform,
                userId = userId,
                version = version,
                creationTime = creationTime,
                clientId = clientId,
                userHandle = userHandle,
                backUpVersion = backUpVersion
        )

        private val metaDataJson =
                """
            {
                "platform": "$platform",
                "user_id": "$userId",
                "version": "$version",
                "creation_time": "$creationTime",
                "client_id": "$clientId",
                "userHandle": "$userHandle",
                "backUpVersion": $backUpVersion
            }
        """.trimIndent()
    }
}
