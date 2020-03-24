package com.waz.zclient.storage.userdatabase.assets

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AssetTablesMigrationsTests : UserDatabaseMigrationTest() {

    @Test
    fun givenV1AssetInsertedIntoAssetsTable_whenMigrationDone_thenAssertDataIsStillIntact() {
        val assetId = "i747749kk-77"
        val assetType = "IMAGE"
        val assetData = "data"

        AssetsV1TableTestHelper.insertV1Asset(
            assetId,
            assetType,
            assetData,
            testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(getV1Assets()[0]) {
                assert(this.id == assetId)
                assert(this.assetType == assetType)
                assert(this.data == assetData)
            }
        }
    }

    @Test
    fun givenV2AssetInsertedIntoAssetsV2Table_whenMigrationDone_thenAssertDataIsStillIntact() {
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

        validateMigrations()

        runBlocking {
            with(getV2Assets()[0]) {
                assert(this.id == assetId)
                assert(this.token == assetToken)
                assert(this.name == assetName)
                assert(this.encryption == encryption)
                assert(this.mime == mime)
                assert(this.sha.contentEquals(sha))
                assert(this.size == size)
                assert(this.source == source)
                assert(this.preview == preview)
                assert(this.details == details)
                assert(this.conversationId == conversationId)
            }
        }
    }

    @Test
    fun givenDownloadAssetInsertedIntoDownloadAssetsTable_whenMigrationDone_thenAssertDataIsStillIntact() {
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

        validateMigrations()

        runBlocking {
            with(getDownloadAssets()[0]) {
                assert(this.id == assetId)
                assert(this.mime == mime)
                assert(this.downloaded == downloaded)
                assert(this.size == size)
                assert(this.name == assetName)
                assert(this.preview == preview)
                assert(this.details == details)
                assert(this.status == status)
            }
        }
    }

    @Test
    fun givenUploadAssetInsertedIntoUploadAssetsTable_whenMigrationDone_thenAssertDataIsStillIntact() {
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

        validateMigrations()

        runBlocking {
            with(getUploadAssets()[0]) {
                assert(this.id == uploadAssetId)
                assert(this.source == assetToken)
                assert(this.name == assetName)
                assert(this.encryption == encryption)
                assert(this.mime == mime)
                assert(this.sha.contentEquals(sha))
                assert(this.md5.contentEquals(md5))
                assert(this.size == size)
                assert(this.source == source)
                assert(this.preview == preview)
                assert(this.details == details)
                assert(this.uploadStatus == uploadStatus)
                assert(this.isPublic == isPublic)
                assert(this.uploaded == uploaded)
                assert(this.retention == retention)
                assert(this.assetId == assetId)
                assert(this.encryptionSalt == null)
            }
        }
    }

    private suspend fun getV1Assets() =
        getUserDatabase().assetsV1Dao().allAssets()

    private suspend fun getV2Assets() = getUserDatabase().assetsDao().allAssets()

    private suspend fun getDownloadAssets() =
        getUserDatabase().downloadAssetsDao().allDownloadAssets()

    private suspend fun getUploadAssets() =
        getUserDatabase().uploadAssetsDao().allUploadAssets()
}
