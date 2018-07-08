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

public final class Back {

    private Back() {}

    private static final float DEFAULT_OVERSHOOT_AMOUNT = 1.70158f;

    public static class EaseIn implements Interpolator {

        private final float overshoot;

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing in: accelerating from zero velocity.
         */
        public EaseIn() {
            this(DEFAULT_OVERSHOOT_AMOUNT);
        }

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing in: accelerating from zero velocity.
         *
         * @param overshoot Overshoot ammount: higher overshoot means greater overshoot
         *          (0 produces cubic easing with no overshoot,
         *          and the default value of 1.70158 produces an overshoot of 10 percent).
         */
        public EaseIn(float overshoot) {
            this.overshoot = overshoot;
        }

        @Override
        public float getInterpolation(float t) {
            return t * t * ((overshoot + 1) * t - overshoot);
        }
    }

    public static class EaseOut implements Interpolator {

        private final float overshoot;

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing out: decelerating from zero velocity.
         */
        public EaseOut() {
            this(DEFAULT_OVERSHOOT_AMOUNT);
        }

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing out: decelerating from zero velocity.
         *
         * @param overshoot Overshoot ammount: higher overshoot means greater overshoot
         *          (0 produces cubic easing with no overshoot,
         *          and the default value of 1.70158 produces an overshoot of 10 percent).
         */
        public EaseOut(float overshoot) {
            this.overshoot = overshoot;
        }

        @Override
        public float getInterpolation(float t) {
            return (t = t - 1) * t * ((overshoot + 1) * t + overshoot) + 1;
        }
    }

    public static class EaseInOut implements Interpolator {

        private final float overshoot;

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing in/out: acceleration until halfway, then deceleration.
         */
        public EaseInOut() {
            this(DEFAULT_OVERSHOOT_AMOUNT);
        }

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing in/out: acceleration until halfway, then deceleration.
         *
         * @param overshoot Overshoot ammount: higher overshoot means greater overshoot
         *          (0 produces cubic easing with no overshoot,
         *          and the default value of 1.70158 produces an overshoot of 10 percent).
         */
        public EaseInOut(float overshoot) {
            this.overshoot = overshoot;
        }

        @Override
        public float getInterpolation(float t) {
            if ((t /= 2f) < 1) {
                return .5f * (t * t * ((overshoot * 1.525f + 1) * t - (overshoot * 1.525f)));
            } else {
                return .5f * ((t -= 2) * t * ((overshoot * 1.525f + 1) * t + overshoot * 1.525f) + 2);
            }
        }
    }

    public static class EaseOutIn implements Interpolator {

        private final float overshoot;

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing out/in: deceleration until halfway, then acceleration.
         */
        public EaseOutIn() {
            this(DEFAULT_OVERSHOOT_AMOUNT);
        }

        /**
         * Easing equation function for a back (overshooting cubic easing: (overshoot+1)*t^3 - overshoot*t^2)
         * easing out/in: deceleration until halfway, then acceleration.
         *
         * @param overshoot Overshoot ammount: higher overshoot means greater overshoot
         *          (0 produces cubic easing with no overshoot,
         *          and the default value of 1.70158 produces an overshoot of 10 percent).
         */
        public EaseOutIn(float overshoot) {
            this.overshoot = overshoot;
        }

        @Override
        public float getInterpolation(float t) {
            t = t * 2;
            if (t < 1f) {
                return .5f * ((t -= 1) * t * ((overshoot + 1) * t + overshoot) + 1);
            } else {
                return .5f * t * t * ((overshoot + 1) * t - overshoot) + .5f;
            }
        }
    }
}
