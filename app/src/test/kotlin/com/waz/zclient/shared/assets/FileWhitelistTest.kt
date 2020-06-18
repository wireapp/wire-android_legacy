package com.waz.zclient.shared.assets

import com.waz.zclient.UnitTest
import org.junit.Test

class FileWhitelistTest : UnitTest() {
    @Test
    fun `given a disabled whitelist, allow any filename`() {
        val whitelist = FileWhitelist(false, "")
        assert(whitelist.isAllowed("aaa.txt"))
        assert(whitelist.isAllowed("BBB.jpg"))
        assert(whitelist.isAllowed("ua.ua.MP4"))
        assert(whitelist.isAllowed("sp3c14l_ch4s.avi"))
        assert(whitelist.isAllowed("no_extension"))
    }

    @Test
    fun `given an empty but enabled whitelist, disallow all filenames`() {
        val whitelist = FileWhitelist(true, "")
        assert(!whitelist.isAllowed("aaa.txt"))
        assert(!whitelist.isAllowed("BBB.jpg"))
        assert(!whitelist.isAllowed("ua.ua.MP4"))
        assert(!whitelist.isAllowed("sp3c14l_ch4s.avi"))
        assert(!whitelist.isAllowed("no_extension"))
    }

    @Test
    fun `given a whitelist, allow only filenames with whitelist extensions`() {
        val whitelist = FileWhitelist(true, "txt,mp4")
        assert(whitelist.isAllowed("aaa.txt"))
        assert(!whitelist.isAllowed("BBB.jpg"))
        assert(whitelist.isAllowed("ua.ua.MP4"))
        assert(!whitelist.isAllowed("sp3c14l_ch4s.avi"))
        assert(!whitelist.isAllowed("no_extension"))
    }
}