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
import android.widget.{LinearLayout, TextView}
import com.waz.utils.returning
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.participants.NotificationsOptionsMenuController
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R, SpinnerController}

class NotificationsOptionsFragment extends FragmentHelper {
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val convController = inject[ConversationController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.notifications_options_fragment, container, false)

  private lazy val optionsListLayout = returning(view[LinearLayout](R.id.list_view)) { vh =>
    convController.currentConv.map(_.muted).onUi { e =>
      vh.foreach { layout =>
        layout.removeAllViews()
        ConversationController.MuteSets.zipWithIndex.foreach { case (muteSet, index) =>
          val view = getLayoutInflater.inflate(R.layout.conversation_option_item, layout, false)
          val separatorVisible = index != ConversationController.MuteSets.size - 1

          findById[TextView](view, R.id.text).setText(NotificationsOptionsMenuController.menuItem(muteSet).title)
          findById[GlyphTextView](view, R.id.glyph).setVisible(e.equals(muteSet))
          findById[View](view, R.id.separator).setVisible(separatorVisible)

          view.onClick {
            if (e != muteSet) {
              val spinner = inject[SpinnerController]
              spinner.showSpinner(true)

              (for {
                convId <- convController.currentConvId.head
                _      <- convController.setMuted(convId, muteSet)
              } yield {}).onComplete { res =>
                if (res.isFailure) showToast(getString(R.string.generic_error_message))
                spinner.showSpinner(false)
                this.getFragmentManager.popBackStack()
              }
            }
          }
          layout.addView(view)
        }
      }
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    optionsListLayout
  }
}

object NotificationsOptionsFragment {
  val Tag: String = getClass.getSimpleName
}
