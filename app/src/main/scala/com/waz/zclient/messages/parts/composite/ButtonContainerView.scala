package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.model.{ButtonId, MessageId}
import com.waz.utils.events.{EventStream, Subscription}
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.{R, ViewHelper}

class ButtonContainerView(context: Context, attrs: AttributeSet, style: Int)
    extends LinearLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)

  val selectedButtonId = EventStream[(MessageId, ButtonId)]()

  private lazy val messagesController = inject[MessagesController]

  private var subscription = Option.empty[Subscription]

  //we already receive ordered
  def bindMessage(messageId: MessageId): Unit = {
    subscription.foreach(_.destroy())
    subscription = Option(messagesController.getButtons(messageId).onUi { items =>
      removeAllViews()
      items.map(ButtonItemViewUIModel(_, selectedButtonId)).foreach { uiModel =>
        val buttonItemView =
          inflate(R.layout.composite_message_alarm_buttonitemview, this, addToParent = false)
            .asInstanceOf[ButtonItemView]
        buttonItemView.bindButton(uiModel)
        addView(buttonItemView)
      }
    })
  }
}
