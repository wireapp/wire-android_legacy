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
package com.waz.zclient.preferences.pages

import android.app.AlertDialog
import android.content.{Context, DialogInterface, Intent}
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout}
import com.waz.ZLog
import com.waz.ZLog.ImplicitTag._
import com.waz.content.UserPreferences
import com.waz.model.otr.Client
import com.waz.model.{AccentColor, AccountDataOld, Availability}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient._
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.messages.UsersController
import com.waz.zclient.preferences.views.TextButton
import com.waz.zclient.tracking.OpenedManageTeam
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, RichView, StringUtils, UiStorage, UserSignal, ZTimeFormatter}
import com.waz.zclient.views.AvailabilityView
import org.threeten.bp.{LocalDateTime, ZoneId}

trait ProfileView {
  val onDevicesDialogAccept: EventStream[Unit]
  val onManageTeamClick: EventStream[Unit]

  def setUserName(name: String): Unit
  def setAvailability(visible: Boolean, availability: Availability): Unit
  def setHandle(handle: String): Unit
  def setProfilePictureDrawable(drawable: Drawable): Unit
  def setAccentColor(color: Int): Unit
  def setTeamName(name: Option[String]): Unit
  def showNewDevicesDialog(devices: Seq[Client]): Unit
  def setManageTeamEnabled(enabled: Boolean): Unit
  def setAddAccountEnabled(enabled: Boolean): Unit
}

class ProfileViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfileView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_profile_layout)

  val navigator = inject[BackStackNavigator]

  val userNameText = findById[TypefaceTextView](R.id.profile_user_name)
  val userPicture = findById[ImageView](R.id.profile_user_picture)
  val userHandleText = findById[TypefaceTextView](R.id.profile_user_handle)
  val teamNameText = findById[TypefaceTextView](R.id.profile_user_team)
  val teamDivider = findById[View](R.id.settings_team_divider)
  val teamButton = findById[TextButton](R.id.settings_team)
  val newTeamButton = findById[TextButton](R.id.profile_new)
  val settingsButton = findById[TextButton](R.id.profile_settings)

  override val onDevicesDialogAccept = EventStream[Unit]()
  override val onManageTeamClick: EventStream[Unit] = teamButton.onClickEvent.map(_ => ())

  private var deviceDialog = Option.empty[AlertDialog]

  teamButton.onClickEvent.on(Threading.Ui) { _ =>
    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.pref_manage_team_url)))) }
  teamButton.setVisible(false)
  teamDivider.setVisible(false)

  newTeamButton.onClickEvent.on(Threading.Ui) { _ =>
    new ProfileBottomSheetDialog(context, R.style.message__bottom_sheet__base).show()
  }

  settingsButton.onClickEvent.on(Threading.Ui) { _ =>
    navigator.goTo(SettingsBackStackKey())
  }

  userPicture.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = navigator.goTo(ProfilePictureBackStackKey())
  })

  override def setUserName(name: String): Unit = userNameText.setText(name)

  private lazy val userAvailability = findById[AvailabilityView](R.id.profile_user_availability)

  override def setAvailability(visible: Boolean, availability: Availability): Unit = {
    userAvailability.setVisible(visible)
    userAvailability.set(availability)
  }

  override def setHandle(handle: String): Unit = userHandleText.setText(handle)

  override def setProfilePictureDrawable(drawable: Drawable): Unit = userPicture.setImageDrawable(drawable)

  override def setAccentColor(color: Int): Unit = {}

  override def setTeamName(name: Option[String]) = {
    name match {
      case Some(teamName) =>
        teamNameText.setText(context.getString(R.string.preferences_profile_in_team, teamName))
      case None =>
        teamNameText.setText("")
    }
  }

  override def setManageTeamEnabled(enabled: Boolean): Unit = {
    teamButton.setEnabled(enabled)
    teamButton.setVisibility(if (enabled) View.VISIBLE else View.INVISIBLE)
    teamDivider.setVisibility(if (enabled) View.VISIBLE else View.INVISIBLE)
  }

  override def showNewDevicesDialog(devices: Seq[Client]) = {
    deviceDialog.foreach(_.dismiss())
    deviceDialog = None
    if (devices.nonEmpty) {
      val builder = new AlertDialog.Builder(context)
      deviceDialog = Option(builder.setTitle(R.string.new_devices_dialog_title)
        .setMessage(getNewDevicesMessage(devices))
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int) = {
            dialog.dismiss()
            onDevicesDialogAccept ! (())
          }
        })
        .setNegativeButton(R.string.new_devices_dialog_manage_devices, new DialogInterface.OnClickListener {
          override def onClick(dialog: DialogInterface, which: Int) = {
            dialog.dismiss()
            navigator.goTo(DevicesBackStackKey())
          }
        })
        .show())
    }
  }

  override def setAddAccountEnabled(enabled: Boolean): Unit = {
    newTeamButton.setEnabled(enabled)
    newTeamButton.setAlpha(if (enabled) 1f else 0.5f)
  }

  private def getNewDevicesMessage(devices: Seq[Client]): String = {
    val now = LocalDateTime.now(ZoneId.systemDefault)

    val deviceNames = devices.map { device =>
      val time =
        device.regTime match {
          case Some(regTime) =>
            ZTimeFormatter.getSeparatorTime(context, now, LocalDateTime.ofInstant(regTime, ZoneId.systemDefault),
              DateFormat.is24HourFormat(context), ZoneId.systemDefault, false)
          case _ =>
            ""
        }
      s"${device.model}${if (device.label.isEmpty) "" else s" (${device.label})"}\n$time"
    }.mkString("\n\n")

    val infoMessage = context.getString(R.string.new_devices_dialog_info)

    Seq(deviceNames, infoMessage).mkString("\n\n")
  }

}
object ProfileView {
  val Tag = ZLog.logTagFor[ProfileView]
}

