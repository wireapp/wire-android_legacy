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

import com.waz.model.EmailAddress
import org.scalatest.{FeatureSpec, Ignore, Matchers}

class EmailAddressSpec extends FeatureSpec with Matchers {
  val atext = "ABCDEFGHIJKLMNOPQRSTUVWXYZ!#$%&'*+-/=?^_`{|}~abcdefghijklmnopqrstuvwxyz0123456789"
  val atextLC = "abcdefghijklmnopqrstuvwxyz!#$%&'*+-/=?^_`{|}~abcdefghijklmnopqrstuvwxyz0123456789"
  val label = "abcdefghijklmnopqrstuvwxyz-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
  val labelLC = "abcdefghijklmnopqrstuvwxyz-0123456789abcdefghijklmnopqrstuvwxyz"

  feature("email address normalization") {
    scenario("valid email address") {
      Map(
        "a@b.c"                                                    -> "a@b.c",
        "a@b-c.d"                                                  -> "a@b-c.d",
        "a@b-c.d-c"                                                -> "a@b-c.d-c",
        "a@b3-c.d4"                                                -> "a@b3-c.d4",
        "a@b-4c.d-c4"                                              -> "a@b-4c.d-c4",
        s"$atext.$atext@$label.$label"                             -> s"$atextLC.$atextLC@$labelLC.$labelLC",
        "Meep Møøp <Meep.Moop@EMail.me>"                           -> "meep.moop@email.me",
        "=?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?= <keld@some.domain>" -> "keld@some.domain",
        "=?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?=@some.domain"        -> "=?iso-8859-1?q?keld_j=f8rn_simonsen?=@some.domain",
        "\"Meep Møøp\" <Meep.Moop@EMail.me>"                       -> "meep.moop@email.me",
        "Meep   Møøp  <Meep.Moop@EMail.me>"                        -> "meep.moop@email.me",
        "Meep \"_the_\" Møøp <Meep.Moop@EMail.me>"                 -> "meep.moop@email.me",
        "   white@space.com    "                                   -> "white@space.com",
        "मानक \"हिन्दी\" <manaka.hindi@langua.ge>"                    -> "manaka.hindi@langua.ge"
      ) foreach { case (input, output) =>
        EmailAddress.parse(input) shouldEqual Some(EmailAddress(output))
      }
    }

    scenario("invalid email address") {
      Seq(
        "",
        atext,
        "a@b",
        "a@b3",
        "a@b.c-",
        "a@3b.c",
        "two words@something.org",
        "\"Quoted Address\"@some.domain", // valid according to RFC 2822
        "\"Meep Moop\" <\"The =^.^= Meeper\"@x.y", // valid according to RFC 2822
        "mailbox@[11.22.33.44]", // valid according to RFC 2822
        "some prefix with <two words@something.org>",
        "x@something_odd.com",
        "x@host.with?query=23&parameters=42",
        "some.mail@host.with.port:12345",
        "comments(inside the address)@are(actually).not(supported, but nobody uses them anyway)",  // valid according to RFC 2822
        "\"you need to close quotes@proper.ly",
        "\"you need\" <to.close@angle-brackets.too"
      ) foreach { input =>
        EmailAddress.parse(input) shouldBe None
      }
    }
  }
}
