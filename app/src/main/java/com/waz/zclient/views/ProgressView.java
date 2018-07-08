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
package com.waz.zclient.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;

public class ProgressView extends GlyphTextView {

    private long animationDuration;
    private ValueAnimator rotationAnimator;

    public ProgressView(Context context) {
        this(context, null);
    }

    public ProgressView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText(R.string.glyph__loading);
        animationDuration = getResources().getInteger(R.integer.loading_spinner__animation_duration);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        onVisibilityHasChanged();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        onVisibilityHasChanged();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        onVisibilityHasChanged();
    }

    private void onVisibilityHasChanged() {
        int visibility = getVisibility();
        if (visibility == View.GONE || visibility == View.INVISIBLE) {
            stopAnimation();
        } else {
            startAnimation();
        }
    }

    public void startAnimation() {
        stopAnimation();
        rotationAnimator = ObjectAnimator.ofFloat(this, View.ROTATION, 360);
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        rotationAnimator.setDuration(animationDuration);
        rotationAnimator.setRepeatMode(ValueAnimator.RESTART);
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.start();
    }

    public void stopAnimation() {
        if (rotationAnimator == null) {
            return;
        }
        rotationAnimator.setRepeatCount(1);
        rotationAnimator.cancel();
    }

}
