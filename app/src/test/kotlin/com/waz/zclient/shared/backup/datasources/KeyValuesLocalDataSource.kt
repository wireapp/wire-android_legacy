package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.KeyValuesJSONEntity
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class KeyValuesLocalDataSourceTest : UnitTest() {

    private val keyValuesEntity = KeyValuesEntity(
        key = "key",
        value = "value"
    )

    @Mock
    private lateinit var keyValuesDao: KeyValuesDao
    private lateinit var dataSource: KeyValuesLocalDataSource

    @Before
    fun setup() {
        dataSource = KeyValuesLocalDataSource(keyValuesDao)
    }

    @Test
    fun `convert a key values entity to a json entity and back`() = run {
        val keyValuesJSONEntity = KeyValuesJSONEntity.from(keyValuesEntity)
        val result: KeyValuesEntity = keyValuesJSONEntity.toEntity()

        result shouldEqual keyValuesEntity
    }

    @Test
    fun `convert a key values entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(keyValuesEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual keyValuesEntity
    }

}