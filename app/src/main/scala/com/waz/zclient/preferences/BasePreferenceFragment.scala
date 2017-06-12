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
package com.waz.zclient.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.support.annotation.{CallSuper, Nullable}
import android.support.v7.preference.{Preference, XpPreferenceFragment}
import android.view.View
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.core.controllers.tracking.events.Event
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient._
import net.xpece.android.support.preference.PreferenceDividerDecoration

import scala.util.Try

abstract class BasePreferenceFragment extends XpPreferenceFragment
  with FragmentHelper
  with ServiceContainer
  with SharedPreferences.OnSharedPreferenceChangeListener {

  @CallSuper def onCreatePreferences2(savedInstanceState: Bundle, rootKey: String) =
    getPreferenceManager.setSharedPreferencesName(UserPreferencesController.USER_PREFS_TAG)

  override def onStart() = {
    super.onStart()
    getActivity.setTitle(getPreferenceScreen.getTitle)
    getPreferenceManager.getSharedPreferences.registerOnSharedPreferenceChangeListener(this)
  }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle) = {
    super.onViewCreated(view, savedInstanceState)
    getListView.setFocusable(false)
    getListView.setItemAnimator(null)
    getListView.addItemDecoration(new PreferenceDividerDecoration(getContext).drawBetweenCategories(false))
    setDivider(null)
  }

  override def onStop() = {
    getPreferenceManager.getSharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    super.onStop()
  }

  override def getStoreFactory =
    Try(ZApplication.from(getActivity).getStoreFactory).toOption.orNull

  override def getControllerFactory =
    Try(ZApplication.from(getActivity).getControllerFactory).toOption.orNull

  override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) =
    Option(key).filter(_.nonEmpty).foreach { k =>
      val event = handlePreferenceChanged(sharedPreferences, k)
      if (event != null) inject[GlobalTrackingController].tagEvent(event)
    }

  def handlePreferenceChanged(sharedPreferences: SharedPreferences, key: String): Event = null

  def injectJava[A](dependencyClass: Class[A]): A =
    Try(getActivity.asInstanceOf[BaseActivity].injectJava[A](dependencyClass)).toOption.getOrElse(null.asInstanceOf[A])

  def findPref[P <: Preference](keyId: Int): P = findPreference(getString(keyId)).asInstanceOf[P]
}
