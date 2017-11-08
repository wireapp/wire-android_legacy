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
package com.waz.zclient.pages.main.profile.preferences.pages

import android.content.{Context, DialogInterface, Intent}
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.net.Uri
import android.os.{Bundle, Parcel, Parcelable}
import android.support.v4.app.{Fragment, FragmentTransaction}
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.api.impl.AccentColor
import com.waz.model.EmailAddress
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.controllers.global.PasswordController
import com.waz.zclient.pages.main.profile.preferences.dialogs.{ChangeEmailDialog, ChangePhoneDialog, VerifyEmailPreferenceFragment, VerifyPhoneNumberPreferenceFragment}
import com.waz.zclient.pages.main.profile.preferences.views.{EditNameDialog, PictureTextButton, TextButton}
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.preferences.dialogs.AccentColorPickerFragment
import com.waz.zclient.tracking.{GlobalTrackingController, LoggedOutEvent}
import com.waz.zclient.ui.utils.TextViewUtils._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils._
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, StringUtils, UiStorage, UserSignal}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.utils.RichView

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

  def setName(name: String): Unit
  def setHandle(handle: String): Unit
  def setEmail(email: Option[String]): Unit
  def setPhone(phone: Option[String]): Unit
  def setPictureDrawable(drawable: Drawable): Unit
  def setAccentDrawable(drawable: Drawable): Unit
  def setDeleteAccountEnabled(enabled: Boolean): Unit
}

class AccountViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with AccountView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_account_layout)

  val nameButton          = findById[TextButton](R.id.preferences_account_name)
  val handleButton        = findById[TextButton](R.id.preferences_account_handle)
  val emailButton         = findById[TextButton](R.id.preferences_account_email)
  val phoneButton         = findById[TextButton](R.id.preferences_account_phone)
  val pictureButton       = findById[PictureTextButton](R.id.preferences_account_picture)
  val colorButton         = findById[PictureTextButton](R.id.preferences_account_accent)
  val resetPasswordButton = findById[TextButton](R.id.preferences_account_reset_pw)
  val logoutButton        = findById[TextButton](R.id.preferences_account_logout)
  val deleteAccountButton = findById[TextButton](R.id.preferences_account_delete)

  override val onNameClick          = nameButton.onClickEvent.map(_ => ())
  override val onHandleClick        = handleButton.onClickEvent.map(_ => ())
  override val onEmailClick         = emailButton.onClickEvent.map(_ => ())
  override val onPhoneClick         = phoneButton.onClickEvent.map(_ => ())
  override val onPictureClick       = pictureButton.onClickEvent.map(_ => ())
  override val onAccentClick        = colorButton.onClickEvent.map(_ => ())
  override val onPasswordResetClick = resetPasswordButton.onClickEvent.map(_ => ())
  override val onLogoutClick        = logoutButton.onClickEvent.map(_ => ())
  override val onDeleteClick        = deleteAccountButton.onClickEvent.map(_ => ())

  override def setName(name: String) = nameButton.setTitle(name)

  override def setHandle(handle: String) = handleButton.setTitle(handle)

  override def setEmail(email: Option[String]) = emailButton.setTitle(email.getOrElse(getString(R.string.pref_account_add_email_title)))

  override def setPhone(phone: Option[String]) = phoneButton.setTitle(phone.getOrElse(getString(R.string.pref_account_add_phone_title)))

  override def setPictureDrawable(drawable: Drawable) = pictureButton.setDrawableStart(Some(drawable))

  override def setAccentDrawable(drawable: Drawable) = colorButton.setDrawableStart(Some(drawable))

  override def setDeleteAccountEnabled(enabled: Boolean) = deleteAccountButton.setVisible(enabled)
}

case class AccountBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_account_screen_title

  override def layoutId = R.layout.preferences_account

  private var controller = Option.empty[AccountViewController]

  override def onViewAttached(v: View) =
    controller = Option(v.asInstanceOf[AccountViewImpl]).map(view => new AccountViewController(view)(view.wContext.injector, view, view.getContext))

  override def onViewDetached() =
    controller = None
}

