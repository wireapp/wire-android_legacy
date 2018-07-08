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
package com.waz.zclient.newreg.fragments.country;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;

public class CountryCodeAdapter extends BaseAdapter {
    private List<Country> countries;

    public void setCountryList(List<Country> countries) {
        this.countries = countries;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        if (countries == null) {
            return 0;
        }
        return countries.size();
    }

    @Override
    public Country getItem(int i) {
        return countries.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        CountryViewHolder viewHolder;

        if (convertView == null) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.row__country, viewGroup, false);
            viewHolder = new CountryViewHolder();
            viewHolder.nameTextView = ViewUtils.getView(view, R.id.ttv_new_reg__signup__phone__country__row);
            viewHolder.countryCodeTextView =  ViewUtils.getView(view, R.id.ttv_new_reg__signup__phone__country__row_code);
            convertView = view;
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (CountryViewHolder) convertView.getTag();
        }


        viewHolder.nameTextView.setText(getItem(i).getName().trim());
        if (!TextUtils.isEmpty(getItem(i).getCountryCode())) {
            viewHolder.countryCodeTextView.setText(String.format("+%s", getItem(i).getCountryCode().trim()));
        }

        return convertView;
    }

    public static class CountryViewHolder {
        TextView nameTextView;
        TextView countryCodeTextView;
    }
}
