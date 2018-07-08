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
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.AudioOverview;
import com.waz.api.RecordingControls;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;
import org.threeten.bp.Instant;


public class VoiceFilterRecordingLayout extends FrameLayout implements
                                                            VoiceFilterController.RecordingObserver,
                                                            Subscriber<float[]> {
    private static final String GLYPH_PLACEHOLDER = "_GLYPH_";

    private static final int MAX_NUM_OF_SOUND_LEVELS = 1;

    private WaveGraphView waveGraphView;
    private View hintContainer;
    private VoiceFilterController controller;
    private Subscription subscription;

    public VoiceFilterRecordingLayout(Context context) {
        this(context, null);
    }

    public VoiceFilterRecordingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoiceFilterRecordingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        hintContainer = ViewUtils.getView(this, R.id.ll__voice_filter_hint_container);
        waveGraphView = ViewUtils.getView(this, R.id.wgv__voice_filter);
        waveGraphView.setVisibility(View.GONE);

        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.startRecording();
            }
        });


        String original = getResources().getString(R.string.audio_message__recording__tap_to_record);


        int indexWrap = original.indexOf('\n');
        int indexGlyph = original.indexOf(GLYPH_PLACEHOLDER);
        int indexGlyphEnd = indexGlyph + GLYPH_PLACEHOLDER.length();

        TextView textView = ViewUtils.getView(this, R.id.ttv__voice_filter__tap_to_record_1st_line);
        textView.setText(original.substring(0, indexWrap));

        textView = ViewUtils.getView(this, R.id.ttv__voice_filter__tap_to_record_2nd_line_begin);
        textView.setText(original.substring(indexWrap + 1, indexGlyph));


        textView = ViewUtils.getView(this, R.id.ttv__voice_filter__tap_to_record_2nd_line_end);
        textView.setText(original.substring(indexGlyphEnd));


    }

    public void reset() {
        hintContainer.setVisibility(View.VISIBLE);
        hintContainer.setAlpha(1f);
        waveGraphView.setVisibility(View.GONE);
    }

    public void setController(VoiceFilterController controller) {
        this.controller = controller;
        this.controller.addObserver(this);
    }

    @Override
    public void onRecordingStarted(RecordingControls recording, Instant timestamp) {
        hintContainer.animate()
                     .alpha(0)
                     .withEndAction(new Runnable() {
                         @Override
                         public void run() {
                             hintContainer.setVisibility(View.GONE);
                         }
                     });


        waveGraphView.setVisibility(View.VISIBLE);
        waveGraphView.setAlpha(0);
        waveGraphView
            .animate()
            .alpha(1f);

        subscription = recording.soundLevels(MAX_NUM_OF_SOUND_LEVELS).subscribe(this);
    }

    @Override
    public void onRecordingFinished(AudioAssetForUpload recording,
                                    boolean fileSizeLimitReached,
                                    AudioOverview overview) {
        subscription.cancel();
    }

    @Override
    public void onRecordingCanceled() {
        subscription.cancel();
    }

    @Override
    public void onReRecord() {
        reset();
    }

    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {

    }

    @Override
    public void next(float[] value) {
        waveGraphView.setLevels(value);
    }

    public void setAccentColor(int accentColor) {
        waveGraphView.setAccentColor(accentColor);
    }
}
