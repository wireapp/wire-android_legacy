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

import android.content.Context;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.waz.api.Asset;
import com.waz.api.AssetStatus;
import com.waz.api.Message;
import com.waz.api.NetworkMode;
import com.waz.api.PlaybackControls;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.media.PlayedAudioMessageEvent;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.AssetActionButton;
import com.waz.zclient.views.OnDoubleClickListener;
import org.threeten.bp.Duration;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class AudioMessageViewController extends MessageViewController implements AccentColorObserver {

    private View view;
    private AssetActionButton actionButton;
    private ProgressDotsView progressDotsView;
    private TextView audioDurationText;
    private SeekBar audioSeekBar;
    private LinearLayout selectionContainer;
    private EphemeralDotAnimationView ephemeralDotAnimationView;

    private Asset asset;
    private PlaybackControls playbackControls;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (message.isEphemeral() && message.isExpired()) {
                messageExpired();
            } else if (asset == null) {
                asset = message.getAsset();
                actionButton.setOnClickListener(actionButtonOnClickListener);
                assetModelObserver.setAndUpdate(asset);
            }
        }
    };

    private final ModelObserver<Asset> assetModelObserver = new ModelObserver<Asset>() {
        @Override
        public void updated(Asset model) {
            audioDurationText.setText(StringUtils.formatTimeSeconds(asset.getDuration().getSeconds()));
            setProgressDotsVisible(receivingMessage(model));
            if (playbackControls == null && asset.getStatus() == AssetStatus.DOWNLOAD_DONE) {
                setPlaybackControls(false);
            }
        }
    };

    private final ModelObserver<PlaybackControls> playbackControlsModelObserver = new ModelObserver<PlaybackControls>() {
        @Override
        public void updated(PlaybackControls playbackControls) {
            actionButton.setPlaybackControls(playbackControls);
            String time;
            if (playbackControls.getDuration().equals(playbackControls.getPlayhead()) || playbackControls.getPlayhead().isZero()) {
                time = StringUtils.formatTimeSeconds(playbackControls.getDuration().getSeconds());
            } else {
                time = StringUtils.formatTimeSeconds(playbackControls.getPlayhead().getSeconds());
            }
            audioDurationText.setText(time);

            audioSeekBar.setEnabled(true);
            audioSeekBar.setMax((int) playbackControls.getDuration().toMillis());
            audioSeekBar.setProgress((int) playbackControls.getPlayhead().toMillis());
        }
    };

    private final OnDoubleClickListener containerOnClickListener = new OnDoubleClickListener() {
        @Override
        public void onDoubleClick() {
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

        @Override
        public void onSingleClick() {
            if (footerActionCallback != null) {
                footerActionCallback.toggleVisibility();
            }
        }
    };

    private final View.OnClickListener actionButtonOnClickListener = new OnDoubleClickListener()  {
        @Override
        public void onDoubleClick() {
            if (message.isEphemeral()) {
                return;
            } else if (message.isLikedByThisUser()) {
                message.unlike();
            } else {
                messageViewsContainer.getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.LIKED_MESSAGE);
                message.like();
            }
        }

        @Override
        public void onSingleClick() {
            onActionClick();
        }
    };

    public AudioMessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        view = View.inflate(context, R.layout.row_conversation_audio_message, null);
        actionButton = ViewUtils.getView(view, R.id.aab__row_conversation__audio_button);
        progressDotsView = ViewUtils.getView(view, R.id.pdv__row_conversation__audio_placeholder_dots);
        audioDurationText = ViewUtils.getView(view, R.id.ttv__row_conversation__audio_duration);
        audioSeekBar = ViewUtils.getView(view, R.id.sb__audio_progress);
        selectionContainer = ViewUtils.getView(view, R.id.tfll__audio_message_container);
        selectionContainer.setOnLongClickListener(this);
        selectionContainer.setOnClickListener(containerOnClickListener);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);

        actionButton.setProgressColor(messageViewsContainer.getControllerFactory().getAccentColorController().getColor());

        audioSeekBar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (playbackControls == null) {
                    return true;
                }
                return false;
            }
        });
        audioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && playbackControls != null) {
                    playbackControls.setPlayhead(Duration.ofMillis(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    @Override
    protected void onSetMessage(Separator separator) {
        containerOnClickListener.reset();
        messageModelObserver.setAndUpdate(message);
        actionButton.setMessage(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        ephemeralDotAnimationView.setMessage(message);
    }

    @Override
    public void recycle() {
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        ephemeralDotAnimationView.setMessage(null);
        containerOnClickListener.reset();
        messageModelObserver.clear();
        playbackControlsModelObserver.clear();
        assetModelObserver.clear();
        playbackControls = null;
        asset = null;
        actionButton.setOnClickListener(null);
        actionButton.clearProgress();
        audioDurationText.setText(StringUtils.formatTimeSeconds(0));
        audioSeekBar.setEnabled(false);
        progressDotsView.setExpired(false);
        super.recycle();
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        actionButton.setProgressColor(color);
        updateSeekbarColor(color);
    }

    private void setProgressDotsVisible(boolean isVisible) {
        if (isVisible) {
            progressDotsView.setVisibility(VISIBLE);
            selectionContainer.setVisibility(GONE);
        } else {
            progressDotsView.setVisibility(GONE);
            selectionContainer.setVisibility(VISIBLE);
        }
    }

    private void updateSeekbarColor(int color) {
        Drawable drawable = audioSeekBar.getProgressDrawable();
        if (drawable == null) {
            return;
        }
        if (drawable instanceof LayerDrawable) {
            LayerDrawable layerDrawable = (LayerDrawable) drawable;
            Drawable progress = layerDrawable.findDrawableByLayerId(android.R.id.progress);
            if (progress != null) {
                drawable = progress;
            }
        }
        drawable.setColorFilter(new LightingColorFilter(0xFF000000, color));
        drawable = audioSeekBar.getThumb();
        drawable.setColorFilter(new LightingColorFilter(0xFF000000, color));
    }

    public void onActionClick() {
        if (messageViewsContainer == null ||
            messageViewsContainer.isTornDown()) {
            return;
        }
        switch (asset.getStatus()) {
            case UPLOAD_FAILED:
                if (message.getMessageStatus() != Message.Status.SENT) {
                    message.retry();
                }
                break;
            case UPLOAD_NOT_STARTED:
            case META_DATA_SENT:
            case PREVIEW_SENT:
            case UPLOAD_IN_PROGRESS:
                if (message.getMessageStatus() != Message.Status.SENT) {
                    asset.getUploadProgress().cancel();
                }
                break;
            case UPLOAD_DONE:
                messageViewsContainer.getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
                    @Override
                    public void execute(NetworkMode networkMode) {
                        setPlaybackControls(true);
                    }
                });
                break;
            case DOWNLOAD_DONE:
                if (playbackControls == null) {
                    setPlaybackControls(true);
                } else {
                    playOrPause();
                }
                break;
            case DOWNLOAD_FAILED:
                setPlaybackControls(true);
                break;
            case DOWNLOAD_IN_PROGRESS:
                asset.getDownloadProgress().cancel();
                break;
            default:
                break;
        }
    }

    private void setPlaybackControls(final boolean autoPlay) {
        asset.getPlaybackControls(new Asset.LoadCallback<PlaybackControls>() {
            @Override
            public void onLoaded(PlaybackControls playbackControls) {
                if (messageViewsContainer == null || messageViewsContainer.isTornDown()) {
                    return;
                }
                playbackControlsModelObserver.setAndUpdate(playbackControls);
                AudioMessageViewController.this.playbackControls = playbackControls;
                if (autoPlay) {
                    playOrPause();
                }
            }

            @Override
            public void onLoadFailed() {
                Toast.makeText(context, R.string.audio_message_error__generic, Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void playOrPause() {
        if (playbackControls == null) {
            return;
        }
        if (playbackControls.isPlaying()) {
            playbackControls.stop();
        } else {
            playbackControls.play();
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(new PlayedAudioMessageEvent(
                asset.getMimeType(),
                (int) asset.getDuration().getSeconds(),
                !message.getUser().isMe(),
                messageViewsContainer.getStoreFactory().getConversationStore().getCurrentConversation()));
        }
    }

    private void messageExpired() {
        assetModelObserver.clear();
        setProgressDotsVisible(true);
        progressDotsView.setExpired(true);
    }

}
