package com.waz.zclient.feature.backup

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import kotlinx.coroutines.runBlocking
import org.junit.Before

import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class BackUpDataSourceTest : UnitTest() {

    @Mock
    private lateinit var backUpLocalDataSource: BackUpIOHandler<String>

    @Mock
    private lateinit var databaseLocalDataSource: BackUpIOHandler<Int>

    @Captor
    private lateinit var backUpWriteIterator: ArgumentCaptor<Iterator<String>>

    @Captor
    private lateinit var databaseWriteIterator: ArgumentCaptor<Iterator<Int>>

    @Mock
    private lateinit var mapper: BackUpDataMapper<String, Int>

    private lateinit var backUpDataSource: BackUpDataSource<String, Int>

    @Before
    fun setUp() {
        backUpDataSource = object : BackUpDataSource<String, Int>() {
            override val databaseLocalDataSource: BackUpIOHandler<Int> =
                this@BackUpDataSourceTest.databaseLocalDataSource

            override val backUpLocalDataSource: BackUpIOHandler<String> =
                this@BackUpDataSourceTest.backUpLocalDataSource

            override val mapper: BackUpDataMapper<String, Int> =
                this@BackUpDataSourceTest.mapper
        }
    }

    @Test
    fun `given data sources and mapper, when backUp is called, then reads from databaseLocalDataSource, creates writeIterator, and writes to backUpLocalDataSource`() {
        runBlocking {
            `when`(databaseLocalDataSource.readIterator()).thenReturn(entitiesList.listIterator())
            entitiesList.forEachIndexed { index, i ->
                `when`(mapper.fromEntity(i)).thenReturn(modelsList[index])
            }

            backUpDataSource.backUp()

            verify(databaseLocalDataSource).readIterator()

            verify(backUpLocalDataSource).write(capture(backUpWriteIterator))
            backUpWriteIterator.value.withIndex().forEach {
                assertEquals(modelsList[it.index], it.value)
            }
            entitiesList.forEach {
                verify(mapper).fromEntity(it)
            }
        }
    }

    @Test
    fun `given data sources and mapper, when restore is called, then reads from backUpLocalDataSource, creates writeIterator, and writes to databaseLocalDataSource`() {
        runBlocking {
            `when`(backUpLocalDataSource.readIterator()).thenReturn(modelsList.listIterator())
            modelsList.forEachIndexed { index, s ->
                `when`(mapper.toEntity(s)).thenReturn(entitiesList[index])
            }

            backUpDataSource.restore()

            verify(backUpLocalDataSource).readIterator()
            verify(databaseLocalDataSource).write(capture(databaseWriteIterator))
            databaseWriteIterator.value.withIndex().forEach {
                assertEquals(entitiesList[it.index], it.value)
            }
            modelsList.forEach {
                verify(mapper).toEntity(it)
            }
        }
    }

    companion object {
        private val modelsList = listOf("one", "two", "three")
        private val entitiesList = listOf(1, 2, 3)
    }
}
