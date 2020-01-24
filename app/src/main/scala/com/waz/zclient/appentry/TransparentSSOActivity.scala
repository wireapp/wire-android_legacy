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
package com.waz.zclient.appentry

import android.os.Bundle
import com.waz.zclient._

class TransparentSSOActivity extends BaseActivity {

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    applyTransparentTheme
    setContentView(R.layout.transparent_layout)
    showSsoFragment
  }

  private def applyTransparentTheme() = {
    if (themeController.isDarkTheme) setTheme(R.style.Theme_Dark_Transparent)
    else setTheme(R.style.Theme_Light_Transparent)
  }

  private def showSsoFragment() = getSupportFragmentManager.beginTransaction()
    .replace(R.id.layout_container, TransparentSSOFragment(), TransparentSSOFragment.Tag)
    .commit()
}


