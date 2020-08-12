package com.waz.zclient.framework.data.buttons

import com.waz.zclient.framework.data.TestDataProvider
import java.util.UUID

data class ButtonTestData(
    val messageId: String,
    val buttonId: String,
    val title: String,
    val ordinal: Int,
    val state: Int
)

object ButtonsTestDataProvider : TestDataProvider<ButtonTestData>() {
    override fun provideDummyTestData(): ButtonTestData =
        ButtonTestData(
            messageId = UUID.randomUUID().toString(),
            buttonId = "1",
            title = "title",
            ordinal = 0,
            state = 0
        )
}
