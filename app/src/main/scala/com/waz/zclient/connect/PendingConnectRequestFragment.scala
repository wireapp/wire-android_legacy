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
package com.waz.zclient.connect

import android.content.Context
import android.os.Bundle
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import com.waz.ZLog.ImplicitTag._
import com.waz.api.User.ConnectionStatus
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.pages.main.participants.ProfileAnimation
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}

class PendingConnectRequestFragment extends BaseFragment[PendingConnectRequestFragment.Container]
  with FragmentHelper {

  import PendingConnectRequestFragment._
  import Threading.Implicits.Ui

  implicit def context: Context = getActivity

  private lazy val usersController  = inject[UsersController]
  private lazy val zms              = inject[Signal[ZMessaging]]

  private lazy val userId         = UserId(getArguments.getString(ArgUserId))
  private lazy val userRequester  = UserRequester.valueOf(getArguments.getString(ArgUserRequester))

  private lazy val user  = usersController.user(userId)
  private lazy val userConnection = user.map(_.connection)

  private lazy val isIgnoredConnection = userConnection.map(_ == ConnectionStatus.IGNORED)

  private lazy val userNameView = returning(view[TypefaceTextView](R.id.user_name)) { vh =>
    user.map(_.getDisplayName).onUi(t => vh.foreach(_.setText(t)))
  }
  private lazy val userHandleView = returning(view[TypefaceTextView](R.id.user_handle)) { vh =>
    user.map(user => StringUtils.formatHandle(user.handle.map(_.string).getOrElse("")))
      .onUi(t => vh.foreach(_.setText(t)))
  }
  private lazy val footerMenu = returning(view[FooterMenu](R.id.fm__footer)) { vh =>
    userConnection.map {
      case ConnectionStatus.IGNORED | ConnectionStatus.PENDING_FROM_USER => View.VISIBLE
      case ConnectionStatus.PENDING_FROM_OTHER if userRequester == UserRequester.PARTICIPANTS => View.VISIBLE
      case _ => View.GONE
    }.onUi { visibility => vh.foreach(_.setVisibility(visibility)) }

    userConnection.map {
      case ConnectionStatus.PENDING_FROM_OTHER if userRequester == UserRequester.PARTICIPANTS =>
        getString(R.string.glyph__minus)
      case _ => ""
    }.onUi { text => vh.foreach(_.setRightActionText(text)) }

    isIgnoredConnection.map {
      case true => R.string.glyph__plus
      case false => R.string.glyph__undo
    }.map(getString).onUi { text => vh.foreach(_.setLeftActionText(text)) }
    isIgnoredConnection.map {
      case true => R.string.send_connect_request__connect_button__text
      case false => R.string.connect_request__cancel_request__label
    }.map(getString).onUi { text => vh.foreach(_.setLeftActionLabelText(text)) }
  }

  private lazy val imageViewProfile = view[ImageView](R.id.pending_connect)

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    def defaultAnimation = super.onCreateAnimation(transit, enter, nextAnim)
    def isConvRequester = {
      val userRequester = UserRequester.valueOf(getArguments.getString(ArgUserRequester))
      userRequester == UserRequester.CONVERSATION
    }

    if (isConvRequester || nextAnim != 0) defaultAnimation
    else {
      val centerX = getOrientationIndependentDisplayWidth(getActivity) / 2
      val centerY = getOrientationIndependentDisplayHeight(getActivity) / 2
      val duration =
        if (enter) getInt(R.integer.open_profile__animation_duration)
        else getInt(R.integer.close_profile__animation_duration)
      val delay =
        if (enter) getInt(R.integer.open_profile__delay)
        else 0

      new ProfileAnimation(enter, duration, delay, centerX, centerY)
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_connect_request_pending, viewContainer, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    userHandleView

    val assetDrawable = new ImageAssetDrawable(
      user.map(_.picture).collect { case Some(p) => WireImage(p) },
      scaleType = ScaleType.CenterInside,
      request = RequestBuilder.Round
    )
    imageViewProfile.foreach(_.setImageDrawable(assetDrawable))

    userNameView.foreach { v =>
      val paddingTop =
        if (userRequester == UserRequester.PARTICIPANTS) 0
        else getDimenPx(R.dimen.wire__padding__regular)

      v.setPaddingRelative(0, paddingTop, 0, 0)
    }

    footerMenu.foreach(_.setCallback(new FooterMenuCallback {
      override def onLeftActionClicked(): Unit = userConnection.head foreach {
        case ConnectionStatus.IGNORED =>
          usersController.connectToUser(userId).foreach(_.foreach { _ =>
            getContainer.onAcceptedConnectRequest(userId)
          })
        case ConnectionStatus.PENDING_FROM_OTHER if userRequester == UserRequester.PARTICIPANTS =>
          usersController.connectToUser(userId).foreach(_.foreach { _ =>
            getContainer.onAcceptedConnectRequest(userId)
          })
        case ConnectionStatus.PENDING_FROM_USER =>
          zms.head.map(_.connection.cancelConnection(userId)).foreach { _ =>
            getActivity.onBackPressed()
          }
        case _ =>
      }
      override def onRightActionClicked(): Unit = userConnection.head foreach {
        case ConnectionStatus.PENDING_FROM_OTHER if userRequester == UserRequester.PARTICIPANTS =>
          getContainer.showRemoveConfirmation(userId)
        case _ =>
      }
    }))

  }

}

object PendingConnectRequestFragment {
  val Tag: String = classOf[PendingConnectRequestFragment].getName
  val ArgUserId = "ARGUMENT_USER_ID"
  val ArgUserRequester = "ARGUMENT_USER_REQUESTER"

  def newInstance(userId: UserId, userRequester: UserRequester): PendingConnectRequestFragment =
    returning(new PendingConnectRequestFragment)(fragment =>
      fragment.setArguments(
        returning(new Bundle) { args =>
          args.putString(ArgUserId, userId.str)
          args.putString(ArgUserRequester, userRequester.toString)
        }
      )
    )

  trait Container extends UserProfileContainer {
    def onAcceptedConnectRequest(userId: UserId): Unit
  }

}
