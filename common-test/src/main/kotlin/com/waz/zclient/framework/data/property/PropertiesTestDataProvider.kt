package com.waz.zclient.framework.data.property

import com.waz.zclient.framework.data.TestDataProvider

data class PropertiesTestData(
    val key: String,
    val value: String
)

object PropertiesTestDataProvider : TestDataProvider<KeyValueTestData>() {
    override fun provideDummyTestData(): KeyValueTestData = KeyValueTestData("logging_in_user", "true")
}


