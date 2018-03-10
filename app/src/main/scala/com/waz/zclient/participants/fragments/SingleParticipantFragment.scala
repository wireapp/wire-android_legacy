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
import android.support.annotation.Nullable
import android.support.v4.view.ViewPager
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.ZLog.ImplicitTag._
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.zclient.common.controllers.{BrowserController, ThemeController, UserAccountsController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.{ParticipantOtrDeviceAdapter, ParticipantsController, TabbedParticipantPagerAdapter}
import com.waz.zclient.ui.views.tab.TabIndicatorLayout
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, StringUtils}
import com.waz.zclient.views.menus.FooterMenuCallback
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future

class SingleParticipantFragment extends FragmentHelper {
  import Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  import SingleParticipantFragment._

  private lazy val participantOtrDeviceAdapter = new ParticipantOtrDeviceAdapter

  private lazy val viewPager = view[ViewPager](R.id.vp_single_participant_viewpager)

  private lazy val convController         = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val browserController      = inject[BrowserController]
  private lazy val pickUserController     = inject[IPickUserController]

  private lazy val userNameView = returning(view[TextView](R.id.user_name)) { vh =>
    participantsController.otherParticipantId.map(_.isDefined).onUi { visible =>
      vh.foreach(_.setVisible(visible))
    }

    participantsController.otherParticipant.map(_.getDisplayName).onUi { name =>
      vh.foreach(_.setText(name))
    }

    participantsController.otherParticipant.map(_.isVerified).onUi { visible =>
      vh.foreach { view =>
        val shield = if (visible) Option(getDrawable(R.drawable.shield_full)) else None

        shield.foreach { sh =>
          val pushDown = getDimenPx(R.dimen.wire__padding__1)
          sh.setBounds(0, pushDown, sh.getIntrinsicWidth, sh.getIntrinsicHeight + pushDown)
          view.setCompoundDrawablePadding(getDimenPx(R.dimen.wire__padding__tiny))
        }
        val old = view.getCompoundDrawables
        view.setCompoundDrawablesRelative(shield.orNull, old(1), old(2), old(3))
      }
    }
  }

  private lazy val userHandle = returning(view[TextView](R.id.user_handle)) { vh =>
    val handle = participantsController.otherParticipant.map(_.handle.map(_.string))

    handle
      .map(_.isDefined)
      .onUi(vis => vh.foreach(_.setVisible(vis)))

    handle
      .map {
        case Some(h) => StringUtils.formatHandle(h)
        case _       => ""
      }.onUi(str => vh.foreach(_.setText(str)))
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_participants_single_tabbed, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    viewPager.foreach { pager =>
      pager.setAdapter(new TabbedParticipantPagerAdapter(participantOtrDeviceAdapter, new FooterMenuCallback {

        override def onLeftActionClicked(): Unit =
          participantsController.isGroup.head.flatMap {
            case false => userAccountsController.hasCreateConvPermission.head.map {
              case true => pickUserController.showPickUser(IPickUserController.Destination.PARTICIPANTS)
              case _ => //
            }
            case _ => Future.successful {
              participantsController.onHideParticipants ! {}
              participantsController.otherParticipantId.head.foreach {
                case Some(userId) => userAccountsController.getOrCreateAndOpenConvFor(userId)
                case _ =>
              }
            }
          }

        override def onRightActionClicked(): Unit =
          (for {
            isGroup <- participantsController.isGroup.head
            convId  <- convController.currentConvId.head
          } yield (isGroup, convId)).flatMap {
            case (false, convId) => Future.successful(screenController.showConversationMenu(false, convId))
            case (true,  convId) => userAccountsController.hasRemoveConversationMemberPermission(convId).head.flatMap {
              case true =>
                participantsController.otherParticipantId.head.map {
                  case Some(userId) => participantsController.showRemoveConfirmation(userId)
                  case _ => //
                }
              case _ => Future.successful({})
            }
          }
        }))

      val tabIndicatorLayout = findById[TabIndicatorLayout](v, R.id.til_single_participant_tabs)
      tabIndicatorLayout.setPrimaryColor(getColorWithTheme(if (themeController.isDarkTheme) R.color.text__secondary_dark else R.color.text__secondary_light, getContext))
      tabIndicatorLayout.setViewPager(pager)
    }

    if (Option(savedInstanceState).isEmpty) viewPager.foreach { pager =>
      pager.setCurrentItem(getStringArg(ArgPageToOpen) match {
        case Some(TagDevices) => 1
        case _                => 0
      })

    }

    participantOtrDeviceAdapter.onClientClick.onUi { client =>
      participantsController.otherParticipantId.head.foreach {
        case Some(userId) =>
          Option(getParentFragment).foreach {
            case f: ParticipantFragment => f.showOtrClient(userId, client.id)
            case _ =>
          }
        case _ =>
      }
    }

    participantOtrDeviceAdapter.onHeaderClick {
      _ => browserController.openUrl(getString(R.string.url_otr_learn_why))
    }

    userNameView
    userHandle
  }

  override def onBackPressed(): Boolean = {
    participantsController.unselectParticipant()
    super.onBackPressed()
  }
}

object SingleParticipantFragment {
  val Tag: String = classOf[SingleParticipantFragment].getName
  val TagDevices: String = s"${classOf[SingleParticipantFragment].getName}/devices"

  private val ArgPageToOpen: String = "ARG_PAGE_TO_OPEN"

  def newInstance(pageToOpen: Option[String] = None): SingleParticipantFragment =
    returning(new SingleParticipantFragment) { f =>
      pageToOpen.foreach { p =>
        f.setArguments(returning(new Bundle){
          _.putString(ArgPageToOpen, p)
        })
      }
    }
}
