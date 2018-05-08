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
import android.widget.FrameLayout;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.AudioOverview;
import com.waz.api.RecordingControls;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;
import org.threeten.bp.Instant;

public class VoiceFilterLayout extends FrameLayout implements VoiceFilterController.RecordingObserver {
    private final VoiceFilterController voiceFilterController;

    private VoiceFilterContent voiceFilterContent;
    private VoiceFilterToolbar voiceFilterToolbar;
    private Callback callback;

    public VoiceFilterLayout(Context context) {
        this(context, null);
    }

    public VoiceFilterLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VoiceFilterLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        voiceFilterController = new VoiceFilterController();
        voiceFilterController.addObserver(this);

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        voiceFilterToolbar = ViewUtils.getView(this, R.id.vft);
        voiceFilterContent = ViewUtils.getView(this, R.id.vfc);

        voiceFilterToolbar.setVoiceFilterController(voiceFilterController);
        voiceFilterContent.getVoiceFilterRecordingLayout().setController(voiceFilterController);
        voiceFilterContent.getVoiceFilterGridLayout().setController(voiceFilterController);
    }

    @Override
    public void onRecordingStarted(RecordingControls recording, Instant timestamp) {
        if (callback != null) {
            callback.onAudioMessageRecordingStarted();
        }
    }

    @Override
    public void onRecordingFinished(AudioAssetForUpload recording,
                                    boolean fileSizeLimitReached,
                                    AudioOverview overview) {
        voiceFilterContent.showNext();
        voiceFilterToolbar.showNext();
    }

    @Override
    public void onRecordingCanceled() {
        if (callback != null) {
            callback.onCancel();
        }
    }

    @Override
    public void onReRecord() {
        voiceFilterContent.showNext();
        voiceFilterToolbar.showNext();
    }

    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {
        if (callback != null) {
            callback.sendRecording(audioAssetForUpload, appliedAudioEffect);
        }
    }

    public void setAccentColor(int accentColor) {
        voiceFilterContent.setAccentColor(accentColor);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void onClose() {
        voiceFilterController.quit();
    }

    public interface Callback {
        void onCancel();

        void onAudioMessageRecordingStarted();

        void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect);
    }
}
