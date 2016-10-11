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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import com.waz.api.ImageAsset;
import com.waz.api.LoadHandle;
import com.waz.api.MediaAsset;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.controllers.tracking.events.media.PlayedYouTubeMessageEvent;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.MessageUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.OnDoubleClickListener;

import java.util.List;

public class YouTubeMessageViewController extends MessageViewController implements View.OnClickListener,
                                                                                   ImageAsset.BitmapCallback,
                                                                                   TextMessageLinkTextView.Callback {

    private View view;
    private ImageView imageView;
    private TextMessageLinkTextView textMessageLinkTextView;
    private View headerContainerView;
    private GlyphTextView glyphTextView;
    private TypefaceTextView errorTextView;
    private TypefaceTextView titleTextView;
    private EphemeralDotAnimationView ephemeralDotAnimationView;

    private LoadHandle loadHandle;
    private ImageAsset imageAsset;
    private MediaAsset mediaAsset;
    private final float alphaOverlay;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (imageAsset != null) {
                imageAssetModelObserver.clear();
                imageAsset = null;
            }
            if (message.isEphemeral() && message.isExpired()) {
                messageExpired();
                return;
            }
            final Message.Part mediaPart = MessageUtils.getFirstRichMediaPart(message);
            if (mediaPart == null) {
                showError();
                return;
            }
            mediaAsset = mediaPart.getMediaAsset();
            if (mediaAsset == null || mediaAsset.isEmpty()) {
                showError();
                return;
            }
            titleTextView.setText(mediaAsset.getTitle());
            imageAsset = mediaAsset.getArtwork();
            imageAssetModelObserver.setAndUpdate(imageAsset);
        }
    };

    private final ModelObserver<ImageAsset> imageAssetModelObserver = new ModelObserver<ImageAsset>() {
        @Override
        public void updated(ImageAsset imageAsset) {
            if (loadHandle != null) {
                loadHandle.cancel();
            }
            loadHandle = imageAsset.getBitmap(getThumbnailWidth(), YouTubeMessageViewController.this);
        }
    };

    private final OnDoubleClickListener onDoubleClickListener = new OnDoubleClickListener() {
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

    @SuppressLint("InflateParams")
    public YouTubeMessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_youtube, null);
        textMessageLinkTextView = ViewUtils.getView(view, R.id.tmltv__row_conversation__message);
        textMessageLinkTextView.setMessageViewsContainer(messageViewsContainer);
        textMessageLinkTextView.setOnClickListener(onDoubleClickListener);
        textMessageLinkTextView.setCallback(this);
        imageView = ViewUtils.getView(view, R.id.iv__row_conversation__youtube_image);
        imageView.setOnClickListener(onDoubleClickListener);
        imageView.setOnLongClickListener(this);
        errorTextView = ViewUtils.getView(view, R.id.ttv__youtube_message__error);
        glyphTextView = ViewUtils.getView(view, R.id.gtv__youtube_message__play);
        glyphTextView.setOnClickListener(this);
        titleTextView = ViewUtils.getView(view, R.id.ttv__youtube_message__title);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);
        headerContainerView = ViewUtils.getView(view, R.id.ll_youtube_message__header_container);

        alphaOverlay = ResourceUtils.getResourceFloat(context.getResources(), R.dimen.content__youtube__alpha_overlay);
        imageView.getLayoutParams().height = (int) ((double) ViewUtils.getOrientationIndependentDisplayWidth(context) * 9 / 16);
        imageView.setAlpha(0f);

        afterInit();
    }

    @Override
    protected void onSetMessage(Separator separator) {
        onDoubleClickListener.reset();
        textMessageLinkTextView.setMessage(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(textMessageLinkTextView);
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
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        messageModelObserver.clear();
        imageAssetModelObserver.clear();
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(textMessageLinkTextView);
        }
        ephemeralDotAnimationView.setMessage(null);
        onDoubleClickListener.reset();
        if (loadHandle != null) {
            loadHandle.cancel();
        }
        imageAsset = null;
        mediaAsset = null;
        textMessageLinkTextView.recycle();
        imageView.setImageDrawable(null);
        glyphTextView.setText(context.getString(R.string.glyph__play));
        glyphTextView.setVisibility(View.VISIBLE);
        errorTextView.setVisibility(View.GONE);
        imageView.setAlpha(0f);
        imageView.setColorFilter(null);
        titleTextView.setText("");
        headerContainerView.setVisibility(View.VISIBLE);
        super.recycle();
    }

    @Override
    public void onClick(View v) {
        if (mediaAsset == null ||
            messageViewsContainer == null) {
            return;
        }
        mediaAsset.prepareStreaming(new MediaAsset.StreamingCallback() {
            @Override
            public void onSuccess(List<Uri> uris) {
                if (messageViewsContainer == null) {
                    return;
                }
                if (uris.size() == 1) {
                    messageViewsContainer.onOpenUrl(uris.get(0).toString());
                } else {
                    messageViewsContainer.onOpenUrl(mediaAsset.getLinkUri().toString());
                }
            }

            @Override
            public void onFailure(int code, String message, String label) {
                showError();
            }
        });
        messageViewsContainer.getControllerFactory()
                             .getTrackingController()
                             .tagEvent(new PlayedYouTubeMessageEvent(!message.getUser().isMe(),
                                                                     messageViewsContainer.getStoreFactory().getConversationStore().getCurrentConversation()));
        messageViewsContainer.getControllerFactory()
                             .getTrackingController()
                             .updateSessionAggregates(RangedAttribute.YOUTUBE_CONTENT_CLICKS);
    }

    private void showError() {
        titleTextView.setText("");
        if (messageViewsContainer.getStoreFactory().getNetworkStore().hasInternetConnection()) {
            errorTextView.setVisibility(View.VISIBLE);
        }
        glyphTextView.setText(context.getString(R.string.glyph__movie));
        glyphTextView.setTextColor(context.getResources().getColor(R.color.content__youtube__error_indicator__color));
    }

    @Override
    public void onBitmapLoaded(final Bitmap bitmap, boolean isPreview) {
        if (bitmap == null ||
            imageView.getDrawable() != null ||
            isPreview) {
            return;
        }
        errorTextView.setVisibility(View.GONE);
        imageView.setImageBitmap(bitmap);
        imageView.setColorFilter(ColorUtils.injectAlpha(alphaOverlay, Color.BLACK), PorterDuff.Mode.DARKEN);
        ViewUtils.fadeInView(imageView);
    }

    @Override
    public void onBitmapLoadingFailed() {
        showError();
    }

    private int getThumbnailWidth() {
        final int bitmapWidth;
        if (view.getMeasuredWidth() > 0) {
            bitmapWidth = view.getMeasuredWidth();
        } else {
            bitmapWidth = ViewUtils.getOrientationIndependentDisplayWidth(context);
        }
        return bitmapWidth;
    }

    @Override
    public void onTextMessageLinkTextViewOnLongClicked(View view) {
        onLongClick(view);
    }

    private void messageExpired() {
        imageAssetModelObserver.clear();
        if (loadHandle != null) {
            loadHandle.cancel();
        }
        imageView.setImageDrawable(new ColorDrawable(ContextCompat.getColor(context, R.color.ephemera)));
        imageView.setVisibility(View.VISIBLE);
        imageView.setAlpha(1f);
        imageView.clearColorFilter();
        glyphTextView.setVisibility(View.GONE);
        errorTextView.setVisibility(View.GONE);
        headerContainerView.setVisibility(View.GONE);
    }

}
