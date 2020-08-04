package com.waz.zclient.feature.backup.conversations.folders

import com.waz.zclient.UnitTest
import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify

class FoldersBackUpDaoTest : UnitTest() {
    private lateinit var foldersBackupDao: FoldersBackUpDao

    @Mock
    private lateinit var foldersDao: FoldersDao

    @Before
    fun setup() {
        foldersBackupDao = FoldersBackUpDao(foldersDao)
    }

    @Test
    fun `given all items are requested, then get items from dao`() {
        runBlocking {
            foldersBackupDao.allItems()

            verify(foldersDao).allFolders()
        }
    }

    @Test
    fun `given entity, when insert is called, then insert same entity`() {
        runBlocking {
            val entity = Mockito.mock(FoldersEntity::class.java)

            foldersBackupDao.insert(entity)

            verify(foldersDao).insert(entity)
        }
    }
}
