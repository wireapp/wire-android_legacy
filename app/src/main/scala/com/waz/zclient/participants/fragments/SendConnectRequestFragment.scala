package com.waz.zclient.participants.fragments

import android.os.Bundle
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model.{ConversationRole, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.navigation.Page
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.R
import scala.concurrent.duration._
import com.waz.zclient.pages.main.MainPhoneFragment

class SendConnectRequestFragment extends SingleParticipantFragment {
  import SendConnectRequestFragment._
  import Threading.Implicits.Ui

  override protected val layoutId: Int = R.layout.fragment_participants_not_tabbed

  private lazy val usersController = inject[UsersController]

  private lazy val userToConnectId = UserId(getArguments.getString(ArgumentUserId))
  private lazy val userRequester = UserRequester.valueOf(getArguments.getString(ArgumentUserRequester))

  private lazy val fromParticipants = userRequester == UserRequester.PARTICIPANTS
  private lazy val fromDeepLink = userRequester == UserRequester.DEEP_LINK

  override protected lazy val readReceipts = Signal.const(Option.empty[String])

  override protected def initViews(savedInstanceState: Bundle): Unit = {
    detailsView
    footerMenu
  }

  override protected lazy val detailsView = returning( view[RecyclerView](R.id.not_tabbed_recycler_view) ) { vh =>
    vh.foreach(_.setLayoutManager(new LinearLayoutManager(ctx)))

    (for {
      zms           <- inject[Signal[ZMessaging]].head
      Some(user)    <- participantsController.getUser(userToConnectId)
      isGroup       <- participantsController.isGroup.head
      isGuest       =  !user.isWireBot && user.isGuest(zms.teamId)
      isExternal    =  !user.isWireBot && user.isExternal(zms.teamId)
      isDarkTheme   <- inject[ThemeController].darkThemeSet.head
      isWireless    =  user.expiresAt.isDefined
    } yield (user, isGuest, isExternal, isDarkTheme, isGroup, isWireless)).foreach {
      case (user, isGuest, isExternal, isDarkTheme, isGroup, isWireless) =>
        val formattedHandle = StringUtils.formatHandle(user.handle.map(_.string).getOrElse(""))
        val participantRole = participantsController.participants.map(_.get(userToConnectId))
        val selfRole =
          if (fromParticipants)
            participantsController.selfRole.map(Option(_))
          else
            Signal.const(Option.empty[ConversationRole])

        val adapter = new UnconnectedParticipantAdapter(user.id, isGuest, isExternal, isDarkTheme, isGroup, isWireless, user.name, formattedHandle)
        subs += Signal(timerText, participantRole, selfRole).onUi {
          case (tt, pRole, sRole) => adapter.set(tt, pRole, sRole)
        }
        subs += adapter.onParticipantRoleChange.on(Threading.Background)(participantsController.setRole(user.id, _))
        vh.foreach(_.setAdapter(adapter))
    }
  }

  private lazy val returnPage =
    if (fromParticipants || userRequester == UserRequester.DEEP_LINK)
      Page.CONVERSATION_LIST
    else
      Page.PICK_USER

  override protected lazy val footerCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit =
      usersController.connectToUser(userToConnectId).foreach(_.foreach { _ => getActivity.onBackPressed() } )

    override def onRightActionClicked(): Unit =
      if (fromParticipants)
        inject[ConversationController].currentConv.head.foreach { conv =>
          if (conv.isActive)
            inject[IConversationScreenController].showConversationMenu(false, conv.id)
        }
  }

  override protected lazy val leftActionStrings = Signal.empty[(Int, Int)]

  override protected lazy val footerMenu = returning( view[FooterMenu](R.id.not_tabbed_footer) ) { vh =>
    vh.foreach { menu =>
      menu.setLeftActionText(getString(R.string.glyph__plus))
      menu.setLeftActionLabelText(getString(R.string.send_connect_request__connect_button__text))
      menu.setCallback(footerCallback)
    }

    if (fromParticipants) {
      subs += participantsController.selfRole.map(_.canRemoveGroupMember).map { remPerm =>
        getString(if (remPerm)  R.string.glyph__more else R.string.empty_string)
      }.onUi(text => vh.foreach(_.setRightActionText(text)))
    }
  }

  override def onBackPressed(): Boolean = {
    inject[IPickUserController].hideUserProfile()
    if (fromParticipants) participantsController.selectedParticipant ! None

    if (fromDeepLink) {
      CancellableFuture.delay(750.millis).map { _ =>
        navigationController.setVisiblePage(Page.CONVERSATION_LIST, MainPhoneFragment.Tag)
      }
    } else navigationController.setLeftPage(returnPage, SendConnectRequestFragment.Tag)
    true
  }
}

object SendConnectRequestFragment {
  val Tag: String = classOf[SendConnectRequestFragment].getName
  val ArgumentUserId = "ARGUMENT_USER_ID"
  val ArgumentUserRequester = "ARGUMENT_USER_REQUESTER"

  def newInstance(userId: String, userRequester: UserRequester): SendConnectRequestFragment =
    returning(new SendConnectRequestFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgumentUserId, userId)
        args.putString(ArgumentUserRequester, userRequester.toString)
      })
    )
}