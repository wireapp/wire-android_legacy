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
package com.waz.zclient.controllers.spotify;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.PlayConfig;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.PlayerStateCallback;
import com.spotify.sdk.android.player.Spotify;
import com.waz.zclient.R;
import com.waz.zclient.controllers.mediaplayer.IMediaPlayer;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerListener;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerState;

import java.util.concurrent.TimeUnit;


public class SpotifyMediaPlayer implements IMediaPlayer,
                                           Player.InitializationObserver,
                                           PlayerNotificationCallback,
                                           ConnectionStateCallback {

    private Context context;
    private ISpotifyController spotifyController;
    private Player mediaPlayer;
    private MediaPlayerListener listener;
    private MediaPlayerState mediaPlayerState;
    private Uri dataSource;
    private Handler refreshCurrentDurationHandler;
    private int currentPosition;

    public SpotifyMediaPlayer(Context context, ISpotifyController spotifyController) {
        this.context = context;
        this.spotifyController = spotifyController;
        this.mediaPlayer = Spotify.getPlayer(spotifyController.getPlayerConfig(), this, this);
        this.mediaPlayer.addPlayerNotificationCallback(this);
        this.mediaPlayer.addConnectionStateCallback(this);
        this.mediaPlayerState = MediaPlayerState.Idle;
        this.currentPosition = 0;
        this.refreshCurrentDurationHandler = new Handler();
    }

    @Override
    public void setDataSource(Uri uri) {
        this.dataSource = uri;
        setState(MediaPlayerState.Initialized);
    }

    @Override
    public void start() {
        if (mediaPlayer.isLoggedIn()) {
            onLoggedIn();
        } else {
            mediaPlayer.login(spotifyController.getPlayerConfig().oauthToken);
        }
    }

    @Override
    public void onLoggedIn() {
        if (mediaPlayerState == MediaPlayerState.Paused) {
            mediaPlayer.getPlayerState(new PlayerStateCallback() {
                @Override
                public void onPlayerState(PlayerState playerState) {
                    if (dataSource.toString().equals(playerState.trackUri)) {
                        mediaPlayer.resume();
                    } else {
                        reset();
                        onLoggedIn();
                    }
                }
            });
        } else {
            if (listener != null && !mediaPlayerState.isStartAllowed()) {
                setState(MediaPlayerState.Prepared);
                listener.onPrepared();
            } else {
                mediaPlayer.play(PlayConfig.createFor(dataSource.toString())
                                           .withInitialPosition(currentPosition));
            }
        }
    }

    @Override
    public void onLoggedOut() {
        reset();
    }

    @Override
    public void onLoginFailed(Throwable throwable) {
        onError(throwable);
    }

    @Override
    public void onTemporaryError() {
    }

    @Override
    public void onConnectionMessage(String s) {
    }

    private void scheduleTimeUpdate() {
        final long refreshRate = context.getResources().getInteger(R.integer.mediaplayer__time_refresh_rate_ms);
        refreshCurrentDurationHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (mediaPlayer == null ||
                                        mediaPlayerState != MediaPlayerState.Started) {
                                        return;
                                    }
                                    mediaPlayer.getPlayerState(new PlayerStateCallback() {
                                        @Override
                                        public void onPlayerState(PlayerState playerState) {
                                            currentPosition = playerState.positionInMs;
                                        }
                                    });
                                    refreshCurrentDurationHandler.postDelayed(this, refreshRate);
                                }
                            },
                            refreshRate);
    }

    @Override
    public void pause() {
        mediaPlayer.pause();
        setState(MediaPlayerState.Paused);
        if (listener != null) {
            listener.onPause();
        }
    }

    @Override
    public void stop() {
        mediaPlayer.pause();
        currentPosition = 0;
        setState(MediaPlayerState.Stopped);
        if (listener != null) {
            listener.onStop();
        }
    }

    @Override
    public void release() {
        stop();
        context = null;
        refreshCurrentDurationHandler = null;
        Spotify.destroyPlayer(this);
        try {
            // See https://github.com/spotify/android-sdk/issues/40
            mediaPlayer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        spotifyController = null;
        mediaPlayer = null;
    }

    @Override
    public void reset() {
        mediaPlayer.clearQueue();
        setState(MediaPlayerState.Idle);
    }

    @Override
    public void seekTo(int positionMs) {
        currentPosition = positionMs;
        mediaPlayer.seekToPosition(positionMs);
        if (mediaPlayerState != MediaPlayerState.Paused) {
            start();
        }
    }

    @Override
    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public MediaPlayerState getState() {
        return mediaPlayerState;
    }

    @Override
    public void setState(MediaPlayerState state) {
        mediaPlayerState = state;
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    @Override
    public void setMediaPlayerListener(MediaPlayerListener listener) {
        this.listener = listener;
    }

    @Override
    public void onInitialized(Player player) {
        setState(MediaPlayerState.Initialized);
        if (listener != null) {
            listener.onPrepared();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (listener != null) {
            listener.onError();
        }
        setState(MediaPlayerState.Error);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        if (playerState != null) {
            currentPosition = playerState.positionInMs;
        }
        switch (eventType) {
            case PLAY:
            case TRACK_START:
                setState(MediaPlayerState.Started);
                if (listener != null) {
                    listener.onStart();
                }
                scheduleTimeUpdate();
                break;
            case LOST_PERMISSION:
            case PAUSE:
                setState(MediaPlayerState.Paused);
                if (listener != null) {
                    listener.onPause();
                }
                break;
            case TRACK_END:
            case TRACK_CHANGED:
                currentPosition = 0;
                setState(MediaPlayerState.Stopped);
                if (listener != null) {
                    listener.onCompletion();
                }
                break;
            case END_OF_CONTEXT:
            case SKIP_NEXT:
            case SKIP_PREV:
            case SHUFFLE_ENABLED:
            case SHUFFLE_DISABLED:
            case REPEAT_ENABLED:
            case REPEAT_DISABLED:
            case BECAME_ACTIVE:
            case BECAME_INACTIVE:
            case AUDIO_FLUSH:
            case EVENT_UNKNOWN:
                break;
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String s) {
        if (listener != null) {
            listener.onError();
        }
    }
}
