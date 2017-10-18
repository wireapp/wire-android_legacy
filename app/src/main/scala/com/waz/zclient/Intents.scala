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
package com.waz.zclient

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.Locale

import android.app.PendingIntent
import android.content.{ClipData, Context, Intent}
import android.net.Uri
import android.text.TextUtils
import com.waz.model.{AccountId, ConvId}
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.utils.StringUtils
import hugo.weaving.DebugLog

object Intents {

  private val WIRE_SCHEME = "wire"
  private val PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN = "password-reset-successful"
  private val SMS_CODE_TOKEN = "verify-phone"
  private val INVITE_HOST_TOKEN = "connect"
  private val EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION = "EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION"
  private val GOOGLE_MAPS_INTENT_URI = "geo:0,0?q=%s,%s"
  private val GOOGLE_MAPS_WITH_LABEL_INTENT_URI = "geo:0,0?q=%s,%s(%s)"
  private val GOOGLE_MAPS_INTENT_PACKAGE = "com.google.android.apps.maps"
  private val GOOGLE_MAPS_WEB_LINK = "http://maps.google.com/maps?z=%d&q=loc:%f+%f+(%s)"
  private val IMAGE_MIME_TYPE = "image/*"
  private val HTTPS_SCHEME = "https"
  private val WIRE_HOST = "wire.com"

  private lazy val FromNotificationExtra = "from_notification"
  private lazy val FromSharingExtra      = "from_sharing"
  private lazy val StartCallExtra        = "start_call"
  private lazy val AccountIdExtra        = "account_id"
  private lazy val ConvIdExtra           = "conv_id"

  private lazy val OpenPageExtra         = "open_page"

  type Page = String
  object Page {
    lazy val Settings = "Settings"
    lazy val Devices  = "Devices"
  }

  def CallIntent(accountId: AccountId, convId: ConvId, requestCode: Int)
                (implicit context: Context) =
    ZIntent(context, accountId, Some(convId), requestCode, startCall = true)

  def QuickReplyIntent(accountId: AccountId, convId: ConvId, requestCode: Int)
                      (implicit context: Context) =
    ZIntent(context, accountId, Some(convId), requestCode, classOf[PopupActivity])

  def OpenConvIntent(accountId: AccountId, convId: ConvId, requestCode: Int)
                    (implicit context: Context) =
    ZIntent(context, accountId, Some(convId), requestCode)

  def OpenAccountIntent(accountId: AccountId, requestCode: Int = System.currentTimeMillis().toInt)
                       (implicit context: Context) =
    ZIntent(context, accountId)

  def EnterAppIntent(showSettings: Boolean = false)(implicit context: Context) =
    returning(new Intent(context, classOf[MainActivity])) { i =>
      if (showSettings) i.putExtra(OpenPageExtra, Page.Settings)
    }

  def ShowDevicesIntent(implicit context: Context) =
    new Intent(context, classOf[PreferencesActivity]).putExtra(OpenPageExtra, Page.Devices)

