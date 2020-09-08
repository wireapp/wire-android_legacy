package com.waz.zclient.framework.data.property

import com.waz.zclient.framework.data.TestDataProvider

data class PropertiesTestData(
    val key: String,
    val value: String
)

object PropertiesTestDataProvider : TestDataProvider<PropertiesTestData>() {
    override fun provideDummyTestData(): PropertiesTestData = PropertiesTestData("logging_in_user", "true")
}
