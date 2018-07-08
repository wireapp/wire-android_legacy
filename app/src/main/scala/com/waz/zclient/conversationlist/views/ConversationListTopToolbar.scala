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
import com.waz.ZLog
import com.waz.model.{Availability, IntegrationData, UserData}
import com.waz.service.ZMessaging
import com.waz.utils.NameParts
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.conversationlist.{ConversationListAdapter, ListSeparatorDrawable}
import com.waz.zclient.drawables.TeamIconDrawable
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, UiStorage, UserSignal}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}

abstract class ConversationListTopToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {

  private implicit val logTag = ZLog.logTagFor[ConversationListTopToolbar]

  inflate(R.layout.view_conv_list_top)

  val buttonContainer = findById[View](R.id.button_container)
  val bottomBorder = findById[View](R.id.conversation_list__border)
  val profileButton = findById[ImageView](R.id.conversation_list_settings)
  val backButton = findById[GlyphButton](R.id.conversation_list_back)
  val closeButton = findById[GlyphButton](R.id.conversation_list_close)
  val closeButtonEnd = findById[GlyphButton](R.id.conversation_list_close_end)
  val title = findById[TypefaceTextView](R.id.conversation_list_title)
  val settingsIndicator = findById[CircleView](R.id.conversation_list_settings_indicator)

  val onRightButtonClick = EventStream[View]()

  protected var scrolledToTop = true
  protected val separatorDrawable = new ListSeparatorDrawable(getColor(R.color.white_24))
  protected val animationDuration = getResources.getInteger(R.integer.team_tabs__animation_duration)

  setClipChildren(false)
  bottomBorder.setBackground(separatorDrawable)

  buttonContainer.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = onRightButtonClick ! v
  })

  def setScrolledToTop(scrolledToTop: Boolean): Unit =
    if (this.scrolledToTop != scrolledToTop) {
      this.scrolledToTop = scrolledToTop
      if (!scrolledToTop) {
        separatorDrawable.animateCollapse()
      } else {
        separatorDrawable.animateExpand()
      }
    }

  def setTitle(mode: ConversationListAdapter.ListMode, currentUser: Option[UserData]): Unit = (mode, currentUser) match {
    case (ConversationListAdapter.Normal, Some(user)) if user.teamId.nonEmpty =>
      title.setText(user.displayName)
      AvailabilityView.displayLeftOfText(title, user.availability, title.getCurrentTextColor, pushDown = true)
      title.onClick { AvailabilityView.showAvailabilityMenu(AvailabilityChanged.ListHeader) }
    case (ConversationListAdapter.Normal, Some(user)) =>
      title.setText(user.displayName)
      AvailabilityView.displayLeftOfText(title, Availability.None, title.getCurrentTextColor)
      title.setOnClickListener(null)
    case (mode, userOpt) =>
      title.setText(mode.nameId)
      AvailabilityView.displayLeftOfText(title, Availability.None, title.getCurrentTextColor)
      title.setOnClickListener(null)
  }

  def setTitle(integration: IntegrationData): Unit = {
    title.setText(integration.name)
    title.setOnClickListener(null)
  }

}

class NormalTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int)  extends ConversationListTopToolbar(context, attrs, defStyleAttr){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  val zms = inject[Signal[ZMessaging]]
  val controller = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  val drawable = new TeamIconDrawable()
  val info = for {
    z <- zms
    user <- UserSignal(z.selfUserId)
    team <- z.teams.selfTeam
  } yield (user, team)

  info.onUi {
    case (user, Some(team)) =>
      drawable.assetId ! None
      drawable.setInfo(NameParts.maybeInitial(team.name).getOrElse(""), TeamIconDrawable.TeamCorners, selected = false)
    case (user, _) =>
      drawable.assetId ! user.picture
      drawable.setInfo(NameParts.maybeInitial(user.displayName).getOrElse(""), TeamIconDrawable.UserCorners, selected = false)
  }
  profileButton.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  profileButton.setImageDrawable(drawable)
  profileButton.setVisible(true)
  closeButton.setVisible(false)
  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.setMinMax(0f, 1.0f)
  separatorDrawable.setClip(1.0f)

  override def setScrolledToTop(scrolledToTop: Boolean): Unit =
    if (this.scrolledToTop != scrolledToTop) {
      super.setScrolledToTop(scrolledToTop)
    }

  def setIndicatorVisible(visible: Boolean): Unit =
    settingsIndicator.setVisible(visible)

  def setIndicatorColor(color: Int): Unit =
    settingsIndicator.setAccentColor(color)

  def setLoading(loading: Boolean): Unit =
    profileButton.setImageDrawable(if (loading) getDrawable(R.drawable.list_row_chathead_loading) else drawable)
}


class ArchiveTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int)  extends ConversationListTopToolbar(context, attrs, defStyleAttr){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  profileButton.setVisible(false)
  closeButton.setVisible(true)
  settingsIndicator.setVisible(false)
  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.animateExpand()
}

class IntegrationTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int)  extends ConversationListTopToolbar(context, attrs, defStyleAttr){
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  profileButton.setVisible(false)
  backButton.setVisible(true)
  closeButtonEnd.setVisible(true)
  settingsIndicator.setVisible(false)
  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.animateExpand()
}
