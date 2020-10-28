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
package com.waz.zclient.notifications.controllers

import android.app.NotificationManager
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.waz.bitmap.BitmapUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetId
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.threading.Threading
import com.wire.signals.Signal
import com.waz.utils.wrappers.URI
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.IntentUtils._
import com.waz.zclient.{Injectable, Injector, R, WireContext}

import scala.util.Try

class ImageNotificationsController(implicit cxt: WireContext, inj: Injector)
  extends Injectable with DerivedLogTag {

  import ImageNotificationsController._

  val zms = inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
  val notManager = inject[NotificationManager]

  val savedImageId = Signal[Option[AssetId]](None)
  val savedImageUri = Signal[URI]()

  def showImageSavedNotification(imageId: AssetId, uri: URI): Unit = Option(imageId).zip(Option(uri)).foreach {
    case (id, ur) =>
      savedImageId ! Some(id)
      savedImageUri ! uri
  }

  def dismissImageSavedNotification() = {
    notManager.cancel(ZETA_SAVE_IMAGE_NOTIFICATION_ID)
    savedImageId ! None
  }

  //TODO use image controller when available from messages rewrite branch
  zms.zip(savedImageId).flatMap {
    case (zms, Some(imageId)) =>
      zms.assetsStorage.signal(imageId).flatMap {
        case _ => Signal.empty[BitmapResult] //TODO: Use new asset engine
      }
    case _ => Signal.empty[BitmapResult]
  }.zip(savedImageUri).on(Threading.IO) {
    //case (BitmapResult.BitmapLoaded(bitmap, _), uri) => showBitmap(bitmap, uri)
    //case (_, uri) => showBitmap(null, uri)
    _ =>
  }

  private def showBitmap(bitmap: Bitmap, uri: URI): Unit = {
    val summaryText = getString(R.string.notification__image_saving__content__subtitle)
    val notificationTitle = getString(R.string.notification__image_saving__content__title)

    val notificationStyle = new NotificationCompat.BigPictureStyle()
      .bigPicture(bitmap)
      .setSummaryText(summaryText)

    val builder = new NotificationCompat.Builder(cxt, NotificationManagerWrapper.OngoingNotificationsChannelId)
      .setContentTitle(notificationTitle)
      .setContentText(summaryText)
      .setSmallIcon(R.drawable.ic_menu_save_image_gallery)
      .setLargeIcon(BitmapUtils.cropRect(bitmap, toPx(largeIconSizeDp)))
      .setStyle(notificationStyle)
      .setContentIntent(getGalleryIntent(cxt, uri))
      .addAction(R.drawable.ic_menu_share, getString(R.string.notification__image_saving__action__share), getPendingShareIntent(cxt, uri)).setLocalOnly(true).setAutoCancel(true)

    def showNotification() = notManager.notify(ZETA_SAVE_IMAGE_NOTIFICATION_ID, builder.build())

    Try(showNotification()).recover { case e =>
      error(l"Notify failed: try without bitmap. Error: $e")
      builder.setLargeIcon(null)
      try showNotification()
      catch {
        case e: Throwable => error(l"second display attempt failed, aborting", e)
      }
    }
  }
}

object ImageNotificationsController extends DerivedLogTag {
  val largeIconSizeDp = 64
  val ZETA_SAVE_IMAGE_NOTIFICATION_ID: Int = 1339274
}
