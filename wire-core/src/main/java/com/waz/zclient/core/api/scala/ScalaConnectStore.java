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
package com.waz.zclient.core.api.scala;

import android.content.Context;
import com.waz.api.CommonConnections;
import com.waz.api.ErrorResponse;
import com.waz.api.IConversation;
import com.waz.api.InvitationTokenFactory;
import com.waz.api.Invitations;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.core.R;
import com.waz.zclient.core.stores.connect.ConnectStore;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScalaConnectStore extends ConnectStore {

    private ZMessagingApi zMessagingApi;
    private Context context;
    private Invitations invites;
    private CommonConnections commonConnections;

    Set<Invitations.ConnectionCallback> connectionCallbacks;
    Map<UserRequester, User> users;

    public ScalaConnectStore(Context context, ZMessagingApi zMessagingApi) {
        this.zMessagingApi = zMessagingApi;
        this.context = context;
        connectionCallbacks = new HashSet<>();
        invites = zMessagingApi.getInvitations();
        users = new HashMap<>();
    }

    @Override
    public void tearDown() {
        removeUserListener();
        removeCommonConnectionsListener();

        commonConnections = null;
        users = null;
        invites = null;
        context = null;
        connectionCallbacks = null;
    }

    @Override
    public void addConnectRequestObserver(ConnectStoreObserver connectStoreObserver) {
        super.addConnectRequestObserver(connectStoreObserver);
        for (UserRequester userRequester : users.keySet()) {
            notifyConnectUserUpdated(users.get(userRequester), userRequester);
        }
    }

    @Override
    public void loadUser(String userId, UserRequester userRequester) {
        removeUserListener();
        User user = zMessagingApi.getUser(userId);
        users.put(userRequester, user);

        switch (userRequester) {
            case SEARCH:
                user.addUpdateListener(searchUserListener);
                searchUserListener.updated();
                break;
            case CONVERSATION:
                user.addUpdateListener(conversationUserListener);
                conversationUserListener.updated();
                break;
            case PARTICIPANTS:
                user.addUpdateListener(participantsUserListener);
                participantsUserListener.updated();
                break;
            case POPOVER:
                user.addUpdateListener(popoverUserListener);
                popoverUserListener.updated();
                break;
        }
    }

    @Override
    public void loadCommonConnections(CommonConnections commonConnections) {
        removeCommonConnectionsListener();
        this.commonConnections = commonConnections;
        commonConnections.addUpdateListener(commonConnectionsListener);
        commonConnectionsListener.updated();
    }

    @Override
    public IConversation connectToNewUser(User user, String firstMessage) {
        return user.connect(firstMessage);
    }

    @Override
    public void requestInviteUri(Invitations.InvitationUriCallback callback) {
        invites.generateInvitationUri(callback);
    }

    @Override
    public void requestConnection(String token) {
        Invitations.GenericToken inviteToken = InvitationTokenFactory.genericTokenFromCode(token);
        String myName = zMessagingApi.getSelf().getName();
        String message = context.getString(R.string.people_picker__invite__message, myName);
        Invitations.ConnectionCallback callback = new Invitations.ConnectionCallback() {
            @Override
            public void onConnectionRequested(IConversation iConversation) {
                if (connectionCallbacks == null) {
                    return;
                }
                connectionCallbacks.remove(this);
                notifyInviteRequestSent(iConversation);
            }

            @Override
            public void onRequestFailed(ErrorResponse errorResponse) {
                if (connectionCallbacks == null) {
                    return;
                }
                connectionCallbacks.remove(this);
            }
        };
        connectionCallbacks.add(callback);
        invites.requestConnection(inviteToken, message, callback);
    }

    private void removeUserListener() {
        User searchUser = users.get(UserRequester.SEARCH);
        if (searchUser != null) {
            searchUser.removeUpdateListener(searchUserListener);
        }

        User conversationUser = users.get(UserRequester.CONVERSATION);
        if (conversationUser != null) {
            conversationUser.removeUpdateListener(conversationUserListener);
        }

        User participantsUser = users.get(UserRequester.PARTICIPANTS);
        if (participantsUser != null) {
            participantsUser.removeUpdateListener(participantsUserListener);
        }

        User popoverUser = users.get(UserRequester.POPOVER);
        if (popoverUser != null) {
            popoverUser.removeUpdateListener(popoverUserListener);
        }
    }

    private void removeCommonConnectionsListener() {
        if (commonConnections != null) {
            commonConnections.removeUpdateListener(commonConnectionsListener);
        }
    }

    private UpdateListener searchUserListener = new UpdateListener() {
        @Override
        public void updated() {
            if (users.get(UserRequester.SEARCH) != null) {
                User searchUser = users.get(UserRequester.SEARCH);
                notifyConnectUserUpdated(searchUser, UserRequester.SEARCH);
            }
        }
    };

    private UpdateListener participantsUserListener = new UpdateListener() {
        @Override
        public void updated() {
            if (users.get(UserRequester.PARTICIPANTS) != null) {
                User participantsUser = users.get(UserRequester.PARTICIPANTS);
                notifyConnectUserUpdated(participantsUser, UserRequester.PARTICIPANTS);
            }
        }
    };
    private UpdateListener popoverUserListener = new UpdateListener() {
        @Override
        public void updated() {
            if (users.get(UserRequester.POPOVER) != null) {
                User participantsUser = users.get(UserRequester.POPOVER);
                notifyConnectUserUpdated(participantsUser, UserRequester.POPOVER);
            }
        }
    };

    private UpdateListener conversationUserListener = new UpdateListener() {
        @Override
        public void updated() {
            if (users.get(UserRequester.CONVERSATION) != null) {
                User conversationUser = users.get(UserRequester.CONVERSATION);
                notifyConnectUserUpdated(conversationUser, UserRequester.CONVERSATION);
            }
        }
    };


    private final UpdateListener commonConnectionsListener = new UpdateListener() {
        @Override
        public void updated() {
            if (commonConnections != null) {
                notifyCommonConnectionsUpdated(commonConnections);
            }
        }
    };
}
