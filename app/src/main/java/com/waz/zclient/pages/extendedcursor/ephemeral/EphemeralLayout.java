/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.pages.extendedcursor.ephemeral;

import android.content.Context;
import android.support.v7.view.ContextThemeWrapper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import com.waz.api.EphemeralExpiration;
import timber.log.Timber;

import java.lang.reflect.Field;
import java.util.LinkedList;

public class EphemeralLayout extends LinearLayout {

    private Callback callback;
    private NumberPicker numberPicker;

    public EphemeralLayout(Context context) {
        this(context, null);
    }

    public EphemeralLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EphemeralLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ContextThemeWrapper cw = new ContextThemeWrapper(getContext(), com.waz.zclient.ui.R.style.NumberPickerText);
        numberPicker = new NumberPicker(cw);
        numberPicker.setMinValue(0);
        final EphemeralExpiration[] ephemeralExpirationsValues = getAvailableEphemeralExpirations();
        numberPicker.setMaxValue(ephemeralExpirationsValues.length - 1);
        String[] values = new String[ephemeralExpirationsValues.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = getDisplayName(ephemeralExpirationsValues[i]);
        }
        numberPicker.setDisplayedValues(values);
        numberPicker.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null) {
                    callback.onEphemeralExpirationSelected(ephemeralExpirationsValues[numberPicker.getValue()], true);
                }
            }
        });
        numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                if (callback != null) {
                    callback.onEphemeralExpirationSelected(ephemeralExpirationsValues[numberPicker.getValue()], false);
                }
            }
        });
        try {
            // Remove ugly dividers, should replace with something else though... TODO
            Field f = numberPicker.getClass().getDeclaredField("mSelectionDivider"); //NoSuchFieldException
            f.setAccessible(true);
            f.set(numberPicker, null);
        } catch (Throwable t) {
            Timber.e(t, "Something went wrong");
        }
        addView(numberPicker);
    }

    public void setSelectedExpiration(EphemeralExpiration expiration) {
        numberPicker.setValue(expiration.ordinal());
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onEphemeralExpirationSelected(EphemeralExpiration expiration, boolean close);
    }

    private EphemeralExpiration[] getAvailableEphemeralExpirations() {
        LinkedList<EphemeralExpiration> values = new LinkedList<>();
        values.add(EphemeralExpiration.NONE);
        values.add(EphemeralExpiration.FIVE_SECONDS);
        values.add(EphemeralExpiration.FIFTEEN_SECONDS);
        values.add(EphemeralExpiration.ONE_MINUTE);
        return values.toArray(new EphemeralExpiration[values.size()]);
    }

    private String getDisplayName(EphemeralExpiration expiration) {
        switch (expiration) {
            case NONE:
                return getContext().getString(com.waz.zclient.core.R.string.ephemeral_message__timeout__off);
            case FIVE_SECONDS:
                return getContext().getString(com.waz.zclient.core.R.string.ephemeral_message__timeout__5_sec);
            case FIFTEEN_SECONDS:
                return getContext().getString(com.waz.zclient.core.R.string.ephemeral_message__timeout__15_sec);
            case ONE_MINUTE:
                return getContext().getString(com.waz.zclient.core.R.string.ephemeral_message__timeout__1_min);
            case FIFTEEN_MINUTES:
                return getContext().getString(com.waz.zclient.core.R.string.ephemeral_message__timeout__15_min);
            default:
                return expiration.name();
        }
    }
}
