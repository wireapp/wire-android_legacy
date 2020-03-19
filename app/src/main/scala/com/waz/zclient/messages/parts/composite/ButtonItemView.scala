package com.waz.zclient.messages.parts.composite

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{ProgressBar, TextView}
import androidx.constraintlayout.widget.ConstraintLayout
import com.waz.model.{ButtonData, ButtonId, MessageId}
import com.waz.utils.events.SourceStream
import com.waz.zclient.{R, ViewHelper}

class ButtonItemView(context: Context, attrs: AttributeSet, style: Int)
    extends ConstraintLayout(context, attrs, style)
    with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  inflate(R.layout.message_button)

  private lazy val button              = findById[ButtonWithConfirmation](R.id.message_button_item_button)
  private lazy val progressBar         = findById[ProgressBar](R.id.message_button_item_progressbar)
  private lazy val errorText: TextView = findById[TextView](R.id.message_button_item_error_text)

  private var onClickStream = Option.empty[SourceStream[(MessageId, ButtonId)]]

  def bindButton(uiModel: ButtonItemViewUIModel): Unit = {
    button.setText(uiModel.button.title)
    uiModel.button.state match {
      case ButtonData.ButtonError      => setUnselected(true)
      case ButtonData.ButtonNotClicked => setUnselected(false)
      case ButtonData.ButtonWaiting    => setWaiting()
      case ButtonData.ButtonConfirmed  => setConfirmed()
    }

    onClickStream = Some(uiModel.onClick)

    button.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = onClickStream.foreach { _ ! uiModel.button.id }
    })
  }

  private def setConfirmed(): Unit = {
    hideError()
    progressBar.setVisibility(View.GONE)
    button.setConfirmed(true)
    button.setEnabled(false)
  }

  private def setWaiting(): Unit = {
    hideError()
    progressBar.setVisibility(View.VISIBLE)
    button.setConfirmed(false)
    button.setEnabled(false)
  }

  private def setUnselected(hasError: Boolean): Unit = {
    progressBar.setVisibility(View.GONE)
    if (hasError) showError() else hideError()
    button.setConfirmed(false)
    button.setEnabled(true)
  }

  private def showError(): Unit = errorText.setVisibility(View.VISIBLE)

  private def hideError(): Unit = errorText.setVisibility(View.GONE)
}

case class ButtonItemViewUIModel(button: ButtonData, onClick: SourceStream[(MessageId, ButtonId)])
