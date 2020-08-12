package com.waz.zclient.framework.data.buttons

import com.waz.zclient.framework.data.TestDataProvider

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
            messageId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
            buttonId = "1",
            title = "title",
            ordinal = 0,
            state = 0
        )
}
