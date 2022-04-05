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
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.google.android.material.tabs.TabLayout
import com.waz.model.{ConversationRole, DisplayHandleDomainPolicies, UserField, UserId}
import com.wire.signals.{CancellableFuture, Signal, SourceSignal, Subscription}
import com.waz.service.{BackendConfig, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.zclient.common.controllers.{BrowserController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.pages.main.MainPhoneFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.{ParticipantOtrDeviceAdapter, ParticipantsController}
import com.waz.zclient.utils.{ContextUtils, GuestUtils, RichView}
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import com.waz.threading.Threading._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.participants.ParticipantsController.ClassifiedConversation
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.getColor
import com.wire.signals.ext.ClockSignal

class SingleParticipantFragment extends FragmentHelper {
  import Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  import SingleParticipantFragment._
  protected lazy val participantsController = inject[ParticipantsController]
  protected lazy val userAccountsController = inject[UserAccountsController]
  protected lazy val navigationController   = inject[INavigationController]
  protected lazy val usersController        = inject[UsersController]
  protected lazy val isFederationSupported  = inject[BackendConfig].federationSupport.isSupported

  protected var subs = Set.empty[Subscription]

  protected val layoutId: Int = R.layout.fragment_participants_single_tabbed

  private lazy val fromDeepLink: Boolean = getBooleanArg(FromDeepLink)

  protected val visibleTab = Signal[SingleParticipantFragment.Tab](DetailsTab)

  private var tabLayout : TabLayout = _
  private def initTabLayout(): Unit = returning(view[TabLayout](R.id.details_and_devices_tabs)) {
    _.foreach { layout =>
      tabLayout = layout

      if (fromDeepLink)
        layout.setVisibility(View.GONE)
      else {
        layout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener {
          override def onTabSelected(tab: TabLayout.Tab): Unit = {
            visibleTab ! SingleParticipantFragment.Tab.tabs.find(_.pos == tab.getPosition).getOrElse(DetailsTab)
          }

          override def onTabUnselected(tab: TabLayout.Tab): Unit = {}
          override def onTabReselected(tab: TabLayout.Tab): Unit = {}
        })
      }
    }
  }

  protected lazy val timerText = for {
    expires <- participantsController.otherParticipant.map(_.expiresAt)
    clock   <- if (expires.isDefined) ClockSignal(5.minutes) else Signal.const(Instant.EPOCH)
  } yield expires match {
    case Some(expiresAt) => Some(GuestUtils.timeRemainingString(expiresAt.instant, clock))
    case _               => None
  }

  protected lazy val readReceipts: Signal[Option[String]] =
    Signal.zip(participantsController.isGroup, userAccountsController.isTeam, userAccountsController.readReceiptsEnabled).map {
      case (false, true, true)  => Some(getString(R.string.read_receipts_info_title_enabled))
      case (false, true, false) => Some(getString(R.string.read_receipts_info_title_disabled))
      case _                    => None
    }

  private def createOtrDeviceAdapter(): ParticipantOtrDeviceAdapter = returning(new ParticipantOtrDeviceAdapter) { adapter =>
    subs += adapter.onClientClick.onUi { client =>
      participantsController.otherParticipantId.head.foreach {
        case Some(userId) =>
          Option(getParentFragment).foreach {
            case f: ParticipantFragment => f.showOtrClient(userId, client.id)
            case _ =>
          }
        case _ =>
      }
    }

    subs += adapter.onHeaderClick.onUi { _ => inject[BrowserController].openOtrLearnWhy() }
  }

  private def initDevicesView(): Unit = returning(view[RecyclerView](R.id.devices_recycler_view)) { vh =>
    visibleTab.onUi {
      case DevicesTab => vh.foreach(_.setVisible(true))
      case _          => vh.foreach(_.setVisible(false))
    }

    vh.foreach { view =>
      view.setLayoutManager(new LinearLayoutManager(ctx))
      view.setHasFixedSize(true)
      view.setAdapter(createOtrDeviceAdapter())
      view.setPaddingBottomRes(R.dimen.participants__otr_device__padding_bottom)
      view.setClipToPadding(false)
    }
  }

  protected def initDetailsView() : Unit = returning( view[RecyclerView](R.id.details_recycler_view) ) { vh =>
    subs += visibleTab.onUi {
      case DetailsTab => vh.foreach(_.setVisible(true))
      case _          => vh.foreach(_.setVisible(false))
    }

    if (fromDeepLink)
      vh.foreach(_.setMarginTop(ContextUtils.getDimenPx(R.dimen.wire__padding__50)))

    vh.foreach(_.setLayoutManager(new LinearLayoutManager(ctx)))

    (for {
      zms                <- inject[Signal[ZMessaging]].head
      user               <- participantsController.otherParticipant.head
      isGroup            <- participantsController.isGroup.head
      isFederated        =  usersController.isFederated(user)
      isGuest            =  !user.isWireBot && user.isGuest(zms.teamId, zms.selfDomain)
      isExternal         =  !user.isWireBot && user.isExternal(zms.teamId, zms.selfDomain)
      isTeamTheSame      =  !user.isWireBot && user.teamId == zms.teamId && zms.teamId.isDefined
      // if the user is from our team we ask the backend for the rich profile (but we don't wait for it)
      _                  =  if (isTeamTheSame) zms.users.syncRichInfoNowForUser(user.id) else Future.successful(())
      isDarkTheme        <- inject[ThemeController].darkThemeSet.head
      isWireless         =  user.expiresAt.isDefined
    } yield (user.id, user.email, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isTeamTheSame, isFederated)).foreach {
      case (userId, emailAddress, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isTeamTheSame, isFederated) =>
        val adapter = new SingleParticipantAdapter(userId, isGuest, isExternal, isDarkTheme, isGroup, isWireless, isFederated)
        val emailUserField = emailAddress match {
          case Some(email) => Seq(UserField(getString(R.string.user_profile_email_field_name), email.toString()))
          case None        => Seq.empty
        }

        subs += Signal.zip(
          participantsController.otherParticipant.map(_.fields),
          timerText,
          readReceipts,
          participantsController.participants.map(_.getOrElse(userId, ConversationRole.MemberRole)),
          participantsController.selfRole
        ).onUi {
          case (fields, tt, rr, pRole, sRole) if isTeamTheSame =>
            adapter.set(emailUserField ++ fields, tt, rr, pRole, sRole)
          case (_, tt, rr, pRole, sRole) =>
            adapter.set(emailUserField, tt, rr, pRole, sRole)
        }

        subs += adapter.onParticipantRoleChange.on(Threading.Background)(participantsController.setRole(userId, _))
        vh.foreach(_.setAdapter(adapter))
    }
  }

  private def initUserHandle(): Unit = returning(view[TextView](R.id.user_handle)) { vh =>
    subs += (for {
      otherUser <- participantsController.otherParticipant
      handle    =  usersController.displayHandle(otherUser, DisplayHandleDomainPolicies.ShowIfNotSame)
    } yield handle).onUi {
      case handle if handle.nonEmpty =>
        vh.foreach { view =>
          view.setText(handle)
          view.setVisible(true)
        }
      case _ =>
        vh.foreach(_.setVisible(false))
    }
  }

  protected val userToConnectId: SourceSignal[Option[UserId]] = Signal(Option.empty[UserId])

  private val userToClassify: Signal[ClassifiedConversation] = userToConnectId.flatMap {
    case None => participantsController.isOtherParticipantClassified
    case Some(userId) => participantsController.isConvWithUserClassified(userId)
  }

  private lazy val classifiedBanner = returning(view[FrameLayout](R.id.single_participant_classified_banner)) { vh =>
    userToClassify.onUi {
      case ClassifiedConversation.Classified =>
        vh.foreach { view =>
          view.setBackgroundColor(getColor(R.color.background_light))
          view.setVisible(true)
        }
      case ClassifiedConversation.Unclassified =>
        vh.foreach { view =>
          view.setBackgroundColor(getColor(R.color.background_dark))
          view.setVisible(true)
        }
      case ClassifiedConversation.None =>
        vh.foreach(_.setVisible(false))
    }
  }

  private lazy val classifiedBannerText = returning(view[TypefaceTextView](R.id.single_participant_classified_banner_text)) { vh =>
    userToClassify.onUi {
      case ClassifiedConversation.Classified =>
        vh.foreach { view =>
          view.setTransformedText(getString(R.string.conversation_is_classified))
          view.setTextColor(getColor(R.color.background_dark))
          view.setVisible(true)
        }
      case ClassifiedConversation.Unclassified =>
        vh.foreach { view =>
          view.setTransformedText(getString(R.string.conversation_is_unclassified))
          view.setTextColor(getColor(R.color.background_light))
          view.setVisible(true)
        }
      case ClassifiedConversation.None =>
        vh.foreach(_.setVisible(false))
    }
  }

  private def initClassifiedConversation(): Unit =
    if (isFederationSupported) {
      classifiedBanner
      classifiedBannerText
    }

  protected lazy val footerCallback: FooterMenuCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit =
      participantsController.otherParticipant.map(_.expiresAt.isDefined).head.foreach {
        case false => participantsController.isGroup.head.flatMap {
          case false if !fromDeepLink => userAccountsController.hasCreateConvPermission.head.map {
            case true => inject[CreateConversationController].onShowCreateConversation ! true
            case _ =>
          }
          case _ => Future.successful {
            participantsController.onLeaveParticipants ! true
            participantsController.otherParticipantId.head.foreach {
              case Some(userId) =>
                inject[IConversationScreenController].hideUser()
                userAccountsController.getOrCreateAndOpenConvFor(userId)
              case _ =>
            }
          }
        }
        case _ =>
      }

    override def onRightActionClicked(): Unit =
      inject[ConversationController].currentConv.head.foreach { conv =>
        if (conv.isActive)
          inject[IConversationScreenController].showConversationMenu(false, conv.id)
      }
  }

  protected lazy val leftActionStrings: Signal[(Int, Int)] = for {
    isWireless    <- participantsController.otherParticipant.map(_.expiresAt.isDefined)
    flags         <- participantsController.flags
    isGroupOrBot  =  flags.isGroup || flags.hasBot
    canCreateConv <- userAccountsController.hasCreateConvPermission
    isExternal    <- userAccountsController.isExternal
  } yield if (isWireless) {
    (R.string.empty_string, R.string.empty_string)
  } else if (fromDeepLink) {
    (R.string.glyph__conversation, R.string.conversation__action__open_conversation)
  } else if (!isExternal && !isGroupOrBot && canCreateConv) {
    (R.string.glyph__add_people, R.string.conversation__action__create_group)
  } else if (isExternal && !isGroupOrBot) {
    (R.string.empty_string, R.string.empty_string)
  } else {
    (R.string.glyph__conversation, R.string.conversation__action__open_conversation)
  }

  protected def initFooterMenu(): Unit = returning( view[FooterMenu](R.id.fm__footer) ) { vh =>
    // TODO: merge this logic with ConversationOptionsMenuController
    subs += (for {
        conv            <- participantsController.conv
        remPerm         <- participantsController.selfRole.map(_.canRemoveGroupMember)
        selfIsProUser   <- userAccountsController.isTeam
        other           <- participantsController.otherParticipant
        otherIsGuest    =  other.isGuest(conv.team, conv.domain)
        showRightAction =  if (fromDeepLink) !selfIsProUser || otherIsGuest else remPerm
        rightActionStr  =  getString(if (showRightAction) R.string.glyph__more else R.string.empty_string)
      } yield rightActionStr).onUi(text => vh.foreach(_.setRightActionText(text)))

    subs += leftActionStrings.onUi { case (icon, text) =>
      vh.foreach { menu =>
        menu.setLeftActionText(getString(icon))
        menu.setLeftActionLabelText(getString(text))
      }
    }

    vh.foreach(_.setCallback(footerCallback))
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(layoutId, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)
    initViews(savedInstanceState)
  }

  protected def initViews(savedInstanceState: Bundle): Unit = {
    initUserHandle()
    initClassifiedConversation()
    initDetailsView()
    initDevicesView()
    initFooterMenu()
    initTabLayout()

    if (Option(savedInstanceState).isEmpty) {
      val tab = Tab(getStringArg(TabToOpen))
      visibleTab ! tab
      tabLayout.getTabAt(tab.pos).select()
    }
  }

  override def onBackPressed(): Boolean = {
    participantsController.selectedParticipant ! None
    if (fromDeepLink) CancellableFuture.delay(750.millis).map { _ =>
      navigationController.setVisiblePage(Page.CONVERSATION_LIST, MainPhoneFragment.Tag)
    }
    super.onBackPressed()
  }

  override def onDestroy(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    super.onDestroy()
  }
}

object SingleParticipantFragment {
  val Tag: String = classOf[SingleParticipantFragment].getName

  sealed trait Tab {
    val str: String
    val pos: Int
  }

  case object DetailsTab extends Tab {
    override val str: String = s"${classOf[SingleParticipantFragment].getName}/details"
    override val pos: Int = 0
  }

  case object DevicesTab extends Tab {
    override val str: String = s"${classOf[SingleParticipantFragment].getName}/devices"
    override val pos: Int = 1
  }

  object Tab {
    val tabs = List[Tab](DetailsTab, DevicesTab)
    def apply(str: Option[String] = None): Tab = str match {
      case Some(DevicesTab.str) => DevicesTab
      case _ => DetailsTab
    }
  }

  private val TabToOpen: String = "TAB_TO_OPEN"


  private val FromDeepLink: String = "FROM_DEEP_LINK"

  def newInstance(tabToOpen: Option[String] = None, fromDeepLink: Boolean = false): SingleParticipantFragment =
    returning(new SingleParticipantFragment) { f =>
      val args = new Bundle()
      args.putBoolean(FromDeepLink, fromDeepLink)
      tabToOpen.foreach { t => args.putString(TabToOpen, t)}
      f.setArguments(args)
    }
}
