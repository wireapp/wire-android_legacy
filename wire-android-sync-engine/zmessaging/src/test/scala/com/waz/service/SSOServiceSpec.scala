/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.specs.ZSpec
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SSOServiceSpec extends ZSpec {

  val service = new SSOService(null)

  feature("Extracting token") {

    scenario("when string contains token") {
      noException shouldBe thrownBy { service.extractToken("wire-38400000-8cf0-11bd-b23e-10b96e4ef00d").get }
      noException shouldBe thrownBy { service.extractToken("wire-38400000-8CF0-11BD-B23E-10B96E4EF00D").get }
      noException shouldBe thrownBy { service.extractToken("just string wire-38400000-8CF0-11BD-B23E-10B96E4EF00D ").get }
    }

    scenario("when string does not contain token") {
      intercept[NoSuchElementException] { service.extractToken("notwire-38400000-8cf0-11bd-b23z-10b96e4ef00d").get }
      intercept[NoSuchElementException] { service.extractToken("Wire-338400000-8CF0-11BD-B23E-10B96E4EF00D").get }
    }

  }

  feature("login by domain") {

    scenario("given a valid email address, when extractDomain called, returns the part after @ sign") {
      val email = "somebody@team-22.gmail.com"

      val domain = service.extractDomain(email)

      domain shouldBe "team-22.gmail.com"
    }
  }

}
