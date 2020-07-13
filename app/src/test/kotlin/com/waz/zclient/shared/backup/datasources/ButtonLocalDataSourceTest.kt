package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.ButtonJSONEntity
import com.waz.zclient.shared.backup.datasources.local.ButtonLocalDataSource
import com.waz.zclient.storage.db.buttons.ButtonDao
import com.waz.zclient.storage.db.buttons.ButtonEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class ButtonLocalDataSourceTest : UnitTest() {

    private val buttonEntity = ButtonEntity(
        messageId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        buttonId = "1",
        title = "Ok",
        ordinal = 0,
        state = 1
    )

    @Mock
    private lateinit var buttonDao: ButtonDao
    private lateinit var dataSource: ButtonLocalDataSource

    @Before
    fun setup() {
        dataSource = ButtonLocalDataSource(buttonDao)
    }

    @Test
    fun `convert a button entity to a json entity and back`() = run {
        val buttonJSONEntity = ButtonJSONEntity.from(buttonEntity)
        val result: ButtonEntity = buttonJSONEntity.toEntity()

        result shouldEqual buttonEntity
    }

    @Test
    fun `convert a button entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(buttonEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual buttonEntity
    }
}