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

import android.content.{Context, DialogInterface}
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.os.{Bundle, Parcel, Parcelable}
import android.support.v4.app.{Fragment, FragmentTransaction}
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.api.impl.AccentColor
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient._
import com.waz.zclient.controllers.tracking.events.profile.SignOut
import com.waz.zclient.core.controllers.tracking.events.session.LoggedOutEvent
import com.waz.zclient.pages.main.profile.preferences.dialogs.{AddEmailAndPasswordPreferenceDialogFragment, AddPhoneNumberPreferenceDialogFragment}
import com.waz.zclient.pages.main.profile.preferences.views.{EditNameDialog, TextButton}
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.preferences.dialogs.AccentColorPickerFragment
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.utils.ViewUtils._
import com.waz.zclient.utils.{BackStackNavigator, StringUtils, UiStorage, UserSignal, BackStackKey}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}

import scala.concurrent.Future

trait AccountView {
  val onNameClick: EventStream[Unit]
  val onHandleClick: EventStream[Unit]
  val onEmailClick: EventStream[Unit]
  val onPhoneClick: EventStream[Unit]
  val onPictureClick: EventStream[Unit]
  val onAccentClick: EventStream[Unit]
  val onResetClick: EventStream[Unit]
  val onLogoutClick: EventStream[Unit]
  val onDeleteClick: EventStream[Unit]

  def setName(name: String): Unit
  def setHandle(handle: String): Unit
  def setEmail(email: String): Unit
  def setPhone(phone: String): Unit
  def setPictureDrawable(drawable: Drawable): Unit
  def setAccentDrawable(drawable: Drawable): Unit
}

class AccountViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with AccountView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_account_layout)

  val nameButton          = findById[TextButton](R.id.preferences_account_name)
  val handleButton        = findById[TextButton](R.id.preferences_account_handle)
  val emailButton         = findById[TextButton](R.id.preferences_account_email)
  val phoneButton         = findById[TextButton](R.id.preferences_account_phone)
  val pictureButton       = findById[TextButton](R.id.preferences_account_picture)
  val colorButton         = findById[TextButton](R.id.preferences_account_accent)
  val resetPasswordButton = findById[TextButton](R.id.preferences_account_reset_pw)
  val logoutButton        = findById[TextButton](R.id.preferences_account_logout)
  val deleteAccountButton = findById[TextButton](R.id.preferences_account_delete)

  override val onNameClick    = nameButton.onClickEvent.map(_ => ())
  override val onHandleClick  = handleButton.onClickEvent.map(_ => ())
  override val onEmailClick   = emailButton.onClickEvent.map(_ => ())
  override val onPhoneClick   = phoneButton.onClickEvent.map(_ => ())
  override val onPictureClick = pictureButton.onClickEvent.map(_ => ())
  override val onAccentClick  = colorButton.onClickEvent.map(_ => ())
  override val onResetClick   = resetPasswordButton.onClickEvent.map(_ => ())
  override val onLogoutClick  = logoutButton.onClickEvent.map(_ => ())
  override val onDeleteClick  = deleteAccountButton.onClickEvent.map(_ => ())

  override def setName(name: String) = nameButton.setTitle(name)

  override def setHandle(handle: String) = handleButton.setTitle(handle)

  override def setEmail(email: String) = emailButton.setTitle(email)

  override def setPhone(phone: String) = phoneButton.setTitle(phone)

  override def setPictureDrawable(drawable: Drawable) = pictureButton.setDrawableStart(Some(drawable))

  override def setAccentDrawable(drawable: Drawable) = colorButton.setDrawableStart(Some(drawable))
}

case class AccountBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_account_screen_title

  override def layoutId = R.layout.preferences_account

  var controller = Option.empty[AccountViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[AccountViewImpl]).map(view => new AccountViewController(view)(view.wContext.injector, view, view.getContext))
  }

  override def onViewDetached() = {
    controller = None
  }
}

object AccountBackStackKey {
  val CREATOR: Parcelable.Creator[AccountBackStackKey] = new Parcelable.Creator[AccountBackStackKey] {
    override def createFromParcel(source: Parcel) = AccountBackStackKey()
    override def newArray(size: Int) = Array.ofDim(size)
  }
}

class AccountViewController(view: AccountView)(implicit inj: Injector, ec: EventContext, context: Context) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]
  lazy val tracking = inject[GlobalTrackingController]
  val navigator = inject[BackStackNavigator]

  val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
  } yield self

  val account = for {
    zms <- zms
    account <- zms.account.accountData
  } yield account

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect{case Some(pic) => WireImage(pic)}

  view.setPictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  self.on(Threading.Ui) { self =>
    self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
    view.setName(self.getDisplayName)
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

  account.on(Threading.Ui){ account =>
    view.setEmail(account.email.fold("-")(_.str))
    view.setPhone(account.phone.fold("-")(_.str))
  }

  view.onNameClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
      showPrefDialog(EditNameDialog.newInstance(self.name), EditNameDialog.Tag)
    }(Threading.Ui)
  }

  view.onHandleClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
      import com.waz.zclient.preferences.dialogs.ChangeHandleFragment._
      showPrefDialog(newInstance(self.handle.fold("")(_.string), cancellable = true), FragmentTag)
    }(Threading.Ui)
  }

  //TODO: How to handle email verification?
  view.onEmailClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
      import AddEmailAndPasswordPreferenceDialogFragment._
      showPrefDialog(newInstance, TAG)
    }(Threading.Ui)
  }

  view.onPhoneClick.on(Threading.Ui){ _ =>
    account.head.map{ account =>
      import AddPhoneNumberPreferenceDialogFragment._
      showPrefDialog(newInstance(account.phone.map(_.str).getOrElse("")), TAG)
    }(Threading.Ui)
  }

  view.onPictureClick.on(Threading.Ui){ _ => navigator.goTo(ProfilePictureBackStackKey()) }

  view.onAccentClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
      showPrefDialog(new AccentColorPickerFragment(), AccentColorPickerFragment.fragmentTag)
    }(Threading.Ui)
  }

  view.onResetClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
    }(Threading.Ui)
  }

  view.onLogoutClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
      showAlertDialog(context, null,
        context.getString(R.string.pref_account_sign_out_warning_message),
        context.getString(R.string.pref_account_sign_out_warning_verify),
        context.getString(R.string.pref_account_sign_out_warning_cancel),
        new DialogInterface.OnClickListener() {
          def onClick(dialog: DialogInterface, which: Int) = {
            context.asInstanceOf[PreferencesActivity].getControllerFactory.getUsernameController.tearDown()
            // TODO: Remove old SignOut event https://wearezeta.atlassian.net/browse/AN-4232
            Future.sequence(Seq(tracking.tagEvent(new SignOut), tracking.tagEvent(new LoggedOutEvent)))(Seq.canBuildFrom, Threading.Background)
            zms.map(_.account).head.flatMap(_.logout())(Threading.Ui)
          }
        }, null)
    }(Threading.Ui)
  }

  view.onDeleteClick.on(Threading.Ui){ _ =>
    self.head.map{ self =>
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
