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
package com.waz.zclient.pages.main.conversation.views.row.message.views;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import com.waz.api.ImageAsset;
import com.waz.api.KindOfMedia;
import com.waz.api.LoadHandle;
import com.waz.api.MediaAsset;
import com.waz.api.Message;
import com.waz.api.NetworkMode;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.mediaplayer.DefaultMediaPlayer;
import com.waz.zclient.controllers.mediaplayer.MediaPlayerState;
import com.waz.zclient.controllers.streammediaplayer.IStreamMediaPlayerController;
import com.waz.zclient.controllers.streammediaplayer.StreamMediaPlayerObserver;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.core.stores.network.NetworkStoreObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.MessageUtils;
import com.waz.zclient.utils.ThreadUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.media.MediaPlayerView;

public abstract class MediaPlayerViewController extends MessageViewController implements MediaPlayerView.MediaPlayerListener,
                                                                                         StreamMediaPlayerObserver,
                                                                                         TextMessageLinkTextView.Callback,
                                                                                         NetworkStoreObserver,
                                                                                         AccentColorObserver {

    private View view;
    private TextMessageLinkTextView textMessageLinkTextView;
    protected MediaPlayerView mediaPlayerView;
    private EphemeralDotAnimationView ephemeralDotAnimationView;
    private View mediaPlayerContainerView;
    private boolean updateProgressEnabled;
    private LoadHandle loadHandle;
    private MediaAsset mediaAsset;
    private ImageAsset imageAsset;
    private final long refreshRate;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (message.isEphemeral() && message.isExpired()) {
                messageExpired();
                return;
            }
            final Message.Part mediaPart = MessageUtils.getFirstRichMediaPart(message);
            if (imageAsset != null) {
                imageAssetModelObserver.clear();
                imageAsset = null;
            }
            if (mediaPart == null) {
                showError();
                return;
            }
            mediaAsset = mediaPart.getMediaAsset();
            if (mediaAsset == null ||
                mediaAsset.isEmpty()) {
                showError();
                return;
            }
            imageAsset = mediaAsset.getArtwork();
            imageAssetModelObserver.setAndUpdate(imageAsset);
        }
    };

    private final ModelObserver<ImageAsset> imageAssetModelObserver = new ModelObserver<ImageAsset>() {
        @Override
        public void updated(ImageAsset model) {
            final int bitmapWidth = view.getMeasuredWidth() > 0 ?
                                    view.getMeasuredWidth() :
                                    ViewUtils.getOrientationIndependentDisplayWidth(context);
            loadHandle = imageAsset.getBitmap(bitmapWidth, new ImageAsset.BitmapCallback() {
                @Override
                public void onBitmapLoaded(Bitmap bitmap, boolean isPreview) {
                    if (isPreview ||
                        message == null) {
                        return;
                    }
                    updateMediaPlayerView(bitmap);
                }

                @Override
                public void onBitmapLoadingFailed() {
                    if (message == null) {
                        return;
                    }
                    showError();
                }
            });
        }
    };

    private final Runnable timeUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaAsset == null ||
                getPlayerController() == null) {
                return;
            }
            if (mediaPlayerView == null ||
                message == null ||
                !getPlayerController().isSelectedMessage(message) ||
                !getPlayerController().getMediaPlayerState(message).isTimeUpdateScheduleAllowed() ||
                mediaAsset.getDuration() == null) {
                return;
            }
            updateTime();
            mediaPlayerView.postDelayed(this, refreshRate);
        }
    };

    @SuppressLint("InflateParams")
    public MediaPlayerViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        refreshRate = context.getResources().getInteger(R.integer.mediaplayer__time_refresh_rate_ms);
        updateProgressEnabled = true;
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_media_player, null);
        textMessageLinkTextView = ViewUtils.getView(view, R.id.tmltv__row_conversation__message);
        textMessageLinkTextView.setMessageViewsContainer(messageViewsContainer);
        textMessageLinkTextView.setCallback(this);
        mediaPlayerView = ViewUtils.getView(view, R.id.mpv__row_conversation__message_media_player);
        mediaPlayerView.setOnLongClickListener(this);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);
        mediaPlayerContainerView = ViewUtils.getView(view, R.id.fl__row_conversation__media_player_container);
        resetMediaPlayerView(R.string.mediaplayer__artist__placeholder, getSource());
        afterInit();
    }

    @Override
    protected void onSetMessage(Separator separator) {
        textMessageLinkTextView.setMessage(message);
        messageViewsContainer.getStoreFactory().getNetworkStore().addNetworkStoreObserver(this);
        getPlayerController().addStreamMediaObserver(this);
        resetMediaPlayerView(R.string.mediaplayer__artist__placeholder, getSource());
        mediaPlayerView.setMediaPlayerListener(this);
        mediaPlayerView.setSourceImage(getSourceImage());
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        ephemeralDotAnimationView.setMessage(message);
        messageModelObserver.setAndUpdate(message);
    }

    @Override
    protected void updateMessageEditingStatus() {
        super.updateMessageEditingStatus();
        float opacity = messageViewsContainer.getControllerFactory().getConversationScreenController().isMessageBeingEdited(message) ?
                        ResourceUtils.getResourceFloat(context.getResources(), R.dimen.content__youtube__alpha_overlay) :
                        1f;
        textMessageLinkTextView.setAlpha(opacity);
    }

    @Override
    public void recycle() {
        messageModelObserver.clear();
        imageAssetModelObserver.clear();
        ephemeralDotAnimationView.setMessage(null);
        unscheduleTimeUpdate();
        textMessageLinkTextView.recycle();
        mediaPlayerView.setSeekBarEnabled(false);
        if (getPlayerController() != null) {
            getPlayerController().removeStreamMediaObserver(this);
            messageViewsContainer.getStoreFactory().getNetworkStore().removeNetworkStoreObserver(this);
        }
        resetMediaPlayerView(R.string.mediaplayer__artist__placeholder, getSource());
        if (loadHandle != null) {
            loadHandle.cancel();
            loadHandle = null;
        }
        imageAsset = null;
        mediaAsset = null;
        updateProgressEnabled = true;
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        mediaPlayerView.setVisibility(View.VISIBLE);
        mediaPlayerContainerView.setBackground(null);

        super.recycle();
    }

    @SuppressLint("ResourceAsColor")
    private void resetMediaPlayerView(@StringRes int artist, @StringRes int title) {
        mediaPlayerView.setMediaPlayerListener(null);
        mediaPlayerView.setTime(0, 100);
        mediaPlayerView.setSeekBarEnabled(false);
        mediaPlayerView.setImage(null);
        mediaPlayerView.setAllowControl(true);
        mediaPlayerView.updateControl(MediaPlayerState.Idle);
        mediaPlayerView.setControlVisibility(View.INVISIBLE);

        mediaPlayerView.setLoadingIndicatorEnabled(true);
        mediaPlayerView.setMusicIndicatorVisibility(View.GONE);
        mediaPlayerView.setCircleColor(R.color.mediaplayer__seekbar_placeholder_circle);
        mediaPlayerView.setCircleStrokeWidth(R.dimen.mediaplayer__seekbar_small_stroke_width);

        mediaPlayerView.setArtist(artist);
        mediaPlayerView.setTitle(title);
        mediaPlayerView.hideHint();
    }

    @Nullable
    public View getView() {
        return view;
    }

    private void updateTime() {
        if (!updateProgressEnabled) {
            return;
        }
        ThreadUtils.checkMain();
        int currentPosition = getPlayerController().getPosition(message);
        if (currentPosition == DefaultMediaPlayer.PLAYING_NOT_STARTED) {
            currentPosition = 0;
        }
        mediaPlayerView.setTime(currentPosition, getDuration());
    }

    public void scheduleTimeUpdate() {
        unscheduleTimeUpdate();
        mediaPlayerView.postDelayed(timeUpdateRunnable, refreshRate);
    }

    public void unscheduleTimeUpdate() {
        if (mediaPlayerView == null ||
            mediaPlayerView.getHandler() == null) {
            return;
        }
        mediaPlayerView.removeCallbacks(timeUpdateRunnable);
        updateTime();
    }

    ///////////////////////////////////////////////////////////////////
    //
    //  Implementation of MediaPlayerView.MediaPlayerListener
    //
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onSeekStart() {
        updateProgressEnabled = false;
    }

    @Override
    public void onSeekEnd(int positionMs) {
        updateProgressEnabled = true;
        getPlayerController().seekTo(message, positionMs);
    }

    @Override
    public void onControlClicked() {
        if (mediaAsset == null ||
            mediaAsset.getKind() != KindOfMedia.TRACK) {
            return;
        }
        final MediaPlayerState mediaPlayerState = getMediaPlayerState();
        if (mediaPlayerState == MediaPlayerState.Started) {
            pause();
        } else {
            play();
        }
    }

    @Override
    public void onOpenExternalClicked() {
        if (mediaAsset != null) {
            openExternal();
        } else {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(message.getBody())));
        }
    }

    ///////////////////////////////////////////////////////////////////
    //
    //  Implementation of StreamMediaPlayerManager().StreamMediaPlayerListener
    //
    ///////////////////////////////////////////////////////////////////

    @Override
    public void onPlay(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        scheduleTimeUpdate();
        mediaPlayerView.setAllowControl(true);
        mediaPlayerView.updateControl(MediaPlayerState.Started);
        if (getMediaPlayerState().isSeekToAllowed()) {
            mediaPlayerView.setSeekBarEnabled(isSeekingEnabled());
        }
    }

    @Override
    public void onPause(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        mediaPlayerView.updateControl(MediaPlayerState.Paused);
        unscheduleTimeUpdate();
    }

    @Override
    public void onStop(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        mediaPlayerView.updateControl(MediaPlayerState.Stopped);
        unscheduleTimeUpdate();
        mediaPlayerView.setSeekBarEnabled(false);
    }

    @Override
    public void onPrepared(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        mediaPlayerView.updateControl(MediaPlayerState.Prepared);
        mediaPlayerView.setSeekBarEnabled(isSeekingEnabled());
    }

    @Override
    public void onError(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        unscheduleTimeUpdate();
        messageViewsContainer.getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                showError();
            }
        });
    }

    @Override
    @CallSuper
    public void onComplete(Message m) {
        if (getPlayerController() == null ||
            !getPlayerController().isSelectedMessage(message)) {
            return;
        }
        mediaPlayerView.updateControl(MediaPlayerState.PlaybackCompleted);
        unscheduleTimeUpdate();
        mediaPlayerView.setTime(0, 100);
    }

    @Override
    public void onTrackChanged(Message newMessage) {
        if (getPlayerController() == null ||
            getPlayerController().isSelectedMessage(message)) {
            scheduleTimeUpdate();
            return;
        }
        unscheduleTimeUpdate();
        mediaPlayerView.updateControl(MediaPlayerState.Stopped);
        mediaPlayerView.setSeekBarEnabled(false);
    }

    ///////////////////////////////////////////////////////////////////
    //
    //  Implementation of IMediaProvider.OnInformationAvailableListener
    //
    ///////////////////////////////////////////////////////////////////

    @SuppressLint("ResourceAsColor")
    protected void updateMediaPlayerView(Bitmap bitmap) {
        if (getPlayerController() == null || mediaAsset == null) {
            return;
        }
        mediaPlayerView.setLoadingIndicatorEnabled(false);
        mediaPlayerView.setImage(bitmap);
        mediaPlayerView.setCircleColor(R.color.mediaplayer__seekbar_circle_color);
        mediaPlayerView.setCircleStrokeWidth(R.dimen.mediaplayer__seekbar__stroke_width);
        mediaPlayerView.setArtist(mediaAsset.getArtistName());
        mediaPlayerView.setTitle(mediaAsset.getTitle());

        final MediaPlayerState mediaPlayerState = getMediaPlayerState();
        mediaPlayerView.setAllowControl(true);
        mediaPlayerView.updateControl(mediaPlayerState);
        if (mediaAsset.getKind() != KindOfMedia.TRACK) {
            mediaPlayerView.setSeekBarEnabled(false);
            mediaPlayerView.setLoadingIndicatorEnabled(false);
            mediaPlayerView.setCircleStrokeWidth(R.dimen.mediaplayer__seekbar_small_stroke_width);
            mediaPlayerView.setCircleColor(R.color.mediaplayer__seekbar_placeholder_circle);
            mediaPlayerView.setAllowControl(false);
            mediaPlayerView.showHint(R.string.mediaplayer__open_external);
        }
        updateTime();
        if (mediaPlayerState == MediaPlayerState.Started) {
            scheduleTimeUpdate();
        }
        if (mediaPlayerState.isSeekToAllowed()) {
            mediaPlayerView.setSeekBarEnabled(isSeekingEnabled());
        }
    }

    private MediaPlayerState getMediaPlayerState() {
        return getPlayerController().getMediaPlayerState(message);
    }

    @Override
    public void onPlaceholderTap() {
        final MediaPlayerState mediaPlayerState = getMediaPlayerState();
        if (mediaPlayerState.needInitialization()) {
            // Error case
            resetMediaPlayerView(R.string.mediaplayer__artist__error, getSource());
            mediaPlayerView.setMediaPlayerListener(this);
            mediaPlayerView.setSourceImage(getSourceImage());
            messageModelObserver.forceUpdate();
        } else {
            onControlClicked();
        }
    }

    @Override
    public void onPlaceholderDoubleTap() {
        if (message.isEphemeral()) {
            return;
        } else if (message.isLikedByThisUser()) {
            message.unlike();
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.unlike(message.getConversation(),
                                                                                                                       message,
                                                                                                                       ReactedToMessageEvent.Method.DOUBLE_TAP));
        } else {
            message.like();
            messageViewsContainer.getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.LIKED_MESSAGE);
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.like(message.getConversation(),
                                                                                                                     message,
                                                                                                                     ReactedToMessageEvent.Method.DOUBLE_TAP));
        }
    }

    @SuppressLint("ResourceAsColor")
    protected void showError() {
        mediaPlayerView.setSeekBarEnabled(false);
        mediaPlayerView.setControlVisibility(View.INVISIBLE);
        mediaPlayerView.setLoadingIndicatorEnabled(false);
        mediaPlayerView.setMusicIndicatorVisibility(View.VISIBLE);
        mediaPlayerView.setTime(0, 100);
        mediaPlayerView.setImage(null);
        mediaPlayerView.setCircleStrokeWidth(R.dimen.mediaplayer__seekbar_small_stroke_width);
        mediaPlayerView.setCircleColor(R.color.mediaplayer__seekbar_placeholder_circle);
        mediaPlayerView.setTitle(getSource());
        mediaPlayerView.setArtist(R.string.mediaplayer__artist__error);
        mediaPlayerView.setAllowControl(false);
    }

    protected IStreamMediaPlayerController getPlayerController() {
        if (messageViewsContainer == null ||
            messageViewsContainer.isTornDown()) {
            return null;
        }
        return messageViewsContainer.getControllerFactory().getStreamMediaPlayerController();
    }

    ///////////////////////////////////////////////////////////////////
    //
    //  Implementation of NetworkStoreObserver
    //
    ///////////////////////////////////////////////////////////////////


    @Override
    public void onConnectivityChange(boolean hasInternet, boolean isServerError) {
        if (messageViewsContainer == null ||
            messageViewsContainer.isTornDown()) {
            return;
        }
        if (!hasInternet) {
            if (getPlayerController().isSelectedMessage(message)) {
                pause();
            }
        } else {
            messageModelObserver.forceUpdate();
        }
    }

    @Override
    public void onNoInternetConnection(boolean isServerError) { }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        if (mediaPlayerView == null) {
            return;
        }
        mediaPlayerView.setProgressColor(color);
        textMessageLinkTextView.onAccentColorHasChanged(sender, color);
    }

    protected void openExternal() {
        Intent intent = new Intent(Intent.ACTION_VIEW, mediaAsset.getLinkUri());
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    protected int getDuration() {
        return mediaAsset == null || mediaAsset.getDuration() == null ? 0 : (int) mediaAsset.getDuration().toMillis();
    }

    protected void play() {
        if (mediaPlayerView == null ||
            getPlayerController() == null) {
            return;
        }

        messageViewsContainer.getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                mediaPlayerView.setAllowControl(false);
                getPlayerController().play(message, mediaAsset);
            }
        });
    }

    protected void pause() {
        mediaPlayerView.updateControl(getPlayerController().getMediaPlayerState(message));
        getPlayerController().pause(message.getConversationId());
    }

    @Override
    public boolean onHintClicked() {
        if (mediaAsset.getKind() != KindOfMedia.TRACK) {
            onOpenExternalClicked();
            return true;
        }
        return false;
    }

    @Override
    public void onTextMessageLinkTextViewOnLongClicked(View view) {
        onLongClick(view);
    }

    @CallSuper
    protected void messageExpired() {
        if (loadHandle != null) {
            loadHandle.cancel();
        }
        imageAssetModelObserver.clear();
        mediaPlayerView.setVisibility(View.INVISIBLE);
        mediaPlayerContainerView.setBackgroundColor(ContextCompat.getColor(context, R.color.ephemera));
    }

    ///////////////////////////////////////////////////////////////////
    //
    //  Abstract methods
    //
    ///////////////////////////////////////////////////////////////////

    @StringRes protected abstract int getSource();

    @DrawableRes protected abstract int getSourceImage();

    protected abstract boolean isSeekingEnabled();
}
