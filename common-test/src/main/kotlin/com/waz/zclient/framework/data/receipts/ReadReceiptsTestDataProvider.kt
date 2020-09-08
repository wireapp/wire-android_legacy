package com.waz.zclient.framework.data.receipts

import com.waz.zclient.framework.data.TestDataProvider
import java.util.UUID

data class ReadReceiptsTestData(
    val messageId: String,
    val userId: String,
    val timestamp: Long
)

object ReadReceiptsTestDataProvider : TestDataProvider<ReadReceiptsTestData>() {
    override fun provideDummyTestData(): ReadReceiptsTestData =
        ReadReceiptsTestData(
            messageId = UUID.randomUUID().toString(),
            userId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis()
        )
}
