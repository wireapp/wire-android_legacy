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
package com.waz.zclient

import java.text.Normalizer
import java.util.Locale

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.Nullable
import com.waz.model.Handle
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.crypto.ZSecureRandom
import com.wire.signals.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.{ContextUtils, RichView, StringUtils}
import com.waz.threading.Threading._

import scala.util.{Failure, Success}

object SetHandleFragment {
  val Tag: String = classOf[SetHandleFragment].getName

  def apply(): SetHandleFragment = new SetHandleFragment()

  trait Container {
    def onChooseUsernameChosen(): Unit

    def onUsernameSet(): Unit
  }

}

class SetHandleFragment extends BaseFragment[SetHandleFragment.Container] with FragmentHelper {

  import Threading.Implicits.Ui

  private implicit def context: Context = getActivity

  private lazy val browser = inject[BrowserController]

  private val NORMAL_ATTEMPTS = 30
  private val RANDOM_ATTEMPTS = 20
  private val MAX_RANDOM_TRAILING_NUMBER = 1000

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val accentColor = inject[AccentColorController].accentColor

  private lazy val nameTextView = returning(view[TypefaceTextView](R.id.ttv__name)) { vh =>
    self.map(_.name).onUi(name => vh.foreach(_.setText(name)))
  }
  private lazy val usernameTextView = view[TypefaceTextView](R.id.ttv__username)
  private lazy val keepButton = returning(view[ZetaButton](R.id.zb__username_first_assign__keep)){ vh =>
    accentColor.map(_.color).onUi(color => vh.foreach(_.setAccentColor(color)))
  }
  private lazy val chooseYourOwnButton = returning(view[ZetaButton](R.id.zb__username_first_assign__choose)) { vh =>
    accentColor.map(_.color).onUi(color => vh.foreach(_.setAccentColor(color)))
  }
  private lazy val summaryTextView = view[TypefaceTextView](R.id.ttv__username_first_assign__summary)

  private lazy val self = for {
    z <- zms
    userData <- z.usersStorage.signal(z.selfUserId)
  } yield userData

  private var suggestedUsername: String = ""

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {

    usernameTextView.foreach(_.setVisibility(View.INVISIBLE))
    keepButton.foreach(_.setVisibility(View.GONE))

    chooseYourOwnButton.foreach { chooseYourOwnButton =>
      chooseYourOwnButton.setIsFilled(true)
      chooseYourOwnButton.onClick(getContainer.onChooseUsernameChosen())
    }

    keepButton.foreach { keepButton =>
      keepButton.setIsFilled(false)
      keepButton.onClick {
        zms.head.map(_.users.updateHandle(Handle.from(suggestedUsername))).map { _ =>
          getContainer.onUsernameSet()
        }
      }
    }

    summaryTextView.foreach { summaryTextView =>
      TextViewUtils.linkifyText(summaryTextView, Color.WHITE, R.string.wire__typeface__light, false, new Runnable() {
        def run(): Unit = browser.openUserNamesLearnMore()
      })
    }

    self.head.foreach { self =>
      self.handle.foreach { handle =>
        suggestedUsername = handle.toString
        usernameTextView.foreach(_.setText(StringUtils.formatHandle(handle.toString)))
      }
      startUsernameGenerator(self.name)
    }
    nameTextView
  }


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    self.map(_.handle.nonEmpty).onUi {
      case true => getContainer.onUsernameSet()
      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, @Nullable container: ViewGroup, @Nullable savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_username_first_launch, container, false)

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    true
  }

  def onValidUsernameGenerated(generatedUsername: String) = {
    verbose(l"onValidUsernameGenerated ${redactedString(generatedUsername)}")
    suggestedUsername = generatedUsername
    usernameTextView.foreach { usernameTextView =>
      usernameTextView.setText(StringUtils.formatHandle(suggestedUsername))
      usernameTextView.setVisibility(View.VISIBLE)
    }
    keepButton.foreach(_.setVisibility(View.VISIBLE))
  }

  private def startUsernameGenerator(baseName: String): Unit = {
    val baseGeneratedUsername = generateUsernameFromName(baseName)
    val randomUsername = generateUsernameFromName("")
    zms.head.map { z =>
      z.handlesClient.getHandlesValidation(getAttempts(baseGeneratedUsername, NORMAL_ATTEMPTS) ++ getAttempts(randomUsername, RANDOM_ATTEMPTS))
      .onComplete {
        case Success(response) =>
          response match {
            case Left(_) =>
              ContextUtils.showToast(R.string.username__set__toast_error)
            case Right(nicks) =>
              onValidUsernameGenerated(nicks.head.username)
          }
        case Failure(_) =>
          ContextUtils.showToast(R.string.username__set__toast_error)
      }
    }
  }

  private def getAttempts(base: String, attempts: Int): Seq[Handle] =
    (0 until attempts).map(getTrailingNumber).map { tN =>
      Handle.from(StringUtils.truncate(base, HandleLength.HandleMaxLength - tN.length) + tN)
    }

  private def getTrailingNumber(attempt: Int): String = {
    val blah = ZSecureRandom.nextInt(0, MAX_RANDOM_TRAILING_NUMBER * 10 ^ (attempt / 10))
    if (attempt > 0) String.format(Locale.getDefault, "%d", Int.box(blah))
    else ""
  }

  private def generateUsernameFromName(name: String): String = {
    var cleanName: String = Handle.transliterated(name).toLowerCase
    cleanName = Normalizer.normalize(cleanName, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
    cleanName = Normalizer.normalize(cleanName, Normalizer.Form.NFD).replaceAll("\\W+", "")
    if (cleanName.isEmpty) {
      cleanName = generateFromDictionary()
    }
    cleanName
  }

  private def generateFromDictionary(): String = {
    val names = ContextUtils.getStringArray(R.array.random_names)
    val adjectives = ContextUtils.getStringArray(R.array.random_adjectives)
    val namesIndex = ZSecureRandom.nextInt(names.length)
    val adjectivesIndex = ZSecureRandom.nextInt(adjectives.length)
    (adjectives(adjectivesIndex) + names(namesIndex)).toLowerCase
  }
}
