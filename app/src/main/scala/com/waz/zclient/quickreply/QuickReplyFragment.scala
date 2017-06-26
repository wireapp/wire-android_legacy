/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
import com.waz.api.MessageContent
import com.waz.api.impl.Conversation
import com.waz.model.ConvId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.core.controllers.tracking.events.notifications.{OpenedAppFromQuickReplyEvent, SwitchedMessageInQuickReplyEvent}
import com.waz.zclient.pages.main.popup.ViewPagerLikeLayoutManager
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.text.{TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}

object QuickReplyFragment {
  private val ConvIdExtra = "EXTRA_CONVERSATION_ID"

  def newInstance(convId: ConvId): Fragment = {
    val fragment = new QuickReplyFragment
    val args = new Bundle
    args.putString(ConvIdExtra, convId.str)
    fragment.setArguments(args)
    fragment
  }
}

class QuickReplyFragment extends Fragment with FragmentHelper {
  import com.waz.threading.Threading.Implicits.Ui

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val tracking = inject[GlobalTrackingController]
  lazy val accentColor = inject[AccentColorController]

  lazy val message = view[TypefaceEditText](R.id.tet__quick_reply__message)
  lazy val layoutManager = new ViewPagerLikeLayoutManager(getContext)

  lazy val convId = ConvId(getArguments.getString(QuickReplyFragment.ConvIdExtra))
  lazy val adapter = new QuickReplyContentAdapter(getContext, convId)

  lazy val conv = for {
    zs <- zms
    conv <- zs.convsStorage.signal(convId)
  } yield conv

  val firstVisibleItemPosition = Signal(0)

  lazy val counterStr = for {
    unreadCount <- conv.map(_.unreadCount)
    selectedPos <- firstVisibleItemPosition
  } yield (unreadCount > 1, getString(R.string.quick_reply__counter, new Integer(math.max(1, selectedPos + 1)), new Integer(unreadCount)))

  var subscriptions = Seq.empty[com.waz.utils.events.Subscription]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.layout_quick_reply, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    val name: TypefaceTextView         = findById(R.id.ttv__quick_reply__name)
    val counter: TypefaceTextView      = findById(R.id.ttv__quick_reply__counter)
    val openWire: View                 = findById(R.id.ll__quick_reply__open_external)
    val contentContainer: RecyclerView = findById(R.id.rv__quick_reply__content_container)

    contentContainer.setLayoutManager(layoutManager)
    contentContainer.setAdapter(adapter)

    counter onClick {
      tracking.tagEvent(new SwitchedMessageInQuickReplyEvent)
      contentContainer.smoothScrollToPosition((layoutManager.findFirstVisibleItemPosition + 1) % adapter.getItemCount)
    }
    contentContainer.addOnScrollListener(new RecyclerView.OnScrollListener() {
      override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
          firstVisibleItemPosition ! layoutManager.findFirstVisibleItemPosition
          tracking.tagEvent(new SwitchedMessageInQuickReplyEvent)
        }
      }
    })
    message.setOnEditorActionListener(new TextView.OnEditorActionListener {
      override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode == KeyEvent.KEYCODE_ENTER && event.getAction == KeyEvent.ACTION_DOWN)) {
          val sendText = textView.getText.toString
          if (TextUtils.isEmpty(sendText)) return false

          textView.setEnabled(false)
          for {
            zs <- zms.head
            c <- conv.head
            isOtto <- Conversation.isOtto(c, zs.usersStorage)
            msg <- zs.convsUi.sendMessage(c.id, new MessageContent.Text(sendText))
          } {
            textView.setEnabled(true)
            if (msg.isDefined) {
              TrackingUtils.onSentTextMessage(tracking, c, isOtto)
              getActivity.finish()
            }
          }

          return true
        }
        false
      }
    })
    openWire onClick {
      Option(getActivity) foreach { activity =>
        tracking.tagEvent(new OpenedAppFromQuickReplyEvent)
        startActivity(IntentUtils.getAppLaunchIntent(getContext, convId.str, message.getText.toString))
        activity.finish()
      }
    }

    subscriptions = Seq(
      conv.map(_.displayName).on(Threading.Ui) { name.setText },
      accentColor.accentColor.map(_.getColor()).on(Threading.Ui) { message.setAccentColor },
      counterStr.on(Threading.Ui) { case (visible, str) =>
        counter.setVisible(visible)
        counter.setText(str)
      }
    )
  }

  override def onResume(): Unit = {
    super.onResume()
    message.postDelayed(new Runnable() {
      override def run(): Unit = {
        Option(message.get) foreach { msg =>
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
