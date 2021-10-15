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
package com.waz.model

import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model
import com.waz.model.AccountData.Password
import com.waz.sync.client.AuthenticationManager
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.utils.Locales.currentLocaleOrdering
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}

/**
  * Each AccountData row in the ZGlobal database represents one logged in user. To be logged in, they must have a cookie. Upon being forcefully
  * logged out, this entry should be removed.
  *
  * Any information that needs to be deregistered can be kept here (e.g., de-registered cookies, tokens, clients etc)
  */

final case class AccountData(id:           UserId              = UserId(),
                             domain:       Domain              = Domain.Empty,
                             teamId:       Option[TeamId]      = None,
                             cookie:       Cookie              = Cookie(""), //defaults for tests
                             accessToken:  Option[AccessToken] = None,
                             pushToken:    Option[PushToken]   = None,
                             password:     Option[Password]    = None, //password never saved to database
                             ssoId:        Option[SSOId]       = None
                            ) extends Identifiable[UserId] {

  override def toString: String =
    s"""AccountData:
       | id:              $id
       | domain:          $domain
       | teamId:          $teamId
       | cookie:          $cookie
       | accessToken:     $accessToken
       | registeredPush:  $pushToken
       | password:        $password
       | ssoId:           $ssoId
    """.stripMargin
}

object AccountData {

  final case class Password(str: String) extends AnyVal {
    override def toString: String = str
  }

  //Labels can be used to revoke all cookies for a given client
  //TODO save labels and use them for cleanup later
  final case class Label(str: String) extends AnyVal {
    override def toString: String = str
  }

  object Label extends (String => Label) {
    def apply(): Label = Id.random()

    implicit object Id extends Id[Label] {
      override def random(): Label = Label(Uid().toString)
      override def decode(str: String): Label = Label(str)
    }
  }

  implicit object AccountDataDao extends Dao[AccountData, UserId] {
    val Id             = id[UserId]('_id, "PRIMARY KEY").apply(_.id)
    val Domain         = text[model.Domain]('domain, _.str, model.Domain(_))(_.domain)
    val TeamId         = opt(id[TeamId]('team_id)).apply(_.teamId)
    val Cookie         = text[Cookie]('cookie, _.str, AuthenticationManager.Cookie)(_.cookie)
    val Token          = opt(text[AccessToken]('access_token, JsonEncoder.encodeString[AccessToken], JsonDecoder.decode[AccessToken]))(_.accessToken)
    val RegisteredPush = opt(id[PushToken]('registered_push))(_.pushToken)
    val SSOId          = opt(json[SSOId]('sso_id))(_.ssoId)

    override val idCol = Id
    override val table = Table("ActiveAccounts", Id, Domain, TeamId, Cookie, Token, RegisteredPush, SSOId)

    override def apply(implicit cursor: DBCursor): AccountData = AccountData(Id, Domain, TeamId, Cookie, Token, RegisteredPush, None, SSOId)
  }
}

final case class PhoneNumber(str: String) extends AnyVal {
  override def toString: String = str
}

object PhoneNumber extends (String => PhoneNumber) {
  implicit def IsOrdered: Ordering[PhoneNumber] = currentLocaleOrdering.on(_.str)
  implicit val Encoder: JsonEncoder[PhoneNumber] = JsonEncoder.build(p => js => js.put("phone", p.str))
  implicit val Decoder: JsonDecoder[PhoneNumber] = JsonDecoder.lift(implicit js => PhoneNumber(JsonDecoder.decodeString('phone)))
}

final case class ConfirmationCode(str: String) extends AnyVal {
  override def toString: String = str
}
