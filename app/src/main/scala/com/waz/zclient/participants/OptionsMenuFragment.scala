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
package com.waz.zclient.participants

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.verbose
import com.waz.model.ConvId
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.returning
import com.waz.zclient.common.views.UserDetailsView
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.theme.OptionsTheme
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.utils.ViewUtils.getView
import com.waz.zclient.{FragmentHelper, R, ViewHelper}

import scala.concurrent.duration._

class OptionsMenuFragment extends Fragment with FragmentHelper {

  import OptionsMenuFragment._

  var optionsMenu = Option.empty[OptionsMenu]
  var ctrl = Option.empty[OptionsMenuController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    returning(inflater.inflate(R.layout.fragment_conversation_option_menu, container, false)) { container =>
      optionsMenu = Some(returning(findById[OptionsMenu](container, R.id.om__participant)) { v =>
        this.ctrl = Some(v.ctrl)
        Option(savedInstanceState).flatMap(st => Option(st.getString(ArgConvId))).map(ConvId).foreach(v.ctrl.convId ! Some(_))
        v.ctrl.inConversationList ! getArguments.getBoolean(ArgInConvList)
      })
    }

  override def onSaveInstanceState(outState: Bundle) = {
    super.onSaveInstanceState(outState)
    ctrl.flatMap(_.inConversationList.currentValue).foreach(outState.putBoolean(ArgInConvList, _))
    ctrl.flatMap(_.conv.currentValue.flatten).map(_.id.str).foreach(outState.putString(ArgConvId, _))
  }

  def open(convId: ConvId): Unit = {
    ctrl.foreach(_.convId ! Some(convId))
    optionsMenu.foreach(_.open())
  }

  def close(): Boolean = {
    verbose(s"close")
    optionsMenu.exists(_.close())
  }
}

object OptionsMenuFragment {
  val Tag = classOf[OptionsMenuFragment].getName

  private val ArgInConvList = "ARGUMENT_IN_LIST"
  private val ArgConvId     = "ARGUMENT_CONV_ID"

