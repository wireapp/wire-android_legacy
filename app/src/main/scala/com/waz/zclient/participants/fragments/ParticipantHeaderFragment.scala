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

import android.content.Context
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view._
import android.widget.TextView
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.ManagerFragment.Page
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.creation.{CreateConversationController, AddParticipantsFragment}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, ManagerFragment, R}

class ParticipantHeaderFragment extends FragmentHelper {
  implicit def cxt: Context = getActivity

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val newConvController      = inject[CreateConversationController]
  private lazy val accentColor            = inject[AccentColorController].accentColor.map(_.getColor)

  lazy val page = Option(getParentFragment) match {
    case Some(f: ManagerFragment) => f.currentContent
    case _                        => Signal.const(Option.empty[Page])
  }

  lazy val pageTag = page.map(_.map(_.tag))

  lazy val addingUsers = pageTag.map(_.contains(AddParticipantsFragment.Tag))

  private lazy val toolbar = returning(view[Toolbar](R.id.t__participants__toolbar)) { vh =>
    (for {
      p    <- page
      dark <- themeController.darkThemeSet
    } yield
      p match {
        case Some(Page(AddParticipantsFragment.Tag, _)) => Some(if (dark) R.drawable.ic_action_close_light else R.drawable.ic_action_close_dark)
        case Some(Page(_, false)) => Some(if (dark) R.drawable.action_back_light else R.drawable.action_back_dark)
        case _ => None
      })
      .onUi { icon =>
        vh.foreach { v => icon match {
          case Some(res) => v.setNavigationIcon(res)
          case None      => v.setNavigationIcon(null) //can't squash these calls - null needs to be of type Drawable, not int
        }}
      }
  }

  private lazy val confButton = returning(view[TextView](R.id.confirmation_button)) { vh =>

    val confButtonEnabled = newConvController.users.map(_.nonEmpty)
    confButtonEnabled.onUi(e => vh.foreach(_.setEnabled(e)))

    confButtonEnabled.flatMap {
      case false => Signal.const(getColor(R.color.teams_inactive_button))
      case _ => accentColor
    }.onUi(c => vh.foreach(_.setTextColor(c)))

    addingUsers.onUi(vis => vh.foreach(_.setVisible(vis)))
    vh.onClick { _ =>
      newConvController.addUsersToConversation()
      getActivity.onBackPressed()
    }
  }

  private lazy val closeButton = returning(view[TextView](R.id.close_button)) { vh =>
    addingUsers.map(!_).onUi(vis => vh.foreach(_.setVisible(vis)))
    vh.onClick(_ => participantsController.onHideParticipants ! true)
  }

  private lazy val headerReadOnlyTextView = returning(view[TextView](R.id.participants__header)) { vh =>

    page.map(_.map(_.tag)).flatMap {
      case Some(GroupParticipantsFragment.Tag |
                GuestOptionsFragment.Tag) => Signal.const(getString(R.string.participants_details_header_title))

      case Some(EphemeralOptionsFragment.Tag) => Signal.const(getString(R.string.ephemeral_message__options_header))

      case Some(AddParticipantsFragment.Tag) => newConvController.users.map(_.size).map {
        case 0 => getString(R.string.add_people_empty_header)
        case x => getString(R.string.add_people_count_header, x.toString)
      }

      case _ => Signal.const(getString(R.string.empty_string))
    }.onUi(t => vh.foreach(_.setText(t)))
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.fragment_participants_header, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    toolbar
    headerReadOnlyTextView
    closeButton
    confButton
  }

  override def onResume(): Unit = {
    super.onResume()
    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = getActivity.onBackPressed()
    }))
  }

  override def onPause(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onPause()
  }
}

object ParticipantHeaderFragment {
  val TAG: String = classOf[ParticipantHeaderFragment].getName

  def newInstance: ParticipantHeaderFragment = new ParticipantHeaderFragment
}
