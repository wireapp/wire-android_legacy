package com.waz.zclient.participants.fragments

import android.os.Bundle
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}

class BlockedUserFragment extends UntabbedRequestFragment {

  import com.waz.threading.Threading.Implicits.Ui

  override protected val Tag: String = BlockedUserFragment.Tag

  override protected lazy val footerCallback = new FooterMenuCallback {
    override def onLeftActionClicked(): Unit =
      inject[UsersController].unblockUser(userToConnectId).foreach(_ => getActivity.onBackPressed())

    override def onRightActionClicked(): Unit =
      for {
        conv    <- inject[ConversationController].currentConv.head
        remPerm <- removeMemberPermission.head
      } yield
        if (conv.isActive && remPerm)
          inject[IConversationScreenController].showConversationMenu(false, conv.id)
  }

  override protected lazy val footerMenu = returning( view[FooterMenu](R.id.not_tabbed_footer) ) { vh =>
    vh.foreach { menu =>
      menu.setLeftActionText(getString(R.string.glyph__block))
      menu.setLeftActionLabelText(getString(R.string.connect_request__unblock__button__text))
      menu.setCallback(footerCallback)
    }

    if (fromParticipants) {
      subs += removeMemberPermission.map { remPerm =>
        getString(if (remPerm)  R.string.glyph__more else R.string.empty_string)
      }.onUi(text => vh.foreach(_.setRightActionText(text)))
    }
  }
}

object BlockedUserFragment {
  val Tag: String = classOf[BlockedUserFragment].getName

  def newInstance(userId: UserId, userRequester: UserRequester): BlockedUserFragment =
    returning(new BlockedUserFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(UntabbedRequestFragment.ArgumentUserId, userId.str)
        args.putString(UntabbedRequestFragment.ArgumentUserRequester, userRequester.toString)
      })
    )
}