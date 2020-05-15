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
package com.waz.zclient.conversationlist.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{FrameLayout, ImageView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Availability, UserData}
import com.waz.service.teams.TeamsService
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.{NameParts, returning}
import com.waz.zclient.common.drawables.TeamIconDrawable
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.conversationlist.ConversationListController.{Folders, ListMode, Normal}
import com.waz.zclient.conversationlist.ListSeparatorDrawable
import com.waz.zclient.messages.UsersController
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

abstract class ConversationListTopToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {
  
  inflate(R.layout.view_conv_list_top)

  val onRightButtonClick = EventStream[View]()
  protected val separatorDrawable = new ListSeparatorDrawable(getColor(R.color.white_24))
  private val title = findById[TypefaceTextView](R.id.conversation_list_title)
  private var scrolledToTop = true

  returning(findById[View](R.id.button_container)) {
    _.setOnClickListener(new OnClickListener {
      override def onClick(v: View) = onRightButtonClick ! v
    })
  }

  returning(findById[View](R.id.conversation_list__border)) {
    _.setBackground(separatorDrawable)
  }

  setClipChildren(false)

  def setScrolledToTop(scrolledToTop: Boolean): Unit = if (this.scrolledToTop != scrolledToTop) {
    this.scrolledToTop = scrolledToTop
    if (!scrolledToTop) separatorDrawable.animateCollapse() else separatorDrawable.animateExpand()
  }

  def setTitle(mode: ListMode, currentUser: Option[UserData]): Unit = (mode, currentUser) match {
    case (Normal | Folders, Some(user)) if user.teamId.nonEmpty =>
      title.setText(user.name)
      AvailabilityView.displayStartOfText(title, user.availability, title.getCurrentTextColor, pushDown = true)
      title.onClick { AvailabilityView.showAvailabilityMenu(AvailabilityChanged.ListHeader) }
    case (Normal | Folders, Some(user)) =>
      title.setText(user.name)
      AvailabilityView.displayStartOfText(title, Availability.None, title.getCurrentTextColor)
      title.setOnClickListener(null)
    case _ =>
      title.setText(mode.nameId)
      AvailabilityView.displayStartOfText(title, Availability.None, title.getCurrentTextColor)
      title.setOnClickListener(null)
  }
}

class NormalTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int)
  extends ConversationListTopToolbar(context, attrs, defStyleAttr){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val drawable = new TeamIconDrawable

  private val profileButton = returning(findById[ImageView](R.id.conversation_list_settings)) { button =>
    button.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    button.setImageDrawable(drawable)
    button.setVisible(true)
  }

  private val settingsIndicator = findById[CircleView](R.id.conversation_list_settings_indicator)

  separatorDrawable.setDuration(0)
  separatorDrawable.setMinMax(0.0f, 1.0f)
  separatorDrawable.setClip(1.0f)

  (for {
    teams <- inject[Signal[TeamsService]]
    team  <- teams.selfTeam
    user  <- inject[UsersController].selfUser
  } yield (user, team)).onUi {
    case (_, Some(team)) =>
      drawable.setPicture(team.picture)
      drawable.setInfo(NameParts.maybeInitial(team.name).getOrElse(""), TeamIconDrawable.TeamShape, selected = false)
    case (user, _) =>
      drawable.setPicture(user.picture)
      drawable.setInfo(NameParts.maybeInitial(user.name).getOrElse(""), TeamIconDrawable.UserShape, selected = false)
  }

  def setIndicatorVisible(visible: Boolean): Unit = settingsIndicator.setVisible(visible)

  def setIndicatorColor(color: Int): Unit = settingsIndicator.setAccentColor(color)

  def setLoading(loading: Boolean): Unit =
    profileButton.setImageDrawable(if (loading) getDrawable(R.drawable.list_row_chathead_loading) else drawable)
}


class ArchiveTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int)
  extends ConversationListTopToolbar(context, attrs, defStyleAttr){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  returning(findById[GlyphButton](R.id.conversation_list_close)) {
    _.setVisible(true)
  }

  separatorDrawable.setDuration(0)
  separatorDrawable.animateExpand()
}
