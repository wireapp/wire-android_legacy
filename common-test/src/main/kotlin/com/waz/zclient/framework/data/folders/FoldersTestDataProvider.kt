package com.waz.zclient.framework.data.folders

import com.waz.zclient.framework.data.TestDataProvider

data class FoldersTestData(
    val id: String,
    val name: String,
    val type: Int
)

object FoldersTestDataProvider : TestDataProvider<FoldersTestData>() {
    override fun data() = FoldersTestData(
        id = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        name = "Work colleagues",
        type = 0
    )
}
