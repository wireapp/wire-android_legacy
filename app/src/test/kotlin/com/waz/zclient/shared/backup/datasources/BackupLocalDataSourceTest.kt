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

        `when`(keyValuesDao.getBatch(ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(listOf(keyValuesEntity))

        val jsonStr = dataSource.iterator().next()

        val result: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr)
        result.size shouldEqual 1
        result[0] shouldEqual keyValuesEntity
    }

    @Test
    fun `read data as a json array strings in batches`(): Unit = runBlocking {
        val batchSize = 3
        val entities = listOf(
            KeyValuesEntity("1", "a"),
            KeyValuesEntity("2", "b"),
            KeyValuesEntity("3", "c"),
            KeyValuesEntity("4", "d"),
            KeyValuesEntity("5", "e")
        )

        val dataSource = KeyValuesLocalDataSource(keyValuesDao, batchSize)

        `when`(keyValuesDao.size()).thenReturn(entities.size)
        `when`(keyValuesDao.getBatch(3, 0)).thenReturn(entities.take(3))
        `when`(keyValuesDao.getBatch(3, 3)).thenReturn(entities.drop(3).take(3))

        val it = dataSource.iterator()
        val jsonStr1 = it.next()
        val result1: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr1)
        result1.size shouldEqual 3
        it.hasNext() shouldEqual true

        val jsonStr2 = it.next()
        val result2: List<KeyValuesEntity> = dataSource.deserializeList(jsonStr2)
        result2.size shouldEqual 2
        it.hasNext() shouldEqual false
    }
}