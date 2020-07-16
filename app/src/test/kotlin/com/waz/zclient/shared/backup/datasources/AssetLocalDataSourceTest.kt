package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.AssetLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.AssetsJSONEntity
import com.waz.zclient.storage.db.assets.AssetsDao
import com.waz.zclient.storage.db.assets.AssetsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class AssetLocalDataSourceTest : UnitTest() {

    private val assetsEntity = AssetsEntity(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        token = "5UFmZ-Bmy1NP5Ninrc21XQ==",
        name = "",
        encryption = "AES_CBS__TzF3CPcCs6lCuLRISq64MAByIAm/TELGUj9XXdTHKF0",
        mime = "image/png",
        sha = ByteArray(256) { it.toByte() },
        size = 1796931,
        source = null,
        preview = null,
        details = "{\"ImageDetails\":{\"dimensions\":{\"width\":1080,\"height\":1080}}}",
        conversationId = null
    )

    @Mock
    private lateinit var assetsDao: AssetsDao
    private lateinit var dataSource: AssetLocalDataSource

    @Before
    fun setup() {
        dataSource = AssetLocalDataSource(assetsDao)
    }

    @Test
    fun `two json entities made from one asset entity should be equal`(): Unit {
        val one = AssetsJSONEntity.from(assetsEntity)
        val two = AssetsJSONEntity.from(assetsEntity)

        // tests overriden hashCode and equals
        one shouldEqual two
    }

    @Test
    fun `convert an asset entity to a json entity and back`() = run {
        val assetsJSONEntity = AssetsJSONEntity.from(assetsEntity)
        val result: AssetsEntity = assetsJSONEntity.toEntity()

        result shouldEqual assetsEntity
    }

    @Test
    fun `convert an asset entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(assetsEntity)
        println(jsonStr)

        val result = dataSource.deserialize(jsonStr)

        result.id shouldEqual assetsEntity.id
    }
}