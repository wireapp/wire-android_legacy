package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatButton
import com.waz.zclient.{R, ViewHelper}

class ButtonWithConfirmation(context: Context, attrs: AttributeSet, style: Int)
    extends AppCompatButton(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private var confirmed = false

  override def onCreateDrawableState(extraSpace: Int): Array[Int] = {
    val superState = super.onCreateDrawableState(extraSpace + 1)
    if (confirmed) {
      View.mergeDrawableStates(superState, ButtonWithConfirmation.STATE_CONFIRMED)
    }
    superState
  }

  def setConfirmed(confirmed: Boolean) : Unit = {
    this.confirmed = confirmed
    setContentDescription(
      if (confirmed) getContext.getString(R.string.composite_message_confirmed_button_content_description, getText)
      else getText
    )
  }
}

object ButtonWithConfirmation {
  private val STATE_CONFIRMED = Array(R.attr.state_confirmed)
}
