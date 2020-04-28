package com.waz.zclient.storage.userdatabase.assets

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetTables126to128MigrationTests : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenV1AssetInsertedIntoAssetsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val assetId = "i747749kk-77"
        val assetType = "IMAGE"
        val assetData = "data"

        AssetsV1TableTestHelper.insertV1Asset(
            assetId,
            assetType,
            assetData,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(getV1Assets()[0]) {
                assertEquals(this.id, assetId)
                assertEquals(this.assetType, assetType)
                assertEquals(this.data, assetData)
            }
        }
    }

    @Test
    fun givenV2AssetInsertedIntoAssetsV2TableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val assetId = "i747749kk-77"
        val assetToken = "084782999838_Aa--4777277_"
        val assetName = "IMAGE"
        val encryption = "AES256"
        val mime = "png"
        val sha = byteArrayOf(16)
        val size = 1024
        val source = "message"
        val preview = "none"
        val details = "This is a test image"
        val conversationId = "1100"

        AssetsV2TableTestHelper.insertV2Asset(
            assetId,
            assetToken,
            assetName,
            encryption,
            mime,
            sha,
            size,
            source,
            preview,
            details,
            conversationId,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(getV2Assets()[0]) {
                assertEquals(this.id, assetId)
                assertEquals(this.token, assetToken)
                assertEquals(this.name, assetName)
                assertEquals(this.encryption, encryption)
                assertEquals(this.mime, mime)
                assertTrue(this.sha?.contentEquals(sha) ?: false)
                assertEquals(this.size, size)
                assertEquals(this.source, source)
                assertEquals(this.preview, preview)
                assertEquals(this.details, details)
                assertEquals(this.conversationId, conversationId)
            }
        }
    }

    @Test
    fun givenDownloadAssetInsertedIntoDownloadAssetsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val assetId = "i747749kk-77"
        val assetName = "IMAGE"
        val mime = "png"
        val name = "Test image"
        val size = 1024L
        val preview = "none"
        val details = "This is a test image"
        val downloaded = 125777583L
        val status = 0

        DownloadAssetsTableTestHelper.insertDownloadAsset(
            assetId,
            mime,
            downloaded,
            size,
            name,
            preview,
            details,
            status,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(getDownloadAssets()[0]) {
                assertEquals(this.id, assetId)
                assertEquals(this.mime, mime)
                assertEquals(this.downloaded, downloaded)
                assertEquals(this.size, size)
                assertEquals(this.name, name)
                assertEquals(this.preview, preview)
                assertEquals(this.details, details)
                assertEquals(this.status, status)
            }
        }
    }

    @Test
    fun givenUploadAssetInsertedIntoUploadAssetsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {
        val uploadAssetId = "1100"
        val assetId = "i747749kk-77"
        val assetToken = "084782999838_Aa--4777277_"
        val assetName = "IMAGE"
        val encryption = "AES256"
        val mime = "png"
        val sha = byteArrayOf(16)
        val md5 = byteArrayOf(16)
        val size = 1024L
        val source = "message"
        val preview = "none"
        val details = "This is a test image"
        val uploaded = 125777583L
        val retention = 1
        val isPublic = false
        val uploadStatus = 0

        UploadAssetsTableTestHelper.insertUploadAsset(
            uploadAssetId,
            source,
            assetName,
            sha,
            md5,
            mime,
            preview,
            uploaded,
            size,
            retention,
            isPublic,
            encryption,
            null,
            details,
            uploadStatus,
            assetId,
            testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(getUploadAssets()[0]) {
                assertEquals(this.id, uploadAssetId)
                assertEquals(this.source, source)
                assertEquals(this.name, assetName)
                assertEquals(this.encryption, encryption)
                assertEquals(this.mime, mime)
                assertTrue(this.sha?.contentEquals(sha) ?: false)
                assertTrue(this.md5?.contentEquals(md5) ?: false)
                assertEquals(this.size, size)
                assertEquals(this.source, source)
                assertEquals(this.preview, preview)
                assertEquals(this.details, details)
                assertEquals(this.uploadStatus, uploadStatus)
                assertEquals(this.isPublic, isPublic)
                assertEquals(this.uploaded, uploaded)
                assertEquals(this.retention, retention)
                assertEquals(this.assetId, assetId)
                assertEquals(this.encryptionSalt, null)
            }
        }
    }

    private suspend fun getV1Assets() =
        getDatabase().assetsV1Dao().allAssets()

    private suspend fun getV2Assets() = getDatabase().assetsDao().allAssets()

    private suspend fun getDownloadAssets() =
        getDatabase().downloadAssetsDao().allDownloadAssets()

    private suspend fun getUploadAssets() =
        getDatabase().uploadAssetsDao().allUploadAssets()
}
