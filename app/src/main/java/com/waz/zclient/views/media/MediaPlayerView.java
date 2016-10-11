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
package com.waz.zclient.views.media;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.ColorRes;
import android.support.annotation.DimenRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.waz.zclient.R;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerState;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.OnDoubleClickListener;
import com.waz.zclient.views.ProgressView;
import com.waz.zclient.views.images.CircularSeekBar;

public class MediaPlayerView extends FrameLayout implements CircularSeekBar.OnCircularSeekBarChangeListener {

    private ZetaButton hintButton;
    private GlyphTextView musicTrackIndicator;
    private ProgressView loadingIndicatorProgressView;
    private ImageView logoImageView;
    private TypefaceTextView artistTextView;
    private TypefaceTextView titleTextView;
    private CircularSeekBar progressSeekBar;
    private GlyphTextView controlImageView;
    private MediaPlayerListener mediaPlayerListener;
    private MediaPlayerState lastMediaState;
    private ObjectAnimator greyOutAnimator;
    private boolean allowControl;
    private OnLongClickListener longClickListener;

    public MediaPlayerView(Context context) {
        this(context, null);
    }

    public MediaPlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaPlayerView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        View view = LayoutInflater.from(context).inflate(R.layout.media_player_layout, this, true);

        musicTrackIndicator = ViewUtils.getView(view, R.id.gtv__media_music_indicator);
        loadingIndicatorProgressView = ViewUtils.getView(view, R.id.pb__media_loading_indicator);
        logoImageView = ViewUtils.getView(view, R.id.iv__media_logo);
        logoImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerListener.onOpenExternalClicked();
            }
        });
        artistTextView = ViewUtils.getView(view, R.id.ttv__media_user);
        titleTextView = ViewUtils.getView(view, R.id.ttv__media_title);
        hintButton = ViewUtils.getView(view, R.id.zb__media_music_hint);
        hintButton.setIsFilled(false);
        hintButton.setVisibility(GONE);
        hintButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerListener.onHintClicked();
            }
        });
        progressSeekBar = ViewUtils.getView(view, R.id.sb__media_progress);
        progressSeekBar.setOnSeekBarChangeListener(this);
        progressSeekBar.setProgressEnabled(false);
        progressSeekBar.setOnArtClickListener(new OnDoubleClickListener() {
            @Override
            public void onDoubleClick() {
                if (mediaPlayerListener != null) {
                    mediaPlayerListener.onPlaceholderDoubleTap();
                }
            }

            @Override
            public void onSingleClick() {
                if (mediaPlayerListener != null) {
                    mediaPlayerListener.onPlaceholderTap();
                }
            }
        });
        progressSeekBar.setOnArtLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return longClickListener != null && longClickListener.onLongClick(v);
            }
        });
        controlImageView = ViewUtils.getView(this, R.id.gtv__media_play);
        controlImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaPlayerListener != null) {
                    mediaPlayerListener.onControlClicked();
                }
            }
        });

        // Enable for transparent cutout
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        super.setOnLongClickListener(l);
        longClickListener = l;
    }

    @Override
    public void onProgressChanged(CircularSeekBar circularSeekBar, int progress, boolean fromUser) {}

    @Override
    public void onStopTrackingTouch(CircularSeekBar seekBar) {
        mediaPlayerListener.onSeekEnd(seekBar.getProgress());
    }

    @Override
    public void onStartTrackingTouch(CircularSeekBar seekBar) {
        mediaPlayerListener.onSeekStart();
    }

    public void setMediaPlayerListener(MediaPlayerListener mediaPlayerListener) {
        this.mediaPlayerListener = mediaPlayerListener;
    }

    public void setSeekBarEnabled(boolean seekingEnabled) {
        progressSeekBar.setProgressEnabled(seekingEnabled);
    }

    public void setLoadingIndicatorEnabled(boolean enabled) {
        loadingIndicatorProgressView.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void setControlVisibility(int visible) {
        controlImageView.setVisibility(visible);
    }

    public void setMusicIndicatorVisibility(int visible) {
        musicTrackIndicator.setVisibility(visible);
    }

    public void setCircleStrokeWidth(@DimenRes int width) {
        progressSeekBar.setCircleStrokeWidth(getResources().getDimension(width));
    }

    public void setCircleColor(@ColorRes int color) {
        progressSeekBar.setCircleColor(getResources().getColor(color));
    }

    public void animateHint(@StringRes int hintText) {
        showHint(getResources().getString(hintText), true);
    }

    public void showHint(@StringRes int hintText) {
        showHint(getResources().getString(hintText), false);
    }

    public void showHint(String hintText, boolean animate) {
        if (hintButton == null ||
            hintButton.getVisibility() == VISIBLE ||
            greyOutAnimator != null) {
            return;
        }
        hintButton.setText(hintText);
        hintButton.setClickable(true);
        if (animate) {
            hintButton.animate()
                      .alpha(1f)
                      .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                      .withStartAction(new Runnable() {
                          @Override
                          public void run() {
                              hintButton.setVisibility(VISIBLE);
                          }
                      })
                      .start();
            greyOutAnimator = ObjectAnimator.ofFloat(progressSeekBar, CircularSeekBar.DARKEN_LEVEL, 0f, ResourceUtils.getResourceFloat(getResources(), R.dimen.mediaplayer__font_hint_darken_overlay));
            greyOutAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_short));
            greyOutAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    greyOutAnimator = null;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    greyOutAnimator = null;
                }
            });
            greyOutAnimator.start();
        } else {
            hintButton.setAlpha(1f);
            hintButton.setVisibility(VISIBLE);
            progressSeekBar.setDarkenLevel(ResourceUtils.getResourceFloat(getResources(), R.dimen.mediaplayer__font_hint_darken_overlay));
        }
    }

    public void hideHint() {
        if (hintButton == null ||
            hintButton.getVisibility() == GONE) {
            return;
        }
        hintButton.animate()
                  .alpha(0f)
                  .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                  .withEndAction(new Runnable() {
                      @Override
                      public void run() {
                          hintButton.setVisibility(GONE);
                      }
                  })
                  .start();
        greyOutAnimator = ObjectAnimator.ofFloat(progressSeekBar, CircularSeekBar.DARKEN_LEVEL, ResourceUtils.getResourceFloat(getResources(), R.dimen.mediaplayer__font_hint_darken_overlay), 0f);
        greyOutAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_short));
        greyOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                greyOutAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                greyOutAnimator = null;
            }
        });
        greyOutAnimator.start();
    }

    public void setAllowControl(boolean allowControl) {
        this.allowControl = allowControl;
        final MediaPlayerState state = lastMediaState;
        this.lastMediaState = null;
        updateControl(state);
    }

    public interface MediaPlayerListener {
        void onSeekStart();

        void onSeekEnd(int positionMs);

        void onControlClicked();

        void onOpenExternalClicked();

        void onPlaceholderTap();

        void onPlaceholderDoubleTap();

        boolean onHintClicked();
    }

    public void updateControl(MediaPlayerState mediaPlayerState) {
        if (!allowControl) {
            controlImageView.setVisibility(INVISIBLE);
            controlImageView.setClickable(false);
            return;
        }

        if (lastMediaState == mediaPlayerState) {
            return;
        }

        controlImageView.setClickable(true);
        controlImageView.setVisibility(View.VISIBLE);

        if (mediaPlayerState.isPauseControl()) {
            controlImageView.setText(getResources().getString(R.string.glyph__pause));
        } else if (mediaPlayerState.isPlayControl()) {
            controlImageView.setText(getResources().getString(R.string.glyph__play));
        } else {
            controlImageView.setVisibility(View.INVISIBLE);
            controlImageView.setClickable(false);
        }
        lastMediaState = mediaPlayerState;
    }

    public void setTime(int mediaTime, int maxTime) {
        if (progressSeekBar.getMax() != maxTime) {
            progressSeekBar.setMax(maxTime);
        }
        if (progressSeekBar.getProgress() != mediaTime) {
            progressSeekBar.setProgress(mediaTime);
        }
    }

    public void setArtist(String artist) {
        artistTextView.setText(artist);
    }

    public void setArtist(@StringRes int id) {
        artistTextView.setText(id);
    }

    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setTitle(@StringRes int id) {
        titleTextView.setText(id);
    }

    public void setSourceImage(@DrawableRes int image) {
        logoImageView.setImageResource(image);
    }

    public void setImage(@Nullable Bitmap image) {
        if (image != null) {
            progressSeekBar.setImageBitmap(image);
            setMusicIndicatorVisibility(GONE);
        } else {
            progressSeekBar.setImageResource(R.color.mediaplayer__seekbar_circle_inner_color);
            setMusicIndicatorVisibility(VISIBLE);
        }
    }

    public void setProgressColor(int color) {
        progressSeekBar.setCircleProgressColor(color);
        hintButton.setAccentColor(color);
    }

    public void release() {
        loadingIndicatorProgressView = null;
        logoImageView = null;
        artistTextView = null;
        titleTextView = null;
        progressSeekBar = null;
        controlImageView = null;
        hintButton = null;
        musicTrackIndicator = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width >= getResources().getDimensionPixelSize(R.dimen.mediaplayer__max_width)) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(getResources().getDimensionPixelSize(R.dimen.mediaplayer__max_width),
                                                           MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
