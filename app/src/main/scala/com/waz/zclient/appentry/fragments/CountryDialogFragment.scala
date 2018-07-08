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
package com.waz.zclient.appentry.fragments

import android.app.{AlertDialog, Dialog}
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.content.ContextCompat
import android.view.{LayoutInflater, View}
import android.widget.{AdapterView, ListView}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.newreg.fragments.country.CountryCodeAdapter
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, R}

object CountryDialogFragment {
  val TAG  = classOf[CountryDialogFragment].getName
}

class CountryDialogFragment extends DialogFragment with FragmentHelper with AdapterView.OnItemClickListener {

  private lazy val countryAdapter = new CountryCodeAdapter

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val view = LayoutInflater.from(getActivity).inflate(R.layout.fragment_phone__country_dialog, null)
    val listView = findById[ListView](view, R.id.lv__country_code)
    listView.setAdapter(countryAdapter)
    listView.setOnItemClickListener(this)
    listView.setDivider(new ColorDrawable(ContextCompat.getColor(getActivity, R.color.country_divider_color)))
    listView.setDividerHeight(ViewUtils.toPx(getActivity, 1))

    new AlertDialog.Builder(getActivity).setView(view).setCancelable(true).create
  }

  override def onStart() = {
    super.onStart()
    countryAdapter.setCountryList(activity.getCountryController.getSortedCountries)
  }

  def activity = getActivity.asInstanceOf[AppEntryActivity]

  override def onItemClick(adapterView: AdapterView[_], view: View, i: Int, l: Long): Unit = {
    activity.getCountryController.setCountry(countryAdapter.getItem(i))
    dismiss()
  }
}