case class ProfileBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_profile_screen_title

  override def layoutId = R.layout.preferences_profile

  var controller = Option.empty[ProfileViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfileViewImpl]).map(view => new ProfileViewController(view)(view.wContext.injector, view))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class ProfileViewController(view: ProfileView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  import ProfileViewController._

  implicit val uiStorage = inject[UiStorage]

  lazy val accounts        = inject[AccountsService]
  lazy val zms             = inject[Signal[ZMessaging]]
  lazy val tracking        = inject[TrackingService]
  lazy val usersController = inject[UsersController]
  lazy val usersAccounts   = inject[UserAccountsController]

  val currentUser = accounts.activeAccountId.collect { case Some(id) => id }

  val self = for {
    userId <- currentUser
    self   <- UserSignal(userId)
  } yield self

  val team = zms.flatMap(_.teams.selfTeam)

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect{ case Some(pic) => WireImage(pic) }

  val incomingClients = for {
    z       <- zms
    client  <- z.userPrefs(UserPreferences.SelfClient).signal
    clients <- client.clientId.fold(Signal.empty[Seq[Client]])(aid => z.otrClientsStorage.incomingClientsSignal(z.selfUserId, aid))
  } yield clients

  view.setProfilePictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  self.on(Threading.Ui) { self =>
    view.setAccentColor(AccentColor(self.accent).color)
    self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
    view.setUserName(self.getDisplayName)
  }

  for {
    userId    <- currentUser
    av <- usersController.availability(userId)
  } yield av

  usersController.availabilityVisible.zip(self.map(_.availability)).on(Threading.Ui) {
    case (visible, availability) => view.setAvailability(visible, availability)
  }

  team.on(Threading.Ui) { team => view.setTeamName(team.map(_.name)) }

  incomingClients.onUi { clients => view.showNewDevicesDialog(clients) }

  view.onDevicesDialogAccept.on(Threading.Background) { _ =>
    zms.head.flatMap(z => z.otrClientsService.updateUnknownToUnverified(z.selfUserId))(Threading.Background)
  }

  usersAccounts.selfPermissions
    .map(_.contains(AccountDataOld.Permission.AddTeamMember))
    .onUi(view.setManageTeamEnabled)

  ZMessaging.currentAccounts.accountsWithManagers.map(_.size < MaxAccountsCount).onUi(view.setAddAccountEnabled)

  view.onManageTeamClick { _ => tracking.track(OpenedManageTeam(), currentUser.currentValue) }
}

object ProfileViewController {
  val MaxAccountsCount = 2
}
