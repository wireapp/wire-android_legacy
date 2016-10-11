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
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import com.waz.api.ImageAsset;
import com.waz.api.LoadHandle;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.utils.TypefaceUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.MessageUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.OnDoubleClickListener;
import com.waz.zclient.views.images.ImageAssetView;

public class LinkPreviewViewController extends MessageViewController implements ImageAssetView.BitmapLoadedCallback,
                                                                                TextMessageLinkTextView.Callback {

    private View view;
    private TextMessageLinkTextView textMessageLinkTextView;
    private View linkPrevieContainerView;
    private TextView titleTextView;
    private TextView urlTextView;
    private ImageAssetView previewImageAssetView;
    private View progressDotsView;
    private View previewImageContainerView;
    private EphemeralDotAnimationView ephemeralDotAnimationView;
    private LoadHandle previewImageLoadHandle;
    private final Typeface defaultTitleTypeface;
    private final Typeface defaultUrlTypeface;

    private final ModelObserver<Message> messageObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (messageBodyIsSingleLink()) {
                textMessageLinkTextView.setVisibility(View.GONE);
            } else {
                textMessageLinkTextView.setMessage(message);
                textMessageLinkTextView.setVisibility(View.VISIBLE);
            }

            Message.Part linkPart = MessageUtils.getFirstRichMediaPart(message);
            if (linkPart == null) {
                linkPrevieContainerView.setVisibility(View.GONE);
                return;
            } else {
                linkPrevieContainerView.setVisibility(View.VISIBLE);
            }
            titleTextView.setText(Html.fromHtml(linkPart.getTitle()));
            urlTextView.setText(StringUtils.normalizeUri(linkPart.getContentUri()).toString());
            if (linkPart.getImage() == null ||
                linkPart.getImage().isEmpty()) {
                return;
            }
            previewImageContainerView.setVisibility(View.VISIBLE);
            if (message.isEphemeral() && message.isExpired()) {
                messageExpired();
            } else {
                imageAssetModelObserver.addAndUpdate(linkPart.getImage());
            }
        }
    };

    private final ModelObserver<ImageAsset> imageAssetModelObserver = new ModelObserver<ImageAsset>() {
        @Override
        public void updated(ImageAsset imageAsset) {
            previewImageAssetView.setImageAsset(imageAsset);
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
            if (TextUtils.isEmpty(urlTextView.getText())) {
                return;
            }
            if (!message.isEphemeral() || message.isExpired()) {
                messageViewsContainer.onOpenUrl(urlTextView.getText().toString());
            }
            if (footerActionCallback != null) {
                footerActionCallback.toggleVisibility();
            }
        }
    };

    public LinkPreviewViewController(Context context, final MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        view = View.inflate(context, R.layout.row_conversation_link_preview, null);
        textMessageLinkTextView = ViewUtils.getView(view, R.id.cv__row_conversation__link_preview__text_message);
        linkPrevieContainerView = ViewUtils.getView(view, R.id.cv__row_conversation__link_preview__container);
        titleTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__link_preview__title);
        urlTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__link_preview__url);
        previewImageAssetView = ViewUtils.getView(view, R.id.iv__row_conversation__link_preview__image);
        progressDotsView = ViewUtils.getView(view, R.id.pdv__row_conversation__link_preview__placeholder_dots);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);
        previewImageContainerView = ViewUtils.getView(view, R.id.fl__row_conversation__link_preview__image_container);
        previewImageContainerView.setVisibility(View.GONE);
        previewImageAssetView.setBitmapLoadedCallback(this);

        textMessageLinkTextView.setOnClickListener(onDoubleClickListener);
        textMessageLinkTextView.setCallback(this);
        linkPrevieContainerView.setOnClickListener(onDoubleClickListener);
        linkPrevieContainerView.setOnLongClickListener(this);

        textMessageLinkTextView.setMessageViewsContainer(messageViewsContainer);

        defaultTitleTypeface = titleTextView.getTypeface();
        defaultUrlTypeface = urlTextView.getTypeface();
    }

    @Override
    protected void onSetMessage(Separator separator) {
        onDoubleClickListener.reset();
        messageObserver.setAndUpdate(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(textMessageLinkTextView);
        ephemeralDotAnimationView.setMessage(message);
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
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(textMessageLinkTextView);
        }
        ephemeralDotAnimationView.setMessage(null);
        onDoubleClickListener.reset();
        previewImageContainerView.setVisibility(View.GONE);
        progressDotsView.setVisibility(View.VISIBLE);
        messageObserver.clear();
        previewImageAssetView.clearImage();
        imageAssetModelObserver.clear();
        urlTextView.setText("");
        titleTextView.setText("");
        if (previewImageLoadHandle != null) {
            previewImageLoadHandle.cancel();
        }
        previewImageLoadHandle = null;
        textMessageLinkTextView.recycle();
        titleTextView.setTypeface(defaultTitleTypeface);
        urlTextView.setTypeface(defaultUrlTypeface);
        super.recycle();
    }

    private boolean messageBodyIsSingleLink() {
        int numberOfLinks = 0;
        for (int i = 0; i < message.getParts().length; i++) {
            if (message.getParts()[i].getPartType() == Message.Part.Type.WEB_LINK) {
                numberOfLinks++;
            }
        }
        return numberOfLinks == 1 && !message.getBody().trim().contains(" ");
    }

    @Override
    public void onBitmapLoadFinished(boolean bitmapLoaded) {
        previewImageContainerView.setVisibility(bitmapLoaded ? View.VISIBLE : View.GONE);
        if (bitmapLoaded) {
            progressDotsView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTextMessageLinkTextViewOnLongClicked(View view) {
        onLongClick(view);
    }

    private void messageExpired() {
        imageAssetModelObserver.clear();
        if (previewImageLoadHandle != null) {
            previewImageLoadHandle.cancel();
        }
        previewImageAssetView.clearImage();
        previewImageAssetView.setImageDrawable(new ColorDrawable(ContextCompat.getColor(context, R.color.ephemera)));
        Typeface redactedTypeface = TypefaceUtils.getTypeface(TypefaceUtils.getRedactedTypedaceName());
        titleTextView.setTypeface(redactedTypeface);
        urlTextView.setTypeface(redactedTypeface);
    }
}
