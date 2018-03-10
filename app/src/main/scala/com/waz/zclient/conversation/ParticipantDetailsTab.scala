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
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog.ImplicitTag._
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.paintcode.GuestIcon
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, UiStorage}
import com.waz.zclient.views.ShowAvailabilityView
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{R, ViewHelper}

class ParticipantDetailsTab(val context: Context, callback: FooterMenuCallback) extends LinearLayout(context, null, 0) with ViewHelper {

  inflate(R.layout.single_participant_tab_details)
  setOrientation(LinearLayout.VERTICAL)

  private implicit val uiStorage     = inject[UiStorage]
  private val zms                    = inject[Signal[ZMessaging]]
  private val usersController        = inject[UsersController]
  private val participantsController = inject[ParticipantsController]
  private val userAccountsController = inject[UserAccountsController]
  private val themeController        = inject[ThemeController]

  private val imageView = findById[ImageView](R.id.chathead)

  private val footerMenu = returning(findById[FooterMenu](R.id.fm__footer)) {
    _.setCallback(callback)
  }

  private lazy val guestIndication     = findById[LinearLayout](R.id.guest_indicator)
  private lazy val userAvailability    = findById[ShowAvailabilityView](R.id.participant_availability)

  private val picture: Signal[ImageSource] =
    participantsController.otherParticipant.map(_.picture).collect { case Some(pic) => WireImage(pic) }

  private val otherUserIsGuest = for {
    teamId <- zms.map(_.teamId)
    user   <- participantsController.otherParticipant
  } yield !user.isWireBot && user.isGuest(teamId)

  otherUserIsGuest.onUi(guestIndication.setVisible(_))

  returning(findById[ImageView](R.id.participant_guest_indicator_icon)) { icon =>
    val color = if (themeController.isDarkTheme) R.color.wire__text_color_primary_dark_selector else R.color.wire__text_color_primary_light_selector
    icon.setImageDrawable(GuestIcon(color))
  }

  imageView.setImageDrawable(new ImageAssetDrawable(picture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  usersController.availabilityVisible.onUi(userAvailability.setVisible)

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

}


