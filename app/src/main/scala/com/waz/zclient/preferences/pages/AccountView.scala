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

import android.app.Activity
import android.content.{Context, DialogInterface}
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.os.{Bundle, Parcel, Parcelable}
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AccentColor, DisplayHandleDomainPolicies, Domain, EmailAddress, Name, PhoneNumber, Picture}
import com.waz.service.AccountsService.UserInitiated
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.wire.signals.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.appentry.{AppEntryActivity, DialogErrorMessage}
import com.waz.zclient.common.controllers.{BrowserController, UserAccountsController}
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.preferences.dialogs._
import com.waz.zclient.preferences.views.{EditNameDialog, PictureTextButton, SwitchPreference, TextButton}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils._
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, RichView, UiStorage}
import com.waz.zclient.{BuildConfig, _}
import com.waz.threading.Threading._

trait AccountView {
  val onNameClick:          EventStream[Unit]
  val onHandleClick:        EventStream[Unit]
  val onEmailClick:         EventStream[Unit]
  val onPhoneClick:         EventStream[Unit]
  val onPictureClick:       EventStream[Unit]
  val onAccentClick:        EventStream[Unit]
  val onPasswordResetClick: EventStream[Unit]
  val onLogoutClick:        EventStream[Unit]
  val onDeleteClick:        EventStream[Unit]
  val onBackupClick:        EventStream[Unit]
  val onDataUsageClick:     EventStream[Unit]
  val onReadReceiptSwitch:  EventStream[Boolean]

  def setName(name: String): Unit
  def setHandle(handle: String): Unit
  def setEmail(email: Option[EmailAddress]): Unit
  def setPhone(phone: Option[PhoneNumber]): Unit
  def setTeam(team: Option[Name]): Unit
  def setDomain(domain: Domain): Unit
  def setPicture(picture: Picture): Unit
  def setAccentDrawable(drawable: Drawable): Unit
  def setDeleteAccountEnabled(enabled: Boolean): Unit
  def setEmailEnabled(enabled: Boolean): Unit
  def setPhoneNumberEnabled(enabled: Boolean): Unit
  def setReadReceipt(enabled: Boolean): Unit
  def setResetPasswordEnabled(enabled: Boolean): Unit
  def setAccountLocked(locked: Boolean): Unit
}

class AccountViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style)
  with AccountView with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_account_layout)

  val nameButton          = findById[TextButton](R.id.preferences_account_name)
  val handleButton        = findById[TextButton](R.id.preferences_account_handle)
  val emailButton         = findById[TextButton](R.id.preferences_account_email)
  val phoneButton         = findById[TextButton](R.id.preferences_account_phone)
  val teamButton          = findById[TextButton](R.id.preferences_account_team)
  val domainButton        = findById[TextButton](R.id.preferences_account_domain)
  val pictureButton       = findById[PictureTextButton](R.id.preferences_account_picture)
  val colorButton         = findById[PictureTextButton](R.id.preferences_account_accent)
  val resetPasswordButton = findById[TextButton](R.id.preferences_account_reset_pw)
  val logoutButton        = findById[TextButton](R.id.preferences_account_logout)
  val deleteAccountButton = findById[TextButton](R.id.preferences_account_delete)
  val backupButton        = findById[TextButton](R.id.preferences_backup)
  val dataUsageButton     = findById[TextButton](R.id.preferences_data_usage_permissions)
  val readReceiptsSwitch  = findById[SwitchPreference](R.id.preferences_account_read_receipts)
  val personalInformationHeaderLabel = findById[TypefaceTextView](R.id.preference_personal_information_header)
  val appearanceHeader = findById[TypefaceTextView](R.id.preferences_account_appearance_header)

  // Hide data usage section if there is nothing to show in there
  val showPersonalInformationSection = BuildConfig.SUBMIT_CRASH_REPORTS || BuildConfig.ALLOW_MARKETING_COMMUNICATION
  dataUsageButton.setVisible(showPersonalInformationSection)
  personalInformationHeaderLabel.setVisible(showPersonalInformationSection)


  override val onNameClick          = nameButton.onClickEvent.map(_ => ())
  override val onHandleClick        = handleButton.onClickEvent.map(_ => ())
  override val onEmailClick         = emailButton.onClickEvent.map(_ => ())
  override val onPhoneClick         = phoneButton.onClickEvent.map(_ => ())
  override val onPictureClick       = pictureButton.onClickEvent.map(_ => ())
  override val onAccentClick        = colorButton.onClickEvent.map(_ => ())
  override val onPasswordResetClick = resetPasswordButton.onClickEvent.map(_ => ())
  override val onLogoutClick        = logoutButton.onClickEvent.map(_ => ())
  override val onDeleteClick        = deleteAccountButton.onClickEvent.map(_ => ())
  override val onBackupClick        = backupButton.onClickEvent.map(_ => ())
  override val onDataUsageClick     = dataUsageButton.onClickEvent.map(_ => ())
  override val onReadReceiptSwitch  = readReceiptsSwitch.onCheckedChange

  override def setName(name: String) = nameButton.setTitle(name)

  override def setHandle(handle: String) = handleButton.setTitle(handle)

  override def setEmail(email: Option[EmailAddress]) = emailButton.setTitle(email.map(_.str).getOrElse(getString(R.string.pref_account_add_email_title)))

  override def setPhone(phone: Option[PhoneNumber]) = phoneButton.setTitle(phone.map(_.str).getOrElse(getString(R.string.pref_account_add_phone_title)))

  override def setTeam(team: Option[Name]): Unit = {
    team.foreach(name => teamButton.setTitle(name.str))
    teamButton.setVisible(team.isDefined)
  }

  override def setDomain(domain: Domain): Unit =
    if (domain.isDefined) {
      domainButton.setTitle(domain.str)
      domainButton.setVisible(true)
    } else {
      domainButton.setVisible(false)
    }

  override def setPicture(picture: Picture) = {
    WireGlide(context)
      .load(picture)
      .apply(new RequestOptions().transform(new CircleCrop()))
      .into(new CustomViewTarget[View, Drawable](pictureButton) {
      override def onResourceCleared(placeholder: Drawable): Unit =
        pictureButton.setDrawableStart(None)

      override def onLoadFailed(errorDrawable: Drawable): Unit =
        pictureButton.setDrawableStart(None)

      override def onResourceReady(resource: Drawable, transition: Transition[_ >: Drawable]): Unit = {
        pictureButton.setDrawableStart(Some(resource))
      }
    })
  }

  override def setAccentDrawable(drawable: Drawable) = colorButton.setDrawableStart(Some(drawable))

  override def setDeleteAccountEnabled(enabled: Boolean) = deleteAccountButton.setVisible(enabled)

  override def setEmailEnabled(enabled: Boolean) = emailButton.setVisible(enabled)

  override def setPhoneNumberEnabled(enabled: Boolean) = phoneButton.setVisible(enabled)

  override def setReadReceipt(enabled: Boolean) = readReceiptsSwitch.setChecked(enabled, disableListener = true)

  override def setResetPasswordEnabled(enabled: Boolean) = resetPasswordButton.setVisible(enabled)

  override def setAccountLocked(locked: Boolean): Unit = {
    nameButton.setEnabled(!locked)
    handleButton.setEnabled(!locked)
    emailButton.setEnabled(!locked)
    phoneButton.setEnabled(!locked)
    pictureButton.setEnabled(!locked)
    colorButton.setEnabled(!locked)
    appearanceHeader.setVisible(!locked)
    pictureButton.setVisible(!locked)
    colorButton.setVisible(!locked)
  }
}

case class AccountBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_account_screen_title

  override def layoutId = R.layout.preferences_account

  private var controller = Option.empty[AccountViewController]

  override def onViewAttached(v: View) =
    controller = Option(v.asInstanceOf[AccountViewImpl]).map(view => new AccountViewController(view)(view.wContext.injector, view.eventContext, view.getContext))

  override def onViewDetached() =
    controller = None
}

