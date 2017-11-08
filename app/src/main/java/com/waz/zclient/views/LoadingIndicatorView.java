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
package com.waz.zclient.views;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

public class LoadingIndicatorView extends FrameLayout {

    @IntDef({INFINITE_LOADING_BAR, SPINNER, SPINNER_WITH_DIMMED_BACKGROUND, PROGRESS_LOADING_BAR})
    public @interface Type { }
    public static final int INFINITE_LOADING_BAR = 0;
    public static final int SPINNER = 1;
    public static final int SPINNER_WITH_DIMMED_BACKGROUND = 2;
    public static final int PROGRESS_LOADING_BAR = 3;

    private final Runnable showSpinnerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!setToVisible) {
                return;
            }
            progressView.setVisibility(VISIBLE);
            infiniteLoadingBarView.setVisibility(GONE);
            progressLoadingBarView.setVisibility(GONE);
            setBackgroundColor(Color.TRANSPARENT);
            ViewUtils.fadeInView(LoadingIndicatorView.this);
        }
    };
    private final Runnable showSpinnerRunnableWithDimmedBackground = new Runnable() {
        @Override
        public void run() {
            if (!setToVisible) {
                return;
            }
            progressView.setVisibility(VISIBLE);
            infiniteLoadingBarView.setVisibility(GONE);
            progressLoadingBarView.setVisibility(GONE);
            setBackgroundColor(backgroundColor);
            ViewUtils.fadeInView(LoadingIndicatorView.this);
        }
    };
    private final Runnable showInfiniteBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (!setToVisible) {
                return;
            }
            progressView.setVisibility(GONE);
            infiniteLoadingBarView.setVisibility(VISIBLE);
            progressLoadingBarView.setVisibility(GONE);
            setBackgroundColor(Color.TRANSPARENT);
            ViewUtils.fadeInView(LoadingIndicatorView.this);
        }
    };
    private final Runnable showProgressBarRunnable = new Runnable() {
        @Override
        public void run() {
            if (!setToVisible) {
                return;
            }
            progressView.setVisibility(GONE);
            infiniteLoadingBarView.setVisibility(GONE);
            progressLoadingBarView.setVisibility(VISIBLE);
            setBackgroundColor(Color.TRANSPARENT);
            ViewUtils.fadeInView(LoadingIndicatorView.this);
        }
    };
    private final Runnable hideRunnable = new Runnable() {
        @Override
        public void run() {
            ViewUtils.fadeOutView(LoadingIndicatorView.this);
        }
    };

    private InfiniteLoadingBarView infiniteLoadingBarView;
    private ProgressLoadingBarView progressLoadingBarView;
    private ProgressView progressView;
    private @Type int type;
    private Handler handler;
    private boolean setToVisible;
    private int backgroundColor;

    public LoadingIndicatorView(Context context) {
        this(context, null);
    }

    public LoadingIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LoadingIndicatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        infiniteLoadingBarView = new InfiniteLoadingBarView(getContext());
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infiniteLoadingBarView.setVisibility(GONE);
        addView(infiniteLoadingBarView, params);

        progressLoadingBarView = new ProgressLoadingBarView(getContext());
        params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLoadingBarView.setVisibility(GONE);
        addView(progressLoadingBarView, params);

        progressView = new ProgressView(getContext());
        progressView.setTextColor(Color.WHITE);
        progressView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimensionPixelSize(R.dimen.loading_spinner__size));
        params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        progressView.setVisibility(GONE);
        addView(progressView, params);

        this.type = SPINNER;
        handler = new Handler(Looper.getMainLooper());
    }

    @SuppressWarnings("WrongConstant")
    public void show() {
        show(type);
    }

    public void show(@Type int type) {
        show(type, 0);
    }

    public void show(@Type int type, boolean darkTheme) {
        if (darkTheme) {
            applyDarkTheme();
        } else {
            applyLightTheme();
        }
        show(type);
    }

    public void show(@Type int type, long delayMs) {
        final Runnable selectedRunnable;
        switch (type) {
            case INFINITE_LOADING_BAR:
                selectedRunnable = showInfiniteBarRunnable;
                break;
            case SPINNER:
                selectedRunnable = showSpinnerRunnable;
                break;
            case SPINNER_WITH_DIMMED_BACKGROUND:
                selectedRunnable = showSpinnerRunnableWithDimmedBackground;
                break;
            case PROGRESS_LOADING_BAR:
                selectedRunnable = showProgressBarRunnable;
                break;
            default:
                return;
        }
        setToVisible = true;
        handler.removeCallbacks(null);
        handler.postDelayed(selectedRunnable, delayMs);
    }

    public void hide() {
        setToVisible = false;
        handler.removeCallbacks(null);
        handler.post(hideRunnable);
    }

    public void setColor(int color) {
        infiniteLoadingBarView.setColor(color);
        progressLoadingBarView.setColor(color);
    }

    public void setProgress(float progress) {
        progressLoadingBarView.setProgress(progress);
    }

    public void applyLightTheme() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            progressView.setTextColor(getResources().getColor(R.color.text__primary_light));
            //noinspection deprecation
            backgroundColor = getResources().getColor(R.color.text__primary_disabled_dark);
        } else {
            progressView.setTextColor(getResources().getColor(R.color.text__primary_light, getContext().getTheme()));
            backgroundColor = getResources().getColor(R.color.text__primary_disabled_dark, getContext().getTheme());
        }
    }

    public void applyDarkTheme() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            progressView.setTextColor(getResources().getColor(R.color.text__primary_dark));
            //noinspection deprecation
            backgroundColor = getResources().getColor(R.color.text__primary_disabled_light);
        } else {
            progressView.setTextColor(getResources().getColor(R.color.text__primary_dark, getContext().getTheme()));
            backgroundColor = getResources().getColor(R.color.text__primary_disabled_light, getContext().getTheme());
        }
    }

    public void setType(@Type int type) {
        setType(type, false);
    }

    public void setType(@Type int type, boolean show) {
        if (this.type == type) {
            return;
        }
        this.type = type;
        if (show) {
            show();
        }
    }

}
