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


trait PasswordValidator {
  def isValidPassword(password: String): Boolean
}

object PasswordValidator {
  def apply(rule: String => Boolean): PasswordValidator = new PasswordValidator {
      def isValidPassword(password: String): Boolean = rule(password)
    }

  def combine(rules: PasswordValidator*): PasswordValidator =
    apply(p => rules.forall(_.isValidPassword(p)))

  def createStrongPasswordValidator(minLength: Int, maxLength: Int): PasswordValidator = combine(
    satisfiesPasswordLength(minLength, maxLength),
    containsLowercaseLetter,
    containsUppercaseLetter,
    containsDigit,
    containsSpecialCharacter
  )

  def satisfiesPasswordLength(minLength: Int, maxLength: Int): PasswordValidator = apply { p =>
    val length = p.codePointCount(0, p.length)
    length >= minLength && length <= maxLength
  }

  val containsLowercaseLetter: PasswordValidator =
    apply(p => "[a-z]".r.findFirstIn(p).isDefined)

  val containsUppercaseLetter: PasswordValidator =
    apply(p => "[A-Z]".r.findFirstIn(p).isDefined)

  val containsDigit: PasswordValidator =
    apply(p => "[0-9]".r.findFirstIn(p).isDefined)

  val containsSpecialCharacter: PasswordValidator =
    apply(p => "[^a-zA-Z0-9]".r.findFirstIn(p).isDefined)

}
