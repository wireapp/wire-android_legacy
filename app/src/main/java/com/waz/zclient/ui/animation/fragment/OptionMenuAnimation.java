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
package com.waz.zclient.ui.animation.fragment;

import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;

/**
 * http://motion.wearezeta.com/options-menu-phone/index.html
 */
public class OptionMenuAnimation extends Animation {
    private boolean enter;
    private float height;

    public OptionMenuAnimation(boolean enter, int duration, int delay, int height) {
        this.enter = enter;
        this.height = height;

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
            float newHeight = (1 - interpolatedTime) * height;
            m.postTranslate(0, newHeight);
        } else {
            float newHeight = interpolatedTime * height;
            m.postTranslate(0, newHeight);
        }
        super.applyTransformation(interpolatedTime, t);
    }
}
