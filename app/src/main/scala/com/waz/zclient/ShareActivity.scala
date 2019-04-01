/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import java.io.File

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.content.{ContentUris, Context, Intent}
import android.net.Uri
import android.os.{Build, Bundle, Environment}
import android.provider.DocumentsContract._
import android.provider.MediaStore
import android.support.v4.app.ShareCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.permissions.PermissionsService
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.utils.wrappers.AndroidURI
import com.waz.utils.wrappers.AndroidURIUtil.fromFile
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.SharingController.{FileContent, ImageContent}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient.log.LogUI._
import com.waz.zclient.sharing.ShareToMultipleFragment
import com.waz.zclient.views.menus.ConfirmationMenu

import scala.collection.immutable.ListSet
import scala.util.control.NonFatal


class ShareActivity extends BaseActivity with ActivityHelper {
  import ShareActivity._

  lazy val sharing = inject[SharingController]
  lazy val accounts = inject[AccountsService]

  private lazy val confirmationMenu = returning(findById[ConfirmationMenu](R.id.cm__conversation_list__login_prompt)) { cm =>
    cm.setCallback(new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean) = finish()
      override def negativeButtonClicked() = {}
      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean) = {}
    })

    inject[AccentColorController].accentColor.map(_.color).onUi(cm.setButtonColor)
    accounts.accountManagers.map(_.isEmpty).onUi(cm.animateToShow)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_share)

    if (savedInstanceState == null)
      getSupportFragmentManager
        .beginTransaction
        .add(R.id.fl_main_content, ShareToMultipleFragment.newInstance(), ShareToMultipleFragment.TAG)
        .commit

    confirmationMenu
  }

  override def onStart() = {
    super.onStart()
    handleIncomingIntent()
  }

  override def getBaseTheme = R.style.Theme_Share

  override protected def onNewIntent(intent: Intent) = {
    setIntent(intent)
    handleIncomingIntent()
  }

  private def handleIncomingIntent() =
    inject[PermissionsService].requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).map {
      case true =>
        val intent = getIntent
        verbose(l"${RichIntent(intent)}")
        val ir = ShareCompat.IntentReader.from(this)
        if (!ir.isShareIntent) finish()
        else {
          if (ir.getStreamCount == 0 && ir.getType == "text/plain") sharing.publishTextContent(ir.getText.toString)
          else {
            val uris =
              (if (ir.isMultipleShare) (0 until ir.getStreamCount).flatMap(i => Option(ir.getStream(i))) else Option(ir.getStream).toSeq)
                .flatMap(uri => getPath(getApplicationContext, uri))

            if (uris.nonEmpty)
              sharing.sharableContent ! Some(if (ir.getType.startsWith("image/") && uris.size == 1) ImageContent(uris) else FileContent(uris))
            else finish()
          }
        }
      case _ => finish()
    }(Threading.Ui)

  override def onBackPressed() =
    withFragmentOpt(ShareToMultipleFragment.TAG) {
      case Some(f: ShareToMultipleFragment) if f.onBackPressed() => //
      case _ => super.onBackPressed()
    }

}

object ShareActivity extends DerivedLogTag {

  /*
   * This part (the methods getPath and getDataColumn) of the Wire software are based heavily off of code posted in this
   * Stack Overflow answer.
   * (https://stackoverflow.com/a/20559372/1751834)
   *
   * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
   * (http://creativecommons.org/licenses/by-sa/2.5)
   *
   * Contributors on StackOverflow:
   *  - Paul Burke (https://stackoverflow.com/users/377260/paul-burke)
   */

  /**
    * Get a file path from a URI. This will get the the path for Storage Access Framework Documents, as well as the _data
    * field for the MediaStore and other file-based ContentProviders.
    *
    * @param context The context.
    * @param uri     The URI to query.
    */
  def getPath(context: Context, uri: Uri): Option[AndroidURI] = {
    val default = Some(new AndroidURI(uri)) // to be returned in most cases if we fail to resolve the path
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isDocumentUri(context, uri)) {
      (uri.getAuthority match {
        case "com.android.externalstorage.documents" =>
          val split = getDocumentId(uri).split(":")
          // TODO handle non-primary volumes
          if ("primary".equalsIgnoreCase(split(0))) Some(fromFile(new File(Environment.getExternalStorageDirectory + "/" + split(1))))
          else None

        case "com.android.providers.downloads.documents" =>
          val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), getDocumentId(uri).toLong)
          getDocumentPath(context, contentUri)

        case "com.android.providers.media.documents" =>
          val split = getDocumentId(uri).split(":")
          val contentUri = split(0) match {
            case "image" => Some(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            case "video" => Some(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            case "audio" => Some(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            case _ => None
          }
          contentUri.flatMap(uri => getDocumentPath(context, uri, "_id=?", Array[String](split(1))))
        case _ if isDocumentUri(context, uri) =>
          getDocumentPath(context, uri).orElse(default)
        case _ =>
          warn(l"Unrecognised authority for uri: $uri")
          None
      }).orElse(default)
    } else
      (uri.getScheme.toLowerCase match {
        case "content" => getDocumentPath(context, uri).orElse(default)
        case _ =>
          warn(l"Unreachable content: $uri")
          default
      }).flatMap { u =>
        //filter out attempts to trick us into sending application/sensitive data
        val path = u.getPath

        if (!u.getLastPathSegment.contains(".Android_wbu") && (path.contains(context.getPackageName) ||
            path.startsWith("/proc"))) {
          None
        } else {
          Some(u)
        }
      }
  }

  /**
    * Get the value of the data column for this URI. This is useful for MediaStore Uris, and other file-based ContentProviders.
    *
    * @param context       The context.
    * @param uri           The URI to query.
    * @param selection     (Optional) Filter used in the query.
    * @param selectionArgs (Optional) Selection arguments used in the query.
    * @return The value of the _data column, which is typically a file path.
    */
  def getDocumentPath(context: Context, uri: Uri, selection: String = null, selectionArgs: Array[String] = null): Option[AndroidURI] = {
    val column = android.provider.MediaStore.MediaColumns.DATA
    val cursor = Option(context.getContentResolver.query(uri, Array(), selection, selectionArgs, null))

    returning(cursor.flatMap { c =>
      try {
        if (c.moveToFirst) Option(c.getString(c.getColumnIndexOrThrow(column))) else None
      } catch {
        case NonFatal(e) =>
          warn(l"Unable to get data column", e)
          None
      }
    })(_ => cursor.foreach(_.close()))
  }.map(p => fromFile(new File(p)))
}
