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
import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout}
import com.bumptech.glide.request.RequestOptions
import com.waz.content.UserPreferences
import com.waz.model.otr.Client
import com.waz.model.{AccentColor, Availability, DisplayHandleDomainPolicies, Picture, TeamData, UserPermissions}
import com.waz.service.teams.TeamsService
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.wire.signals.{EventContext, EventStream, Signal}
import com.waz.zclient.BuildConfig.ACCOUNT_CREATION_ENABLED
import com.waz.zclient._
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.common.controllers.{BrowserController, UserAccountsController}
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.messages.UsersController
import com.waz.zclient.preferences.pages.ProfileViewController.MaxAccountsCount
import com.waz.zclient.preferences.views.TextButton
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, RichView, UiStorage, UserSignal}
import com.waz.zclient.views.AvailabilityView
import com.waz.threading.Threading._

trait ProfileView {
  val onDevicesDialogAccept: EventStream[Unit]
  val onManageTeamClick: EventStream[Unit]
  val onReadReceiptsDismissed: EventStream[Unit]

  def setUserName(name: String): Unit
  def setAvailability(visible: Boolean, availability: Availability): Unit
  def setHandle(handle: String): Unit
  def setProfilePicture(picture: Picture): Unit
  def setAccentColor(color: Int): Unit
  def setTeamName(name: Option[String]): Unit
  def showNewDevicesDialog(devices: Seq[Client]): Unit
  def setManageTeamEnabled(enabled: Boolean): Unit
  def setAddAccountEnabled(enabled: Boolean): Unit
  def showReadReceiptsChanged(current: Boolean): Unit
  def clearDialog(): Unit
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
  override val onReadReceiptsDismissed = EventStream[Unit]()
  override val onManageTeamClick: EventStream[Unit] = teamButton.onClickEvent.map(_ => ())

  private var dialog = Option.empty[AlertDialog]

  teamButton.onClickEvent.on(Threading.Ui) { _ => inject[BrowserController].openPrefsManageTeam() }
  teamButton.setVisible(false)
  teamDivider.setVisible(false)

