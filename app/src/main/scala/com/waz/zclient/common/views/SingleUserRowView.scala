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
import android.util.{AttributeSet}
import android.view.View.OnClickListener
import android.view.{Gravity, View, ViewGroup}
import android.widget.{CompoundButton, ImageView, LinearLayout, RelativeLayout}
import androidx.appcompat.widget.AppCompatCheckBox
import com.waz.model.{Availability, IntegrationData, TeamId, UserData}
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.{ThemeController, ThemedView}
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIcon}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{GuestUtils, StringUtils, _}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}
import com.wire.signals.{EventStream, Signal, SourceStream}
import com.wire.signals.{EventStream, SourceStream}
import com.wire.signals.{EventStream, Signal, SourceStream}
import org.threeten.bp.Instant

class SingleUserRowView(context: Context, attrs: AttributeSet, style: Int)
  extends RelativeLayout(context, attrs, style) with ViewHelper with ThemedView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.single_user_row_view)

  private lazy val chathead       = findById[ChatHeadView](R.id.chathead)
  private lazy val nameView       = findById[TypefaceTextView](R.id.name_text)
  private lazy val subtitleView   = findById[TypefaceTextView](R.id.username_text)
  private lazy val checkbox       = findById[AppCompatCheckBox](R.id.checkbox)
  private lazy val verifiedShield = findById[ImageView](R.id.verified_image_view)
  private lazy val guestPartnerIndicator = findById[ImageView](R.id.guest_external_image_view)
  private lazy val videoIndicator = findById[ImageView](R.id.video_status_image_view)
  private lazy val audioIndicator = findById[ImageView](R.id.audio_status_image_view)
  private lazy val nextIndicator  = returning(findById[ImageView](R.id.next_indicator))(_.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40)))
  private lazy val separator      = findById[View](R.id.separator)
  private lazy val auxContainer   = findById[ViewGroup](R.id.aux_container)
  private lazy val guestIndicator = returning(findById[ImageView](R.id.guest_image_view))(_.setImageDrawable(GuestIcon(R.color.light_graphite)))
  private lazy val externalIndicator   = findById[ImageView](R.id.external_image_view)

  private lazy val youTextString = getString(R.string.content__system__you).capitalize
  private lazy val youText        = returning(findById[TypefaceTextView](R.id.you_text))(_.setText(s"($youTextString)"))

  val onSelectionChanged: SourceStream[Boolean] = EventStream()
  private var solidBackground = false

  checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =
      onSelectionChanged ! isChecked
  })

  this.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = setChecked(!checkbox.isChecked)
  })

  private val chosenCurrentTheme = currentTheme.collect { case Some(t) => t }
  chosenCurrentTheme.onUi { theme => setTheme(theme, solidBackground) }

  private val videoEnabled = Signal(false)
  private val screenShareEnabled = Signal(false)

  Signal.zip(videoEnabled, screenShareEnabled).map { case (v, s) => v || s }.onUi(videoIndicator.setVisible)
  Signal.zip(chosenCurrentTheme, videoEnabled, screenShareEnabled).onUi {
    case (Theme.Light, true, _)     => videoIndicator.setImageResource(R.drawable.ic_video_light_theme)
    case (Theme.Light, false, true) => videoIndicator.setImageResource(R.drawable.ic_screenshare_light_theme)
    case (Theme.Dark, true, _)      => videoIndicator.setImageResource(R.drawable.ic_video_dark_theme)
    case (Theme.Dark, false, true)  => videoIndicator.setImageResource(R.drawable.ic_screenshare_dark_theme)
    case _ =>
  }

  private val isMuted = Signal(false)
  audioIndicator.setVisible(true)
  Signal.zip(chosenCurrentTheme, isMuted).onUi {
    case (Theme.Light, true)  => audioIndicator.setImageResource(R.drawable.ic_muted_light_theme)
    case (Theme.Light, false) => audioIndicator.setImageResource(R.drawable.ic_unmuted_light_theme)
    case (Theme.Dark, true)   => audioIndicator.setImageResource(R.drawable.ic_muted_dark_theme)
    case (Theme.Dark, false)  => audioIndicator.setImageResource(R.drawable.ic_unmuted_dark_theme)
    case _ =>
  }

  private val isGuest = Signal(false)
  private val isPartner = Signal(false)

  Signal.zip(isGuest, isPartner).map { case (v, s) => v || s }.onUi(guestPartnerIndicator.setVisible)
  Signal.zip(chosenCurrentTheme, isGuest, isPartner).onUi {
    case (Theme.Light, true, _)     => guestPartnerIndicator.setImageResource(R.drawable.ic_guest_light_theme)
    case (Theme.Light, false, true) => guestPartnerIndicator.setImageResource(R.drawable.ic_partner_light_theme)
    case (Theme.Dark, true, _)      => guestPartnerIndicator.setImageResource(R.drawable.ic_guest_dark_theme)
    case (Theme.Dark, false, true)  => guestPartnerIndicator.setImageResource(R.drawable.ic_partner_dark_theme)
    case _ =>
  }

  def setTitle(text: String, isSelf: Boolean): Unit = {
    nameView.setText(text)
    youText.setVisible(isSelf)
  }

  def setSubtitle(text: String): Unit =
    if (text.isEmpty) subtitleView.setVisibility(View.GONE)
    else {
      subtitleView.setVisibility(View.VISIBLE)
      subtitleView.setText(text)
    }

  def setChecked(checked: Boolean): Unit = checkbox.setChecked(checked)

  private def setVerified(verified: Boolean) = verifiedShield.setVisible(verified)

  def showArrow(show: Boolean): Unit = nextIndicator.setVisible(show)

  def setCallParticipantInfo(user: CallParticipantInfo): Unit = {
    chathead.loadUser(user.userId)
    setTitle(user.displayName, user.isSelf)
    subtitleView.setVisible(false)
    videoEnabled ! user.isVideoEnabled
    screenShareEnabled ! user.isScreenShareEnabled
    isMuted ! user.isMuted
    isGuest ! user.isGuest
    isPartner ! user.isExternal
    setVerified(user.isVerified)
  }

  def setUserData(userData:       UserData,
                  teamId:         Option[TeamId],
                  createSubtitle: (UserData) => String = SingleUserRowView.defaultSubtitle): Unit = {
    chathead.setUserData(userData, userData.isInTeam(teamId))
    setTitle(userData.name, userData.isSelf)
    setAvailability(if (teamId.isDefined) userData.availability else Availability.None)
    setVerified(userData.isVerified)
    setSubtitle(createSubtitle(userData))
    setIsGuest(userData.isGuest(teamId) && !userData.isWireBot)
    setIsExternal(userData.isExternal(teamId) && !userData.isWireBot)
  }

  private def setIsGuest(guest: Boolean): Unit = guestIndicator.setVisible(guest)
  
  private def setIsExternal(external: Boolean): Unit = externalIndicator.setVisible(external)

  def setIntegration(integration: IntegrationData): Unit = {
    chathead.setIntegration(integration)
    setTitle(integration.name, isSelf = false)
    setAvailability(Availability.None)
    setVerified(false)
    setSubtitle(integration.summary)
  }

  def showCheckbox(show: Boolean): Unit = checkbox.setVisible(show)

  def setTheme(theme: ThemeController.Theme, background: Boolean): Unit = {
    val (backgroundDrawable, checkboxDrawable) = (theme, background) match {
      case (ThemeController.Theme.Light, true)  => (new ColorDrawable(getColor(R.color.background_light)), R.drawable.checkbox_black)
      case (ThemeController.Theme.Dark, true)   => (new ColorDrawable(getColor(R.color.background_dark)), R.drawable.checkbox)
      case (ThemeController.Theme.Light, false) => (getDrawable(R.drawable.selector__transparent_button), R.drawable.checkbox_black)
      case (ThemeController.Theme.Dark, false)  => (getDrawable(R.drawable.selector__transparent_button), R.drawable.checkbox)
      case _ => throw new IllegalArgumentException
    }
    nameView.forceTheme(Some(theme))
    separator.setBackgroundColor(getStyledColor(R.attr.thinDividerColor, inject[ThemeController].getTheme(theme)))
    setBackground(backgroundDrawable)
    checkbox.setButtonDrawable(returning(getDrawable(checkboxDrawable))(_.setLevel(1)))
  }

  def setAvailability(availability: Availability): Unit =
    AvailabilityView.displayStartOfText(nameView, availability, nameView.getCurrentTextColor, pushDown = true)

  def setSeparatorVisible(visible: Boolean): Unit = separator.setVisible(visible)

  def setCustomViews(views: Seq[View]): Unit = {
    auxContainer.removeAllViews()
    views.foreach { v =>
      val params = returning(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))(_.gravity = Gravity.CENTER)
      v.setLayoutParams(params)
      auxContainer.addView(v)
    }
  }
}

object SingleUserRowView {
  def defaultSubtitle(user: UserData)(implicit context: Context): String = {
    val handle = user.handle.map(h => StringUtils.formatHandle(h.string))
    val expiration = user.expiresAt.map(ea => GuestUtils.timeRemainingString(ea.instant, Instant.now))
    expiration.orElse(handle).getOrElse("")
  }
}
