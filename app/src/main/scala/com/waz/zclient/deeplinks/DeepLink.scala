package com.waz.zclient.deeplinks

import java.util.UUID

import android.content.Intent
import com.waz.model.{ConvId, UserId}
import com.waz.zclient.BuildConfig

import scala.util.Try
import scala.util.matching.Regex

sealed trait DeepLink

object DeepLink {

  private val Scheme = BuildConfig.CUSTOM_URL_SCHEME

  private val DeepLinkRegex: Regex = s"$Scheme:\\/{2}(.+)\\/(.+)".r("host", "data")

  private val UuidRegex: Regex =
    "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}".r

  private val SsoTokenRegex: Regex = s"wire-(${UuidRegex.regex})".r("id")

  def apply(intent: Intent): Option[DeepLink] = Option(intent.getDataString).flatMap(apply)

  def apply(link: String): Option[DeepLink] = DeepLinkRegex.findFirstMatchIn(link) match {
    case None => None
    case Some(result) =>
      val (host, data) = (result.group("host"), result.group("data"))

      host match {
        case "start-sso" => SSOLogin.apply(data)
        case "user" => User.apply(data)
        case "conversation" => Conversation.apply(data)
        case _ => None
      }
  }

  case class SSOLogin(token: UUID) extends DeepLink

  object SSOLogin {
    def apply(token: String): Option[SSOLogin] =
      SsoTokenRegex.findFirstMatchIn(token) match {
        case Some(result) =>
          Try { UUID.fromString(result.group("id")) }.toOption.map(id => SSOLogin(id))
        case _ => None
      }
  }

  case class User(userId: UserId) extends DeepLink

  object User {
    def apply(id: String): Option[User] = UuidRegex.findFirstIn(id).map(i => User(UserId(i)))
  }

  case class Conversation(convId: ConvId) extends DeepLink

  object Conversation {
    def apply(id: String): Option[Conversation] = UuidRegex.findFirstIn(id).map(i => Conversation(ConvId(i)))
  }

}
