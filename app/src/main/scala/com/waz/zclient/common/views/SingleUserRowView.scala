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
package com.waz.zclient.common.views

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.support.v7.widget.AppCompatCheckBox
import android.util.AttributeSet
import android.view.{Gravity, View, ViewGroup}
import android.view.View.OnClickListener
import android.widget.{CompoundButton, ImageView, LinearLayout, RelativeLayout}
import com.waz.ZLog.ImplicitTag.implicitLogTag
import com.waz.model.{Availability, IntegrationData, TeamId, UserData}
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.{ThemeController, ThemedView}
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIcon, VideoIcon}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{GuestUtils, StringUtils}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

class SingleUserRowView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with ThemedView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.single_user_row_view)
  setTheme(Theme.Light, background = true)

  private lazy val chathead = findById[ChatheadView](R.id.chathead)
  private lazy val nameView = findById[TypefaceTextView](R.id.name_text)
  private lazy val subtitleView = findById[TypefaceTextView](R.id.username_text)
  private lazy val checkbox = findById[AppCompatCheckBox](R.id.checkbox)
  private lazy val verifiedShield = findById[ImageView](R.id.verified_shield)
  private lazy val guestIndicator = returning(findById[ImageView](R.id.guest_indicator))(_.setImageDrawable(GuestIcon(R.color.light_graphite)))
  private lazy val videoIndicator = returning(findById[ImageView](R.id.video_indicator))(_.setImageDrawable(VideoIcon(R.color.light_graphite)))
  private lazy val nextIndicator = returning(findById[ImageView](R.id.next_indicator))(_.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40)))
  private lazy val separator = findById[View](R.id.separator)
  private lazy val auxContainer = findById[ViewGroup](R.id.aux_container)

  val onSelectionChanged: SourceStream[Boolean] = EventStream()
  private var solidBackground = false

  checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =
      onSelectionChanged ! isChecked
  })

  this.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = setChecked(!checkbox.isChecked)
  })

  currentTheme.collect{ case Some(t) => t }.onUi { theme => setTheme(theme, solidBackground) }

  def setTitle(text: String): Unit = {
    nameView.setText(text)
  }

  def setSubtitle(text: Option[String]): Unit = text.fold(subtitleView.setVisibility(View.GONE)) { t =>
    subtitleView.setVisibility(View.VISIBLE)
    subtitleView.setText(t)
  }

  def setChecked(checked: Boolean): Unit = checkbox.setChecked(checked)

  private def setVerified(verified: Boolean) = verifiedShield.setVisibility(if (verified) View.VISIBLE else View.GONE)

  def showArrow(show: Boolean): Unit = nextIndicator.setVisibility(if (show) View.VISIBLE else View.GONE)

  def setCallParticipantInfo(user: CallParticipantInfo): Unit = {
    chathead.setUserId(user.userId, user.zms)
    setTitle(user.displayName)
    setVerified(user.isVerified)
    subtitleView.setVisibility(View.GONE)
    setIsGuest(user.isGuest)
    videoIndicator.setVisibility(if (user.isVideoEnabled) View.VISIBLE else View.GONE)
  }

  def setUserData(userData: UserData, teamId: Option[TeamId], showSubtitle: Boolean = true): Unit = {
    chathead.setUserId(userData.id)
    setTitle(userData.getDisplayName)
    if (teamId.isDefined) setAvailability(userData.availability)
    setVerified(userData.isVerified)
    val handle = userData.handle.map(h => StringUtils.formatHandle(h.string))
    val expiration = userData.expiresAt.map(GuestUtils.timeRemainingString(_, Instant.now))
    if (showSubtitle) setSubtitle(expiration.orElse(handle)) else subtitleView.setVisibility(View.GONE)
    setIsGuest(userData.isGuest(teamId))
  }

  def setIntegration(integration: IntegrationData): Unit = {
    chathead.setIntegration(integration)
    setTitle(integration.name)
    setAvailability(Availability.None)
    setVerified(false)
    setSubtitle(Some(integration.summary))
  }

  def setIsGuest(guest: Boolean): Unit = guestIndicator.setVisibility(if (guest) View.VISIBLE else View.GONE)

  def showCheckbox(show: Boolean): Unit = checkbox.setVisibility(if (show) View.VISIBLE else View.GONE)

  def setTheme(theme: ThemeController.Theme, background: Boolean): Unit = {
    val (backgroundDrawable, checkboxDrawable) = (theme, background) match {
      case (ThemeController.Theme.Light, true)  => (new ColorDrawable(getColor(R.color.background_light)), R.drawable.checkbox_black)
      case (ThemeController.Theme.Dark, true)   => (new ColorDrawable(getColor(R.color.background_dark)), R.drawable.checkbox)
      case (ThemeController.Theme.Light, false) => (getDrawable(R.drawable.selector__transparent_button), R.drawable.checkbox_black)
      case (ThemeController.Theme.Dark, false)  => (getDrawable(R.drawable.selector__transparent_button), R.drawable.checkbox)
      case _ => throw new IllegalArgumentException
    }
    nameView.forceTheme(Some(theme))
    separator.setBackgroundColor(getStyledColor(R.attr.thinDividerColor))
    setBackground(backgroundDrawable)
    checkbox.setButtonDrawable(returning(getDrawable(checkboxDrawable))(_.setLevel(1)))
  }

  def setAvailability(availability: Availability): Unit =
    AvailabilityView.displayLeftOfText(nameView, availability, nameView.getCurrentTextColor, pushDown = true)

  def setSeparatorVisible(visible: Boolean): Unit = separator.setVisibility(if (visible) View.VISIBLE else View.GONE)

  def setCustomViews(views: Seq[View]): Unit = {
    auxContainer.removeAllViews()
    views.foreach { v =>
      val params = returning(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))(_.gravity = Gravity.CENTER)
      v.setLayoutParams(params)
      auxContainer.addView(v)
    }
  }
}
