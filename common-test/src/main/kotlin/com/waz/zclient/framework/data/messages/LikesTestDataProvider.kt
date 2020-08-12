package com.waz.zclient.framework.data.messages

import com.waz.zclient.framework.data.TestDataProvider
import java.util.UUID

data class LikesTestData(
    val messageId: String,
    val userId: String,
    val timeStamp: Int,
    val action: Int
)

object LikesTestDataProvider : TestDataProvider<LikesTestData>() {
    override fun provideDummyTestData(): LikesTestData =
        LikesTestData(
            messageId = UUID.randomUUID().toString(),
            userId = UUID.randomUUID().toString(),
            timeStamp = System.currentTimeMillis().toInt(),
            action = 0
        )
}
