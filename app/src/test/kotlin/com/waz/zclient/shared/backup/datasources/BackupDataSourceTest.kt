package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.KeyValuesLocalDataSource
import com.waz.zclient.shared.backup.datasources.local.PropertiesLocalDataSource
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.nio.file.Files

@ExperimentalCoroutinesApi
class BackupDataSourceTest : UnitTest() {
    @Mock
    private lateinit var keyValuesDao: KeyValuesDao

    @Mock
    private lateinit var propertiesDao: PropertiesDao

    private lateinit var keyValuesLocalDataSource: KeyValuesLocalDataSource
    private lateinit var propertiesLocalDataSource: PropertiesLocalDataSource
    private lateinit var backupDataSource: BackupDataSource

    private val keyValues = listOf(
        KeyValuesEntity("kv1", "a"),
        KeyValuesEntity("kv2", "b"),
        KeyValuesEntity("kv3", "c"),
        KeyValuesEntity("kv4", "d"),
        KeyValuesEntity("kv5", "e")
    )

    private val properties = listOf(
        PropertiesEntity("p1", "A"),
        PropertiesEntity("p2", "B"),
        PropertiesEntity("p3", "C"),
        PropertiesEntity("p4", "D"),
        PropertiesEntity("p5", "E")
    )

    @Before
    fun setup() {
        keyValuesLocalDataSource = KeyValuesLocalDataSource(keyValuesDao, 3)
        propertiesLocalDataSource = PropertiesLocalDataSource(propertiesDao, 2)
        val dataSources = listOf(keyValuesLocalDataSource, propertiesLocalDataSource)
        backupDataSource = BackupDataSource(dataSources)
    }

    @Test
    fun `create files and write down json arrays to them`() = runBlocking {
        `when`(keyValuesDao.size()).thenReturn(keyValues.size)
        `when`(keyValuesDao.getBatch(3, 0)).thenReturn(keyValues.take(3))
        `when`(keyValuesDao.getBatch(3, 3)).thenReturn(keyValues.drop(3).take(3))
        `when`(propertiesDao.size()).thenReturn(properties.size)
        `when`(propertiesDao.getBatch(2, 0)).thenReturn(properties.take(2))
        `when`(propertiesDao.getBatch(2, 2)).thenReturn(properties.drop(2).take(2))
        `when`(propertiesDao.getBatch(2, 4)).thenReturn(properties.drop(4).take(2))

        val targetDir = Files.createTempDirectory("backupDataSourceTest_${System.currentTimeMillis()}").toFile()
        targetDir.deleteOnExit()

        backupDataSource.writeAllToFiles(targetDir)

        val res: Map<String, String> = targetDir.walkTopDown().filter { it.name.endsWith(".json") }.map { file ->
            file.name to file.readText()
        }.toMap()

        res.size shouldEqual 5
        res.keys.filter { it.startsWith(propertiesLocalDataSource.name) }.size shouldEqual 3
        res.keys.filter { it.startsWith(keyValuesLocalDataSource.name) }.size shouldEqual 2
        res.values.all { it.isNotEmpty() } shouldEqual true
    }

    @Test
    fun `don't create files for empty db tables`() = runBlocking {
        `when`(keyValuesDao.size()).thenReturn(keyValues.size)
        `when`(keyValuesDao.getBatch(3, 0)).thenReturn(keyValues.take(3))
        `when`(keyValuesDao.getBatch(3, 3)).thenReturn(keyValues.drop(3).take(3))
        `when`(propertiesDao.size()).thenReturn(0)

        val targetDir = Files.createTempDirectory("backupDataSourceTest_${System.currentTimeMillis()}").toFile()
        targetDir.deleteOnExit()

        backupDataSource.writeAllToFiles(targetDir)

        val res: Map<String, String> = targetDir.walkTopDown().filter { it.name.endsWith(".json") }.map { file ->
            file.name to file.readText()
        }.toMap()

        res.size shouldEqual 2
        res.keys.filter { it.startsWith(propertiesLocalDataSource.name) }.size shouldEqual 0
        res.keys.filter { it.startsWith(keyValuesLocalDataSource.name) }.size shouldEqual 2
        res.values.all { it.isNotEmpty() } shouldEqual true
    }
}