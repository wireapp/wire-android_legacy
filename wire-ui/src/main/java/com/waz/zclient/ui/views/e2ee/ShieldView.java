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
package com.waz.zclient.ui.views.e2ee;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.waz.zclient.ui.R;

public class ShieldView extends ImageView {

    public ShieldView(Context context) {
        this(context, null);
    }

    public ShieldView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShieldView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        boolean isVerified = false;

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ShieldView, 0, 0);
            isVerified = a.getBoolean(R.styleable.ShieldView_shieldVerified, false);
            a.recycle();
        }

        setVerified(isVerified);
    }

    public void setVerified(boolean verified) {
        if (verified) {
            setImageResource(R.drawable.shield_full);
        } else {
            setImageResource(R.drawable.shield_half);
        }
    }
}
