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
package com.waz.zclient.usersearch.viewholders

import android.support.v7.widget.RecyclerView
import android.view.View
import com.waz.service.ContactResult
import com.waz.zclient.R
import com.waz.zclient.usersearch.adapters.PickUsersAdapter
import com.waz.zclient.usersearch.views.ContactRowView
import com.waz.zclient.utils.ViewUtils

class AddressBookContactViewHolder(val view: View, val darkTheme: Boolean) extends RecyclerView.ViewHolder(view) {
  private val contactRowView: ContactRowView = ViewUtils.getView(view, R.id.crv__contactlist_user)
  if (darkTheme) contactRowView.applyDarkTheme()

  def bind(contact: ContactResult, callback: PickUsersAdapter.Callback): Unit = {
    contactRowView.setCallback(callback)
    contactRowView.setContact(contact)
  }
}
