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
import com.waz.api.IConversation;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.views.UserDetailsView;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.common.views.ChatheadView;

public class ConnectRequestMessageViewController extends MessageViewController {

    // User with whom conversation was created
    private View view;
    private ChatheadView chatheadView;
    private UserDetailsView userDetailsView;
    private User otherUser;


    @SuppressLint("InflateParams")
    public ConnectRequestMessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_connect_request, null);
        chatheadView = ViewUtils.getView(view, R.id.cv__row_conversation__connect_request__chat_head);
        userDetailsView = ViewUtils.getView(view, R.id.udv__row_conversation__connect_request__user_details);
    }

    @Override
    public void recycle() {
        userDetailsView.recycle();
        otherUser = null;
        super.recycle();
    }

    @Override
    protected void onSetMessage(Separator separator) {
        otherUser = message.getConversation().getOtherParticipant();
        // TODO this crashes when the conversation is a group conversation
        if (message.getConversation().getType() == IConversation.Type.ONE_TO_ONE) {
            chatheadView.setUser(otherUser);
            userDetailsView.setUser(otherUser);
        }
    }

    @Override
    public View getView() {
        return view;
    }
}
