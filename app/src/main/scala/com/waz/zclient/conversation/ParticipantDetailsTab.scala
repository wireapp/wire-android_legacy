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
import android.view.View
import android.widget.{ImageView, LinearLayout, TextView}
import com.waz.service.ZMessaging
import com.waz.utils.events.{ClockSignal, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.ChatheadView
import com.waz.zclient.messages.UsersController
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIcon}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{GuestUtils, RichView}
import com.waz.zclient.views.ShowAvailabilityView
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

import scala.concurrent.duration._

class ParticipantDetailsTab(val context: Context, callback: FooterMenuCallback, navButtonCallback: () => Unit) extends LinearLayout(context, null, 0) with ViewHelper {

  inflate(R.layout.single_participant_tab_details)
  setOrientation(LinearLayout.VERTICAL)

  private val zms                    = inject[Signal[ZMessaging]]
  private val usersController        = inject[UsersController]
  private val participantsController = inject[ParticipantsController]
  private val userAccountsController = inject[UserAccountsController]
  private val themeController        = inject[ThemeController]
  private val convController         = inject[ConversationController]

  private val imageView = findById[ChatheadView](R.id.chathead)

  private val footerMenu = returning(findById[FooterMenu](R.id.fm__footer)) {
    _.setCallback(callback)
  }

  private lazy val notificationsButton = returning(findById[View](R.id.notifications_button)) {
    _.onClick(navButtonCallback())
  }

  private lazy val notificationsButtonNextIndicator = returning(findById[ImageView](R.id.notifications_button_next_indicator)) {
    _.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
  }

  private lazy val notificationsButtonValueText = returning(findById[TextView](R.id.notifications_button_value_text)) { view =>
    convController.currentConv
      .map(c => ConversationController.muteSetDisplayStringId(c.muted))
      .onUi(textId => view.setText(textId))
  }

  zms.map(_.teamId.isDefined).onUi {
    case true =>
      notificationsButton.setVisible(true)
      notificationsButtonNextIndicator
      notificationsButtonValueText
    case false =>
      notificationsButton.setVisible(false)
  }

  private lazy val guestIndication     = findById[LinearLayout](R.id.guest_indicator)
  private lazy val userAvailability    = findById[ShowAvailabilityView](R.id.participant_availability)
  private lazy val guestIndicatorTimer = findById[TypefaceTextView](R.id.expiration_time)

  private val otherUserIsGuest = for {
    teamId <- zms.map(_.teamId)
    user   <- participantsController.otherParticipant
  } yield !user.isWireBot && user.isGuest(teamId)

  private val leftActionStrings = for {
    isWireless     <- participantsController.otherParticipant.map(_.expiresAt.isDefined)
    isGroupOrBot   <- participantsController.isGroupOrBot
    hasPermissions <- userAccountsController.hasCreateConvPermission
  } yield if (isWireless) {
    (R.string.empty_string, R.string.empty_string)
  } else if (hasPermissions && !isGroupOrBot) {
    (R.string.glyph__add_people, R.string.conversation__action__create_group)
  } else {
    (R.string.glyph__conversation, R.string.empty_string)
  }

  otherUserIsGuest.onUi(guestIndication.setVisible(_))

  returning(findById[ImageView](R.id.participant_guest_indicator_icon)) { icon =>
    val color = if (themeController.isDarkTheme) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector
    icon.setImageDrawable(GuestIcon(color))
  }

  participantsController.otherParticipant.map(_.id){ imageView.setUserId }

  (for {
    expires <- participantsController.otherParticipant.map(_.expiresAt)
    clock   <- if (expires.isDefined) ClockSignal(5.minutes) else Signal.const(Instant.EPOCH)
  } yield expires match {
    case Some(expiresAt) => GuestUtils.timeRemainingString(expiresAt.instant, clock)
    case _ => ""
  }).onUi(guestIndicatorTimer.setText)

  Signal(participantsController.otherParticipant.map(_.expiresAt.isDefined), usersController.availabilityVisible).map {
    case (true, _)         => false
    case (_, isTeamMember) => isTeamMember
  }.onUi { userAvailability.setVisible }

  (for {
    Some(uId) <- participantsController.otherParticipantId
    av        <- usersController.availability(uId)
  } yield av).onUi(userAvailability.set)

  participantsController.isGroupOrBot.flatMap {
    case false => userAccountsController.hasCreateConvPermission.map {
      case true => R.string.glyph__add_people
      case _    => R.string.empty_string
    }
    case _ => Signal.const(R.string.glyph__conversation)
  }.map(getString)
   .onUi(footerMenu.setLeftActionText)

  participantsController.isGroupOrBot.flatMap {
    case false => userAccountsController.hasCreateConvPermission.map {
      case true => R.string.conversation__action__create_group
      case _    => R.string.empty_string
    }
    case _ => Signal.const(R.string.empty_string)
  }.map(getString)
   .onUi(footerMenu.setLeftActionLabelText)

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


