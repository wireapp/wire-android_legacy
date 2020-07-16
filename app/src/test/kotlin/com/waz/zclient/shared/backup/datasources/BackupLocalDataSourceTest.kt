package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class BackupLocalDataSourceTest : UnitTest() {

    @Mock
    private lateinit var keyValuesDao: KeyValuesDao

    @Test
    fun `read all data as a json array string`(): Unit = runBlocking {
        val dataSource = KeyValuesLocalDataSource(keyValuesDao)
        val keyValuesEntity = KeyValuesEntity("key", "value")

        `when`(keyValuesDao.getKeyValuesInBatch(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(listOf(keyValuesEntity))

        val jsonStr = dataSource.nextJSONArrayAsString()!!

        val result: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr)
        result.size shouldEqual 1
        result[0] shouldEqual keyValuesEntity
    }

    @Test
    fun `read only the first batch of data as a json array string`(): Unit = runBlocking {
        val batchSize = 3
        val entities = listOf(
            KeyValuesEntity("1", "a"),
            KeyValuesEntity("2", "b"),
            KeyValuesEntity("3", "c"),
            KeyValuesEntity("4", "d"),
            KeyValuesEntity("5", "e")
        )

        val dataSource = KeyValuesLocalDataSource(keyValuesDao, batchSize)

        `when`(keyValuesDao.getKeyValuesInBatch(3, 0)).thenReturn(entities.take(3))
        `when`(keyValuesDao.getKeyValuesInBatch(3, 3)).thenReturn(entities.drop(3).take(3))
        `when`(keyValuesDao.getKeyValuesInBatch(3, 5)).thenReturn(emptyList())

        val jsonStr1 = dataSource.nextJSONArrayAsString()!!
        val result1: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr1)
        result1.size shouldEqual 3

        val jsonStr2 = dataSource.nextJSONArrayAsString()!!
        val result2: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr2)
        result2.size shouldEqual 2

        val jsonStr3 = dataSource.nextJSONArrayAsString()
        jsonStr3 shouldEqual null
    }
}