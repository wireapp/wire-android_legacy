/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.ui

import android.content.res.ColorStateList
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.view.animation.Animation
import androidx.annotation.{ColorInt, IdRes, NonNull, Nullable}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getDimenPx, getInt}
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, R}

abstract class DefaultToolbarFragment[T] extends BaseFragment[T] with FragmentHelper {

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)

  private lazy val textViewTitle = view[TypefaceTextView](R.id.fragment_default_toolbar_textview_title)
  private lazy val textViewAction = view[TypefaceTextView](R.id.fragment_default_toolbar_textview_action)
  protected lazy val toolbar = view[Toolbar](getToolbarId)

  override def onActivityCreated(@Nullable savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    accentColor.onUi(updateActionButtonColor)
  }

  override def onViewCreated(@NonNull view: View,
                             @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = onNavigationClick()
    }))

    textViewAction.foreach(_.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = onActionClick()
    }))

  }

  override def onCreateAnimation(transit: Int,
                                 enter: Boolean,
                                 nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(
          if (enter) R.integer.framework_animation_duration_long
          else R.integer.framework_animation_duration_medium
        ),
        if (enter) getInt(R.integer.framework_animation_duration_medium) else 0,
        1f
      )
  }

  protected def openFragmentWithAnimation(@IdRes containerResId: Int,
                                          @NonNull fragment: Fragment,
                                          @Nullable tag: String): Unit = {
    getChildFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right
      )
      .replace(containerResId, fragment)
      .addToBackStack(tag)
      .commit
  }

  override def onDestroyView(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onDestroyView()
  }

  @IdRes
  protected def getToolbarId: Int

  protected def onNavigationClick(): Unit

  protected def onActionClick(): Unit

  protected def setTitle(@Nullable text: String): Unit = {
    textViewTitle.foreach(_.setText(text))
  }

  protected def setActionButtonText(@Nullable text: String): Unit = {
    textViewAction.foreach(_.setText(text))
  }

  protected def setActionButtonEnabled(enabled: Boolean): Unit = {
    textViewAction.foreach(_.setEnabled(enabled))
  }

  private def updateActionButtonColor(@ColorInt color: Int): Unit = {
    val states = Array[Array[Int]](
      Array[Int](android.R.attr.state_enabled),
      Array[Int](-android.R.attr.state_enabled)
    )
    val colors = Array[Int](
      color,
      ContextCompat.getColor(requireContext, R.color.teams_inactive_button)
    )
    val colorStateList = new ColorStateList(states, colors)
    textViewAction.foreach(_.setTextColor(colorStateList))
  }
}
