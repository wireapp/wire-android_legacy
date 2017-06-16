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

import android.app.Activity
import android.content.Context
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.graphics.drawable.{ColorDrawable, Drawable}
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.api.impl.AccentColor
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.pages.main.profile.preferences.dialogs.AddPhoneNumberPreferenceDialogFragment
import com.waz.zclient.pages.main.profile.preferences.views.TextButton
import com.waz.zclient.utils.{StringUtils, UiStorage, UserSignal, ViewState}
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient._

trait AccountView {
  def setName(name: String)
  def setHandle(handle: String)
  def setEmail(email: String)
  def setPhone(phone: String)
  def setPictureDrawable(drawable: Drawable)
  def setAccentDrawable(drawable: Drawable)
}

class AccountViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with AccountView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_account_layout)

  val nameButton = findById[TextButton](R.id.preferences_account_name)
  val handleButton = findById[TextButton](R.id.preferences_account_handle)
  val emailButton = findById[TextButton](R.id.preferences_account_email)
  val phoneButton = findById[TextButton](R.id.preferences_account_phone)
  val pictureButton = findById[TextButton](R.id.preferences_account_picture)
  val colorButton = findById[TextButton](R.id.preferences_account_accent)
  val resetPasswordButton = findById[TextButton](R.id.preferences_account_reset_pw)
  val logoutButton = findById[TextButton](R.id.preferences_account_logout)
  val deleteAccountButton = findById[TextButton](R.id.preferences_account_delete)

  phoneButton.onClickEvent.on(Threading.Ui) { _ =>
    getContext.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .add(AddPhoneNumberPreferenceDialogFragment.newInstance(""), AddPhoneNumberPreferenceDialogFragment.TAG)
      .addToBackStack(AddPhoneNumberPreferenceDialogFragment.TAG)
      .commit
  }

  override def setName(name: String) = nameButton.setTitle(name)

  override def setHandle(handle: String) = handleButton.setTitle(handle)

  override def setEmail(email: String) = emailButton.setTitle(email)

  override def setPhone(phone: String) = phoneButton.setTitle(phone)

  override def setPictureDrawable(drawable: Drawable) = pictureButton.setDrawableStart(Some(drawable))

  override def setAccentDrawable(drawable: Drawable) = colorButton.setDrawableStart(Some(drawable))
}

case class AccountViewState() extends ViewState {

  override def name = "Account"//TODO: resource

  override def layoutId = R.layout.preferences_account

  override def onViewAttached(v: View) = {
    Option(v.asInstanceOf[AccountViewImpl]).map(view => new AccountViewController(view)(view.wContext.injector, view))
  }

  override def onViewDetached() = {}
}

class AccountViewController(view: AccountView)(implicit inj: Injector, ec: EventContext) extends Injectable {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

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
}
