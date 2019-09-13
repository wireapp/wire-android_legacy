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
package com.waz.utils

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FeatureSpec, Matchers}

@RunWith(classOf[JUnitRunner])
class PasswordValidatorSpec extends FeatureSpec with Matchers {

  private lazy val sut = PasswordValidator.createStrongPasswordValidator(minLength = 8, maxLength = 16)

  private val validPasswords = Seq(
    "Passw0rd!",            // plain old vanilla password
    "Pass w0rd!",           // contains space
    "Päss w0rd!",           // contains umlaut
    "Päss\uD83D\uDC3Cw0rd!" // contains emoji
  )

  private val invalidPasswords = Seq(
    "aA1!",                 // too short
    "aA1!aA1!aA1!aA1!aA1!", // too long
    "A1!A1!A1!A1!",         // no lowercase
    "a1!a1!a1!a1!",         // no uppercase
    "aA!aA!aA!aA!",         // no numbers
    "aA1aA1aA1aA1"          // no symbols
  )

  feature("Strong password") {

    scenario("validates valid passwords") {
      validPasswords.foreach { p =>
        assert(sut.isValidPassword(p), s"-> p: $p should be valid.")
      }
    }

    scenario("validates invalid passwords") {
      invalidPasswords.foreach { p =>
        assert(!sut.isValidPassword(p), s"-> p: $p should not be valid.")
      }
    }
  }
}
