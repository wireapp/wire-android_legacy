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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.core.app.ShareCompat
import com.waz.content.UserPreferences.FileSharingFeatureEnabled
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Mime
import com.waz.permissions.PermissionsService
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.wrappers.{AndroidURI, AndroidURIUtil}
import com.waz.utils.{IoUtils, returning}
import com.waz.zclient.Intents._
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.SharingController.{FileContent, ImageContent, NewContent}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback
import com.waz.zclient.log.LogUI._
import com.waz.zclient.sharing.ConversationSelectorFragment
import com.waz.zclient.views.menus.ConfirmationMenu

import scala.collection.immutable.ListSet
import scala.util.{Success, Try}

class ShareActivity extends BaseActivity with ActivityHelper {
  import ShareActivity._

  private lazy val confirmationMenu = returning(findById[ConfirmationMenu](R.id.cm__conversation_list__login_prompt)) { cm =>
    cm.setCallback(new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = finish()
      override def negativeButtonClicked(): Unit = {}
      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
    })

    inject[AccentColorController].accentColor.map(_.color).onUi(cm.setButtonColor)
    inject[AccountsService].accountManagers.map(_.isEmpty).onUi(cm.animateToShow)
  }

  private lazy val fileSharingProhibitedPrompt = returning(findById[ConfirmationMenu](R.id.cm__conversation_list__file_sharing_prohibited_prompt)) { cm =>
    cm.setCallback(new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = finish()
      override def negativeButtonClicked(): Unit = {}
      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
    })

    inject[AccentColorController].accentColor.map(_.color).onUi(cm.setButtonColor)

    (for {
      Some(zms)            <- inject[AccountsService].activeZms
      isFileSharingEnabled <- zms.userPrefs.preference(FileSharingFeatureEnabled).signal
    } yield !isFileSharingEnabled).onUi(cm.animateToShow)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_share)

    if (savedInstanceState == null)
      getSupportFragmentManager
        .beginTransaction
        .add(
          R.id.fl_main_content,
          ConversationSelectorFragment.newInstance(getIntent.getType != MessageIntentType),
          ConversationSelectorFragment.TAG
        )
        .commit
    confirmationMenu
    fileSharingProhibitedPrompt
  }

  override def onStart(): Unit = {
    super.onStart()
    handleIncomingIntent()
  }

  override def getBaseTheme: Int = R.style.Theme_Share

  override protected def onNewIntent(intent: Intent): Unit = {
    setIntent(intent)
    handleIncomingIntent()
  }

  private def handleIncomingIntent(): Unit = {
    val incomingIntent = ShareCompat.IntentReader.from(this)
    if (!incomingIntent.isShareIntent) finish()
    else {
      val sharing = inject[SharingController]
      if (incomingIntent.getStreamCount == 0 && incomingIntent.getType == TextIntentType)
        sharing.publishTextContent(incomingIntent.getText.toString)
      else if (incomingIntent.getStreamCount == 0 && incomingIntent.getType == MessageIntentType)
        sharing.sharableContent ! Some(NewContent)
      else if (WireApplication.ensureInitialized())
        inject[PermissionsService].requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).map {
          case true =>
            verbose(l"${RichIntent(getIntent)}")
            val rawUris =
              if (incomingIntent.isMultipleShare)
                (0 until incomingIntent.getStreamCount).flatMap(i => Option(incomingIntent.getStream(i)))
              else
                Option(incomingIntent.getStream).toSeq

            val uris = rawUris.map(uri => (uri, isVideo(uri))).flatMap {
              case (uri, Success(true))  => getVideoContentUri(uri)
              case (uri, Success(false)) => getDocumentContentUri(uri)
              case _                     => None
            }

            if (uris.nonEmpty)
              sharing.sharableContent ! Some(
                if (incomingIntent.getType.startsWith(ImageIntentType) && uris.size == 1) ImageContent(uris)
                else FileContent(uris)
              )
            else
              finish()
          case _ =>
            finish()
        }(Threading.Ui)
    }
  }

  override def onBackPressed(): Unit =
    withFragmentOpt(ConversationSelectorFragment.TAG) {
      case Some(f: ConversationSelectorFragment) if f.onBackPressed() => //
      case _ => super.onBackPressed()
    }

  private lazy val contentResolver = getContentResolver
  private lazy val cacheDir = getExternalCacheDir

  private def isVideo(uri: Uri) =
    Try(contentResolver.getType(uri)).map { mime =>
      Mime.Video.supported.exists(_.str == mime.toLowerCase.trim)
    }

  private def getVideoContentUri(uri: Uri) = {
    Try(contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)).recover {
      case err => error(l"error while taking a persistable uri permission for $uri: ${err.getMessage}")
    }
    Some(AndroidURI(uri))
  }

  private def getDocumentContentUri(uri: Uri) = copyToCache(uri).map(AndroidURIUtil.fromFile)

  private def copyToCache(uri: Uri) =
    if (Option(cacheDir).isEmpty || !cacheDir.exists()) Option.empty[File]
    else {
      val cursor = contentResolver.query(uri, null, null, null, null)
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      cursor.moveToFirst()
      val name = cursor.getString(nameIndex)
      cursor.close()

      Try(contentResolver.openInputStream(uri)).flatMap { stream =>
        Try(returning(new File(cacheDir, name)) { file =>
          if (file.exists()) file.delete()
          file.deleteOnExit()
          IoUtils.copy(stream, file)
        })
      }.toOption
  }
}

object ShareActivity extends DerivedLogTag {
  val MessageIntentType = "message/plain"
  val TextIntentType = "text/plain"
  val ImageIntentType = "image/"
}
