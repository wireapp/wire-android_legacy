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
package com.waz.zclient.utils

import android.content.res.{Configuration, Resources, TypedArray}
import android.content.{Context, DialogInterface, Intent}
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import android.text.format.Formatter
import android.util.{AttributeSet, DisplayMetrics, TypedValue}
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StyleableRes
import com.waz.model.{AccentColor, Availability}
import com.waz.service.AccountsService.{ClientDeleted, InvalidCookie, LogoutReason}
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.appentry.DialogErrorMessage
import com.waz.zclient.ui.utils.ResourceUtils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

object ContextUtils {
  def getColor(resId: Int)(implicit context: Context): Int = ContextCompat.getColor(context, resId)

  def getColorWithThemeJava(resId: Int, context: Context): Int =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) getColor(resId)(context)
    else context.getResources.getColor(resId, context.getTheme)

  def getColorWithTheme(resId: Int)(implicit context: Context): Int =
    getColorWithThemeJava(resId, context)

  def getColorStateList(resId: Int)(implicit context: Context) = ContextCompat.getColorStateList(context, resId)

  def getInt(resId: Int)(implicit context: Context) = context.getResources.getInteger(resId)

  def getString(resId: Int)(implicit context: Context): String = context.getResources.getString(resId)
  def getString(resId: Int, args: String*)(implicit context: Context): String = context.getResources.getString(resId, args:_*)

  def showToast(resId: Int, long: Boolean = true)(implicit context: Context): Unit =
    Toast.makeText(context, resId, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

  def showToast(msg: String)(implicit context: Context): Unit =
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

  def getStringOrEmpty(resId: Int)(implicit context: Context): String = if (resId > 0) getString(resId) else ""
  def getStringOrEmpty(resId: Int, args: String*)(implicit context: Context): String = if (resId > 0) getString(resId, args:_*) else ""

  def getQuantityString(resId: Int, quantity: Int, args: AnyRef*)(implicit context: Context): String = context.getResources.getQuantityString(resId, quantity, args:_*)

  def getDimenPx(resId: Int)(implicit context: Context) = context.getResources.getDimensionPixelSize(resId)
  def getDimen(resId: Int)(implicit context: Context) = context.getResources.getDimension(resId)

  def getDrawable(resId: Int, theme: Option[Resources#Theme] = None)(implicit context: Context): Drawable = {
    context.getResources.getDrawable(resId, theme.orNull)
  }

  def getIntArray(resId: Int)(implicit context: Context) = context.getResources.getIntArray(resId)
  def getStringArray(resId: Int)(implicit context: Context) = context.getResources.getStringArray(resId)
  def getResEntryName(resId: Int)(implicit context: Context) = context.getResources.getResourceEntryName(resId)

  def getResourceFloat(resId: Int)(implicit context: Context) = ResourceUtils.getResourceFloat(context.getResources, resId)

  def toPx(dp: Int)(implicit context: Context) = (dp * context.getResources.getDisplayMetrics.density).toInt

  def getLocale(implicit context: Context) = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      DeprecationUtils.getDefaultLocale(context)
    } else {
      context.getResources.getConfiguration.getLocales.get(0)
    }
  }

  def withStyledAttributes[A](set: AttributeSet, @StyleableRes attrs: Array[Int])(body: TypedArray => A)(implicit context: Context) = {
    val a = context.getTheme.obtainStyledAttributes(set, attrs, 0, 0)
    try body(a) finally a.recycle()
  }

  def getStyledColor(resId: Int)(implicit context: Context): Int = {
    val typedValue  = new TypedValue
    val a  = context.obtainStyledAttributes(typedValue.data, Array[Int](resId))
    val color = a.getColor(0, 0)
    a.recycle()
    color
  }

  def getStyledColor(resId: Int, theme: Resources#Theme, defaultColor: Int = 0)(implicit context: Context): Int = {
    val typedValue  = new TypedValue
    val a  = theme.obtainStyledAttributes(typedValue.data, Array[Int](resId))
    val color = a.getColor(0, defaultColor)
    a.recycle()
    color
  }

  def getStyledDrawable(resId: Int, theme: Resources#Theme)(implicit context: Context): Option[Drawable] = {
    val typedValue  = new TypedValue
    val a  = theme.obtainStyledAttributes(typedValue.data, Array[Int](resId))
    returning(Option(a.getDrawable(0)))(_ => a.recycle())
  }

  /**
    * @return the amount of pixels of the horizontal axis of the phone
    */
  def getOrientationDependentDisplayWidth(implicit context: Context): Int = context.getResources.getDisplayMetrics.widthPixels

  /**
    * @return everytime the amount of pixels of the (in portrait) horizontal axis of the phone
    */
  def getOrientationIndependentDisplayWidth(implicit context: Context): Int =
    if (isInPortrait) context.getResources.getDisplayMetrics.widthPixels
    else context.getResources.getDisplayMetrics.heightPixels

  /**
    * @return the amount of pixels of the vertical axis of the phone
    */
  def getOrientationDependentDisplayHeight(implicit context: Context): Int = context.getResources.getDisplayMetrics.heightPixels

  /**
    * @return everytime the amount of pixels of the (in portrait) width axis of the phone
    */
  def getOrientationIndependentDisplayHeight(implicit context: Context): Int =
    if (isInPortrait) context.getResources.getDisplayMetrics.heightPixels
    else context.getResources.getDisplayMetrics.widthPixels

  def getStatusBarHeight(implicit context: Context): Int = getDimensionPixelSize("status_bar_height")

  def getNavigationBarHeight(implicit context: Context): Int =
    getDimensionPixelSize(if (isInPortrait) "navigation_bar_height" else "navigation_bar_height_landscape")

  private def getDimensionPixelSize(name: String)(implicit context: Context): Int = {
    val resourceId = context.getResources.getIdentifier(name, "dimen", "android")
    if (resourceId > 0) context.getResources.getDimensionPixelSize(resourceId) else 0
  }

  def getRealDisplayWidth(implicit context: Context): Int = {
    val realMetrics = new DisplayMetrics
    context.getSystemService(Context.WINDOW_SERVICE).asInstanceOf[WindowManager].getDefaultDisplay.getRealMetrics(realMetrics)
    realMetrics.widthPixels
  }

  def isInLandscape(implicit context: Context): Boolean = isInLandscape(context.getResources.getConfiguration)
  def isInLandscape(configuration: Configuration): Boolean = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  def isInPortrait(implicit context: Context): Boolean = isInPortrait(context.getResources.getConfiguration)
  def isInPortrait(configuration: Configuration): Boolean = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  def showGenericErrorDialog()(implicit context: Context): Future[Unit] =
    showErrorDialog(R.string.generic_error_header, R.string.generic_error_message)

  def showErrorDialog(dialogErrorMessage: DialogErrorMessage)(implicit context: Context): Future[Unit] =
    showErrorDialog(dialogErrorMessage.headerResource, dialogErrorMessage.bodyResource)

  //INFORMATION ABOUT DIALOGS: https://developer.android.com/guide/topics/ui/dialogs
  def showErrorDialog(headerRes: Int, msgRes: Int)(implicit context: Context): Future[Unit] = {
    showErrorDialog(getString(headerRes), getString(msgRes))
  }

  def showErrorDialog(headerText: String, msg: String)(implicit context: Context): Future[Unit] = {
    val p = Promise[Unit]()
    val dialog = new AlertDialog.Builder(context)
      .setCancelable(false)
      .setTitle(headerText)
      .setMessage(msg)
      .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          dialog.dismiss()
          p.tryComplete(Success({}))
        }
      }).create
    dialog.show()
    p.future
  }

  /// Dialog with title, message and ok.
  def showInfoDialog(title: String, msg: String, positiveRes: Int = android.R.string.ok)
                    (implicit context: Context): Future[Boolean] = {

    val p = Promise[Boolean]()
    new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(msg)
      .setPositiveButton(positiveRes, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = p.tryComplete(Success(true))
      })
      .setCancelable(false)
      .create
      .show()
    p.future
  }

  //TODO Context has to be an Activity - maybe specify this in the type
  def showWifiWarningDialog(size: Long, accentColor: AccentColor)
                           (implicit ex: ExecutionContext, context: Context): Future[Boolean] =
    showConfirmationDialog(
      getString(R.string.asset_upload_warning__large_file__title),
      if (size > 0)
        getString(R.string.asset_upload_warning__large_file__message, Formatter.formatFileSize(context, size))
      else
        getString(R.string.asset_upload_warning__large_file__message_default),
      color = accentColor
    )

  def showPermissionsErrorDialog(titleRes: Int, msgRes: Int, ackRes: Int = android.R.string.ok)(implicit cxt: Context): Future[Unit] = {
    val p = Promise[Unit]()
    new AlertDialog.Builder(cxt)
      .setTitle(titleRes)
      .setMessage(msgRes)
      .setNegativeButton(ackRes, null)
      .setPositiveButton(R.string.permissions_denied_dialog_settings, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit =
          cxt.startActivity(returning(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", cxt.getPackageName, null)))(_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
      })
      .setOnDismissListener(new DialogInterface.OnDismissListener { //From the docs: The system calls onDismiss() upon each event that invokes the onCancel() callback
        override def onDismiss(dialog: DialogInterface) = p.tryComplete(Success({}))
      })
      .create
      .show()
    p.future
  }

  def showConfirmationDialog(title: String, msg: String, color: AccentColor)
                            (implicit ex: ExecutionContext, context: Context): Future[Boolean] =
    showConfirmationDialog(title, msg, android.R.string.ok, android.R.string.cancel, None, color).map(_.getOrElse(false))

  def showConfirmationDialog(title: String,
                             msg: String,
                             positiveRes: Int,
                             negativeRes: Int,
                             color: AccentColor)
                            (implicit ex: ExecutionContext, context: Context): Future[Boolean] =
    showConfirmationDialog(title, msg, positiveRes, negativeRes, None, color).map(_.getOrElse(false))

  def showConfirmationDialog(title: String,
                             msg:   String,
                             positiveRes: Int,
                             negativeRes: Int,
                             neutralRes: Option[Int],
                             color: AccentColor)
                            (implicit context: Context): Future[Option[Boolean]] = {
    val p = Promise[Option[Boolean]]()

    val builder = new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(msg)
      .setPositiveButton(positiveRes, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = p.tryComplete(Success(Some(true)))
      })
      .setNegativeButton(negativeRes, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = dialog.cancel()
      })
      .setOnCancelListener(new DialogInterface.OnCancelListener {
        override def onCancel(dialog: DialogInterface) = p.tryComplete(Success(Some(false)))
      })

    neutralRes.foreach(res =>
      builder.setNeutralButton(res, new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = p.trySuccess(None)
      })
    )

    val dialog = builder.create()

    dialog.show()

    Option(dialog.getButton(DialogInterface.BUTTON_POSITIVE)).foreach { button =>
      button.setTextColor(color.color)
      button.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_END)
    }
    Option(dialog.getButton(DialogInterface.BUTTON_NEGATIVE)).foreach { button =>
      button.setTextColor(color.color)
      button.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_END)
    }
    Option(dialog.getButton(DialogInterface.BUTTON_NEUTRAL)).foreach { button =>
      button.setTextColor(color.color)
      button.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_END)
    }

    p.future
  }

  def showStatusNotificationWarning(availability: Availability, color: AccentColor)
                                   (implicit ex: ExecutionContext, context: Context): Future[Boolean] = {
    val (title, body) = availability match {
      case Availability.None      =>
        (R.string.availability_notification_warning_nostatus_title, R.string.availability_notification_warning_nostatus)
      case Availability.Available =>
        (R.string.availability_notification_warning_available_title, R.string.availability_notification_warning_available)
      case Availability.Busy      =>
        (R.string.availability_notification_warning_busy_title, R.string.availability_notification_warning_busy)
      case Availability.Away      =>
        (R.string.availability_notification_warning_away_title, R.string.availability_notification_warning_away)
    }
    showConfirmationDialog(
      title       = getString(title),
      msg         = getString(body),
      positiveRes = R.string.availability_notification_dont_show,
      negativeRes = R.string.availability_notification_ok,
      color       = color
    )
  }

  def showLogoutWarningIfNeeded(reason: LogoutReason)(implicit context: Context): Future[Unit] = {
    if (reason == InvalidCookie || reason == ClientDeleted) {
      showErrorDialog(R.string.invalid_cookie_dialog_title, R.string.invalid_cookie_dialog_message)
    } else {
      Future.successful(())
    }
  }
}
