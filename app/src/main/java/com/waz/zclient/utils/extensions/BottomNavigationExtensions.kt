@file:JvmName("BottomNavigationUtil")
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
package com.waz.zclient.utils.extensions

import android.support.annotation.IdRes
import android.support.design.internal.BottomNavigationMenuView
import android.support.design.widget.BottomNavigationView
import android.view.View

fun BottomNavigationView.setItemVisible(@IdRes id: Int, visible: Boolean) {
    val menuView: BottomNavigationMenuView = getChildAt(0) as BottomNavigationMenuView
    menuView.findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
}
