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
package com.waz.zclient.pages.main.conversation;

import android.graphics.Matrix;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;

public class MessageStreamAnimation extends Animation {
    public static final String TAG = MessageStreamAnimation.class.getName();
    private boolean enter;
    private int width;

    public MessageStreamAnimation(boolean enter, int duration, int delay, int width) {
        this.enter = enter;
        this.width = width;

        if (enter) {
            setInterpolator(new Expo.EaseOut());
        } else {
            setInterpolator(new Expo.EaseOut());
        }
        setDuration(duration);
        setStartOffset(delay);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        Matrix m = t.getMatrix();
        if (enter) {
            float w = width * (1 - interpolatedTime);
            m.postTranslate(w, 0);
        } else {
            float w = width * (interpolatedTime);
            m.postTranslate(w, 0);
        }
        super.applyTransformation(interpolatedTime, t);
    }

}
