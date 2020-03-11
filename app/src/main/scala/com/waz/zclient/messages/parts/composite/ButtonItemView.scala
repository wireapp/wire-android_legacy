package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{ProgressBar, TextView}
import androidx.constraintlayout.widget.ConstraintLayout
import com.waz.model.ButtonData
import com.waz.utils.events.EventStream
import com.waz.zclient.{R, ViewHelper}

class ButtonItemView(context: Context, attrs: AttributeSet, style: Int)
    extends ConstraintLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.message_button)

  private lazy val button = findById[ButtonWithConfirmation](R.id.message_button_item_button)
  private lazy val progressBar = findById[ProgressBar](R.id.message_button_item_progressbar)
  private lazy val errorText: TextView = findById[TextView](R.id.message_button_item_error_text)

  val selected = EventStream[Unit]()

  button.setOnClickListener(new View.OnClickListener {
    override def onClick(v: View): Unit = {
      selected ! {}
    }
  })

  def bindButton(uiModel: ButtonItemViewUIModel): Unit = {
    button.setText(uiModel.title)
    button.setContentDescription(uiModel.title)
    uiModel.state match {
      case ButtonData.ButtonError(error) => setUnselected(Some(error))
      case ButtonData.ButtonNotClicked   => setUnselected(None)
      case ButtonData.ButtonWaiting      => setWaiting()
      case ButtonData.ButtonConfirmed    => setConfirmed()
    }
  }

  private def setConfirmed(): Unit = {
    clearError()
    progressBar.setVisibility(View.GONE)
    button.setConfirmed(true)
  }

  private def setWaiting(): Unit = {
    clearError()
    progressBar.setVisibility(View.VISIBLE)
    button.setConfirmed(false)
  }

  private def setUnselected(error: Option[String]): Unit = {
    progressBar.setVisibility(View.GONE)
    error.fold(clearError())(setError)
    button.setConfirmed(false)
  }

  private def setError(error: String): Unit = {
    errorText.setText(error)
    errorText.setVisibility(View.VISIBLE)
  }

  private def clearError(): Unit = {
    errorText.setText(null)
    errorText.setVisibility(View.GONE)
  }
}

case class ButtonItemViewUIModel(title: String, state: ButtonData.ButtonState)
