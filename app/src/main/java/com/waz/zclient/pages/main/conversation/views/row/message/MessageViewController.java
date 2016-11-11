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
package com.waz.zclient.pages.main.conversation.views.row.message;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import com.waz.api.Asset;
import com.waz.api.Message;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.footer.FooterActionCallback;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;

import static com.waz.api.AssetStatus.META_DATA_SENT;
import static com.waz.api.AssetStatus.PREVIEW_SENT;
import static com.waz.api.AssetStatus.UPLOAD_IN_PROGRESS;
import static com.waz.api.AssetStatus.UPLOAD_NOT_STARTED;

public abstract class MessageViewController implements ConversationItemViewController,
                                                       View.OnLongClickListener {

    protected Context context;
    protected Message message;
    protected MessageViewsContainer messageViewsContainer;
    protected FooterActionCallback footerActionCallback;

    public MessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        this.context = context;
        this.messageViewsContainer = messageViewsContainer;
    }

    protected void afterInit() {}

    protected void onHeaderClick() {}

    @CallSuper
    protected void setFooterActionCallback(FooterActionCallback footerActionCallback) {
        this.footerActionCallback = footerActionCallback;
    }

    /**
     * Set the model to be displayed. The UI setup logic for each message type goes here.
     *
     * @param separator passed only to help determine the padding around the message,
     *                  as this varies depending on what precedes it.
     */
    abstract protected void onSetMessage(Separator separator);

    @CallSuper
    public void setMessage(@NonNull Message message, @NonNull Separator separator) {
        final Message oldMessage = this.message;
        updateMessageEditingStatus();
        if (oldMessage != null &&
            message.getId().equals(oldMessage.getId())) {
            return;
        }
        recycle();
        beforeSetMessage(oldMessage, message);
        this.message = message;
        onSetMessage(separator);
        updateMessageEditingStatus();
    }

    protected void updateMessageEditingStatus() {

    }

    protected void beforeSetMessage(@Nullable Message oldMessage, Message newMessage) {}

    /**
     * This will be called for you before {@link MessageViewController#onSetMessage(Separator)}
     */
    @Override
    @CallSuper
    public void recycle() {
        message = null;
        if (footerActionCallback != null) {
            footerActionCallback.recycle();
        }
    }

    public Message getMessage() {
        return message;
    }

    protected boolean receivingMessage(Asset asset) {
        return asset != null &&
               (asset.getStatus() == UPLOAD_NOT_STARTED ||
                asset.getStatus() == META_DATA_SENT ||
                asset.getStatus() == PREVIEW_SENT ||
                asset.getStatus() == UPLOAD_IN_PROGRESS) &&
               message.getMessageStatus() == Message.Status.SENT;
    }

    protected String getConversationTypeString() {
        return messageViewsContainer.getConversationType() != null ?
               messageViewsContainer.getConversationType().name() :
               "unspecified";
    }

    @Override
    @CallSuper
    public boolean onLongClick(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        return messageViewsContainer.onItemLongClick(message, this);
    }

    public void closeExtras(){

    }
}
