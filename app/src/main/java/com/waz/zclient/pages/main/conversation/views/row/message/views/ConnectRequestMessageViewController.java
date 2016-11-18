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
import android.widget.TextView;
import com.waz.api.ContactDetails;
import com.waz.api.IConversation;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.common.views.ChatheadView;

public class ConnectRequestMessageViewController extends MessageViewController {

    // User with whom conversation was created
    private View view;
    private ChatheadView chatheadView;
    private TextView userNameTextView;
    private TextView userInfoTextView;
    private User otherUser;

    private final ModelObserver<ContactDetails> contactDetailsModelObserver = new ModelObserver<ContactDetails>() {
        @Override
        public void updated(ContactDetails contactDetails) {
            if (otherUser == null) {
                return;
            }
            String userInfo;
            if (otherUser.getDisplayName().equals(contactDetails.getDisplayName())) {
                // User's Wire name is same as in address book
                userInfo = view.getContext().getResources().getString(R.string.content__message__connect_request__user_info, "");
            } else {
                userInfo = view.getContext().getResources().getString(R.string.content__message__connect_request__user_info,
                                                                      contactDetails.getDisplayName());
            }
            userInfoTextView.setText(userInfo);
        }
    };

    private final ModelObserver<User> userModelObserver = new ModelObserver<User>() {
        @Override
        public void updated(User user) {
            if (context == null ||
                messageViewsContainer == null) {
                return;
            }
            userNameTextView.setText(StringUtils.formatUsername(user.getUsername()));
            if (!user.isContact()) {
                userInfoTextView.setText("");
                contactDetailsModelObserver.pauseListening();
            } else {
                contactDetailsModelObserver.setAndUpdate(user.getFirstContact());
            }
        }
    };


    @SuppressLint("InflateParams")
    public ConnectRequestMessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_connect_request, null);
        chatheadView = ViewUtils.getView(view, R.id.cv__row_conversation__connect_request__chat_head);
        userNameTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__connect_request__username);
        userInfoTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__connect_request__user_info);
    }

    @Override
    public void recycle() {
        userModelObserver.pauseListening();
        contactDetailsModelObserver.pauseListening();
        otherUser = null;
        super.recycle();
    }

    @Override
    protected void onSetMessage(Separator separator) {
        otherUser = message.getConversation().getOtherParticipant();
        // TODO this crashes when the conversation is a group conversation
        if (message.getConversation().getType() == IConversation.Type.ONE_TO_ONE) {
            chatheadView.setUser(otherUser);
        }

        // TODO this crashes when the conversation is a group conversation
        if (message.getConversation().getType() == IConversation.Type.ONE_TO_ONE) {
            userModelObserver.setAndUpdate(otherUser);
        }
    }

    @Override
    public View getView() {
        return view;
    }
}
