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
package com.waz.zclient.controllers.streammediaplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.telephony.TelephonyManager;
import com.waz.api.MediaAsset;
import com.waz.api.MediaAssets;
import com.waz.api.MediaProvider;
import com.waz.api.Message;
import com.waz.zclient.controllers.mediaplayer.DefaultMediaPlayer;
import com.waz.zclient.controllers.mediaplayer.IMediaPlayer;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerListener;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerState;
import com.waz.zclient.controllers.spotify.ISpotifyController;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StreamMediaPlayerController implements IStreamMediaPlayerController,
                                                    MediaPlayerListener,
                                                    AudioManager.OnAudioFocusChangeListener {

    private final Map<String, MediaPlayerState> lastMediaStateHashMap; // key = messageId
    private final Map<String, Long> alreadyPlayedTracks; // key = messageId
    private final Map<String, Integer> lastKnownPositions; // key = messageId

    private final Set<StreamMediaPlayerObserver> streamMediaObservers;
    private final Set<StreamMediaBarObserver> streamMediaBarObservers;

    private Context context;
    private ISpotifyController spotifyController;
    private BroadcastReceiver interruptionBroadcastReceiver;
    private AudioManager audioManager;

    private IMediaPlayer mediaPlayer;
    private Message message = Message.EMPTY;
    private MediaAsset mediaTrack = MediaAssets.EMPTY;

    public StreamMediaPlayerController(Context context, ISpotifyController spotifyController) {
        this.streamMediaObservers = new HashSet<>();
        this.streamMediaBarObservers = new HashSet<>();
        this.lastMediaStateHashMap = new HashMap<>();
        this.lastKnownPositions = new HashMap<>();
        this.alreadyPlayedTracks = new HashMap<>();
        this.context = context;
        this.spotifyController = spotifyController;
        registerBroadcastReceiver();
        registerAudioManager();
    }

    private void registerAudioManager() {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

    }

    private void registerBroadcastReceiver() {
        interruptionBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Message trackMetaData = getMessage();
                if (!getMediaPlayerState(trackMetaData).isPauseControl()) {
                    return;
                }
                if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                    String phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                    if (!phoneState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        return;
                    }
                }
                pause(trackMetaData.getConversationId());
            }
        };
        IntentFilter interruptionIntentFilter = new IntentFilter();
        interruptionIntentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        interruptionIntentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        interruptionIntentFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        context.registerReceiver(interruptionBroadcastReceiver, interruptionIntentFilter);
    }

    private void unregisterBroadcastReceiver() {
        context.unregisterReceiver(interruptionBroadcastReceiver);
        interruptionBroadcastReceiver = null;
    }

    @Override
    public void addStreamMediaObserver(StreamMediaPlayerObserver streamMediaObserver) {
        streamMediaObservers.add(streamMediaObserver);
    }

    @Override
    public synchronized void removeStreamMediaObserver(StreamMediaPlayerObserver streamMediaObserver) {
        streamMediaObservers.remove(streamMediaObserver);
    }

    @Override
    public void addStreamMediaBarObserver(StreamMediaBarObserver streamMediaBarObserver) {
        streamMediaBarObservers.add(streamMediaBarObserver);
    }

    @Override
    public void removeStreamMediaBarObserver(StreamMediaBarObserver streamMediaBarObserver) {
        streamMediaBarObservers.remove(streamMediaBarObserver);
    }

    @Override
    public void tearDown() {
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        lastMediaStateHashMap.clear();
        streamMediaBarObservers.clear();
        streamMediaObservers.clear();
        unregisterBroadcastReceiver();
        audioManager.abandonAudioFocus(this);
        audioManager = null;
        spotifyController = null;
        context = null;
    }

    @Override
    public void setMediaPlayerInstance(MediaProvider type) {
        if (type == MediaProvider.SOUNDCLOUD && mediaPlayer instanceof DefaultMediaPlayer) {
            return;
        }
        if (type == MediaProvider.SPOTIFY && !spotifyController.isLoggedIn() && mediaPlayer instanceof DefaultMediaPlayer) {
            return;
        }
        setMediaPlayerInternal(type);
    }

    private void setMediaPlayerInternal(MediaProvider type) {
        if (this.mediaPlayer != null) {
            this.mediaPlayer.release();
            this.mediaPlayer = null;
        }
        switch (type) {
            case SOUNDCLOUD:
                this.mediaPlayer = new DefaultMediaPlayer();
                break;
            case SPOTIFY:
                if (!spotifyController.isLoggedIn()) {
                    mediaPlayer = new DefaultMediaPlayer();
                } else try {
                    final Class<?> spotifyClass = Class.forName("com.waz.zclient.controllers.spotify.SpotifyMediaPlayer");
                    final Constructor<?> cons = spotifyClass.getConstructor(Context.class, ISpotifyController.class);
                    mediaPlayer = (IMediaPlayer) cons.newInstance(context, spotifyController);
                } catch (Exception e) {
                    mediaPlayer = new DefaultMediaPlayer();
                }
                break;
        }
        initMediaPlayer();
    }

    @Override
    public void play() {
        play(getMessage(), getMediaTrack());
    }

    @Override
    public void play(String conversationId) {
        if (!isSelectedConversation(conversationId)) {
            return;
        }
        play(getMessage(), getMediaTrack());
    }

    @Override
    public void play(final Message message, final MediaAsset mediaAsset) {
        if (isSelectedMessage(message) &&
            getMediaPlayerState().isStartAllowed()) {
            start();
        } else {
            if (mediaPlayer != null) {
                saveLastKnownPosition();
                mediaPlayer.stop();
                mediaPlayer.reset();
            }
            mediaAsset.prepareStreaming(new MediaAsset.StreamingCallback() {
                @Override
                public void onSuccess(List<Uri> uris) {
                    if (uris.size() > 0) {
                        setTrack(message, mediaAsset);
                        setMediaPlayerInstance(mediaAsset.getProvider());
                        mediaPlayer.setDataSource(uris.get(0));
                        start();
                    }
                }

                @Override
                public void onFailure(int code, String message, String label) {
                    onError();
                }
            });
        }
    }

    @Override
    public void pause() {
        pause(getMessage().getConversationId());
    }

    @Override
    public void pause(String conversationId) {
        if (!isSelectedConversation(conversationId)) {
            return;
        }
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @Override
    public int getPosition(Message message) {
        if (isSelectedMessage(message) &&
            isSelectedConversation(message.getConversationId()) &&
            getMediaPlayerState(message).isGetCurrentPositionAllowed()) {
            return mediaPlayer.getCurrentPosition();
        }
        if (!lastKnownPositions.containsKey(message.getId())) {
            return DefaultMediaPlayer.PLAYING_NOT_STARTED;
        }
        return lastKnownPositions.get(message.getId());
    }

    @Override
    public void seekTo(Message message, int positionMs) {
        if (!isSelectedMessage(message)) {
            return;
        }
        mediaPlayer.seekTo(positionMs);
        lastKnownPositions.put(message.getId(), positionMs);
    }

    @Override
    public void release(Message message) {
        if (!isSelectedMessage(message)) {
            return;
        }
        mediaPlayer.stop();
        mediaPlayer.reset();
    }

    private MediaPlayerState getMediaPlayerState() {
        return getMediaPlayerState(getMessage());
    }

    @Override
    public MediaPlayerState getMediaPlayerState(String conversationId) {
        if (!isSelectedConversation(conversationId)) {
            return MediaPlayerState.Idle;
        }
        return getMediaPlayerState(getMessage());
    }

    @Override
    public MediaPlayerState getMediaPlayerState(Message message) {
        MediaPlayerState playerState = lastMediaStateHashMap.get(message.getId());
        return playerState != null ? playerState : MediaPlayerState.Idle;
    }

    @Override
    public boolean isSelectedConversation(String conversationId) {
        return getMessage().getConversationId().equals(conversationId);
    }

    @Override
    public boolean isSelectedMessage(Message message) {
        return getMessage().equals(message);
    }

    private void setTrack(@NonNull Message message, @NonNull MediaAsset mediaTrack) {
        if (this.message != null &&
            this.message.equals(message) &&
            this.mediaTrack != null &&
            this.mediaTrack.equals(mediaTrack)) {
            return;
        }
        this.alreadyPlayedTracks.put(message.getId(), 0L);
        this.message = message;
        this.mediaTrack = mediaTrack;
        notifyTrackChanged();
    }

    @NonNull
    @Override
    public Message getMessage() {
        return message;
    }

    @Override
    public void onStateChanged(MediaPlayerState state) {
        Message selectedMessage = getMessage();
        lastMediaStateHashMap.put(selectedMessage.getId(), mediaPlayer.getState());
    }

    @Override
    public void onPrepared() {
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onPrepared(getMessage());
        }
        if (!lastKnownPositions.containsKey(getMessage().getId())) {
            start();
        } else {
            mediaPlayer.seekTo(lastKnownPositions.get(getMessage().getId()));
        }
    }

    @Override
    public void onError() {
        audioManager.abandonAudioFocus(this);
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onError(getMessage());
        }
    }

    @Override
    public void onCompletion() {
        audioManager.abandonAudioFocus(this);
        // It is weird that in case of an error, we get the callback for completion as well
        if (getMediaPlayerState() != MediaPlayerState.Error) {
            lastKnownPositions.remove(getMessage().getId());
        }
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onComplete(getMessage());
        }
        hideMediaBar();
    }

    @Override
    public void onSeekComplete() {
        saveLastKnownPosition();
        if (getMediaPlayerState() != MediaPlayerState.Paused) {
            start();
        }
    }

    private void initMediaPlayer() {
        mediaPlayer.setMediaPlayerListener(this);
    }

    private void notifyTrackChanged() {
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onTrackChanged(getMessage());
        }
    }

    @Override
    public void stop() {
        stop(getMessage().getConversationId());
    }

    @Override
    public void stop(String conversationId) {
        if (!isSelectedConversation(conversationId) || mediaPlayer == null) {
            return;
        }
        lastKnownPositions.remove(getMessage().getId());
        mediaPlayer.stop();
    }

    @Override
    public void informVisibleItems(List<String> visibleMessageIds) {
        MediaPlayerState mediaPlayerState = getMediaPlayerState();
        if (mediaPlayerState != null) {
            switch (mediaPlayerState) {
                case Stopped:
                case PlaybackCompleted:
                case Error:
                    return;
            }
        }
        if (getMessage().isEmpty()) {
            hideMediaBar();
            return;
        }

        if (visibleMessageIds.contains(getMessage().getId())) {
            hideMediaBar();
        } else {
            showMediaBar();
        }
    }

    @Override
    public void requestScroll() {
        for (StreamMediaBarObserver listener : streamMediaBarObservers) {
            listener.onScrollTo(getMessage());
        }
    }

    private void showMediaBar() {
        for (StreamMediaBarObserver listener : streamMediaBarObservers) {
            listener.onShowMediaBar(getMessage().getConversationId());
        }
    }

    private void hideMediaBar() {
        for (StreamMediaBarObserver listener : streamMediaBarObservers) {
            listener.onHideMediaBar();
        }
    }

    @Override
    public void onStop() {
        audioManager.abandonAudioFocus(this);
        hideMediaBar();
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onStop(getMessage());
        }
    }

    @Override
    public void onPause() {
        audioManager.abandonAudioFocus(this);
        saveLastKnownPosition();
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onPause(getMessage());
        }
    }

    private void saveLastKnownPosition() {
        int position = mediaPlayer.getCurrentPosition();
        if (position == DefaultMediaPlayer.PLAYING_NOT_STARTED) {
            return;
        }
        lastKnownPositions.put(getMessage().getId(), position);
    }

    private void start() {
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mediaPlayer.start();
        }
    }

    @Override
    public void onStart() {
        for (StreamMediaPlayerObserver listener : streamMediaObservers) {
            listener.onPlay(getMessage());
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            stop();
        }
    }

    @NonNull
    @Override
    public MediaAsset getMediaTrack() {
        return mediaTrack;
    }

    @Override
    public void resetMediaPlayer(MediaProvider type) {
        stop();
        setMediaPlayerInternal(type);
        Set<String> strings = new HashSet<>(alreadyPlayedTracks.keySet());
        for (String messageId : strings) {
            alreadyPlayedTracks.remove(messageId);
            lastKnownPositions.remove(messageId);
            lastMediaStateHashMap.remove(messageId);
            if (!getMessage().getId().equals(messageId)) {
                continue;
            }
            setTrack(Message.EMPTY, MediaAssets.EMPTY);
        }
    }
}
