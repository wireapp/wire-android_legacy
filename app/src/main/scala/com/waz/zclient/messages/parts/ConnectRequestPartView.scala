/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import com.waz.model.{UserData, UserId}
import com.waz.service.IntegrationsService
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views._
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future
import com.waz.threading.Threading._

class ConnectRequestPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  override val tpe: MsgPart = MsgPart.ConnectRequest

  lazy val chathead     : ChatHeadView    = findById(R.id.cv__row_conversation__connect_request__chat_head)
  lazy val label        : TextView        = findById(R.id.ttv__row_conversation__connect_request__label)
  lazy val userDetails  : UserDetailsView = findById(R.id.udv__row_conversation__connect_request__user_details)

  private val browser = inject[BrowserController]
  private val users   = inject[UsersController]
  private val integrations = inject[Signal[IntegrationsService]]

  private val user = for {
    self <- inject[Signal[UserId]]
    members <-  message.map(m => Set(m.userId) ++ Set(m.recipient).flatten)
    Some(user) <- members.find(_ != self).fold {
      Signal.const(Option.empty[UserData])
    } { uId =>
      users.user(uId).map(Some(_))
    }
  } yield user


  private val integration = for {
    usr <- user
    intService <- integrations
    integration <- Signal.from((usr.integrationId, usr.providerId) match {
      case (Some(i), Some(p)) => intService.getIntegration(p, i).map {
        case Right(integrationData) => Some(integrationData)
        case Left(_)                => None
      }
      case _ => Future.successful(None)
  })
  } yield integration

  Signal.zip(integration, user.map(_.id)).onUi {
    case (Some(i), _) => chathead.setIntegration(i)
    case (_, usr)     => chathead.loadUser(usr)
  }

  user.map(_.id).foreach(userDetails.setUserId)

  user.map(u => u.isWireBot).onUi {
    case false =>
      label.setTextColor(getStyledColor(R.attr.wirePrimaryTextColor))
      label.setText(R.string.content__message__connect_request__footer)
    case true =>
      label.setTextColor(getColor(R.color.accent_red))
      label.setText(R.string.generic_service_warning)
  }

}