  def newInstance(inConvList: Boolean): OptionsMenuFragment = {
    returning(new OptionsMenuFragment) { f =>
      f.setArguments(returning(new Bundle) { b =>
        b.putBoolean(ArgInConvList, inConvList)
      })
    }
  }

}

class OptionsMenu(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  import OptionsMenu._

  val ctrl = new OptionsMenuController

  LayoutInflater.from(getContext).inflate(R.layout.options_menu, this)

  private val titleTextView   = findById[TypefaceTextView](R.id.ttv__settings_box__title)
  private val userDetailsView = findById[UserDetailsView] (R.id.udv__settings_box__user_details)
  private val cancelView      = findById[TypefaceTextView](R.id.ttv__settings_box__cancel_button)
  private val menuLayout      = findById[LinearLayout]    (R.id.ll__settings_box__container)
  private val backgroundView  = findById[View]            (R.id.v__options_menu__overlay)

  setVisibility(View.GONE)

  ctrl.conv.map(_.map(_.displayName).getOrElse("")).onUi(titleTextView.setText)
  ctrl.otherUser.map(_.isDefined).onUi(userDetailsView.setVisible)
  ctrl.otherUser.map(_.map(_.id)) (_.foreach(userDetailsView.setUserId))
  ctrl.optionsTheme.map(_.getTextColorPrimary).onUi(titleTextView.setTextColor)
  ctrl.optionsTheme.map(_.getOverlayColor).onUi(backgroundView.setBackgroundColor)

  (for {
    items <- ctrl.optionItems
    theme <- ctrl.optionsTheme
  } yield (items, theme)).onUi { case (items, theme) =>

    menuLayout.removeAllViews()

    val rows = Math.ceil(items.size / 3f).toInt
    var itemNumber = 0

    (0 until rows).foreach { i =>

      val row = returning(new LinearLayout(getContext)) { r =>
        r.setOrientation(LinearLayout.HORIZONTAL)
        r.setGravity(Gravity.CENTER)
        val params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        //int sideMargin = getResources().getDimensionPixelSize(R.dimen.options_menu_row_side_margin);
        val topMargin = getDimenPx(R.dimen.options_menu_row_top_margin)
        params.setMargins(0, topMargin, 0, 0)
        r.setLayoutParams(params)
      }

      while (
        row.getChildCount < MaxItemsPerRow &&
          itemNumber < items.size &&
          !((items.size == TotalItemsFive || items.size == TotalItemsFour) && (i == 0 && row.getChildCount == MaxItemsPerRow - 1))) {

        val item = items(itemNumber)

        row.addView(returning(LayoutInflater.from(getContext).inflate(R.layout.options_menu__item, this, false)) { container =>
          returning(getView[FrameLayout](container, R.id.fl_options_menu_button)) { v =>
            val drawable = (item.isToggled, theme.getType) match {
              case (true,  OptionsTheme.Type.DARK) => R.drawable.selector__icon_button__background__dark_toggled
              case (true,  _)                      => R.drawable.selector__icon_button__background__light_toggled
              case (false, OptionsTheme.Type.DARK) => R.drawable.selector__icon_button__background__dark
              case (false, _)                      => R.drawable.selector__icon_button__background__light
            }
            v.setBackground(getDrawable(drawable))
          }

          returning(getView[GlyphTextView](container, R.id.gtv__options_menu_button__glyph)) { v =>
            v.setText(item.resGlyphId)
            val inverted = (item.isToggled, theme.getType) match {
              case (true,  OptionsTheme.Type.DARK) => ctrl.themes.optionsLightTheme
              case (true,  _)                      => ctrl.themes.optionsDarkTheme
              case (false, _)                      => theme
            }
            v.setTextColor(inverted.getTextColorPrimarySelector)
          }

          returning(getView[TextView](container, R.id.ttv__settings_box__item)) { v =>
            v.setText(item.resTextId)
            v.setTextColor(theme.getTextColorPrimarySelector)
          }

          container.onClick {
            ctrl.onMenuItemClicked ! item
            close()
          }
        })
        itemNumber += 1
      }
      menuLayout.addView(row)
    }

    cancelView.setText(getString(R.string.confirmation_menu__cancel))
    cancelView.setTextColor(theme.getTextColorPrimarySelector)
    cancelView.onClick(close())
    menuLayout.addView(cancelView)
  }

  def open(): Unit = if (ctrl.animationState.currentValue.contains(Closed)) {
    CancellableFuture.delay(getInt(R.integer.wire__animation__delay__very_short).millis).map { _ =>
      this.onClick(close())
      setVisibility(View.VISIBLE)
      ctrl.animationState ! Opening

      //animate menu
      val menuHeight = menuLayout.getMeasuredHeight
      menuLayout.setTranslationY(menuHeight)
      menuLayout.animate()
        .withEndAction(new Runnable() {
          override def run() = onAnimationEnded()
        })
        .translationY(0)
        .setStartDelay(getInt(R.integer.wire__animation__delay__short))
        .setDuration(getInt(R.integer.wire__animation__duration__medium))
        .setInterpolator(expoOut)


      // animate background
      backgroundView.setAlpha(0)
      backgroundView.animate()
        .setStartDelay(getInt(R.integer.wire__animation__delay__short))
        .setDuration(getInt(R.integer.wire__animation__duration__regular))
        .setInterpolator(quartOut)
        .alpha(1)

      // animate title
      Set(titleTextView, userDetailsView).foreach { v =>
        v.setAlpha(0)
        v.animate()
          .setStartDelay(getInt(R.integer.wire__animation__delay__regular))
          .setDuration(getInt(R.integer.wire__animation__duration__medium))
          .setInterpolator(quartOut)
          .alpha(1)
      }

    }(Threading.Ui)
  }

  def close(): Boolean = if (ctrl.animationState.currentValue.contains(Open)) {
    setOnClickListener(null)
    ctrl.animationState ! Closing

    val duration = getInt(R.integer.wire__animation__duration__regular)

    val menuHeight = menuLayout.getMeasuredHeight
    menuLayout.animate()
      .withEndAction(new Runnable() {
        override def run() = onAnimationEnded()
      })
      .translationY(menuHeight)
      .setInterpolator(expoIn)
      .setDuration(duration)

    // animate title
    Set(titleTextView, userDetailsView).foreach { v =>
      v.animate()
        .alpha(0)
        .setInterpolator(quartOut)
        .setDuration(duration)
    }

    // animate background
    backgroundView.animate()
      .alpha(0)
      .setInterpolator(quartOut)
      .setStartDelay(getInt(R.integer.wire__animation__delay__long))
      .setDuration(getInt(R.integer.wire__animation__duration__medium))

    true
  } else false

  override def setBackgroundColor(color: Int) = backgroundView.setBackgroundColor(color)

  private def onAnimationEnded() = {
    if (ctrl.animationState.currentValue.contains(Closing)) {
      setVisibility(View.GONE)
      ctrl.animationState ! Closed
    }
    else if (ctrl.animationState.currentValue.contains(Opening))
      ctrl.animationState ! Open
  }
}

object OptionsMenu {

  lazy val quartOut = new Quart.EaseOut
  lazy val expoOut  = new Expo.EaseOut
  lazy val expoIn   = new Expo.EaseIn

  trait AnimState
  case object Open    extends AnimState
  case object Opening extends AnimState
  case object Closing extends AnimState
  case object Closed  extends AnimState

  private val MaxItemsPerRow = 3
  private val TotalItemsFour = 4
  private val TotalItemsFive = 5
}
