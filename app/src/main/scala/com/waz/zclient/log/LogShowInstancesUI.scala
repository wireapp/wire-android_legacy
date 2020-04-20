/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.log

import com.evernote.android.job.Job
import com.waz.avs.VideoPreview
import com.waz.log.LogShow
import com.waz.service.tracking.TrackingEvent
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient.collection.controllers.CollectionController.ContentType
import com.waz.zclient.common.views.ChatHeadView.{ChatHeadViewOptions, CropShape, OverlayIcon}
import com.waz.zclient.core.images.AssetKey
import com.waz.zclient.glide.AssetRequest
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName
import com.waz.zclient.search.SearchController.SearchUserListState

/**
  * A collection of implicit `LogShow` instances for UI types.
  */
trait LogShowInstancesUI {
  import LogShow._

  implicit val AssetRequestLogShow: LogShow[AssetRequest] = logShowWithHash
  implicit val AssetKeyLogShow: LogShow[AssetKey] = logShowWithHash

  implicit val JobLogShow: LogShow[Job] = LogShow.logShowWithHash

  implicit val RichIntentLogShow: LogShow[RichIntent] =
    LogShow.createFrom { i =>
      l"""
         |Intent(
         |  action:           ${i.getAction.map(redactedString)},
         |  flags:            ${redactedString(i.getFlags.toString)},
         |  extras:           ${i.getExtras.map(e => redactedString(e.toString))},
         |  data:             ${i.getDataString.map(redactedString)},
         |  fromNotification: ${i.fromNotification},
         |  fromSharing:      ${i.fromSharing},
         |  startCall:        ${i.startCall},
         |  accountId:        ${i.accountId},
         |  convId:           ${i.convId},
         |  page:             ${i.page.map(redactedString)})
        """.stripMargin
  }

  implicit val SearchUserListStateLogShow: LogShow[SearchUserListState] =
    LogShow.createFrom {
      case SearchUserListState.NoUsers => l"NoUsers"
      case SearchUserListState.NoUsersFound => l"NoUsersFound"
      case SearchUserListState.Users(us) => l"Users(us: $us)"
      case SearchUserListState.NoServices => l"NoServices"
      case SearchUserListState.NoServicesFound => l"NoServicesFound"
      case SearchUserListState.LoadingServices => l"LoadingServices"
      case SearchUserListState.Services(ss) => l"Services(ss: $ss)"
      case SearchUserListState.Error(err) => l"Error(err: $err)"
    }

  implicit val DisplayNameLogShow: LogShow[DisplayName] =
    LogShow.createFrom {
      case DisplayName.Me => l"Me"
      case DisplayName.Other(name) => l"Other(name: ${redactedString(name)})"
    }

  implicit val VideoPreviewLogSow: LogShow[VideoPreview] = LogShow.logShowWithHash

  implicit val MsgBindOptionsLogShow: LogShow[MsgBindOptions] =
    LogShow.createFrom { o =>
      l"""
         |MsgBindOptions(
         |  position: ${o.position},
         |  isSelf: ${o.isSelf},
         |  isLast: ${o.isLast},
         |  isLastSelf: ${o.isLastSelf},
         |  isFirstUnread: ${o.isFirstUnread},
         |  listDimensions: ${o.listDimensions},
         |  isGroup: ${o.isGroup},
         |  teamId: ${o.teamId},
         |  canHaveLink: ${o.canHaveLink},
         |  selfId: ${o.selfId})
       """.stripMargin
    }

  implicit val TrackingEventLogShow: LogShow[TrackingEvent] =
    LogShow.createFrom { e =>
      l"""
         |TrackingEvent(
         |  name: ${redactedString(e.name)},
         |  properties: ${e.props})
       """.stripMargin
    }

  implicit val ContentTypeLogShow: LogShow[ContentType] =
    LogShow.createFrom { t =>
      l"ContentType(msgTypes: ${t.msgTypes}, typeFilter: ${t.typeFilter})"
    }

  implicit val CropShapeLogShow: LogShow[CropShape] =
    LogShow.createFrom(c => l"${showString(c.toString)}")

  implicit val OverlayIconLogShow: LogShow[OverlayIcon] =
    LogShow.createFrom(o => l"${showString(o.toString)}")

  implicit val ChatHeadViewOptionsLogShow: LogShow[ChatHeadViewOptions] =
    LogShow.createFrom { c =>
      import c._
      l"""ChatHeadViewOptions(picture: $picture, backgroundColor: $backgroundColor,
          | grayScale: $grayScale, initials: ${showString(initials)}, cropShape: $cropShape,
          | icon: $icon)
       """.stripMargin
    }
}
