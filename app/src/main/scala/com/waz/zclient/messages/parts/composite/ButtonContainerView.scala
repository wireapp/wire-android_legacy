package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.model.{ButtonData, ButtonId, MessageId}
import com.waz.utils.events.{EventStream, Signal, Subscription}
import com.waz.zclient.ViewHelper

class ButtonContainerView(context: Context, attrs: AttributeSet, style: Int)
    extends LinearLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)

  //TODO: observe this and send ButtonAction request
  val selectedButtonId = EventStream[(MessageId, ButtonId)]()

  val buttonsSignal = Signal[Seq[ButtonData]]()

  private var subscriptions = Set.empty[Subscription]

  //we already receive ordered
  buttonsSignal.onUi { setButtons }

  //TODO: convert to a more efficient way. calculate diff.
  private def setButtons(items: Seq[ButtonData]): Unit = {
    removeAllViews()
    clearSubscriptions()

    items.foreach { data =>
      val buttonItemView = new ButtonItemView(getContext)
      buttonItemView.bindButton(ButtonItemViewUIModel(data.title, data.state))
      subscriptions += buttonItemView.selected.onUi(_ => selectedButtonId ! data.id)
      addView(buttonItemView)
    }
  }

  private def clearSubscriptions(): Unit = {
    subscriptions.foreach(_.unsubscribe())
    subscriptions = Set.empty[Subscription]
  }
}