  def GalleryIntent(context: Context, uri: URI): PendingIntent = {
    // TODO: AN-2276 - Replace with ShareCompat.IntentBuilder
    val androidUri = AndroidURIUtil.unwrap(uri)
    val galleryIntent = new Intent(android.content.Intent.ACTION_VIEW)
      .setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE)
      .putExtra(Intent.EXTRA_STREAM, androidUri)
    galleryIntent.setClipData(new ClipData(null, Array[String](IMAGE_MIME_TYPE), new ClipData.Item(androidUri)))
    PendingIntent.getActivity(context, 0, galleryIntent, 0)
  }

  def PendingShareIntent(context: Context, uri: URI): PendingIntent = {
    val shareIntent = new Intent(context, classOf[ShareSavedImageActivity])
      .putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(uri))
      .putExtra(EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, true)
    PendingIntent.getActivity(context, 0, shareIntent, 0)
  }

  def DebugReportIntent(context: Context, fileUri: URI): Intent = {
    val versionName = try {
      context.getPackageManager.getPackageInfo(context.getPackageName, 0).versionName
    } catch {
      case e: Exception => "n/a"
    }

    val intent = new Intent(Intent.ACTION_SEND)
      .setType("vnd.android.cursor.dir/email")
    val to =
      if (zclient.BuildConfig.DEVELOPER_FEATURES_ENABLED) {
        Array[String]{"android@wire.com"}
      } else {
        Array[String]{"support@wire.com"}
      }
    intent
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      .putExtra(Intent.EXTRA_EMAIL, to)
      .putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(fileUri))
      .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.debug_report__body))
      .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_report__title, versionName))
  }

  def SavedImageShareIntent(context: Context, uri: URI): Intent = {
    val androidUri = AndroidURIUtil.unwrap(uri)
    val shareIntent = new Intent(Intent.ACTION_SEND)
      .putExtra(Intent.EXTRA_STREAM, androidUri)
      .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      .setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE)
    shareIntent.setClipData(new ClipData(null, Array[String]{IMAGE_MIME_TYPE}, new ClipData.Item(androidUri)))
    Intent.createChooser(shareIntent,
      context.getString(R.string.notification__image_saving__action__share))
  }

  def isLaunchFromSaveImageNotificationIntent(intent: Option[Intent]): Boolean = {
    intent match {
      case None => false
      case Some(i) => i.getBooleanExtra(EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, false)
    }
  }

  def GoogleMapsIntent(context: Context, lat: Float, lon: Float, zoom: Int, name: String): Intent = {
    val gmmIntentUri = if (StringUtils.isBlank(name)) {
      Uri.parse(GOOGLE_MAPS_INTENT_URI.format(lat, lon))
    } else {
      Uri.parse(GOOGLE_MAPS_WITH_LABEL_INTENT_URI.format(lat, lon, name))
    }
    val mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri)
    mapIntent.setPackage(GOOGLE_MAPS_INTENT_PACKAGE)
    if (mapIntent.resolveActivity(context.getPackageManager) == null) {
      GoogleMapsWebFallbackIntent(lat, lon, zoom, name)
    } else {
      mapIntent
    }
  }

  def GoogleMapsWebFallbackIntent(lat: Float, lon: Float, zoom: Int, name: String): Intent = {
    val urlEncodedName = try {
      URLEncoder.encode(name, "UTF-8")
    } catch {
      case e: UnsupportedEncodingException => name
    }
    val url = GOOGLE_MAPS_WEB_LINK.formatLocal(Locale.getDefault(), zoom, lat, lon, urlEncodedName)
    new Intent(Intent.ACTION_VIEW, Uri.parse(url))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }

  def InviteIntent(subject: String, body: String): Intent =
    new Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_SUBJECT, subject)
      .putExtra(Intent.EXTRA_TEXT, body)

  private def ZIntent(context:     Context,
                      accountId:   AccountId,
                      convId:      Option[ConvId] = None,
                      requestCode: Int            = System.currentTimeMillis().toInt,
                      clazz:       Class[_]       = classOf[MainActivity],
                      startCall:   Boolean        = false) = {
    val intent = new Intent(context, clazz)
      .putExtra(FromNotificationExtra,        true)
      .putExtra(StartCallExtra,         startCall)
      .putExtra(AccountIdExtra,         accountId.str)
    convId.foreach(c => intent.putExtra(ConvIdExtra, c.str))
    PendingIntent.getActivity(context, requestCode, intent, 0)
  }

  implicit class RichIntent(val intent: Intent) extends AnyVal {

    def clearExtras() = Option(intent).foreach { i =>
      i.removeExtra(FromNotificationExtra)
      i.removeExtra(FromSharingExtra)
      i.removeExtra(StartCallExtra)
      i.removeExtra(AccountIdExtra)
      i.removeExtra(ConvIdExtra)
      i.removeExtra(OpenPageExtra)
    }

    def log =
      s"""NotificationIntent:
          |FromNotification: ${isNotificationIntent(intent)}
          |FromSharing:      $SharingIntent
          |Start call:       ${startCall(intent)}
          |Account id:       ${accountIdOpt(intent)}
          |Conv id:          ${convIdOpt(intent)}
          |Page:             $OpenPageIntent
          |AccountCreated:   $AccountCreatedIntent
        """.stripMargin
  }

  object PasswordResetIntent {
    def unapply(i: Intent): Boolean = {
      val data = i.getData
      schemeValid(data) &&
        PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN.equals(data.getHost)
    }
  }

  object SmsIntent {
    def unapply(i: Intent): Boolean = {
      val data = i.getData
      schemeValid(data) &&
        SMS_CODE_TOKEN.equals(data.getHost)
    }
  }

  object OpenAccountIntent {
    def unapply(i: Intent): Option[AccountId] =
      Option(i).map(_.getStringExtra(AccountIdExtra)).filter(_ != null).map(AccountId)
  }

  object NotificationIntent {
    def unapply(i: Intent): Option[(AccountId, Option[ConvId], Boolean)] = {
      val accountId = accountIdOpt(i)
      if (isNotificationIntent(i) && accountId.isDefined) Some(accountId.get, convIdOpt(i), startCall(i))
      else None
    }
  }

  object SharingIntent {
    def unapply(i: Intent): Boolean = Option(i).exists(_.getBooleanExtra(FromSharingExtra, false))

    def apply(implicit context: Context) =
      new Intent(context, classOf[MainActivity])
        .putExtra(FromSharingExtra, true)
  }

  object OpenPageIntent {
    def unapply(i: Intent): Option[Page] = Option(i).map(_.getStringExtra(OpenPageExtra)).filter(_ != null)
  }

  object AccountCreatedIntent {
    def unapply(i: Intent): Option[Uri] = isTeamAccountCreatedIntent(Option(i))
  }

  object QuickReplyIntent {
    def unapply(i: Intent): Option[(AccountId, ConvId)] = {
      (accountIdOpt(i), convIdOpt(i)) match {
        case (Some(acc), Some(conv)) => Some(acc, conv)
        case _ => None
      }
    }
  }

  private def isNotificationIntent(i: Intent) =
    Option(i).exists(_.getBooleanExtra(FromNotificationExtra, false))

  private def accountIdOpt(i: Intent) =
    Option(i).map(_.getStringExtra(AccountIdExtra)).filter(_ != null).map(AccountId)

  private def convIdOpt(i: Intent) =
    Option(i).map(_.getStringExtra(ConvIdExtra)).filter(_ != null).map(ConvId)

  private def startCall(i: Intent) = Option(i).exists(_.getBooleanExtra(StartCallExtra, false))

  private def schemeValid(uri: Uri): Boolean = uri != null && WIRE_SCHEME.equals(uri.getScheme)

  private def isTeamAccountCreatedIntent(intent: Option[Intent]): Option[Uri] = {
    def isHttpsScheme(uri: Uri): Boolean = uri != null && HTTPS_SCHEME.equals(uri.getScheme)

    intent.flatMap { i =>
      val rex = "^/.+/download"
      val data = i.getData
      if (isHttpsScheme(data) &&
        WIRE_HOST.equals(data.getHost) &&
        data.getPath.matches(rex)) {
        Some(data)
      } else {
        None
      }
    }
  }

  @DebugLog
  def getSmsCode(intent: Intent): String = {
    intent match {
      case SmsIntent() =>
        val data = intent.getData
        if (data.getPath != null && data.getPath.length > 1) {
          data.getPath.substring(1)
        } else {
          null
        }
    }
  }

  @DebugLog
  def getInviteToken(intent: Option[Intent]): Option[String] = {
    intent.flatMap { i =>
      val data = i.getData
      if (schemeValid(data) &&
        INVITE_HOST_TOKEN.equals(data.getHost)) {
        val token =  data.getQueryParameter("code")
        if (!TextUtils.isEmpty(token)) {
          Some(token)
        } else {
          None
        }
      } else {
        None
      }
    }
  }

}
