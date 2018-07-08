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
package com.waz.zclient.ui.views.e2ee;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.support.v7.widget.SwitchCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;


public class OtrSwitch extends LinearLayout implements CompoundButton.OnCheckedChangeListener {
    private SwitchCompat switchCompat;
    private TextView textView;
    private boolean ignoreCallback;

    private CompoundButton.OnCheckedChangeListener listener;

    public OtrSwitch(Context context) {
        this(context, null);
    }

    public OtrSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OtrSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(LinearLayout.HORIZONTAL);

        String theme = getResources().getString(R.string.wire_theme_dynamic);

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.OtrSwitch, 0, 0);
            if (a.hasValue(R.styleable.OtrSwitch_otrTheme)) {
                theme = a.getString(R.styleable.OtrSwitch_otrTheme);
            }
            a.recycle();
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        switchCompat = (SwitchCompat) inflater.inflate(R.layout.otr__switch, this, false);
        textView = (TextView) inflater.inflate(R.layout.otr__switch__text_view, this, false);

        addView(switchCompat);
        addView(textView);

        if (theme.equals(getResources().getString(R.string.wire_theme_dark))) {
            switchCompat.setThumbDrawable(getResources().getDrawable(R.drawable.selector_otr_switch__thumb_dark));
            switchCompat.setTrackDrawable(getResources().getDrawable(R.drawable.selector_otr_switch__track_dark));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                textView.setTextColor(getResources().getColorStateList(R.color.selector_otr_switch__text_dark));
            } else {
                textView.setTextColor(getResources().getColorStateList(R.color.selector_otr_switch__text_dark, getContext().getTheme()));
            }
        }

        if (theme.equals(getResources().getString(R.string.wire_theme_light))) {
            switchCompat.setThumbDrawable(getResources().getDrawable(R.drawable.selector_otr_switch__thumb_light));
            switchCompat.setTrackDrawable(getResources().getDrawable(R.drawable.selector_otr_switch__track_light));
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                textView.setTextColor(getResources().getColorStateList(R.color.selector_otr_switch__text_light));
            } else {
                textView.setTextColor(getResources().getColorStateList(R.color.selector_otr_switch__text_light, getContext().getTheme()));
            }
        }

        switchCompat.setOnCheckedChangeListener(this);

        // init
        textView.setText(R.string.pref_devices_device_not_verified);
        setChecked(false);
    }

    public void setChecked(boolean checked) {
        ignoreCallback = true;
        switchCompat.setChecked(checked);
        ignoreCallback = false;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (listener != null && !ignoreCallback) {
            listener.onCheckedChanged(switchCompat, isChecked);
        }

        if (isChecked) {
            textView.setText(R.string.pref_devices_device_verified);
        } else {
            textView.setText(R.string.pref_devices_device_not_verified);
        }

        textView.setSelected(isChecked);
    }

    public void setOnCheckedListener(CompoundButton.OnCheckedChangeListener listener) {
        this.listener = listener;
    }
}
