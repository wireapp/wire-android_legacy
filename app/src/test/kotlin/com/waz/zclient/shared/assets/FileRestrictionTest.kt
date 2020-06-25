package com.waz.zclient.shared.assets

import com.waz.service.assets.FileRestrictionList
import com.waz.zclient.UnitTest
import org.junit.Test
import org.amshove.kluent.shouldBe

class FileRestrictionTest : UnitTest() {
    @Test
    fun `given a disabled list, allow any filename`() {
        val fileRestrictions = FileRestrictionList( "", false)
        fileRestrictions.isAllowed("aaa.txt") shouldBe true
        fileRestrictions.isAllowed("BBB.jpg") shouldBe true
        fileRestrictions.isAllowed("ua.ua.MP4") shouldBe true
        fileRestrictions.isAllowed("sp3c14l_ch4s.avi") shouldBe true
        fileRestrictions.isAllowed("no_extension") shouldBe true
    }

    @Test
    fun `given an empty list, disallow all filenames`() {
        val fileRestrictions = FileRestrictionList(setOf(""), true)
        fileRestrictions.isAllowed("aaa.txt") shouldBe false
        fileRestrictions.isAllowed("BBB.jpg") shouldBe false
        fileRestrictions.isAllowed("ua.ua.MP4") shouldBe false
        fileRestrictions.isAllowed("sp3c14l_ch4s.avi") shouldBe false
        fileRestrictions.isAllowed("no_extension") shouldBe false
    }

    @Test
    fun `given a list, allow only filenames with allowed extensions`() {
        val fileRestrictions = FileRestrictionList(setOf("txt", "mp4"), true)
        fileRestrictions.isAllowed("aaa.txt") shouldBe  true
        fileRestrictions.isAllowed("BBB.jpg") shouldBe false
        fileRestrictions.isAllowed("ua.ua.MP4") shouldBe true
        fileRestrictions.isAllowed("sp3c14l_ch4s.avi") shouldBe false
        fileRestrictions.isAllowed("no_extension") shouldBe false
    }

    @Test
    fun `given a string of extensions separated by a comma, allow only filenames with them`() {
        val fileRestrictions = FileRestrictionList("txt, mp4", true)
        fileRestrictions.isAllowed("aaa.txt") shouldBe  true
        fileRestrictions.isAllowed("BBB.jpg") shouldBe false
        fileRestrictions.isAllowed("ua.ua.MP4") shouldBe true
        fileRestrictions.isAllowed("sp3c14l_ch4s.avi") shouldBe false
        fileRestrictions.isAllowed("no_extension") shouldBe false
    }

    @Test
    fun `allow for dots in a string of extensions`() {
        val fileRestrictions = FileRestrictionList(".txt, .mp4", true)
        fileRestrictions.isAllowed("aaa.txt") shouldBe  true
        fileRestrictions.isAllowed("BBB.jpg") shouldBe false
        fileRestrictions.isAllowed("ua.ua.MP4") shouldBe true
        fileRestrictions.isAllowed("sp3c14l_ch4s.avi") shouldBe false
        fileRestrictions.isAllowed("no_extension") shouldBe false
    }
}
