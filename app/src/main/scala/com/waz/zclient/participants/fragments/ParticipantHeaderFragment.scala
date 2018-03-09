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
import android.support.v4.app.Fragment
import android.support.v7.widget.Toolbar
import android.view._
import android.view.animation.{AlphaAnimation, Animation}
import android.widget.TextView
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, ManagerFragment, R}

class ParticipantHeaderFragment extends BaseFragment[ParticipantHeaderFragment.Container] with FragmentHelper {
  implicit def cxt: Context = getActivity

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController        = inject[ThemeController]

  private var subs = Set.empty[Subscription]

  private lazy val toolbar = returning(view[Toolbar](R.id.t__participants__toolbar)) { vh =>
    (for {
      navVisible <- Option(getParentFragment) match {
                      case Some(f: ParticipantFragment) => f.navigationIconVisible
                      case _                            => Signal.const(true)
                    }
      darkTheme  <- themeController.darkThemeSet
      icon       =  if (darkTheme) R.drawable.action_back_light else R.drawable.action_back_dark
    } yield if (navVisible) Some(icon) else None).onUi {
      case Some(iconId) => vh.foreach(_.setNavigationIcon(iconId))
      case None         => vh.foreach(_.setNavigationIcon(null))
    }
  }

  private lazy val closeIcon = returning(view[TextView](R.id.participants_header__close_icon)) { vh =>
    vh.onClick(_ => participantsController.onHideParticipants ! {})
  }

  private lazy val headerReadOnlyTextView = returning(view[TextView](R.id.participants__header)) { vh =>
    (Option(getParentFragment) match {
      case Some(f: ManagerFragment) => f.currentContentTag
      case _                        => Signal.const(None)
    }).map { tag: Option[String] =>
      tag.contains(GroupParticipantsFragment.Tag) || tag.contains(GuestOptionsFragment.Tag)
    }.onUi(vis => vh.foreach(_.setVisible(vis)))
  }

  // This is a workaround for the bug where child fragments disappear when
  // the parent is removed (as all children are first removed from the parent)
  // See https://code.google.com/p/android/issues/detail?id=55228
  // Apply the workaround only if this is a child fragment, and the parent is being removed.
  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = Option(getParentFragment) match {
    case Some(parent: Fragment) if enter && parent.isRemoving => returning(new AlphaAnimation(1, 1)){
      _.setDuration(ViewUtils.getNextAnimationDuration(parent))
    }
    case _ => super.onCreateAnimation(transit, enter, nextAnim)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_participants_header, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    toolbar
    headerReadOnlyTextView
    closeIcon
  }

  override def onPause(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))

    super.onPause()
  }

  override def onResume(): Unit = {
    super.onResume()
    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = getActivity.onBackPressed()
    }))
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty

    super.onDestroyView()
  }
}

object ParticipantHeaderFragment {
  val TAG: String = classOf[ParticipantHeaderFragment].getName

  def newInstance: ParticipantHeaderFragment = new ParticipantHeaderFragment

  trait Container {
  }
}
