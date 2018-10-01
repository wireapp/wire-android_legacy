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
package com.waz.zclient.participants.fragments

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{LinearLayout, RelativeLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.model.{ConvExpiry, ConvId}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController._
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, SpinnerController}

import scala.concurrent.duration._

class EphemeralOptionsFragment extends FragmentHelper {

  import com.waz.threading.Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val spinner = inject[SpinnerController]

  private lazy val optionsListLayout = returning(view[LinearLayout](R.id.list_view)) { _ =>
    convController.currentConv.map(_.ephemeralExpiration).map {
      case Some(ConvExpiry(e)) => Some(e)
      case _                   => None
    }.map { value =>
      (if (PredefinedExpirations.contains(value)) PredefinedExpirations else PredefinedExpirations :+ value, value)
    }.onUi((setNewValue _).tupled)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.ephemeral_options_fragment, container, false)

  private def setNewValue(optionsList: Seq[Option[FiniteDuration]], e: Option[FiniteDuration]): Unit = {
    optionsListLayout.foreach { v =>
      v.removeAllViews()
      optionsList.zipWithIndex.map { case (option, index) =>
        getLayoutInflater.inflate(R.layout.conversation_option_item, v, true)
        (option, v.getChildAt(index).asInstanceOf[RelativeLayout], index)
      }.foreach { case (option, r, index) =>

        val textView  = findById[TextView](r, R.id.text)
        val check     = findById[GlyphTextView](r, R.id.glyph)
        val separator = findById[View](r, R.id.separator)

        textView.setText(ConversationController.getEphemeralDisplayString(option))

        check.setVisible(e.equals(option))
        separator.setVisible(index != optionsList.size - 1)

        if (PredefinedExpirations.contains(option)) {
          r.onClick {
            if (e != option) {
              spinner.showSpinner(true)
              (for {
                z <- zms.head
                Some(convId) <- inject[Signal[Option[ConvId]]].head
                _ <- z.convsUi.setEphemeralGlobal(convId, option)
              } yield {
              }).onComplete { res =>
                if (res.isFailure) showToast(getString(R.string.generic_error_message))
                spinner.showSpinner(false)
                this.getFragmentManager.popBackStack()
              }
            }
          }
        } else {
          textView.setTextColor(R.color.text__primary_disabled_light)
          check.setTextColor(R.color.text__primary_disabled_light)
        }
      }
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    optionsListLayout
  }
}

object EphemeralOptionsFragment {
  val Tag = implicitLogTag
}

