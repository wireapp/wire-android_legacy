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
package com.waz.zclient.preferences.views

import android.content.Context
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, ImageView}
import com.waz.api.impl.AccentColor
import com.waz.content.{AccountStorage, TeamsStorage}
import com.waz.model._
import com.waz.service.AccountsService
import com.waz.utils.NameParts
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.drawables.TeamIconDrawable
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.utils.{RichView, UiStorage, UserSignal}
import com.waz.zclient.{R, ViewHelper}


class ProfileAccountTab(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.view_account_tab)
  setLayoutParams(new LayoutParams(context.getResources.getDimensionPixelSize(R.dimen.teams_tab_width), ViewGroup.LayoutParams.MATCH_PARENT))

  private implicit val uiStorage = inject[UiStorage]
  private val userAccountsController = inject[UserAccountsController]

  val icon            = findById[ImageView](R.id.team_icon)
  val iconContainer   = findById[FrameLayout](R.id.team_icon_container)
  val unreadIndicator = findById[CircleView](R.id.unread_indicator_icon)

  val drawable = new TeamIconDrawable

  private val accountId = Signal[UserId]()

  lazy val accounts = inject[AccountsService]
  lazy val accStorage = inject[AccountStorage]

  val account = accountId.flatMap(accStorage.signal).disableAutowiring()

  val selected = Signal(false)

  val zmsSelected = (for {
    acc    <- accountId
    active <- accounts.activeAccountId
  } yield active.contains(acc))
    .disableAutowiring()

  zmsSelected.onUi{ selected ! _ }

  val teamAndUser: Signal[(UserData, Option[TeamData])] =
    for{
      teamId <- account.map(_.teamId)
      userId <- account.map(_.id)
      user   <- UserSignal(userId)
      team   <- teamId match {
        case Some(t) => inject[TeamsStorage].optSignal(t)
        case _ => Signal.const(Option.empty[TeamData])
      }
    } yield (user, team)

  private val unreadCount = for {
    accountId <- accountId
    count  <- userAccountsController.unreadCount.map(_.get(accountId))
  } yield count.getOrElse(0)

  private val accentColor = teamAndUser.map(tau => AccentColor(tau._1.accent).getColor())

  private val picture = teamAndUser.map{
    case (user, Some(team)) =>
      // TODO use team icon when ready
      Option.empty[AssetId]
    case (user, _) =>
      user.picture
  }

  private val initials = teamAndUser.map {
    case (_, Some(team)) => team.name
    case (user, _) => user.displayName
  }.map(NameParts.maybeInitial(_).getOrElse(""))

  private val drawableCorners = teamAndUser.map(_._2.fold(TeamIconDrawable.UserCorners)(_ => TeamIconDrawable.TeamCorners))

  picture.onUi { drawable.assetId ! _ }

  accentColor.onUi { color =>
    unreadIndicator.setAccentColor(color)
    drawable.setBorderColor(color)
  }

  Signal(initials, drawableCorners, selected).onUi {
    case (i, c, s) => drawable.setInfo(i, c, s)
  }

  (for {
    c <- unreadCount
    s <- selected
  } yield c > 0 && !s).onUi(unreadIndicator.setVisible)

  icon.setImageDrawable(drawable)
  setLayerType(View.LAYER_TYPE_SOFTWARE, null)

  teamAndUser.map {
    case (userData, None) => userData.getDisplayName
    case (userData, Some(team)) => team.name
  }.onUi { setContentDescription }

  def setAccount(id: UserId) = accountId ! id

}
