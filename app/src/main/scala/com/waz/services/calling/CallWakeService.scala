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
package com.waz.services.calling

import android.content.{Context, Intent => AIntent}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.service.ZMessaging
import com.waz.services.{FutureService, ZMessagingService}
import com.waz.utils.returning
import com.waz.utils.wrappers.Intent
import com.waz.zclient.log.LogUI._

import scala.concurrent.Future

/**
  * Background service waking up the calling service if a user performs an action via call notifications.
  */
class CallWakeService extends FutureService with ZMessagingService with DerivedLogTag {

  import CallWakeService._
  import com.waz.zclient.Intents.RichIntent

  override protected def onIntent(intent: AIntent, id: Int): Future[Any] = onZmsIntent(intent) { implicit zms =>
    debug(l"onIntent ${RichIntent(intent)}")
    if (intent != null && intent.hasExtra(ConvIdExtra)) {
      implicit val convId: ConvId = ConvId(intent.getStringExtra(ConvIdExtra))
      debug(l"convId: $convId")

      intent.getAction match {
        case ActionEnd           => end()
        case _                   => Future.successful({})
      }
    } else {
      error(l"missing intent extras")
      Future.successful({})
    }
  }

  private def end()(implicit zms: ZMessaging, conv: ConvId) =
    zms.calling.endCall(conv, skipTerminating = true)
}

object CallWakeService {
  val ConvIdExtra = "conv_id"

  val ActionEnd            = "com.waz.zclient.call.ACTION_END"

  def intent(context: Context, user: UserId, conv: ConvId, action: String): Intent = {
    returning(Intent(context, classOf[CallWakeService])) { i =>
      i.setAction(action)
      i.putExtra(ConvIdExtra, conv.str)
      i.putExtra(ZMessagingService.ZmsUserIdExtra, user.str)
    }
  }

  def endIntent(context: Context, user: UserId, conv: ConvId): Intent =
    intent(context, user, conv, ActionEnd)
}