object AccountBackStackKey {
  val CREATOR: Parcelable.Creator[AccountBackStackKey] = new Parcelable.Creator[AccountBackStackKey] {
    override def createFromParcel(source: Parcel) = AccountBackStackKey()
    override def newArray(size: Int) = Array.ofDim(size)
  }
}

class AccountViewController(view: AccountView)(implicit inj: Injector, ec: EventContext, context: Context)
  extends Injectable with DerivedLogTag {

  val zms                = inject[Signal[ZMessaging]]
  val self               = zms.flatMap(_.users.selfUser)
  val team               = zms.flatMap(_.teams.selfTeam)
  val accounts           = inject[AccountsService]
  implicit val uiStorage = inject[UiStorage]
  val navigator          = inject[BackStackNavigator]

  val isTeam = team.map(_.isDefined)
  val phone  = self.map(_.phone)
  val email  = self.map(_.email)
  val domain = inject[Domain]

  val isPhoneNumberEnabled = isTeam.map(!_)

  private val accountIsLocked: Signal[Boolean] = self.map(_.isReadOnlyProfile)

  accountIsLocked.onUi { locked =>
    view.setAccountLocked(locked)
  }

  self.map(_.picture).collect { case Some(pic) => pic}.onUi { id =>
    view.setPicture(id)
  }
  Signal.zip(self, zms.flatMap {_.backend}).onUi {
    case(self, backend) => {
      view.setHandle(self.displayHandle(self.domain, if(backend.federationSupport.isSupported) DisplayHandleDomainPolicies.AlwaysShowDomain else DisplayHandleDomainPolicies.NeverShowDomain))
      view.setName(self.name)
      view.setAccentDrawable(new Drawable {

        val paint = new Paint()

        override def draw(canvas: Canvas) = {
          paint.setColor(AccentColor(self.accent).color)
          canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), getBounds.width() / 2, paint)
        }

        override def setColorFilter(colorFilter: ColorFilter) = {}

        override def setAlpha(alpha: Int) = {}

        override def getOpacity = PixelFormat.OPAQUE
      })
    }
    case _ =>
  }

  phone.onUi(view.setPhone)
  email.onUi(view.setEmail)
  team.map(_.map(_.name)).onUi(view.setTeam)
  view.setDomain(domain)

  Signal.zip(isTeam, accounts.isActiveAccountSSO)
    .map { case (team, sso) => team || sso }
    .onUi(t => view.setDeleteAccountEnabled(!t))

  accounts.isActiveAccountSSO.onUi { sso =>
    view.setEmailEnabled(!sso)
    view.setResetPasswordEnabled(!sso)
  }
  isPhoneNumberEnabled.onUi(view.setPhoneNumberEnabled)

  view.onNameClick.onUi { _ =>
    self.head.map { self =>
      showPrefDialog(EditNameDialog.newInstance(self.name), EditNameDialog.Tag)
    } (Threading.Ui)
  }

  view.onHandleClick.onUi { _ =>
    self.head.map { self =>
      import com.waz.zclient.preferences.dialogs.ChangeHandleFragment._
      showPrefDialog(newInstance(self.handle.fold("")(_.toString), cancellable = true), Tag)
    } (Threading.Ui)
  }

  if (BuildConfig.ALLOW_CHANGE_OF_EMAIL) view.onEmailClick.onUi { _ =>
    import Threading.Implicits.Ui
    accounts.activeAccountManager.head.map(_.foreach(_.hasPassword().future.foreach {
      case Left(ex) =>
        val (h, b) = DialogErrorMessage.genericError(ex.code)
        showErrorDialog(h, b)
      case Right(hasPass) =>
        showPrefDialog(
          returning(ChangeEmailDialog(hasPassword = hasPass)) {
            _.onEmailChanged.onUi { e =>
              val f = VerifyEmailPreferencesFragment(e)
              //hide the verification screen when complete
              self.map(_.email).onChanged.filter(_.contains(e)).onUi { _ =>
                f.dismiss()
              }
              showPrefDialog(f, VerifyEmailPreferencesFragment.Tag)
            }
          },
          ChangeEmailDialog.FragmentTag)
    }))
  }

  //TODO move most of this information to the dialogs themselves -- it's too tricky here to sort out what thread things are running on...
  //currently blocks a little...
  view.onPhoneClick.onUi { _ =>
    import Threading.Implicits.Ui
    for {
      email <- self.head.map(_.email)
      ph    <- self.head.map(_.phone)
    } {
      showPrefDialog(
        returning(ChangePhoneDialog(ph.map(_.str), email.isDefined)) {
          _.onPhoneChanged.onUi {
            case Some(p) =>
              val f = VerifyPhoneFragment.newInstance(p.str)
              //hide the verification screen when complete
              self.map(_.phone).onChanged.filter(_.contains(p)).onUi { _ =>
                f.dismiss()
              }
              showPrefDialog(f, VerifyPhoneFragment.TAG)
            case _ =>
          }
        },
        ChangePhoneDialog.FragmentTag)
    }
  }

  view.onPictureClick.onUi(_ => navigator.goTo(ProfilePictureBackStackKey()))

  view.onAccentClick.onUi { _ =>
    self.head.map { _ =>
      showPrefDialog(new AccentColorPickerFragment(), AccentColorPickerFragment.fragmentTag)
    }(Threading.Ui)
  }

  view.onPasswordResetClick.onUi { _ => inject[BrowserController].openForgotPassword() }

  view.onLogoutClick.onUi { _ =>
    showAlertDialog(context, null,
      getString(R.string.pref_account_sign_out_warning_message),
      getString(R.string.pref_account_sign_out_warning_verify),
      getString(R.string.pref_account_sign_out_warning_cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) = {
          import Threading.Implicits.Ui
          zms.map(_.selfUserId).head.flatMap { id => accounts.logout(id, reason = UserInitiated) }
            .flatMap(_ => accounts.accountsWithManagers.head.map(_.isEmpty)).map {
            case true =>
              context.startActivity(AppEntryActivity.newIntent(context))
              finishPreferencesActivity()
            case false =>
              finishPreferencesActivity()
          }
        }
      }, null)
  }

  view.onDeleteClick.onUi { _ =>
    self.head.map { self =>
      val email = self.email.map(_.str)
      val phone = self.phone.map(_.str)

      val message: String = (email, phone) match {
        case (Some(e), _)    => getString(R.string.pref_account_delete_warning_message_email, e)
        case (None, Some(p)) => getString(R.string.pref_account_delete_warning_message_sms, p)
        case _ => ""
      }

      showAlertDialog(context,
        getString(R.string.pref_account_delete_warning_title),
        getBoldText(context, message),
        getString(R.string.pref_account_delete_warning_verify),
        getString(R.string.pref_account_delete_warning_cancel),
        new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) =
            zms.head.map(_.users.deleteAccount())(Threading.Background)
        }, null)
    }(Threading.Ui)
  }

  view.onBackupClick.onUi { _ =>
    Signal.zip(accounts.isActiveAccountSSO, email).head.map {
      case (true, _)        => navigator.goTo(BackupExportKey())
      case (false, Some(_)) => navigator.goTo(BackupExportKey())
      case _ =>
        showAlertDialog(context,
          R.string.pref_account_backup_warning_title,
          R.string.pref_account_backup_warning_message,
          R.string.pref_account_backup_warning_ok,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int) = dialog.dismiss()
          }, true)
    }(Threading.Ui)
  }

  view.onDataUsageClick.onUi { _ =>
    navigator.goTo(DataUsagePermissionsKey())
  }

  private def showPrefDialog(f: Fragment, tag: String) = {
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }

  private def finishPreferencesActivity() =
    Option(context.asInstanceOf[Activity]).foreach(_.finish())

  inject[UserAccountsController].readReceiptsEnabled.onUi(view.setReadReceipt)

  view.onReadReceiptSwitch.foreach { enabled =>
    zms.head.flatMap(_.propertiesService.setReadReceiptsEnabled(enabled))(Threading.Background)
  }
}
