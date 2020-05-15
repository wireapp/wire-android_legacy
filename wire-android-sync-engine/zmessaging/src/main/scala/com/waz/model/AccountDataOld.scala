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

import com.waz.log.LogSE._
import com.waz.db.Col._
import com.waz.db.{Col, Dao, DbTranslator}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.AccountDataOld.TriTeamId
import com.waz.model.UserPermissions.PermissionsMasks
import com.waz.model.otr.ClientId
import com.waz.sync.client.AuthenticationManager
import com.waz.utils.Locales.currentLocaleOrdering
import com.waz.utils.scrypt.SCrypt
import com.waz.utils.wrappers.{DB, DBContentValues, DBCursor, DBProgram}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder}
import com.waz.sync.client.AuthenticationManager.{AccessToken, Cookie}
import com.waz.utils.crypto.AESUtils
import org.json.JSONObject

import scala.collection.mutable

/**
  * Each AccountData row in the ZGlobal database represents one logged in user. To be logged in, they must have a cookie. Upon being forcefully
  * logged out, this entry should be removed.
  *
  * Any information that needs to be deregistered can be kept here (e.g., de-registered cookies, tokens, clients etc)
  */

case class AccountData(id:           UserId              = UserId(),
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
       | teamId:          $teamId
       | cookie:          $cookie
       | accessToken:     $accessToken
       | registeredPush:  $pushToken
       | password:        $password
       | ssoId:           $ssoId
    """.stripMargin
}

object AccountData {

  case class Password(str: String) {
    override def toString: String = str
  }

  //Labels can be used to revoke all cookies for a given client
  //TODO save labels and use them for cleanup later
  case class Label(str: String) {
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
    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.id)

    val TeamId         = opt(id[TeamId]('team_id)).apply(_.teamId)
    val Cookie         = text[Cookie]('cookie, _.str, AuthenticationManager.Cookie)(_.cookie)
    val Token          = opt(text[AccessToken]('access_token, JsonEncoder.encodeString[AccessToken], JsonDecoder.decode[AccessToken]))(_.accessToken)
    val RegisteredPush = opt(id[PushToken]('registered_push))(_.pushToken)
    val SSOId          = opt(json[SSOId]('sso_id))(_.ssoId)

    override val idCol = Id
    override val table = Table("ActiveAccounts", Id, TeamId, Cookie, Token, RegisteredPush, SSOId)

    override def apply(implicit cursor: DBCursor): AccountData = AccountData(Id, TeamId, Cookie, Token, RegisteredPush, None, SSOId)
  }
}

/**
 * This account data needs to be maintained for migration purposes - it can be deleted after a while (1 year?)
 */
case class AccountDataOld(override val id: AccountId                       = AccountId(),
                          teamId:          TriTeamId                       = Left({}),
                          pendingTeamName: Option[String]                  = None,
                          email:           Option[EmailAddress]            = None,
                          phone:           Option[PhoneNumber]             = None,
                          handle:          Option[Handle]                  = None,
                          registeredPush:  Option[PushToken]               = None,
                          pendingEmail:    Option[EmailAddress]            = None,
                          pendingPhone:    Option[PhoneNumber]             = None,
                          cookie:          Option[Cookie]                  = None,
                          password:        Option[String]                  = None,
                          accessToken:     Option[AccessToken]             = None,
                          userId:          Option[UserId]                  = None,
                          clientId:        Option[ClientId]                = None, //DEPRECATED! use the client id (state) stored in userpreferences instead
                          clientRegState:  String                          = "UNKNOWN", //DEPRECATED! use the client id (state) stored in userpreferences instead
                          privateMode:     Boolean                         = false,
                          regWaiting:      Boolean                         = false,
                          code:            Option[ConfirmationCode]        = None,
                          name:            Option[String]                  = None,
                          firstLogin:      Boolean                         = true,
                          private val _selfPermissions: Long      = 0,
                          private val _copyPermissions: Long      = 0
                      ) extends Identifiable[AccountId] {

  override def toString: String =
    s"""AccountData:
       | id:              $id
       | teamId:          $teamId
       | pendingTeamName: $pendingTeamName
       | email:           $email
       | phone:           $phone
       | handle:          $handle
       | registeredPush:  $registeredPush
       | pendingEmail:    $pendingEmail
       | pendingPhone:    $pendingPhone
       | cookie:          ${cookie.take(6)}
       | password:        In memory?: ${password.isDefined}
       | accessToken:     ${accessToken.take(6)}
       | userId:          $userId
       | privateMode:     $privateMode
       | regWaiting:      $regWaiting
       | code:            $code
       | name:            $name
       | firstLogin       $firstLogin
       | _selfPermissions ${_selfPermissions}
       | _copyPermissions ${_copyPermissions}
    """.stripMargin


  lazy val selfPermissions = UserPermissions.decodeBitmask(_selfPermissions)
  lazy val copyPermissions = UserPermissions.decodeBitmask(_copyPermissions)

  /**
    * A pending phone that matches the current phone signifies the user is trying to login. In this case, they're account
    * needs to be re-verified
    */
  def verified =
    (phone.isDefined && pendingPhone != phone) || email.isDefined

  def updatedNonPending = (pendingEmail, pendingPhone) match {
    case (Some(e), _) => copy(email = Some(e), pendingEmail = None)
    case (_, Some(p)) => copy(phone = Some(p), pendingPhone = None)
    case _ => this
  }

  def updatedPending = (email, phone) match {
    case (Some(e), _) => copy(pendingEmail = Some(e), email = None)
    case (None, Some(p)) => copy(pendingPhone = Some(p), phone = None)
    case _ => this
  }

  def addToRegistrationJson(o: JSONObject) =
    addCredentialsToJson(o, isLogin = !regWaiting)

  private def addCredentialsToJson(o: JSONObject, isLogin: Boolean = true) = {
    o.put("label", id.str)  // this label can be later used for cookie revocation

    (email.orElse(pendingEmail), handle, phone.orElse(pendingPhone), password, code) match {
      case (Some(e), _, _, Some(p), _) =>
        o.put("email", e.str)
        o.put("password", p)

      case (_, Some(h), _, Some(p), _) =>
        o.put("handle", h.string)
        o.put("password", p)

      case (_, _, Some(p), _, Some(c)) =>
        o.put("phone", p.str)
        o.put(if (isLogin) "code" else "phone_code" , c.str)

      case _ =>
    }
  }

  def autoLoginOnRegistration = phone.isDefined

  def updated(user: UserInfo): AccountDataOld =
    copy(userId = Some(user.id), email = user.email.orElse(email), pendingEmail = email.fold(pendingEmail)(_ => Option.empty[EmailAddress]), phone = user.phone.orElse(phone), handle = user.handle.orElse(handle), privateMode = user.privateMode.getOrElse(privateMode))

  def withTeam(teamId: Option[TeamId], permissions: Option[PermissionsMasks]): AccountDataOld =
    copy(teamId = Right(teamId), _selfPermissions = permissions.map(_._1).getOrElse(0), _copyPermissions = permissions.map(_._2).getOrElse(0))

  def isTeamAccount: Boolean =
    teamId.fold(_ => false, _.isDefined)

}

case class PhoneNumber(str: String) extends AnyVal {
  override def toString: String = str
}
object PhoneNumber extends (String => PhoneNumber) {
  implicit def IsOrdered: Ordering[PhoneNumber] = currentLocaleOrdering.on(_.str)
  implicit val Encoder: JsonEncoder[PhoneNumber] = JsonEncoder.build(p => js => js.put("phone", p.str))
  implicit val Decoder: JsonDecoder[PhoneNumber] = JsonDecoder.lift(implicit js => PhoneNumber(JsonDecoder.decodeString('phone)))
}

case class ConfirmationCode(str: String) extends AnyVal {
  override def toString: String = str
}

object AccountDataOld extends DerivedLogTag {

  //TODO Might be nice to have a TriOption type...
  //Left is undefined, Right(None) is no team, Right(Some()) is a team
  type TriTeamId = Either[Unit, Option[TeamId]]

  def apply(email: EmailAddress, password: String): AccountDataOld = {
    val id = AccountId()
    AccountDataOld(id, Left({}), email = Some(email), password = Some(password), phone = None, handle = None)
  }

  def computeHash(id: AccountId, password: String) =
    logTime(l"compute scrypt password hash") {
      if (password.isEmpty) "" else {
        val salt = id.str.replace("-", "").getBytes("utf8").take(16)
        val hash = SCrypt.scrypt(password.getBytes("utf8"), salt, 1024, 8, 1, 32)
        AESUtils.base64(hash)
      }
    }

  implicit object AccountDataOldDao extends Dao[AccountDataOld, AccountId] {
    val Id = id[AccountId]('_id, "PRIMARY KEY").apply(_.id)

    val Team = Col[TriTeamId]("teamId", "TEXT")(new DbTranslator[TriTeamId] {
      override def save(value: TriTeamId, name: String, values: DBContentValues) = value match {
        case Left(_)        => values.putNull(name)
        case Right(None)    => values.put(name, "")
        case Right(Some(t)) => values.put(name, t.str)
      }

      override def bind(value: TriTeamId, index: Int, stmt: DBProgram) = value match {
        case Left(_)        => stmt.bindNull(index)
        case Right(None)    => stmt.bindString(index, "")
        case Right(Some(t)) => stmt.bindString(index, t.str)
      }

      override def load(cursor: DBCursor, index: Int) =
        if (cursor.isNull(index)) Left({})
        else {
          val v = cursor.getString(index)
          if (v == "") Right(None) else Right(Some(TeamId(v)))
        }
    }).apply(_.teamId)

    val PendingTeamName = opt(text('pending_team_name))(_.pendingTeamName)
    val Email = opt(emailAddress('email))(_.email)
    val Phone = opt(phoneNumber('phone))(_.phone)
    val Handle = opt(handle('handle))(_.handle)
    val RegisteredPush = opt(id[PushToken]('registered_push))(_.registeredPush)
    val PendingEmail = opt(emailAddress('pending_email))(_.pendingEmail)
    val PendingPhone = opt(phoneNumber('pending_phone))(_.pendingPhone)
    val Cookie = opt(text[Cookie]('cookie, _.str, AuthenticationManager.Cookie))(_.cookie)
    val Token = opt(text[AccessToken]('access_token, JsonEncoder.encodeString[AccessToken], JsonDecoder.decode[AccessToken]))(_.accessToken)
    val UserId = opt(id[UserId]('user_id)).apply(_.userId)
    val ClientId = opt(id[ClientId]('client_id))(_.clientId)
    val ClientRegState = text('reg_state)(_.clientRegState)
    val PrivateMode = bool('private_mode)(_.privateMode)
    val RegWaiting = bool('reg_waiting)(_.regWaiting)
    val Code = opt(text[ConfirmationCode]('code, _.str, ConfirmationCode))(_.code)
    val Name = opt(text('name))(_.name)
    val FirstLogin = bool('first_login)(_.firstLogin)
    val SelfPermissions = long('self_permissions)(_._selfPermissions)
    val CopyPermissions = long('copy_permissions)(_._copyPermissions)

    override val idCol = Id
    override val table = Table("Accounts", Id, Team, Email, PendingEmail, PendingPhone, PendingTeamName, Cookie, Phone, Token, UserId, ClientId, ClientRegState, Handle, PrivateMode, RegWaiting, RegisteredPush, Code, Name, FirstLogin, SelfPermissions, CopyPermissions)

    override def apply(implicit cursor: DBCursor): AccountDataOld = AccountDataOld(Id, Team, PendingTeamName, Email, Phone, Handle, RegisteredPush, PendingEmail, PendingPhone, Cookie, None, Token, UserId, ClientId, ClientRegState, PrivateMode, RegWaiting, Code, Name, FirstLogin, SelfPermissions, CopyPermissions)

    def findByEmail(email: EmailAddress)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Email.name} = ? OR ${PendingEmail.name} = ?", Array(email.str, email.str), null, null, null))

    def findByPhone(phone: PhoneNumber)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Phone.name} = ? OR ${PendingPhone.name} = ?", Array(phone.str, phone.str), null, null, null))

    def deleteForEmail(email: EmailAddress)(implicit db: DB) = delete(Email, Some(email))

    def findLoggedIn()(implicit db: DB) =
      iterating(db.query(table.name, null, s"${Cookie.name} IS NOT NULL", null, null, null, null))

    def findByPendingTeamName(name: String)(implicit db: DB) =
      iterating(db.query(table.name, null, s"${PendingTeamName.name} = ?", Array(name), null, null, null))
  }
}
