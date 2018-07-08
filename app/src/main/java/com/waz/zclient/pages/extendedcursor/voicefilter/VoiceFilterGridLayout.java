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
package com.waz.zclient.pages.extendedcursor.voicefilter;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.AudioOverview;
import com.waz.api.RecordingControls;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Quad;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.utils.ContextUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import org.threeten.bp.Instant;


public class VoiceFilterGridLayout extends FrameLayout implements
                                                       VoiceFilterController.RecordingObserver,
                                                       VoiceFilterController.PlaybackObserver {
    private static final int NUM_OF_GRID_ROWS = 2;
    private static final int NUM_OF_GRID_COLS = 4;
    private static final long HINT_DELAY = 1500;
    private final Handler handler;

    private int numRows;
    private int numCols;

    private GlyphTextView selectedView;
    private int accentColor;
    private VoiceFilterController controller;

    private WaveBinView waveBinView;
    private TextView trackTime;
    private TextView textViewHint;
    private View timeContainer;

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
        if (selectedView != null) {
            selectedView.setTextColor(accentColor);
        }

        waveBinView.setAccentColor(accentColor);
    }

    public VoiceFilterGridLayout(Context context) {
        this(context, null);
    }

    public VoiceFilterGridLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoiceFilterGridLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        numRows = NUM_OF_GRID_ROWS;
        numCols = NUM_OF_GRID_COLS;

        handler = new Handler();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LinearLayout container = ViewUtils.getView(this, R.id.ll__voice_filter__grid_container);
        timeContainer = ViewUtils.getView(this, R.id.fl__voice_filter_time_hint__container);
        waveBinView = ViewUtils.getView(this, R.id.wbv__voice_filter);
        trackTime = ViewUtils.getView(this, R.id.tv__track_time);
        textViewHint = ViewUtils.getView(this, R.id.tv__voice_filter__hint);
        trackTime.setText(getContext().getString(R.string.timestamp__just_now));
        initLayout(container);

    }

    private void initLayout(LinearLayout container) {

        for (int r = 0; r < numRows; r++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int c = 0; c < numCols; c++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                                                                                 ViewGroup.LayoutParams.MATCH_PARENT);
                params.weight = 1;
                row.addView(createItemView(r, c), params);

                if (c < numCols - 1) {
                    View view = new View(getContext());
                    view.setBackgroundColor(ContextUtils.getColorWithTheme(R.color.white_16, getContext()));
                    row.addView(view,
                                new LayoutParams(ViewUtils.toPx(getContext(), 0.5f),
                                                 ViewGroup.LayoutParams.MATCH_PARENT));
                }
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            params.weight = 1;
            container.addView(row, params);
            if (r < numRows - 1) {
                View view = new View(getContext());
                view.setBackgroundColor(ContextUtils.getColorWithTheme(R.color.white_16, getContext()));
                container.addView(view,
                                  new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                   ViewUtils.toPx(getContext(), 0.5f)));

            }
        }
    }

    public View createItemView(final int r, final int c) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        FrameLayout frameLayout = (FrameLayout) inflater.inflate(R.layout.voice_filter_grid_icon, this, false);
        GlyphTextView glyphTextView = ViewUtils.getView(frameLayout, R.id.gtv__voice_filter_icon);
        glyphTextView.setText(getResource(r, c));
        frameLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectedView((GlyphTextView) ViewUtils.getView(v, R.id.gtv__voice_filter_icon));
                controller.applyEffectAndPlay(getAudioEffect(r, c));
            }
        });

        if (r == 0 && c == 0) {
            setSelectedView(glyphTextView);
        }

        return frameLayout;
    }

    private int getResource(int r, int c) {
        if (r == 0) {
            switch (c) {
                case 0:
                    return R.string.glyph__people;
                case 1:
                    return R.string.glyph__filter_helium;
                case 2:
                    return R.string.glyph__filter_jellyfish;
                case 3:
                    return R.string.glyph__filter_hare;
            }
        } else if (r == 1) {
            switch (c) {
                case 0:
                    return R.string.glyph__filter_cathedral;
                case 1:
                    return R.string.glyph__filter_alien;
                case 2:
                    return R.string.glyph__filter_robot;
                case 3:
                    return R.string.glyph__filter_rollercoaster;
            }
        }

        return R.string.glyph__attention;
    }


    private AudioEffect getAudioEffect(int r, int c) {
        if (r == 0) {
            switch (c) {
                case 0:
                    return AudioEffect.NONE;
                case 1:
                    return AudioEffect.PITCH_UP_INSANE;  // Should be Balloon   (glyph__filter_helium)
                case 2:
                    return AudioEffect.PITCH_DOWN_INSANE; //   Should be JellyFish   (glyph__filter_jellyfish)
                case 3:
                    return AudioEffect.PACE_UP_MED;   // Should be rabbit  (glyph__filter_hare)
            }
        } else if (r == 1) {
            switch (c) {
                case 0:
                    return AudioEffect.REVERB_MAX;    // Should be Church   (glyph__filter_cathedral)
                case 1:
                    return AudioEffect.CHORUS_MAX;    // Should be alien   (glyph__filter_alien)
                case 2:
                    return AudioEffect.VOCODER_MED;    // Should be Robot  (glyph__filter_robot)
                case 3:
                    return AudioEffect.PITCH_UP_DOWN_MAX;   //  Should be Rollercoaster  (glyph__filter_reverse)
            }
        }

        return AudioEffect.NONE;
    }

    @Override
    public void onPlaybackStopped(long seconds) {
        trackTime.setText(StringUtils.formatTimeSeconds(seconds));
        showTime();
    }

    @Override
    public void onPlaybackStarted(AudioOverview overview) {
        showWave();
    }

    @Override
    public void onPlaybackProceeded(long current, long total) {
    }

    private void setSelectedView(GlyphTextView glyphTextView) {
        if (selectedView != null) {
            selectedView.setTextColor(ContextUtils.getColorWithTheme(R.color.text__primary_dark, getContext()));
        }
        glyphTextView.setTextColor(accentColor);
        selectedView = glyphTextView;
    }

    public void setController(VoiceFilterController controller) {
        this.controller = controller;
        this.controller.addObserver(this);
        this.controller.addPlaybackObserver(this);
        waveBinView.setVoiceFilterController(controller);
    }

    @Override
    public void onRecordingStarted(RecordingControls recording, Instant timestamp) {

    }

    @Override
    public void onRecordingFinished(AudioAssetForUpload recording,
                                    boolean fileSizeLimitReached,
                                    AudioOverview overview) {
        trackTime.setText(StringUtils.formatTimeSeconds(recording.getDuration().getSeconds()));
        showTime();
    }

    @Override
    public void onRecordingCanceled() {

    }

    @Override
    public void onReRecord() {
        showTime();
    }


    private void showWave() {
        waveBinView.setVisibility(View.VISIBLE);

        trackTime.setVisibility(View.GONE);
        timeContainer.setVisibility(View.GONE);
        textViewHint.setVisibility(View.GONE);

        handler.removeCallbacks(runnable);
    }

    private void showTime() {
        timeContainer.setVisibility(View.VISIBLE);
        trackTime.setVisibility(View.VISIBLE);
        textViewHint.setVisibility(View.GONE);
        handler.postDelayed(runnable, HINT_DELAY);

        waveBinView.setVisibility(View.GONE);

    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            trackTime
                .animate()
                .alpha(0)
                .setInterpolator(new Quad.EaseIn())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        trackTime.setAlpha(1);
                        trackTime.setVisibility(View.GONE);
                    }
                });


            textViewHint
                .animate()
                .alpha(1)
                .setStartDelay(getResources().getInteger(R.integer.animation_delay_very_long))
                .setInterpolator(new Quad.EaseOut())
                .withStartAction(new Runnable() {
                    @Override
                    public void run() {
                        textViewHint.setVisibility(View.VISIBLE);
                        textViewHint.setAlpha(0);
                    }
                });
        }
    };


    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {

    }
}
