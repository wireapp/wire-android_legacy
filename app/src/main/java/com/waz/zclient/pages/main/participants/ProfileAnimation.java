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
package com.waz.zclient.pages.main.participants;

import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;

public class ProfileAnimation extends Animation {
    public static final String TAG = ProfileAnimation.class.getName();
    private boolean enter;
    private final float px;
    private final float py;

    public ProfileAnimation(boolean enter, int duration, int delay, float px, float py) {
        this.enter = enter;
        this.px = px;
        this.py = py;

        if (enter) {
            setInterpolator(new Expo.EaseOut());
        } else {
            setInterpolator(new Expo.EaseIn());
        }
        setDuration(duration);
        setStartOffset(delay);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {

        Matrix m = t.getMatrix();
        if (enter) {
            t.setAlpha(interpolatedTime);
            float scale = (1 - interpolatedTime) * 0.75f + interpolatedTime;
            m.postScale(scale, scale, px, py);
        } else {
            t.setAlpha(1 - interpolatedTime);
            float scale = (1 - interpolatedTime) + interpolatedTime * 0.5f;
            m.postScale(scale, scale, px, py);
        }
        super.applyTransformation(interpolatedTime, t);
    }

}
