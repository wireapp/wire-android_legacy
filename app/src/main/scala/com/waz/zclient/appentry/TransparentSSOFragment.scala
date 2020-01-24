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
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.zclient._

class TransparentSSOFragment extends SSOFragment {

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.transparent_layout, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit =
    extractTokenAndShowSSODialog(showIfNoToken = true)

  override protected def activity: AppEntryActivity = getActivity.asInstanceOf[AppEntryActivity]

  override protected def isParentActivityTransparent: Boolean = true
}

object TransparentSSOFragment {
  def apply() = new TransparentSSOFragment
  val Tag: String =  "TransparentSSOFragment"
}
