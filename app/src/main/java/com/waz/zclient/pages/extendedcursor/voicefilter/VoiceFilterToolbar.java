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
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ViewAnimator;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.AudioOverview;
import com.waz.api.RecordingControls;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.utils.ViewUtils;
import org.threeten.bp.Instant;


public class VoiceFilterToolbar extends ViewAnimator implements
                                                     View.OnClickListener,
                                                     VoiceFilterController.RecordingObserver {

    private GlyphTextView recordButton;
    private VoiceFilterController voiceFilterController;

    public void setVoiceFilterController(VoiceFilterController voiceFilterController) {
        this.voiceFilterController = voiceFilterController;
        voiceFilterController.addObserver(this);
    }

    public VoiceFilterToolbar(Context context) {
        this(context, null);
    }

    public VoiceFilterToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        addRecordBar();
        addPlayBar();
    }

    private void addRecordBar() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.voice_filter_control_record_bottom, this, false);
        recordButton = ViewUtils.getView(view, R.id.gtv__record_button);
        addView(view);

        recordButton.setOnClickListener(this);
    }

    private void addPlayBar() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.voice_filter_control_filter, this, false);

        ViewUtils.getView(view, R.id.v__voice_re_record).setOnClickListener(this);
        ViewUtils.getView(view, R.id.v__voice_approve).setOnClickListener(this);
        ViewUtils.getView(view, R.id.v__voice_cancel).setOnClickListener(this);

        addView(view);
    }

    @Override
    public void setInAnimation(Animation inAnimation) {
        inAnimation.setStartOffset(getResources().getInteger(R.integer.camera__control__ainmation__in_delay));
        inAnimation.setInterpolator(new Expo.EaseOut());
        inAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setInAnimation(inAnimation);
    }

    @Override
    public void setOutAnimation(Animation outAnimation) {
        outAnimation.setInterpolator(new Expo.EaseIn());
        outAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setOutAnimation(outAnimation);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gtv__record_button:
                if (!recordButton.isSelected()) {
                    voiceFilterController.startRecording();
                } else {
                    voiceFilterController.stopRecording();
                }
                break;
            case R.id.v__voice_re_record:
                voiceFilterController.onReRecord();
                break;
            case R.id.v__voice_approve:
                voiceFilterController.approveAudio();
                break;
            case R.id.v__voice_cancel:
                voiceFilterController.onCancel();
                break;
        }
    }

    public void reset() {
        recordButton.setSelected(false);
        recordButton.setText(R.string.glyph__record_alt);
    }

    @Override
    public void onRecordingStarted(RecordingControls recording, Instant timestamp) {
        recordButton.setSelected(true);
        recordButton.setText(R.string.glyph__stop_alt);
    }

    @Override
    public void onRecordingFinished(AudioAssetForUpload recording,
                                    boolean fileSizeLimitReached,
                                    AudioOverview overview) {
    }

    @Override
    public void onRecordingCanceled() {
        reset();
    }

    @Override
    public void onReRecord() {
        reset();
    }

    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {

    }
}
