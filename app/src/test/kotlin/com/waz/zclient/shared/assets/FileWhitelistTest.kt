package com.waz.zclient.shared.assets

import com.waz.service.assets.FileWhitelist
import com.waz.zclient.UnitTest
import org.junit.Test
import org.amshove.kluent.shouldBe

class FileWhitelistTest : UnitTest() {
    @Test
    fun `given a disabled whitelist, allow any filename`() {
        val whitelist = FileWhitelist( "", false)
        whitelist.isWhiteListed("aaa.txt") shouldBe true
        whitelist.isWhiteListed("BBB.jpg") shouldBe true
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe true
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe true
        whitelist.isWhiteListed("no_extension") shouldBe true
    }

    @Test
    fun `given an empty whitelist, disallow all filenames`() {
        val whitelist = FileWhitelist(setOf(""), true)
        whitelist.isWhiteListed("aaa.txt") shouldBe false
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe false
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }

    @Test
    fun `given a whitelist, allow only filenames with whitelist extensions`() {
        val whitelist = FileWhitelist(setOf("txt", "mp4"), true)
        whitelist.isWhiteListed("aaa.txt") shouldBe  true
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe true
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }

    @Test
    fun `given a string of extensions separated by a comma, allow only filenames with them`() {
        val whitelist = FileWhitelist("txt, mp4", true)
        whitelist.isWhiteListed("aaa.txt") shouldBe  true
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe true
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }

    @Test
    fun `allow for dots in a string of extensions`() {
        val whitelist = FileWhitelist(".txt, .mp4", true)
        whitelist.isWhiteListed("aaa.txt") shouldBe  true
        whitelist.isWhiteListed("BBB.jpg") shouldBe false
        whitelist.isWhiteListed("ua.ua.MP4") shouldBe true
        whitelist.isWhiteListed("sp3c14l_ch4s.avi") shouldBe false
        whitelist.isWhiteListed("no_extension") shouldBe false
    }
}
