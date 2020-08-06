package com.waz.zclient.framework.data.property

import com.waz.zclient.framework.data.TestDataProvider

data class KeyValueTestData(
    val key: String,
    val value: String
)

object KeyValueTestDataProvider : TestDataProvider<KeyValueTestData>() {
    override fun data(): KeyValueTestData = KeyValueTestData("logging_in_user", "true")
}