  if(MaxAccountsCount > 1 && ACCOUNT_CREATION_ENABLED) {
    newTeamButton.setVisible(true)
    newTeamButton.onClickEvent.on(Threading.Ui) { _ =>
      // We want to go directly to the landing page.
      getContext.startActivity(AppEntryActivity.newIntent(getContext))
    }
  } else {
    newTeamButton.setVisible(false)
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

  override def setProfilePicture(picture: Picture): Unit =
    WireGlide(context)
      .load(picture)
      .apply(new RequestOptions().circleCrop())
      .into(userPicture)

  override def setAccentColor(color: Int): Unit = {}

  override def setTeamName(name: Option[String]): Unit = name match {
    case Some(teamName) => teamNameText.setText(context.getString(R.string.preferences_profile_in_team, teamName))
    case None           => teamNameText.setText("")
  }

  override def setManageTeamEnabled(enabled: Boolean): Unit = {
    teamButton.setEnabled(enabled)
    teamButton.setVisibility(if (enabled) View.VISIBLE else View.INVISIBLE)
    teamDivider.setVisibility(if (enabled) View.VISIBLE else View.INVISIBLE)
  }

  def clearDialog(): Unit = {
    dialog.foreach(_.dismiss())
    dialog = None
  }

  override def showNewDevicesDialog(devices: Seq[Client]) = {
    clearDialog()
    if (devices.nonEmpty) {
      val builder = new AlertDialog.Builder(context)
      dialog = Option(builder.setTitle(R.string.new_devices_dialog_title)
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

  override def showReadReceiptsChanged(current: Boolean): Unit = {
    clearDialog()
    val builder = new AlertDialog.Builder(context)
    builder
      .setTitle(if (current) R.string.read_receipts_remotely_enabled_title else R.string.read_receipts_remotely_disabled_title)
      .setMessage(getString(R.string.read_receipts_remotely_changed_message))
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = {
          dialog.dismiss()
          onReadReceiptsDismissed ! (())
        }
      })
      .setCancelable(false)

    dialog = Option(builder.show())
  }

  override def setAddAccountEnabled(enabled: Boolean): Unit = {
    newTeamButton.setEnabled(enabled)
    newTeamButton.setAlpha(if (enabled) 1f else 0.5f)
  }

  private def getNewDevicesMessage(devices: Seq[Client]): String = {
    val deviceNames = devices.map { device =>
      val time =
        device.regTime match {
          case Some(regTime) =>
            TimeStamp(regTime).string
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
  val Tag: String = getClass.getSimpleName
}

case class ProfileBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_profile_screen_title

  override def layoutId = R.layout.preferences_profile

  var controller = Option.empty[ProfileViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfileViewImpl]).map(view => new ProfileViewController(view)(view.wContext.injector, view.eventContext))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class ProfileViewController(view: ProfileView)(implicit inj: Injector, ec: EventContext)
  extends Injectable {
  import ProfileViewController._

  implicit val uiStorage = inject[UiStorage]

  lazy val accounts        = inject[AccountsService]
  lazy val zms             = inject[Signal[ZMessaging]]
  lazy val usersController = inject[UsersController]
  lazy val usersAccounts   = inject[UserAccountsController]
  private lazy val backendConfig = zms.flatMap(_.backend)
  private lazy val userPrefs = zms.map(_.userPrefs)

  val currentUser = accounts.activeAccountId.collect { case Some(id) => id }

  val self = for {
    userId <- currentUser
    self   <- UserSignal(userId)
  } yield self

  lazy val team: Signal[Option[TeamData]] = {
    val teams = inject[Signal[TeamsService]]
    teams.head.foreach(_.syncTeamData())(Threading.Background)
    teams.flatMap(_.selfTeam)
  }

  self.map(_.picture).collect { case Some(pic) => pic }.onUi { view.setProfilePicture }

  val incomingClients = for {
    z       <- zms
    client  <- z.userPrefs(UserPreferences.SelfClient).signal
    clients <- client.clientId.fold(Signal.empty[Seq[Client]])(aid => z.otrClientsStorage.incomingClientsSignal(z.selfUserId, aid))
  } yield clients

  self.on(Threading.Ui) { self =>
    val federationEnabled = backendConfig.currentValue.exists(_.federationSupport.isSupported)
    view.setAccentColor(AccentColor(self.accent).color)
    view.setHandle(self.displayHandle(self.domain, if(federationEnabled) DisplayHandleDomainPolicies.AlwaysShowDomain else DisplayHandleDomainPolicies.NeverShowDomain))
    view.setUserName(self.name)
  }

  for {
    userId    <- currentUser
    av <- usersController.availability(userId)
  } yield av


  usersController.availabilityVisible.zip(self.map(_.availability)).on(Threading.Ui) {
    case (visible, availability) => view.setAvailability(visible, availability)
  }

  team.on(Threading.Ui) { team => view.setTeamName(team.map(_.name)) }

  type DialogInfo = Either[Seq[Client], Boolean]

  private val dialogInfo: Signal[Option[DialogInfo]] = for {
    clients <- incomingClients
    rrChanged <- userPrefs.flatMap(_(UserPreferences.ReadReceiptsRemotelyChanged).signal)
    currentRR <- usersAccounts.readReceiptsEnabled
  } yield if (clients.nonEmpty)
      Some(Left(clients))
    else if (rrChanged)
      Some(Right(currentRR))
    else
      None

  dialogInfo.onUi {
    case Some(Left(clients)) => view.showNewDevicesDialog(clients)
    case Some(Right(currentRR)) => view.showReadReceiptsChanged(currentRR)
    case _ => view.clearDialog()
  }

  view.onDevicesDialogAccept.on(Threading.Background) { _ =>
    zms.head.flatMap(z => z.otrClientsService.updateUnknownToUnverified(z.selfUserId))(Threading.Background)
  }

  view.onReadReceiptsDismissed.on(Threading.Background) { _ =>
    userPrefs.head.flatMap(prefs => prefs(UserPreferences.ReadReceiptsRemotelyChanged) := false)(Threading.Background)
  }

  usersAccounts.selfPermissions
    .map(_.contains(UserPermissions.Permission.AddTeamMember))
    .onUi(view.setManageTeamEnabled)


  if (ACCOUNT_CREATION_ENABLED) {
    ZMessaging.currentAccounts.accountsWithManagers.map(_.size < MaxAccountsCount).onUi(view.setAddAccountEnabled)
  }
}

object ProfileViewController {
  val MaxAccountsCount = BuildConfig.MAX_ACCOUNTS
}
