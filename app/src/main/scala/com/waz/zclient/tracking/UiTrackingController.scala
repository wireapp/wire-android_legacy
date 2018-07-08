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
package com.waz.zclient.tracking

import android.content.Context
import com.waz.service.ZMessaging
import com.waz.service.tracking.ContributionEvent.Action
import com.waz.threading.Threading
import com.waz.utils.events.EventContext
import com.waz.zclient.cursor.{CursorController, CursorMenuItem}
import com.waz.zclient.{Injectable, Injector}

class UiTrackingController(implicit injector: Injector, ctx: Context, ec: EventContext) extends Injectable {
  val cursorController      = inject[CursorController]

  //TODO - slowly re-introduce removed localytics events to mixpanel

  import CursorMenuItem._
  cursorController.onCursorItemClick.onUi {
    case Ping => ZMessaging.globalModule.map(_.trackingService.contribution(Action.Ping))(Threading.Ui)
    case _    => //
  }

  cursorController.onMessageSent.onUi { _ =>
    ZMessaging.globalModule.map(_.trackingService.contribution(Action.Text))(Threading.Ui)
  }
}
