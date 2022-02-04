package com.waz.zclient.participants.fragments

import android.os.Bundle
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils._
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.threading.Threading._

final class ConnectRequestFragment extends UntabbedRequestFragment {
  import com.waz.threading.Threading.Implicits.Ui

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)

  override protected val layoutId: Int = R.layout.fragment_participants_connect

  override protected val Tag: String = ConnectRequestFragment.Tag

  private def initIgnoreButton(): Unit = returning(view[ZetaButton](R.id.zb__connect_request__ignore_button)) { vh =>
    vh.foreach { button =>
      button.setIsFilled(false)
      button.onClick(usersController.ignoreConnectionRequest(userToConnectId).map(_ => getActivity.onBackPressed()))
    }

    accentColor.onUi(c =>
      vh.foreach { button =>
        button.setAccentColor(c)
        button.setTextColor(c)
      }
    )
  }

  private def initAcceptButton(): Unit = returning(view[ZetaButton](R.id.zb__connect_request__accept_button)) { vh =>
    vh.foreach {
      _.onClick(usersController.connectToUser(userToConnectId).map(_ => getActivity.onBackPressed()))
    }

    accentColor.onUi(c => vh.foreach(_.setAccentColor(c)))
  }

  override protected lazy val footerCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit = {}

    override def onRightActionClicked(): Unit =
      for {
        conv    <- inject[ConversationController].currentConv.head
        remPerm <- removeMemberPermission.head
      } yield
        if (conv.isActive && remPerm)
          inject[IConversationScreenController].showConversationMenu(false, conv.id)
  }

  override protected def initFooterMenu(): Unit = returning( view[FooterMenu](R.id.not_tabbed_footer) ) { vh =>
    vh.foreach(_.setCallback(footerCallback))

    if (fromParticipants) {
      subs += removeMemberPermission.map { remPerm =>
        getString(if (remPerm) R.string.glyph__more else R.string.empty_string)
      }.onUi(text => vh.foreach(_.setRightActionText(text)))
    }
  }

  override protected def initViews(savedInstanceState: Bundle): Unit = {
    initDetailsView()
    initClassifiedConversation()
    initFooterMenu()
    initIgnoreButton()
    initAcceptButton()
  }
}

object ConnectRequestFragment {
  val Tag = classOf[ConnectRequestFragment].getName

  def newInstance(userId: UserId, userRequester: UserRequester): ConnectRequestFragment =
    returning(new ConnectRequestFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(UntabbedRequestFragment.ArgumentUserId, userId.str)
        args.putString(UntabbedRequestFragment.ArgumentUserRequester, userRequester.toString)
      })
    )
}
