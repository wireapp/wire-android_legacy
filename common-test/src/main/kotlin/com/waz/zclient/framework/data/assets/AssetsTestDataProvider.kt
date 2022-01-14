package com.waz.zclient.framework.data.assets

import com.waz.zclient.framework.data.TestDataProvider

data class AssetsTestData(
    val id: String,
    val token: String?,
    val domain: String?,
    val name: String,
    val encryption: String,
    val mime: String,
    val sha: ByteArray?,
    val size: Int,
    val source: String?,
    val preview: String?,
    val details: String,
    val conversationId: String?
)

object AssetsTestDataProvider : TestDataProvider<AssetsTestData>() {

    private const val ASSET_FILE_SIZE = 1796931
    private const val SHA_256_BYTES = 256

    override fun provideDummyTestData() = AssetsTestData(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        token = "5UFmZ-Bmy1NP5Ninrc21XQ==",
        domain = "anta.wire.link",
        name = "",
        encryption = "AES_CBS__TzF3CPcCs6lCuLRISq64MAByIAm/TELGUj9XXdTHKF0",
        mime = "image/png",
        sha = ByteArray(SHA_256_BYTES) { it.toByte() },
        size = ASSET_FILE_SIZE,
        source = null,
        preview = null,
        details = "{\"ImageDetails\":{\"dimensions\":{\"width\":1080,\"height\":1080}}}",
        conversationId = null
    )
}
