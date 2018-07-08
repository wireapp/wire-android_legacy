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
  *//**
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
package com.waz.zclient.pages.main.connect

import android.os.Bundle
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment._
import com.waz.zclient.pages.main.participants.ProfileAnimation
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.ui.animation.fragment.FadeAnimation
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StringUtils}
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}

object BlockedUserProfileFragment {
  val Tag: String = ZLog.ImplicitTag.implicitLogTag
  val ARGUMENT_USER_ID = "ARGUMENT_USER_ID"
  val ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER"
  val STATE_IS_SHOWING_FOOTER_MENU = "STATE_IS_SHOWING_FOOTER_MENU"

  def newInstance(userId: String, userRequester: UserRequester): BlockedUserProfileFragment = {
    val newFragment = new BlockedUserProfileFragment
    val args = new Bundle
    args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString)
    args.putString(ARGUMENT_USER_ID, userId)
    newFragment.setArguments(args)
    newFragment
  }

  trait Container extends UserProfileContainer {
    def onUnblockedUser(restoredConversationWithUser: ConvId): Unit
  }

}

class BlockedUserProfileFragment extends BaseFragment[BlockedUserProfileFragment.Container] with FragmentHelper {

  private implicit lazy val ctx = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val accentColor = inject[AccentColorController].accentColor

  private lazy val userId = UserId(getArguments.getString(BlockedUserProfileFragment.ARGUMENT_USER_ID))
  private lazy val user = for {
    zms <- zms
    user <- zms.usersStorage.signal(userId)
  } yield user

  private lazy val pictureSignal: Signal[ImageSource] = user.map(_.picture).collect { case Some(pic) => WireImage(pic) }
  private lazy val profileDrawable = new ImageAssetDrawable(pictureSignal, ImageAssetDrawable.ScaleType.CenterInside, ImageAssetDrawable.RequestBuilder.Round)

  private var userRequester = Option.empty[UserRequester]
  private var isShowingFooterMenu = true
  private var goToConversationWithUser = false

  private lazy val unblockButton = returning(view[ZetaButton](R.id.zb__connect_request__unblock_button)) { vh =>
    accentColor.onUi(color => vh.foreach(_.setAccentColor(color.getColor)))
  }
  private lazy val cancelButton = returning(view[ZetaButton](R.id.zb__connect_request__ignore_button)) { vh =>
    accentColor.onUi(color => vh.foreach(_.setAccentColor(color.getColor)))
  }
  private lazy val smallUnblockButton = returning(view[ZetaButton](R.id.zb__connect_request__accept_button)) { vh =>
    accentColor.onUi(color => vh.foreach(_.setAccentColor(color.getColor)))
  }
  private lazy val unblockMenu = view[LinearLayout](R.id.ll__connect_request__accept_menu)
  private lazy val footerMenu = view[FooterMenu](R.id.fm__footer)
  private lazy val profileImageView = view[ImageView](R.id.blocked_user_picture)

  private lazy val userNameView = returning(view[TypefaceTextView](R.id.user_name)) { vh =>
    user.map(_.getDisplayName).onUi(name => vh.foreach(_.setText(name)))
  }

  private lazy val userUsernameView = returning(view[TypefaceTextView](R.id.user_handle)) { vh =>
    user.map(_.handle.map(h => StringUtils.formatHandle(h.string)).getOrElse("")).onUi(handle => vh.foreach(_.setText(handle)))
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    userRequester = Option(UserRequester.valueOf(getArguments.getString(ARGUMENT_USER_REQUESTER)))
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    var animation = super.onCreateAnimation(transit, enter, nextAnim)
    if ((getControllerFactory.getConversationScreenController.getPopoverLaunchMode ne DialogLaunchMode.AVATAR) && (getControllerFactory.getConversationScreenController.getPopoverLaunchMode ne DialogLaunchMode.COMMON_USER)) {
      val centerX = getOrientationIndependentDisplayWidth(getActivity) / 2
      val centerY = getOrientationIndependentDisplayHeight(getActivity) / 2

      // Fade out animation when starting conversation directly with this user when unblocking
      if (!goToConversationWithUser || enter) if (nextAnim != 0) {
        val duration = getInt(if (enter) R.integer.open_profile__animation_duration else R.integer.close_profile__animation_duration)
        val delay = if (enter) getInt(R.integer.open_profile__delay) else 0
        animation = new ProfileAnimation(enter, duration, delay, centerX, centerY)
      } else {
        goToConversationWithUser = false
        animation = new FadeAnimation(getInt(R.integer.framework_animation_duration_medium), 1, 0)
      }
    }
    animation
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_blocked_user_profile, viewContainer, false)


  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    userNameView
    userUsernameView
    profileImageView.foreach(_.setImageDrawable(profileDrawable))
    unblockButton.foreach(_.setIsFilled(true))
    cancelButton.foreach(_.setIsFilled(true))
    smallUnblockButton.foreach(_.setIsFilled(true))

    if (userRequester.contains(UserRequester.PARTICIPANTS)) {
      unblockButton.foreach(_.setVisibility(View.GONE))
      toggleUnblockAndFooterMenu(isShowingFooterMenu)
      footerMenu.foreach { menu =>
        menu.setLeftActionLabelText(getString(R.string.connect_request__footer__blocked_label))
        menu.setLeftActionText(getString(R.string.glyph__block))
        menu.setRightActionText(getString(R.string.glyph__minus))
        menu.setCallback(new FooterMenuCallback() {
          override def onLeftActionClicked(): Unit = toggleUnblockAndFooterMenu(false)
          override def onRightActionClicked(): Unit = getContainer.showRemoveConfirmation(userId)
        })
      }
      cancelButton.foreach { btn =>
        btn.setEnabled(true)
        btn.onClick(toggleUnblockAndFooterMenu(true))
      }
      smallUnblockButton.foreach { btn =>
        btn.setEnabled(true)
        btn.onClick(unblockUser(userId))
      }
      userNameView.foreach(_.setPaddingRelative(0, 0, 0, 0))
    } else {
      footerMenu.foreach(_.setVisibility(View.GONE))
      unblockMenu.foreach(_.setVisibility(View.GONE))
      unblockButton.foreach { btn =>
        btn.setVisibility(View.VISIBLE)
        btn.onClick(unblockUser(userId))
      }

      userNameView.foreach(_.setPaddingRelative(0, getDimenPx(R.dimen.wire__padding__regular), 0, 0))
    }
  }

  private def toggleUnblockAndFooterMenu(showFooterMenu: Boolean) = {
    footerMenu.foreach(_.setVisibility(if (showFooterMenu) View.VISIBLE else View.GONE))
    unblockMenu.foreach(_.setVisibility(if (showFooterMenu) View.GONE else View.VISIBLE))
    isShowingFooterMenu = showFooterMenu
  }

  private def unblockUser(userId: UserId) = {
    zms.head.map(_.connection.unblockConnection(userId))(Threading.Background)
    goToConversationWithUser = true
    getContainer.onUnblockedUser(ConvId(userId.str))
  }
}