object AccountBackStackKey {
  val CREATOR: Parcelable.Creator[AccountBackStackKey] = new Parcelable.Creator[AccountBackStackKey] {
    override def createFromParcel(source: Parcel) = AccountBackStackKey()
    override def newArray(size: Int) = Array.ofDim(size)
  }
}

class AccountViewController(view: AccountView)(implicit inj: Injector, ec: EventContext, context: Context) extends Injectable {

  val zms                = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]
  val navigator          = inject[BackStackNavigator]
  val password           = inject[PasswordController].password
  val tracking           = inject[GlobalTrackingController]

  val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
  } yield self

  val account = for {
    zms <- zms
    account <- zms.account.accountData
  } yield account

  val team = zms.flatMap(_.teams.selfTeam)

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect{case Some(pic) => WireImage(pic)}

  view.setPictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  self.onUi { self =>
    self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
    view.setName(self.name)
    view.setAccentDrawable(new Drawable {

      val paint = new Paint()

      override def draw(canvas: Canvas) = {
        paint.setColor(AccentColor(self.accent).getColor)
        canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), getBounds.width() / 2, paint)
      }

      override def setColorFilter(colorFilter: ColorFilter) = {}

      override def setAlpha(alpha: Int) = {}

      override def getOpacity = PixelFormat.OPAQUE
    })
  }

  account.onUi { account =>
    view.setEmail(account.email.map(_.str))
    view.setPhone(account.phone.map(_.str))
  }

  team.onUi { team => view.setDeleteAccountEnabled(team.isEmpty) }

  view.onNameClick.onUi { _ =>
    self.head.map { self =>
      showPrefDialog(EditNameDialog.newInstance(self.name), EditNameDialog.Tag)
    } (Threading.Ui)
  }

  view.onHandleClick.onUi { _ =>
    self.head.map { self =>
      import com.waz.zclient.preferences.dialogs.ChangeHandleFragment._
      showPrefDialog(newInstance(self.handle.fold("")(_.string), cancellable = true), FragmentTag)
    } (Threading.Ui)
  }

  view.onEmailClick.onUi { _ =>
    import Threading.Implicits.Ui
    for {
      email <- self.head.map(_.email)
      pw    <- password.head
    } {
      showPrefDialog(
        returning(ChangeEmailDialog(addingEmail = email.isEmpty)) {
          _.onEmailChanged { e =>
            val f = VerifyEmailPreferenceFragment.newInstance(e)
            //hide the verification screen when complete
            self.map(_.email).onChanged.filter(_.contains(EmailAddress(e))).onUi { _ =>
              f.dismiss()
            }
            showPrefDialog(f, VerifyEmailPreferenceFragment.TAG)
          }
        },
        ChangeEmailDialog.FragmentTag)
    }
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
          _.onPhoneChanged {
            case Some(p) =>
              val f = VerifyPhoneNumberPreferenceFragment.newInstance(p.str)
              //hide the verification screen when complete
              self.map(_.phone).onChanged.filter(_.contains(p)).onUi { _ =>
                f.dismiss()
              }
              showPrefDialog(f, VerifyPhoneNumberPreferenceFragment.TAG)
            case _ =>
          }
        },
        ChangePhoneDialog.FragmentTag)
    }
  }

  view.onPictureClick.onUi(_ => navigator.goTo(ProfilePictureBackStackKey()))

  view.onAccentClick.onUi { _ =>
    self.head.map { self =>
      showPrefDialog(new AccentColorPickerFragment(), AccentColorPickerFragment.fragmentTag)
    }(Threading.Ui)
  }

  view.onPasswordResetClick.onUi { _ =>
    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_password_reset))))
  }

  view.onLogoutClick.onUi { _ =>
    showAlertDialog(context, null,
      getString(R.string.pref_account_sign_out_warning_message),
      getString(R.string.pref_account_sign_out_warning_verify),
      getString(R.string.pref_account_sign_out_warning_cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) = {
          context.asInstanceOf[PreferencesActivity].getControllerFactory.getUsernameController.tearDown()
          tracking.onLoggedOut(LoggedOutEvent.Manual)
          zms.map(_.account).head.flatMap(_.logout(true))(Threading.Ui)
          navigator.back()
          navigator.back()
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

  private def showPrefDialog(f: Fragment, tag: String) = {
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }
}
