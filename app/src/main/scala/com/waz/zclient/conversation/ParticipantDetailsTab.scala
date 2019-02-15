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
package com.waz.zclient.conversation

import android.content.Context
import android.support.v7.widget.{LinearLayoutManager, RecyclerView}
import android.widget.LinearLayout
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.service.ZMessaging
import com.waz.utils.events.{ClockSignal, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.SingleParticipantAdapter
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.GuestUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

import scala.concurrent.duration._

class ParticipantDetailsTab(val context: Context, callback: FooterMenuCallback, navButtonCallback: () => Unit) extends LinearLayout(context, null, 0) with ViewHelper {
  import com.waz.threading.Threading.Implicits.Ui

  inflate(R.layout.single_participant_tab_details)
  setOrientation(LinearLayout.VERTICAL)

  private lazy val zms                    = inject[Signal[ZMessaging]]
  private lazy val usersController        = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val userAccountsController = inject[UserAccountsController]

  private val footerMenu = returning(findById[FooterMenu](R.id.fm__footer)) {
    _.setCallback(callback)
  }

  private lazy val availabilityVisible = Signal(participantsController.otherParticipant.map(_.expiresAt.isDefined), usersController.availabilityVisible).map {
    case (true, _)         => false
    case (_, isTeamMember) => isTeamMember
  }

  private lazy val availabilityStatus = for {
    Some(uId) <- participantsController.otherParticipantId
    av        <- usersController.availability(uId)
  } yield av

  private lazy val availability = Signal(availabilityVisible, availabilityStatus).map {
    case (true, status) => Some(status)
    case (false, _)     => None
  }

  private lazy val readReceiptsTitle = zms.flatMap(_.propertiesService.readReceiptsEnabled)
    .map {
      case true  => R.string.read_receipts_info_title_enabled
      case false => R.string.read_receipts_info_title_disabled
    }.map(getString)

  private lazy val otherUserIsGuest = for {
    teamId <- zms.map(_.teamId)
    user   <- participantsController.otherParticipant
  } yield !user.isWireBot && user.isGuest(teamId)

  private lazy val timerText = for {
    expires <- participantsController.otherParticipant.map(_.expiresAt)
    clock   <- if (expires.isDefined) ClockSignal(5.minutes) else Signal.const(Instant.EPOCH)
  } yield expires match {
    case Some(expiresAt) => Some(GuestUtils.timeRemainingString(expiresAt.instant, clock))
    case _ => None
  }

  returning(findById[RecyclerView](R.id.single_participant_recycler_view)){ view =>
    view.setLayoutManager(new LinearLayoutManager(context))

    (for {
      user      <- participantsController.otherParticipant.head
      isGuest   <- otherUserIsGuest.head
      rrVisible <- userAccountsController.isTeam.head
      rrTitle   <- readReceiptsTitle.head
    } yield (user, isGuest, rrVisible, rrTitle)).foreach {
      case (user, isGuest, rrVisible, rrTitle) =>
        view.setAdapter(
          new SingleParticipantAdapter(
            user,
            isGuest,
            availability,
            timerText,
            if (rrVisible) Some(rrTitle) else None,
            inject[ThemeController].isDarkTheme
          )
        )
    }
  }

  private val leftActionStrings = for {
    isWireless     <- participantsController.otherParticipant.map(_.expiresAt.isDefined)
    isGroupOrBot   <- participantsController.isGroupOrBot
    canCreateConv  <- userAccountsController.hasCreateConvPermission
    isPartner      <- userAccountsController.isPartner
  } yield if (isWireless) {
    (R.string.empty_string, R.string.empty_string)
  } else if (!isPartner && !isGroupOrBot && canCreateConv) {
    (R.string.glyph__add_people, R.string.conversation__action__create_group)
  } else if (isPartner && !isGroupOrBot) {
    (R.string.empty_string, R.string.empty_string)
  } else {
    (R.string.glyph__conversation, R.string.empty_string)
  }

  participantsController.isGroupOrBot.flatMap {
    case false => userAccountsController.hasCreateConvPermission.map {
      case true => R.string.glyph__more
      case _    => R.string.empty_string
    }
    case _ => for {
      convId  <- participantsController.conv.map(_.id)
      remPerm <- userAccountsController.hasRemoveConversationMemberPermission(convId)
    } yield if (remPerm) R.string.glyph__minus else R.string.empty_string
  }.map(getString)
   .onUi(footerMenu.setRightActionText)

  leftActionStrings.onUi { case (icon, text) =>
    footerMenu.setLeftActionText(getString(icon))
    footerMenu.setLeftActionLabelText(getString(text))
  }

}


