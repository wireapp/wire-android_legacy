package com.waz.zclient.deeplinks

import android.content.Intent
import com.waz.model.{ConvId, UserId}

sealed trait DeepLink

object DeepLink {
  case class SSOLogin(token: String) extends DeepLink
  case class User(userId: UserId) extends DeepLink
  case class Conversation(convId: ConvId) extends DeepLink

  def apply(intent: Intent): Option[DeepLink] = Option(intent.getDataString).flatMap(apply)
  def apply(link: String): Option[DeepLink] = ???
}

