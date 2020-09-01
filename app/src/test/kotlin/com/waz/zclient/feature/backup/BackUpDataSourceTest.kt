package com.waz.zclient.feature.backup

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.io.BatchReader
import kotlinx.coroutines.runBlocking
import org.junit.Before

import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import java.io.File

class BackUpDataSourceTest : UnitTest() {

    @Mock
    private lateinit var backUpLocalDataSource: BackUpIOHandler<String, File>

    @Mock
    private lateinit var databaseLocalDataSource: BackUpIOHandler<Int, Unit>

    @Mock
    private lateinit var backUpBatchReader: BatchReader<List<String>>

    @Mock
    private lateinit var databaseBatchReader: BatchReader<List<Int>>

    @Captor
    private lateinit var backUpWriteIterator: ArgumentCaptor<BatchReader<List<String>>>

    @Captor
    private lateinit var databaseWriteIterator: ArgumentCaptor<BatchReader<List<Int>>>

    @Mock
    private lateinit var mapper: BackUpDataMapper<String, Int>

    private lateinit var backUpDataSource: BackUpDataSource<String, Int>

    @Before
    fun setUp() {
        backUpDataSource = object : BackUpDataSource<String, Int>() {
            override val databaseLocalDataSource: BackUpIOHandler<Int, Unit> =
                this@BackUpDataSourceTest.databaseLocalDataSource

            override val backUpLocalDataSource: BackUpIOHandler<String, File> =
                this@BackUpDataSourceTest.backUpLocalDataSource

            override val mapper: BackUpDataMapper<String, Int> =
                this@BackUpDataSourceTest.mapper
        }
    }

    @Test
    fun `given data sources and mapper, when backUp is called, then read from databaseLocalDataSource, create writeIterator, and write to backUpLocalDataSource`() {
        runBlocking {
            databaseBatchReader.mockNextItems(entitiesList)
            `when`(databaseLocalDataSource.readIterator()).thenReturn(databaseBatchReader)
            entitiesList.first().forEachIndexed { index, i ->
                `when`(mapper.fromEntity(i)).thenReturn(modelsList.first()[index])
            }

            backUpDataSource.saveBackup()

            verify(databaseLocalDataSource).readIterator()

            verify(backUpLocalDataSource).write(capture(backUpWriteIterator))
            backUpWriteIterator.value.assertItems(modelsList)
            entitiesList.first().forEach {
                verify(mapper).fromEntity(it)
            }
        }
    }

    @Test
    fun `given data sources and mapper, when restore is called, then reads from backUpLocalDataSource, creates writeIterator, and writes to databaseLocalDataSource`() {
        runBlocking {
            backUpBatchReader.mockNextItems(modelsList)
            `when`(backUpLocalDataSource.readIterator()).thenReturn(backUpBatchReader)
            `when`(databaseLocalDataSource.write(capture(databaseWriteIterator))).thenReturn(Either.Right(emptyList()))
            modelsList.first().forEachIndexed { index, s ->
                `when`(mapper.toEntity(s)).thenReturn(entitiesList.first()[index])
            }

            backUpDataSource.restoreBackup()

            verify(backUpLocalDataSource).readIterator()
            verify(databaseLocalDataSource).write(capture(databaseWriteIterator))
            databaseWriteIterator.value.assertItems(entitiesList)
            modelsList.first().forEach {
                verify(mapper).toEntity(it)
            }
        }
    }

    companion object {
        private val modelsList = listOf(listOf("one", "two", "three"))
        private val entitiesList = listOf(listOf(1, 2, 3))
    }
}
