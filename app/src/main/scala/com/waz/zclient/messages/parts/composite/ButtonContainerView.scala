package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.waz.utils.events.{EventStream, Subscription}
import com.waz.zclient.ViewHelper

class ButtonContainerView(context: Context, attrs: AttributeSet, style: Int)
    extends LinearLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  setOrientation(LinearLayout.VERTICAL)

  //TODO: observe this and send ButtonAction request
  val selectedButtonId = EventStream[String]()

  private var subscriptions = Set.empty[Subscription]

  private def buttonItemViews(): Seq[ButtonItemView] = {
    val childCount = getChildCount
    val childViews = Seq[ButtonItemView]()
    for (i <- 0 to childCount) {
      childViews :+ getChildAt(i).asInstanceOf[ButtonItemView]
    }
    childViews
  }

  def setButtons(items: Seq[ButtonItemData]): Unit = {
    removeAllViews()
    clearSubscriptions()

    items.foreach { data =>
      val buttonItemView = new ButtonItemView(getContext)
      buttonItemView.setButton(ButtonItemViewUIModel(data.title, data.error))

      val tag = data.id
      buttonItemView.setTag(tag)
      subscriptions += buttonItemView.selected.onUi(_ => selectedButtonId ! tag)

      addView(buttonItemView)
    }
  }

  private def clearSubscriptions(): Unit = {
    subscriptions.foreach(_.unsubscribe())
    subscriptions = Set.empty[Subscription]
  }

  def confirmButtonSelection(buttonId: String): Unit = {
    val childButtons = buttonItemViews()
    childButtons.foreach(_.clearError())

    val (toConfirm, others) = childButtons.partition(_.getTag() == buttonId)
    toConfirm.headOption.foreach(_.setConfirmed())
    others.foreach(_.setUnselected())
  }
}

//TODO: add state
case class ButtonItemData(id: String,
                          title: String,
                          error: Option[String])
