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
/*
 * This part of the Wire sofware is based on the work of Robert Penner.
 * (http://robertpenner.com/easing)
 *
 * Copyright (C) 2001 Robert Penner
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * Neither the name of the author nor the names of contributors may
 * be used to endorse or promote products derived from this software
 * without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.waz.zclient.ui.animation.interpolators.penner;

import android.view.animation.Interpolator;

import com.waz.zclient.ui.utils.MathUtils;

public final class Expo {

    private Expo() {}

    public static class EaseIn implements Interpolator {

        /**
         * Easing equation function for an exponential (2^t)
         * easing in: accelerating from zero velocity.
         */
        public EaseIn() {
        }

        @Override
        public float getInterpolation(float t) {
            if (MathUtils.floatEqual(t, 0f)) {
                return 0;
            } else {
                return (float) Math.pow(2, 10 * (t - 1));
            }
        }
    }

    public static class EaseOut implements Interpolator {

        /**
         * Easing equation function for an exponential (2^t)
         * easing out: decelerating from zero velocity.
         */
        public EaseOut() {
        }

        @Override
        public float getInterpolation(float t) {
            if (MathUtils.floatEqual(t, 1f)) {
                return 1;
            } else {
                return 1 - (float) Math.pow(2, -10 * t);
            }
        }
    }

    public static class EaseInOut implements Interpolator {

        /**
         * Easing equation function for an exponential (2^t)
         * easing in/out: acceleration until halfway, then deceleration.
         */
        public EaseInOut() {
        }

        @Override
        public float getInterpolation(float t) {
            if (MathUtils.floatEqual(t, 0f)) {
                return 0;
            }
            if (MathUtils.floatEqual(t, 1f)) {
                return 1;
            }
            if ((t /= .5f) < 1) {
                return .5f * (float) Math.pow(2, 10 * (t - 1));
            } else {
                return .5f * (2 - (float) Math.pow(2, -10 * --t));
            }
        }
    }

    public static class EaseOutIn implements Interpolator {

        /**
         * Easing equation function for an exponential (2^t)
         * easing out/in: deceleration until halfway, then acceleration.
         */
        public EaseOutIn() {
        }

        @Override
        public float getInterpolation(float t) {
            if (MathUtils.floatEqual(t * 2, 1f)) {
                return .5f;
            }

            if (t < .5f) {
                return .5f * (1 - (float) Math.pow(2, -10 * t * 2f));
            } else {
                return .5f * (float) Math.pow(2, 10 * (((t * 2) - 1) - 1)) + .5f;
            }
        }
    }
}
