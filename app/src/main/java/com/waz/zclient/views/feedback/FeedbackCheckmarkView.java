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
package com.waz.zclient.views.feedback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Back;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;

/**
 * http://motion.wearezeta.com/popover-feedback/index.html
 */
public class FeedbackCheckmarkView extends FrameLayout {

    public interface Callback {
        void onShowStart();

        void onHideStart();

        void onHideEnd();
    }
    private Callback callback;
    private AnimatorSet showAnimatorSet;

    public FeedbackCheckmarkView(Context context) {
        this(context, null);
    }

    public FeedbackCheckmarkView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FeedbackCheckmarkView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void show() {
        if (callback != null) {
            callback.onShowStart();
        }

        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(this,
                                                                    View.ALPHA,
                                                                    0f,
                                                                    1f);

        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(this,
                                                                     View.SCALE_X,
                                                                     1.8f,
                                                                     1f);


        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(this,
                                                                     View.SCALE_Y,
                                                                     1.8f,
                                                                     1f);

        showAnimatorSet = new AnimatorSet();
        showAnimatorSet.playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator);
        showAnimatorSet.setDuration(getResources().getInteger(R.integer.framework_animation_duration_long));
        showAnimatorSet.setStartDelay(getResources().getInteger(R.integer.framework_animation_delay_short));
        showAnimatorSet.setInterpolator(new Back.EaseOut(2));
        final View container = this;
        showAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                container.setAlpha(0f);
                container.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                }, getResources().getInteger(R.integer.framework_animation_duration_long));
            }
        });
        showAnimatorSet.start();
    }

    private void hide() {
        if (showAnimatorSet != null) {
            showAnimatorSet.cancel();
        }

        if (callback != null) {
            callback.onHideStart();
        }

        ObjectAnimator hideAnimator = ObjectAnimator.ofFloat(this,
                                                                   View.ALPHA,
                                                                   1f,
                                                                   0f);
        hideAnimator.setInterpolator(new Quart.EaseOut());
        hideAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
        hideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (callback != null) {
                    callback.onHideEnd();
                }
            }
        });
        hideAnimator.start();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.feedback_checkmark, this, true);

        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });

    }
}
