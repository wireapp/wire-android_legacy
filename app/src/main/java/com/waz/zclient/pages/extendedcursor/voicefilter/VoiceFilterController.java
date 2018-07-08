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

import android.os.Handler;
import com.waz.api.Asset;
import com.waz.api.AssetFactory;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.AudioOverview;
import com.waz.api.PlaybackControls;
import com.waz.api.RecordingCallback;
import com.waz.api.RecordingControls;
import org.threeten.bp.Instant;

import java.util.ArrayList;
import java.util.List;


public class VoiceFilterController implements
                                   RecordingCallback,
                                   Asset.LoadCallback<AudioAssetForUpload> {
    private static final long UPDATE_PLAY_TIME = 40;

    public List<RecordingObserver> recordingObservers = new ArrayList<>();
    public List<PlaybackObserver> playbackObservers = new ArrayList<>();

    private RecordingControls recordingControl;
    private AudioAssetForUpload audioAssetForUpload;
    private PlaybackControls currentPlayBackControl;
    private AudioOverview overview;

    private Handler handler = new Handler();
    private AudioAssetForUpload originalRecording;
    private AudioEffect appliedAudioEffect;

    public void addObserver(RecordingObserver recordingObserver) {
        recordingObservers.remove(recordingObserver);
        recordingObservers.add(recordingObserver);
    }

    public void removeObserver(RecordingObserver recordingObserver) {
        recordingObservers.remove(recordingObserver);
    }

    public void addPlaybackObserver(PlaybackObserver observer) {
        playbackObservers.remove(observer);
        playbackObservers.add(observer);
    }

    public void tearDown() {
        stopRecording();
        stopPlayback();
        if (audioAssetForUpload != null) {
            audioAssetForUpload.delete();
        }

        if (originalRecording != null) {
            originalRecording.delete();
        }

        handler.removeCallbacks(player);
    }

    public void startRecording() {
        if (recordingControl == null) {
            recordingControl = AssetFactory.recordAudioAsset(this);
        }
    }

    public void stopRecording() {
        if (recordingControl != null) {
            recordingControl.stop();
            recordingControl = null;
        }
    }

    public void stopPlayback() {
        if (currentPlayBackControl != null) {
            handler.removeCallbacks(player);
            currentPlayBackControl.stop();
            currentPlayBackControl = null;
        }
    }

    @Override
    public void onStart(Instant timestamp) {
        for (RecordingObserver recordingObserver : recordingObservers) {
            recordingObserver.onRecordingStarted(recordingControl, timestamp);
        }
    }

    @Override
    public void onComplete(AudioAssetForUpload recording, boolean fileSizeLimitReached, AudioOverview overview) {
        recordingControl = null;
        appliedAudioEffect = null;
        originalRecording = recording;
        this.overview = overview;
        for (RecordingObserver recordingObserver : recordingObservers) {
            recordingObserver.onRecordingFinished(recording, fileSizeLimitReached, overview);
        }
    }

    @Override
    public void onCancel() {
        for (RecordingObserver recordingObserver : recordingObservers) {
            recordingObserver.onRecordingCanceled();
        }
    }

    public void quit() {
        tearDown();
    }

    public void approveAudio() {
        if (audioAssetForUpload == null) {
            originalRecording.applyEffect(AudioEffect.NONE, new Asset.LoadCallback<AudioAssetForUpload>() {

                @Override
                public void onLoaded(AudioAssetForUpload audioAssetForUpload) {
                    VoiceFilterController.this.audioAssetForUpload = audioAssetForUpload;
                    approveAudio();
                }

                @Override
                public void onLoadFailed() {
                    VoiceFilterController.this.audioAssetForUpload = originalRecording;
                    approveAudio();
                }
            });

            return;
        }

        AudioAssetForUpload sendAudio = audioAssetForUpload;
        /*
            Null it to make it is not deleted during tearDown()...
         */
        audioAssetForUpload = null;

        for (RecordingObserver recordingObserver : recordingObservers) {
            recordingObserver.sendRecording(sendAudio, appliedAudioEffect);
        }
    }

    public void onReRecord() {
        for (RecordingObserver recordingObserver : recordingObservers) {
            recordingObserver.onReRecord();
        }

        stopPlayback();
    }

    private void update() {
        handler.postDelayed(player, UPDATE_PLAY_TIME);
    }

    private Runnable player = new Runnable() {
        @Override
        public void run() {

            if (!currentPlayBackControl.isPlaying()) {
                for (PlaybackObserver observer : playbackObservers) {
                    observer.onPlaybackStopped(currentPlayBackControl.getDuration().getSeconds());
                }
                return;
            }

            for (PlaybackObserver observer : playbackObservers) {
                long current = currentPlayBackControl.getPlayhead().toMillis();
                observer.onPlaybackProceeded(current, currentPlayBackControl.getDuration().toMillis());
            }

            update();
        }
    };

    public void applyEffectAndPlay(AudioEffect audioEffect) {
        originalRecording.applyEffect(audioEffect, this);
        appliedAudioEffect = audioEffect;
    }

    @Override
    public void onLoaded(AudioAssetForUpload audioAssetForUpload) {
        handler.removeCallbacks(player);

        if (currentPlayBackControl != null && currentPlayBackControl.isPlaying()) {
            currentPlayBackControl.stop();
        }

        if (this.audioAssetForUpload != null) {
            this.audioAssetForUpload.delete();
        }

        this.audioAssetForUpload = audioAssetForUpload;

        currentPlayBackControl = audioAssetForUpload.getPlaybackControls();
        currentPlayBackControl.play();
        for (PlaybackObserver observer : playbackObservers) {
            observer.onPlaybackStarted(overview);
        }

        update();
    }

    @Override
    public void onLoadFailed() {

    }


    public interface RecordingObserver {
        void onRecordingStarted(RecordingControls recording, Instant timestamp);

        void onRecordingFinished(AudioAssetForUpload recording, boolean fileSizeLimitReached, AudioOverview overview);

        void onRecordingCanceled();

        void onReRecord();

        void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect);
    }

    public interface PlaybackObserver {
        void onPlaybackStopped(long seconds);

        void onPlaybackStarted(AudioOverview overview);

        void onPlaybackProceeded(long current, long total);
    }
}
