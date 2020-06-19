package com.waz.zclient.shared.assets

import com.waz.zclient.UnitTest
import org.junit.Test
import org.amshove.kluent.shouldBe

class FileWhitelistTest : UnitTest() {
    @Test
    fun `given an empty whitelist, disallow all filenames`() {
        val whitelist = FileWhitelist(setOf(""))
        whitelist.isWhiteListed("aaa.txt") shouldBe false
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe false
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }

    @Test
    fun `given a whitelist, allow only filenames with whitelist extensions`() {
        val whitelist = FileWhitelist(setOf("txt", "mp4"))
        whitelist.isWhiteListed("aaa.txt") shouldBe  true
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe true
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }
}
