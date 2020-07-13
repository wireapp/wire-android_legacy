package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.PropertiesJSONEntity
import com.waz.zclient.shared.backup.datasources.local.PropertiesLocalDataSource
import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class PropertiesLocalDataSourceTest : UnitTest() {

    private val propertiesEntity = PropertiesEntity(
        key = "key",
        value = "value"
    )

    @Mock
    private lateinit var propertiesDao: PropertiesDao
    private lateinit var dataSource: PropertiesLocalDataSource

    @Before
    fun setup() {
        dataSource =  PropertiesLocalDataSource(propertiesDao)
    }

    @Test
    fun `convert a properties entity to a json entity and back`(): Unit = run {
        val keyValuesJSONEntity = PropertiesJSONEntity.from(propertiesEntity)
        val result: PropertiesEntity = keyValuesJSONEntity.toEntity()

        result shouldEqual propertiesEntity
    }

    @Test
    fun `convert a properties entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(propertiesEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual propertiesEntity
    }
}