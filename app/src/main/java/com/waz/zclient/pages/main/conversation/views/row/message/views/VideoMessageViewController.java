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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.waz.api.Asset;
import com.waz.api.AssetStatus;
import com.waz.api.ImageAsset;
import com.waz.api.LoadHandle;
import com.waz.api.Message;
import com.waz.api.NetworkMode;
import com.waz.api.ProgressIndicator;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.media.PlayedVideoMessageEvent;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.AssetUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.GlyphProgressView;
import com.waz.zclient.ui.views.OnDoubleClickListener;
import timber.log.Timber;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class VideoMessageViewController extends MessageViewController implements ImageAsset.BitmapCallback,
                                                                                 AccentColorObserver {

    private static final String INFO_DIVIDER = " · ";
    private static final int DEFAULT_ASPECT_RATIO_WIDTH = 4;
    private static final int DEFAULT_ASPECT_RATIO_HEIGHT = 3;

    private View view;
    private ImageView previewImage;
    private GlyphProgressView actionButton;
    private final ProgressDotsView placeHolderDots;
    private TextView videoInfoText;
    private FrameLayout videoPreviewContainer;
    private EphemeralDotAnimationView ephemeralDotAnimationView;
    private View ephemeralTypeView;

    private Asset asset;

    private Drawable normalButtonBackground;
    private Drawable errorButtonBackground;
    private LoadHandle previewImageLoadHandle;

    private final ModelObserver<Message> messageObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            Timber.i("Message status %s", message.getMessageStatus());
            if (message.isEphemeral() && message.isExpired()) {
                refreshPreviewSize();
                messageExpired();
                return;
            }
            assetObserver.setAndUpdate(message.getAsset());
            refreshPreviewSize();
            updateVideoStatus();
        }
    };

    private final ModelObserver<Asset> assetObserver = new ModelObserver<Asset>() {
        @Override
        public void updated(Asset asset) {
            Timber.i("Asset %s status %s", asset.getName(), asset.getStatus());
            VideoMessageViewController.this.asset = asset;
            updateVideoStatus();
        }
    };

    private final ModelObserver<ProgressIndicator> progressIndicatorObserver = new ModelObserver<ProgressIndicator>() {
        @Override
        public void updated(ProgressIndicator progressIndicator) {
            Timber.i("ProgressIndicator state: %s, indefinite: %b", progressIndicator.getState(), progressIndicator.isIndefinite());
            switch (progressIndicator.getState()) {
                case CANCELLED:
                case FAILED:
                case COMPLETED:
                    actionButton.clearProgress();
                    break;
                case RUNNING:
                    if (progressIndicator.isIndefinite()) {
                        actionButton.startEndlessProgress();
                    } else {
                        float progress = progressIndicator.getTotalSize() == 0 ? 0 : (float) progressIndicator.getProgress() / (float) progressIndicator.getTotalSize();
                        actionButton.setProgress(progress);
                    }
                    break;
                case UNKNOWN:
                default:
                    break;

            }
        }
    };

    private final ModelObserver<ImageAsset> imageAssetModelObserver = new ModelObserver<ImageAsset>() {
        @Override
        public void updated(ImageAsset imageAsset) {
            previewImageLoadHandle = imageAsset.getBitmap(getThumbnailWidth(), VideoMessageViewController.this);
        }
    };

    private final OnDoubleClickListener imageOnDoubleClickListener = new OnDoubleClickListener() {
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

    private final View.OnClickListener actionButtinOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onActionClick();
        }
    };

    public VideoMessageViewController(Context context, MessageViewsContainer messageViewContainer) {
        super(context, messageViewContainer);
        view = View.inflate(context, R.layout.row_conversation_video_message, null);

        previewImage = ViewUtils.getView(view, R.id.biv__row_conversation__video_image);
        previewImage.setOnClickListener(imageOnDoubleClickListener);
        previewImage.setOnLongClickListener(this);
        actionButton = ViewUtils.getView(view, R.id.gpv__row_conversation__video_button);
        placeHolderDots = ViewUtils.getView(view, R.id.pdv__row_conversation__video_placeholder_dots);
        videoInfoText = ViewUtils.getView(view, R.id.ttv__row_conversation__video_info);
        videoPreviewContainer = ViewUtils.getView(view, R.id.fl__video_message_container);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);
        ephemeralTypeView = ViewUtils.getView(view, R.id.gtv__row_conversation__video__ephemeral_type);
        ephemeralTypeView.setVisibility(GONE);

        normalButtonBackground = context.getResources().getDrawable(R.drawable.selector__icon_button__background__video_message);
        errorButtonBackground = context.getResources().getDrawable(R.drawable.selector__icon_button__background__video_message__error);

        actionButton.setOnClickListener(actionButtinOnClickListener);
        actionButton.setProgressColor(messageViewsContainer.getControllerFactory().getAccentColorController().getColor());
        actionButton.setBackground(normalButtonBackground);
    }

    @Override
    protected void onSetMessage(Separator separator) {
        imageOnDoubleClickListener.reset();
        messageObserver.setAndUpdate(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        if (messageViewsContainer.getControllerFactory().getThemeController().isDarkTheme()) {
            videoInfoText.setTextColor(context.getResources().getColor(R.color.white));
        } else {
            videoInfoText.setTextColor(context.getResources().getColor(R.color.graphite));
        }
        ephemeralDotAnimationView.setMessage(message);
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        ephemeralDotAnimationView.setMessage(null);
        imageOnDoubleClickListener.reset();
        messageObserver.clear();
        assetObserver.clear();
        progressIndicatorObserver.clear();
        placeHolderDots.setExpired(false);
        imageAssetModelObserver.clear();
        actionButton.clearProgress();
        previewImage.setImageResource(R.drawable.shape_video_message_no_preview);
        previewImage.setVisibility(VISIBLE);
        videoInfoText.setText("");
        videoInfoText.setBackground(null);
        if (previewImageLoadHandle != null) {
            previewImageLoadHandle.cancel();
        }
        previewImageLoadHandle = null;
        super.recycle();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        actionButton.setProgressColor(color);
        ephemeralDotAnimationView.setPrimaryColor(color);
        ephemeralDotAnimationView.setSecondaryColor(ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__timer_alpha),
                                                                           color));
        placeHolderDots.setAccentColor(ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(context),
                                                              color));
    }

    private void refreshPreviewSize() {
        int displayWidth = getThumbnailWidth();
        int originalWidth = message.getImageWidth();
        int originalHeight = message.getImageHeight();
        boolean portrait = originalHeight > originalWidth;
        if (portrait) {
            displayWidth -= (2 * context.getResources().getDimensionPixelSize(R.dimen.content__padding_left));
        }
        updatePreviewImageSize(displayWidth, originalWidth, originalHeight);
    }

    private int getThumbnailWidth() {
        int displayWidth;
        if (view.getMeasuredWidth() > 0) {
            displayWidth = view.getMeasuredWidth();
        } else if (ViewUtils.isInPortrait(context)) {
            displayWidth = ViewUtils.getOrientationIndependentDisplayWidth(context);
        } else {
            displayWidth = ViewUtils.getOrientationIndependentDisplayHeight(context) - context.getResources().getDimensionPixelSize(R.dimen.framework__sidebar_width);
        }
        return displayWidth;
    }
    private int getScaledHeight(int originalWidth, int originalHeight, double finalWidth) {
        double scaleFactor = finalWidth / originalWidth;
        return (int) (originalHeight * scaleFactor);
    }

    private void updateVideoStatus() {
        switch (asset.getStatus()) {
            case UPLOAD_CANCELLED:
                updateViews(context.getString(R.string.glyph__close), normalButtonBackground, null);
                break;
            case UPLOAD_FAILED:
                String action;
                if (message.getMessageStatus() == Message.Status.SENT) {
                    // receiver
                    action = context.getString(R.string.glyph__play);
                } else {
                    // sender
                    action = context.getString(R.string.glyph__redo);
                }
                updateViews(action, errorButtonBackground, null);
                break;
            case UPLOAD_NOT_STARTED:
            case UPLOAD_IN_PROGRESS:
                if (message.getMessageStatus() == Message.Status.FAILED) {
                    updateViews(context.getString(R.string.glyph__redo), errorButtonBackground, null);
                }
                else if (message.getMessageStatus() == Message.Status.SENT) {
                    // receiver

                    placeHolderDots.setVisibility(VISIBLE);
                    actionButton.setVisibility(GONE);
                } else {
                    // sender
                    updateViews(context.getString(R.string.glyph__close), normalButtonBackground, asset.getUploadProgress());
                }
                break;
            case UPLOAD_DONE:
            case DOWNLOAD_DONE:
                imageAssetModelObserver.addAndUpdate(message.getImage());
                updateViews(context.getString(R.string.glyph__play), normalButtonBackground, null);
                break;
            case DOWNLOAD_FAILED:
                updateViews(context.getString(R.string.glyph__redo), errorButtonBackground, null);
                break;
            case DOWNLOAD_IN_PROGRESS:
                updateViews(context.getString(R.string.glyph__close), normalButtonBackground, asset.getDownloadProgress());
                break;
            default:
                break;
        }
    }

    private void updateViews(String action, Drawable background, ProgressIndicator progressIndicator) {
        placeHolderDots.setVisibility(GONE);
        actionButton.setVisibility(VISIBLE);
        actionButton.setText(action);
        actionButton.setBackground(background);
        if (progressIndicator == null) {
            actionButton.clearProgress();
            progressIndicatorObserver.clear();
        } else {
            progressIndicatorObserver.addAndUpdate(progressIndicator);
        }

        StringBuilder info = new StringBuilder(StringUtils.formatTimeSeconds(asset.getDuration().getSeconds()));
        long size = asset.getSizeInBytes();
        if (size > 0 && asset.getStatus() != AssetStatus.DOWNLOAD_DONE) {
            info.append(INFO_DIVIDER)
                .append(Formatter.formatFileSize(context, asset.getSizeInBytes()));
        }
        videoInfoText.setText(info.toString());
    }


    private void onActionClick() {
        switch (asset.getStatus()) {
            case UPLOAD_FAILED:
                if (message.getMessageStatus() != Message.Status.SENT) {
                    message.retry();
                }
                break;
            case UPLOAD_NOT_STARTED:
            case UPLOAD_IN_PROGRESS:
                if (message.getMessageStatus() == Message.Status.FAILED ||
                    message.getMessageStatus() == Message.Status.FAILED_READ) {
                    message.retry();
                } else if (message.getMessageStatus() != Message.Status.SENT) {
                    asset.getUploadProgress().cancel();
                }
                break;
            case UPLOAD_DONE:
                if (messageViewsContainer == null ||
                    messageViewsContainer.isTornDown()) {
                    return;
                }

                messageViewsContainer.getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
                    @Override
                    public void execute(NetworkMode networkMode) {
                        asset.getContentUri(new Asset.LoadCallback<Uri>() {
                            @Override
                            public void onLoaded(Uri uri) {
                                if (messageViewsContainer == null || messageViewsContainer.isTornDown()) {
                                    return;
                                }
                                if (message.isEphemeral()) {
                                    messageViewsContainer.getControllerFactory().getSingleImageController().showVideo(uri);
                                } else {
                                    messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(new PlayedVideoMessageEvent(
                                        (int) asset.getDuration().getSeconds(),
                                        !message.getUser().isMe(),
                                        messageViewsContainer.getStoreFactory().getConversationStore().getCurrentConversation()));
                                    final Intent intent = AssetUtils.getOpenFileIntent(uri, asset.getMimeType());
                                    context.startActivity(intent);
                                }
                            }

                            @Override
                            public void onLoadFailed() {}
                        });
                    }
                });
                break;
            case DOWNLOAD_DONE:
                asset.getContentUri(new Asset.LoadCallback<Uri>() {
                    @Override
                    public void onLoaded(Uri uri) {
                        if (messageViewsContainer == null || messageViewsContainer.isTornDown()) {
                            return;
                        }
                        if (message.isEphemeral()) {
                            messageViewsContainer.getControllerFactory().getSingleImageController().showVideo(uri);
                        } else {
                            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(new PlayedVideoMessageEvent(
                                (int) asset.getDuration().getSeconds(),
                                !message.getUser().isMe(),
                                messageViewsContainer.getStoreFactory().getConversationStore().getCurrentConversation()));
                            final Intent intent = AssetUtils.getOpenFileIntent(uri, asset.getMimeType());
                            context.startActivity(intent);
                        }
                    }

                    @Override
                    public void onLoadFailed() {}
                });
                break;
            case DOWNLOAD_FAILED:
                asset.getContentUri(null);
                break;
            case DOWNLOAD_IN_PROGRESS:
                asset.getDownloadProgress().cancel();
                break;
            default:
                break;
        }
    }
    private void updatePreviewImageSize(int displayWidth, int imageWidth, int imageHeight) {
        if (imageWidth == 0 || imageHeight == 0) {
            imageWidth = DEFAULT_ASPECT_RATIO_WIDTH;
            imageHeight = DEFAULT_ASPECT_RATIO_HEIGHT;
        }
        final int finalHeight = getScaledHeight(imageWidth, imageHeight, displayWidth);
        ViewGroup.LayoutParams layoutParams = previewImage.getLayoutParams();
        layoutParams.width = displayWidth;
        layoutParams.height = finalHeight;
        previewImage.setLayoutParams(layoutParams);
        layoutParams = videoPreviewContainer.getLayoutParams();
        layoutParams.width = displayWidth;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        videoPreviewContainer.setLayoutParams(layoutParams);
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, boolean isPreview) {
        if (bitmap == null) {
            return;
        }
        int displayWidth = getThumbnailWidth();
        if (bitmap.getHeight() > bitmap.getWidth()) {
            displayWidth -= (2 * context.getResources().getDimensionPixelSize(R.dimen.content__padding_left));
        }
        updatePreviewImageSize(displayWidth, bitmap.getWidth(), bitmap.getHeight());
        previewImage.setImageBitmap(bitmap);
        ViewUtils.fadeInView(previewImage);
        videoInfoText.setBackgroundResource(R.drawable.gradient_video_mesasge_info_background);
        videoInfoText.setTextColor(context.getResources().getColor(R.color.white));
    }

    @Override
    public void onBitmapLoadingFailed() {
        // noop
    }

    private void messageExpired() {
        assetObserver.clear();
        imageAssetModelObserver.clear();
        progressIndicatorObserver.clear();
        if (previewImageLoadHandle != null) {
            previewImageLoadHandle.cancel();
        }
        placeHolderDots.setExpired(true);
        placeHolderDots.setVisibility(VISIBLE);
        actionButton.setVisibility(GONE);
        previewImage.setVisibility(INVISIBLE);
        videoInfoText.setText("");
        ephemeralTypeView.setVisibility(VISIBLE);
    }

}
