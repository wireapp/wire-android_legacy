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
package com.waz.zclient.quickreply

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.api.{EphemeralExpiration, MessageContent}
import com.waz.model.{AccountId, ConvId}
import com.waz.service.ZMessaging
import com.waz.service.tracking.ContributionEvent
import com.waz.service.tracking.ContributionEvent.Action
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.pages.main.popup.ViewPagerLikeLayoutManager
import com.waz.zclient.ui.text.{TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}

object QuickReplyFragment {
  private val ConvIdExtra = "EXTRA_CONVERSATION_ID"
  private val AccountIdExtra = "EXTRA_ACCOUNT_ID"

  def newInstance(accountId: AccountId, convId: ConvId): Fragment = {
    returning(new QuickReplyFragment) {
      _.setArguments(returning(new Bundle) { args =>
        args.putString(ConvIdExtra, convId.str)
        args.putString(AccountIdExtra, accountId.str)
      })
    }
  }
}

class QuickReplyFragment extends Fragment with FragmentHelper {
  import QuickReplyFragment._
  import com.waz.threading.Threading.Implicits.Ui

  lazy val convId = ConvId(getArguments.getString(ConvIdExtra))
  lazy val accountId = AccountId(getArguments.getString(AccountIdExtra))

  //TODO make an accounts/zms controller or something
  lazy val zms = ZMessaging.currentAccounts.zms(accountId).collect { case Some(z) => z }

  lazy val sharing  = inject[SharingController]

  lazy val accentColor = for {
    z <- zms
    Some(accent) <- inject[AccentColorController].accentColor(z.accountId)
  } yield accent

  lazy val message = findById[TypefaceEditText](R.id.tet__quick_reply__message)
  lazy val layoutManager = new ViewPagerLikeLayoutManager(getContext)

  lazy val adapter = new QuickReplyContentAdapter(getContext, accountId, convId)

  lazy val conv = for {
    zs <- zms
    conv <- zs.convsStorage.signal(convId)
  } yield conv

  val firstVisibleItemPosition = Signal(0)

  lazy val counterStr = for {
    unreadCount <- conv.map(_.unreadCount.messages)
    selectedPos <- firstVisibleItemPosition
  } yield (unreadCount > 1, getString(R.string.quick_reply__counter, new Integer(math.max(1, selectedPos + 1)), new Integer(unreadCount)))

  var subscriptions = Seq.empty[com.waz.utils.events.Subscription]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    verbose("onCreateView")
    inflater.inflate(R.layout.layout_quick_reply, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    verbose("onViewCreated")

    val name: TypefaceTextView         = findById(R.id.ttv__quick_reply__name)
    val counter: TypefaceTextView      = findById(R.id.ttv__quick_reply__counter)
    val openWire: View                 = findById(R.id.ll__quick_reply__open_external)
    val contentContainer: RecyclerView = findById(R.id.rv__quick_reply__content_container)

    contentContainer.setLayoutManager(layoutManager)
    contentContainer.setAdapter(adapter)

    counter onClick {
      contentContainer.smoothScrollToPosition((layoutManager.findFirstVisibleItemPosition + 1) % adapter.getItemCount)
    }

    contentContainer.addOnScrollListener(new RecyclerView.OnScrollListener() {
      override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          firstVisibleItemPosition ! layoutManager.findFirstVisibleItemPosition
        }
      }
    })

    message.setOnEditorActionListener(new TextView.OnEditorActionListener {
      override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode == KeyEvent.KEYCODE_ENTER && event.getAction == KeyEvent.ACTION_DOWN)) {
          val sendText = textView.getText.toString
          if (TextUtils.isEmpty(sendText)) false
          else {
            textView.setEnabled(false)
            for {
              z           <- zms.head
              c           <- conv.head
              withService <- z.conversations.isWithService(c.id)
              isGroup     <- z.conversations.isGroupConversation(c.id)
              _           <- z.convsUi.setEphemeral(c.id, EphemeralExpiration.NONE)
              msg         <- z.convsUi.sendMessage(c.id, new MessageContent.Text(sendText))
            } {
              textView.setEnabled(true)
              if (msg.isDefined) {
                ZMessaging.globalModule.map(_.trackingService.track(
                  ContributionEvent(Action.Text, isGroup, c.ephemeral, withService, !c.isTeamOnly, c.isMemberFromTeamGuest(z.teamId)),
                  Some(z.accountId)
                ))
                getActivity.finish()
              }
            }
            true
          }
        } else false
      }
    })

    openWire onClick {
      ZMessaging.currentAccounts.switchAccount(accountId).onComplete { _ =>
        Option(getActivity) foreach { activity =>
          sharing.publishTextContent(message.getText.toString)
          sharing.onContentShared(activity, Set(convId))
          activity.finish()
        }
      }
    }

    subscriptions = Seq(
      conv.map(_.displayName).onUi { name.setText },
      accentColor.map(_.getColor()).onUi { message.setAccentColor },
      counterStr.onUi { case (visible, str) =>
        counter.setVisible(visible)
        counter.setText(str)
      }
    )
  }

  override def onResume(): Unit = {
    super.onResume()
    verbose("onResume")
    message.postDelayed(new Runnable() {
      override def run(): Unit = {
        Option(message) foreach { msg =>
          msg.requestFocus
          msg.setCursorVisible(true)
          KeyboardUtils.showKeyboard(getActivity)
        }
      }
    }, 100)
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()

    subscriptions foreach { _.destroy() }
  }
}
