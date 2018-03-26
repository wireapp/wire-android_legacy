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
import android.util.{AttributeSet, TypedValue}
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import com.waz.threading.CancellableFuture
import com.waz.utils.returning
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future
import scala.concurrent.duration._

class LoadingIndicatorView(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ViewHelper {

  import com.waz.threading.Threading.Implicits.Ui

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import LoadingIndicatorView._

  private val animations: PartialFunction[AnimationType, () => Unit] = {
    case InfiniteLoadingBar => () => if (setToVisible) {
      progressView.setVisible(false)
      infiniteLoadingBarView.setVisible(true)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(Color.TRANSPARENT)
      ViewUtils.fadeInView(LoadingIndicatorView.this)
    }
    case Spinner => () => if (setToVisible) {
      progressView.setVisible(true)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(Color.TRANSPARENT)
      ViewUtils.fadeInView(LoadingIndicatorView.this)
    }
    case SpinnerWithDimmedBackground(text) => () => if (setToVisible) {
      progressView.setVisible(true)
      textView.setVisible(true)
      textView.setText(text)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(false)
      setBackgroundColor(backgroundColor)
      ViewUtils.fadeInView(LoadingIndicatorView.this)
    }
    case ProgressLoadingBar => () => if (setToVisible) {
      progressView.setVisible(false)
      infiniteLoadingBarView.setVisible(false)
      progressLoadingBarView.setVisible(true)
      setBackgroundColor(Color.TRANSPARENT)
      ViewUtils.fadeInView(LoadingIndicatorView.this)
    }
  }

  private lazy val infiniteLoadingBarView = returning(new InfiniteLoadingBarView(context)) { view =>
    view.setVisible(false)
    addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  }

  private lazy val progressLoadingBarView = returning(new ProgressLoadingBarView(context)) { view =>
    view.setVisible(false)
    addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
  }

  private lazy val progressView = returning(new ProgressView(context)) { view =>
    view.setTextColor(Color.WHITE)
    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.loading_spinner__size))
    view.setVisible(false)

    val params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    params.gravity = Gravity.CENTER
    addView(view, params)
  }

  private lazy val textView = returning(new TypefaceTextView(context)) { view =>
    view.setVisible(false)
    view.setTextColor(Color.WHITE)
    view.setTypeface(getString(R.string.wire__typeface__regular))
    view.setTextSize(getDimen(R.dimen.wire__text_size__tiny))

    val params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    params.gravity = Gravity.CENTER
    params.topMargin = getDimenPx(R.dimen.wire__padding__32)
    addView(view, params)
  }

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

  def hide(): Unit = {
    setToVisible = false
    Future {
      progressView.setVisible(false)
      textView.setVisible(false)
      ViewUtils.fadeOutView(LoadingIndicatorView.this)
    }
  }

  def setColor(color: Int): Unit = {
    infiniteLoadingBarView.setColor(color)
    progressLoadingBarView.setColor(color)
  }

  def setProgress(progress: Float): Unit = progressLoadingBarView.setProgress(progress)

  def applyLightTheme(): Unit = {
    progressView.setTextColor(getColorWithTheme(R.color.text__primary_light, getContext))
    backgroundColor = getColorWithTheme(R.color.text__primary_disabled_dark, getContext)
  }

  def applyDarkTheme(): Unit = {
    progressView.setTextColor(getColorWithTheme(R.color.text__primary_dark, getContext))
    backgroundColor = getColorWithTheme(R.color.black_48, getContext)
  }

}

object LoadingIndicatorView {
  sealed trait AnimationType
  case object InfiniteLoadingBar extends AnimationType
  case object Spinner extends AnimationType
  case class SpinnerWithDimmedBackground(text: String = "") extends AnimationType
  case object ProgressLoadingBar extends AnimationType
}
