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
package com.waz.zclient.views

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.widget.{FrameLayout, ProgressBar}
import com.waz.threading.CancellableFuture
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import scala.concurrent.Future
import scala.concurrent.duration._

class LoadingIndicatorView(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ViewHelper {

  import com.waz.threading.Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import LoadingIndicatorView._

  inflate(R.layout.loading_indicator_layout)

  private val animations: PartialFunction[AnimationType, () => Unit] = {
    case InfiniteLoadingBar => () => if (setToVisible) {
      indeterminateSpinner.setVisible(false)
      infiniteLoadingBarView.setVisible(true)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(Color.TRANSPARENT)
      LoadingIndicatorView.this.fadeIn()
    }
    case Spinner => () => if (setToVisible) {
      indeterminateSpinner.setVisible(true)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(Color.TRANSPARENT)
      LoadingIndicatorView.this.fadeIn()
    }
    case SpinnerWithDimmedBackground(text) => () => if (setToVisible) {
      indeterminateSpinner.setVisible(true)
      indeterminateText.setVisible(true)
      indeterminateText.setText(text)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(backgroundColor)
      LoadingIndicatorView.this.fadeIn()
    }
    case ProgressLoadingBar => () => if (setToVisible) {
      indeterminateSpinner.setVisible(false)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(true)
      setBackgroundColor(Color.TRANSPARENT)
      LoadingIndicatorView.this.fadeIn()
    }
  }

  private lazy val infiniteLoadingBarView = findById[InfiniteLoadingBarView](R.id.indeterminate_bar)

  private lazy val progressLoadingBarView = findById[ProgressLoadingBarView](R.id.loading_bar)

  private lazy val indeterminateSpinner = findById[ProgressBar](R.id.indeterminate_spinner)
  private lazy val indeterminateText = findById[TypefaceTextView](R.id.indeterminate_text)
  private lazy val indeterminateGlyph = findById[GlyphTextView](R.id.spinner_complete_glyph)

  private var setToVisible = false
  private var backgroundColor = 0

  def show(animationType: AnimationType): Unit = show(animationType, 0)

  def show(animationType: AnimationType, darkTheme: Boolean): Unit = {
    if (darkTheme) applyDarkTheme() else applyLightTheme()
    show(animationType)
  }

  def show(animationType: AnimationType, delayMs: Long): Unit = {
    setToVisible = true
    CancellableFuture.delayed(delayMs.millis) { animations(animationType)() }
  }

  def show(animationType: AnimationType, darkTheme: Boolean, delayMs: Long): Unit = {
    if (darkTheme) applyDarkTheme() else applyLightTheme()
    show(animationType, delayMs)
  }

  def hide(): Unit = if (setToVisible) {
    setToVisible = false
    Future {
      indeterminateSpinner.setVisible(false)
      indeterminateText.setVisible(false)
      LoadingIndicatorView.this.fadeOut()
    }
  }

  def hideWithMessage(message: String, delayMs: Long): Unit = if (setToVisible) {
    setToVisible = false
    indeterminateGlyph.setVisible(true)
    indeterminateSpinner.setVisible(false)
    indeterminateText.setVisible(true)
    indeterminateText.setText(message)
    CancellableFuture.delayed(delayMs.millis) {
      indeterminateSpinner.setVisible(false)
      indeterminateText.setVisible(false)
      indeterminateGlyph.setVisible(false)
      LoadingIndicatorView.this.fadeOut()
    }
  }

  def setColor(color: Int): Unit = {
    infiniteLoadingBarView.setColor(color)
    progressLoadingBarView.setColor(color)
  }

  def setProgress(progress: Float): Unit = progressLoadingBarView.setProgress(progress)

  def applyLightTheme(): Unit = {
    indeterminateGlyph.setTextColor(getColor(R.color.text__primary_light))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
      indeterminateSpinner.setIndeterminateTintList(getColorStateList(R.color.text__primary_light))
    backgroundColor = getColorWithTheme(R.color.text__primary_disabled_dark, getContext)
  }

  def applyDarkTheme(): Unit = {
    indeterminateGlyph.setTextColor(getColor(R.color.text__primary_dark))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
      indeterminateSpinner.setIndeterminateTintList(getColorStateList(R.color.text__primary_dark))
    backgroundColor = getColorWithTheme(R.color.black_80, getContext)
  }

}

object LoadingIndicatorView {
  sealed trait AnimationType
  case object InfiniteLoadingBar extends AnimationType
  case object Spinner extends AnimationType
  case class SpinnerWithDimmedBackground(text: String = "") extends AnimationType
  case object ProgressLoadingBar extends AnimationType
}
