package com.waz.zclient.feature.backup.metadata

import com.waz.model.UserId
import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.io.file.SerializationFailure
import kotlinx.serialization.SerializationException
import org.amshove.kluent.shouldEqual
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.util.UUID

class MetaDataHandlerTest : UnitTest() {

    @Mock
    private lateinit var jsonConverter: JsonConverter<BackupMetaData>

    private val backUpVersion = 0
    private val userId = UserId.apply(UUID.randomUUID().toString())
    private val userHandle = "user"

    private val metaData = BackupMetaData(userId.str(), userHandle, backUpVersion)
    private val metaDataJson =
        """
            {
                "userId": "${userId.str()}",
                "userHandle": "$userHandle",
                "backUpVersion": $backUpVersion
            }
        """.trimIndent()

    @Test
    fun `given the user's id, the handle, and the backup version, when the metadata json file is created, then it consists of correct json string`() {
        val tempDir = createTempDir()
        val metaDataHandler = MetaDataHandler(backUpVersion, jsonConverter, tempDir)

        `when`(jsonConverter.toJson(metaData)).thenReturn(metaDataJson)

        metaDataHandler.generateMetaDataFile(userId, userHandle)
            .onFailure { fail(it.toString()) }
            .onSuccess {
                val contents = it.readText()
                assertEquals(metaDataJson, contents)
            }
    }

    @Test
    fun `given a valid metadata json, when it is read, return userId, handle, and backup version`() {
        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(metaDataJson)
        val metaDataHandler = MetaDataHandler(backUpVersion, jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(metaDataJson)).thenReturn(metaData)

        metaDataHandler.readMetaData(metadataFile)
            .onFailure { fail(it.toString()) }
            .onSuccess {
                assertEquals(userId.str(), it.userId)
                assertEquals(userHandle, it.userHandle)
                assertEquals(backUpVersion, it.backUpVersion)
            }
    }

    @Test
    fun `given an invalid metadata json, when it is read, return a serialization failure`() {
        val invalidJson = "error"
        val serializationException = SerializationException("invalid json")

        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(invalidJson)

        val metaDataHandler = MetaDataHandler(backUpVersion, jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(invalidJson)).thenThrow(serializationException)

        val res = metaDataHandler.readMetaData(metadataFile)
        assertEquals(Either.Left(SerializationFailure(serializationException)), res)
    }

    @Test
    fun `given a valid metadata json, when it is checked, return success`() {
        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(metaDataJson)
        val metaDataHandler = MetaDataHandler(backUpVersion, jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(metaDataJson)).thenReturn(metaData)

        metaDataHandler.checkMetaData(metadataFile, userId).onFailure { fail(it.toString()) }
    }

    @Test
    fun `given a valid metadata json, when it is checked and the userId is wrong, return a failure`() {
        val differentUserId = UserId.apply()
        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(metaDataJson)
        val metaDataHandler = MetaDataHandler(backUpVersion, jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(metaDataJson)).thenReturn(metaData)

        metaDataHandler.checkMetaData(metadataFile, differentUserId)
            .onSuccess { fail("UserId should be wrong") }
    }

    @Test
    fun `given a valid metadata json, when it is checked and the backup version is unhandled, return a failure`() {
        val tempDir = createTempDir()
        val metadataFile = createTempFile("metadata", ".json", tempDir)
        metadataFile.writeText(metaDataJson)

        // We should be able to handle all backup versions older (smaller) than the current one,
        // i.e. the current version should be the newest one.
        // If the version from the backup file is bigger than the current one, it means something's wrong.
        val currentBackupVersion = backUpVersion - 1
        val metaDataHandler = MetaDataHandler(currentBackupVersion, jsonConverter, tempDir)

        `when`(jsonConverter.fromJson(metaDataJson)).thenReturn(metaData)

        metaDataHandler.checkMetaData(metadataFile, userId)
            .onSuccess { fail("The backup version should be wrong") }
            .onFailure { it shouldEqual UnknownBackupVersion(backUpVersion) }
    }
}