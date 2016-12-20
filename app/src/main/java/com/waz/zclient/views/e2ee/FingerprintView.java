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
package com.waz.zclient.views.e2ee;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import com.waz.api.Fingerprint;
import com.waz.api.OtrClient;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.api.UpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.OtrUtils;


public class FingerprintView extends TypefaceTextView implements Subscriber<Fingerprint>, UpdateListener {
    public static final String TAG = FingerprintView.class.getName();

    private OtrClient otrClient;
    private DisplayType displayType;
    private Subscription fingerprintSubscription;

    public FingerprintView(Context context) {
        this(context, null);
    }

    public FingerprintView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FingerprintView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOtrClient(OtrClient otrClient, DisplayType displayType) {
        this.otrClient = otrClient;
        this.displayType = displayType;
        this.otrClient.addUpdateListener(this);
        updated();

        switch (displayType) {
            case FINGERPRINT:
                if (fingerprintSubscription != null) {
                    fingerprintSubscription.cancel();
                }

                fingerprintSubscription = otrClient.getFingerprint().subscribe(this);
                break;
        }
    }

    @Override
    public void next(Fingerprint value) {
        String formattedString = OtrUtils.getFormattedFingerprint(new String(value.getRawBytes()));
        CharSequence coloredString = TextViewUtils.getBoldHighlightText(getContext(),
                                                                        formattedString,
                                                                        getCurrentTextColor(),
                                                                        0,
                                                                        formattedString.length());
        this.setText(coloredString);
    }

    @Override
    public void updated() {
        switch (displayType) {
            case DEVICE_ID:
                if (TextUtils.isEmpty(otrClient.getId())) {
                    break;
                }
                String text = String.format(getResources().getString(R.string.otr__device_id), otrClient.getDisplayId());
                int deviceIDStart = text.indexOf(':') + 1;
                if (deviceIDStart == -1) {
                    deviceIDStart = 0;
                }
                CharSequence coloredString = TextViewUtils.getBoldHighlightText(getContext(),
                                                                                text,
                                                                                getCurrentTextColor(),
                                                                                deviceIDStart,
                                                                                text.length());
                setText(coloredString);
                break;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (otrClient != null) {
            otrClient.removeUpdateListener(this);
        }

        if (fingerprintSubscription != null) {
            fingerprintSubscription.cancel();
        }

        otrClient = null;
    }

    public enum DisplayType {
        FINGERPRINT,
        DEVICE_ID
    }
}
