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
import android.view.LayoutInflater;
import android.view.View;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.ui.views.OnDoubleClickListener;

public class TextMessageViewController extends MessageViewController implements TextMessageLinkTextView.Callback,
                                                                                AccentColorObserver {

    private View view;
    private TextMessageLinkTextView textView;
    private EphemeralDotAnimationView ephemeralDotAnimationView;

    private final View.OnClickListener onClickListener = new OnDoubleClickListener() {
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

    private final View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            messageViewsContainer.onItemLongClick(message, TextMessageViewController.this);
            return true;
        }
    };

    @SuppressLint("InflateParams")
    public TextMessageViewController(Context context, final MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_text_message, null);
        textView = ViewUtils.getView(view, R.id.tmltv__row_conversation__message);
        textView.setMessageViewsContainer(messageViewsContainer);
        View textContainer = ViewUtils.getView(view, R.id.ll__row_conversation__message_container);
        textContainer.setOnClickListener(onClickListener);
        textContainer.setOnLongClickListener(onLongClickListener);
        textView.setOnClickListener(onClickListener);
        textView.setCallback(this);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);

        afterInit();
    }

    @Override
    public void onSetMessage(Separator separator) {
        textView.setMessage(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(textView);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        onAccentColorHasChanged(this, messageViewsContainer.getControllerFactory().getAccentColorController().getColor());
        ephemeralDotAnimationView.setMessage(message);
    }

    @Override
    protected void updateMessageEditingStatus() {
        super.updateMessageEditingStatus();
        float opacity = messageViewsContainer.getControllerFactory().getConversationScreenController().isMessageBeingEdited(message) ?
                        ResourceUtils.getResourceFloat(context.getResources(), R.dimen.content__youtube__alpha_overlay) :
                        1f;
        textView.setAlpha(opacity);
    }

    @Override
    protected void onHeaderClick() {
        if (message.getMessageType() == Message.Type.RECALLED && footerActionCallback != null) {
            footerActionCallback.toggleVisibility();
        }
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(textView);
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        textView.recycle();
        ephemeralDotAnimationView.setMessage(null);
        super.recycle();
    }

    @Override
    public void onTextMessageLinkTextViewOnLongClicked(View view) {
        onLongClick(view);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        ephemeralDotAnimationView.setPrimaryColor(color);
        ephemeralDotAnimationView.setSecondaryColor(ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__timer_alpha),
                                                                           color));
        if (message != null &&
            message.isEphemeral() &&
            message.isExpired()) {
            textView.setTextColor(color);
        }
    }
}
