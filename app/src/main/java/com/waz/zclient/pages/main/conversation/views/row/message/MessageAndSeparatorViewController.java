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
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.LinearLayout;
import com.waz.api.Message;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.views.RecyclingLinearLayout;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.pages.main.conversation.views.row.separator.SeparatorViewController;

public class MessageAndSeparatorViewController implements ConversationItemViewController {

    private SeparatorViewController separatorViewController;
    private MessageViewController messageViewController;
    private Context context;

    public MessageAndSeparatorViewController(final MessageViewController messageViewController,
                                             MessageViewsContainer messageViewsContainer,
                                             Context context) {
        this.messageViewController = messageViewController;
        this.context = context;
        separatorViewController = new SeparatorViewController(this.context, messageViewsContainer);
        separatorViewController.setOnSeparatorClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                messageViewController.onHeaderClick();
            }
        });
    }

    public void setModel(@NonNull Message message, @NonNull Separator separator) {
        messageViewController.setMessage(message, separator);
        separatorViewController.setSeparator(separator);
    }

    public MessageViewController getMessageViewController() {
        return messageViewController;
    }

    @Override
    public View getView() {
        RecyclingLinearLayout separatorAndMessageView = new RecyclingLinearLayout(context);
        separatorAndMessageView.setOrientation(LinearLayout.VERTICAL);
        separatorAndMessageView.addView(separatorViewController.getView());
        separatorAndMessageView.addView(messageViewController.getView());
        separatorAndMessageView.setViewController(this);
        return separatorAndMessageView;
    }

    @Override
    public void recycle() {
        messageViewController.recycle();
        separatorViewController.recycle();
    }

    public Message getMessage() {
        return messageViewController.getMessage();
    }

}
