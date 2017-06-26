/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.views

import android.content.Context
import android.graphics.Color
import android.util.{AttributeSet, TypedValue}
import android.view.ViewGroup.MarginLayoutParams
import android.view.{View, ViewGroup}
import android.widget.FrameLayout.LayoutParams
import android.widget.{FrameLayout, ImageView, RelativeLayout}
import com.waz.api.impl.{AccentColor, AccentColors}
import com.waz.model._
import com.waz.threading.Threading
import com.waz.utils.NameParts
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.drawables.TeamIconDrawable
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.RichView

class TeamTabButton(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.view_team_tab)
  setLayoutParams(new LayoutParams(context.getResources.getDimensionPixelSize(R.dimen.teams_tab_width), ViewGroup.LayoutParams.MATCH_PARENT))

  val icon = findById[ImageView](R.id.team_icon)
  val name = findById[TypefaceTextView](R.id.team_name)
  val iconContainer = findById[FrameLayout](R.id.team_icon_container)
  val nameContainer = findById[RelativeLayout](R.id.team_name_container)
  val unreadIndicatorIcon = findById[CircleView](R.id.unread_indicator_icon)
  val unreadIndicatorName = findById[CircleView](R.id.unread_indicator_text)
  val animationDuration = getResources.getInteger(R.integer.team_tabs__animation_duration)
  val accentColor = inject[AccentColorController].accentColor

  val drawable = new TeamIconDrawable
  val params = nameContainer.getLayoutParams.asInstanceOf[MarginLayoutParams]
  val tv = new TypedValue
  val height =
    if (getContext.getTheme.resolveAttribute(android.R.attr.actionBarSize, tv, true))
      TypedValue.complexToDimensionPixelSize(tv.data,getResources.getDisplayMetrics)
    else
      getResources.getDimensionPixelSize(R.dimen.teams_tab_default_height)

  private var selectedColor = AccentColors.defaultColor.getColor()
  private var buttonSelected = false
  var accountData = Option.empty[AccountData]

  icon.setImageDrawable(drawable)
  setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  params.topMargin = height / 2 - getResources.getDimensionPixelSize(R.dimen.teams_tab_text_bottom_margin)
  unreadIndicatorName.setAlpha(0f)

  accentColor.on(Threading.Ui){ accentColor =>
    selectedColor = accentColor.getColor()
    drawable.setBorderColor(accentColor.getColor())
    unreadIndicatorIcon.setAccentColor(accentColor.getColor())
    unreadIndicatorName.setAccentColor(accentColor.getColor())
  }

  def setUserData(accountData: AccountData, userData: UserData, selected: Boolean, unreadCount: Int): Unit = {
    drawable.setInfo(NameParts.maybeInitial(userData.displayName).getOrElse(""), TeamIconDrawable.UserCorners, selected)
    name.setText(userData.getDisplayName)
    drawable.assetId ! userData.picture
    buttonSelected = selected
    unreadIndicatorName.setVisible(unreadCount > 0 && !selected)
    unreadIndicatorIcon.setVisible(unreadCount > 0 && !selected)
    this.accountData = Some(accountData)
  }

  def animateExpand(): Unit = {
    nameContainer.animate().translationY(0f).setDuration(animationDuration).start()
    iconContainer.animate().translationY(0f).setDuration(animationDuration).alpha(1f).start()
    unreadIndicatorName.animate().alpha(0f).start()
    unreadIndicatorIcon.animate().alpha(1f).start()
    name.setTextColor(Color.WHITE)
  }

  def animateCollapse(): Unit = {
    val margin = (height - getResources.getDimensionPixelSize(R.dimen.teams_tab_text_bottom_margin)) / 2
    nameContainer.animate().translationY(-margin).setDuration(animationDuration).start()
    iconContainer.animate().translationY(-margin).alpha(0f).setDuration(animationDuration).start()
    unreadIndicatorName.animate().alpha(1f).start()
    unreadIndicatorIcon.animate().alpha(0f).start()
    val textColor = if (buttonSelected) selectedColor else Color.WHITE
    name.setTextColor(textColor)
  }
}
