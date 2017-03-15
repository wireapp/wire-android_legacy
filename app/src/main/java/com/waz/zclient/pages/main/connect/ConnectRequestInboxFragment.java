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
package com.waz.zclient.pages.main.connect;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import com.waz.api.ConversationsList;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.stores.connect.InboxLinkConversation;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.InboxLoadRequester;
import com.waz.zclient.core.stores.conversation.OnInboxLoadedListener;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.views.ConnectRequestInboxListView;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;

public class ConnectRequestInboxFragment extends BaseFragment<ConnectRequestInboxFragment.Container> implements ConversationStoreObserver,
                                                                                                                OnInboxLoadedListener,
                                                                                                                AccentColorObserver,
                                                                                                                InAppNotificationStoreObserver {
    public static final String TAG = ConnectRequestInboxFragment.class.getName();
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    public static final String NO_ARGUMENT_PROVIDED_CONVERSATION_ID = "";

    private ConnectRequestInboxAdapter inboxAdapter;

    private String visibleConversationId = NO_ARGUMENT_PROVIDED_CONVERSATION_ID;
    private ConnectRequestInboxListView inboxListView;
    private Handler mainHandler;

    public static ConnectRequestInboxFragment newInstance(String conversationId) {
        ConnectRequestInboxFragment newFragment = new ConnectRequestInboxFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup viewContainer, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_connect_request_inbox, viewContainer, false);

        ConnectActionsCallback connectActionsCallback = new ConnectActionsCallback() {
            @Override
            public void onAccepted(IConversation conversation) {
                onAcceptedUser(conversation);
            }

            @Override
            public void onIgnored(User user) {
                onIgnoredUser(user);
            }
        };

        inboxAdapter = new ConnectRequestInboxAdapter(getActivity(), connectActionsCallback);
        inboxListView = ViewUtils.getView(rootView, R.id.crlv_connect_request_inbox__list);
        if (LayoutSpec.isTablet(getActivity())) {
            inboxListView.setStackFromBottom(true);
            inboxListView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        }
        inboxListView.setAdapter(inboxAdapter);

        if (savedInstanceState != null) {
            visibleConversationId = savedInstanceState.getString(ARGUMENT_CONVERSATION_ID);
        } else if (visibleConversationId.equals(NO_ARGUMENT_PROVIDED_CONVERSATION_ID)) {
            visibleConversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
        }

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        getStoreFactory().getInAppNotificationStore().addInAppNotificationObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARGUMENT_CONVERSATION_ID, visibleConversationId);
    }

    @Override
    public void onStop() {
        getStoreFactory().getInAppNotificationStore().removeInAppNotificationObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        inboxAdapter.reset();

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        inboxListView = null;
        super.onDestroyView();
    }

    public void setVisibleConnectRequest(Bundle arguments) {
        String newConversationId = arguments.getString(ARGUMENT_CONVERSATION_ID);

        if (newConversationId != null &&
            !newConversationId.equals(InboxLinkConversation.TAG)) {
            visibleConversationId = newConversationId;
            getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this, InboxLoadRequester.INBOX_SHOW_SPECIFIC);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Connect action callbacks
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void onIgnoredUser(User user) {
        if (inboxAdapter.getCount() <= 1) {
            getContainer().dismissInboxFragment();
        } else {
            visibleConversationId = getNextConversationId();
            getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this,
                                                                                          InboxLoadRequester.INBOX_DISMISS);
        }
    }

    private void onAcceptedUser(IConversation conversation) {
        if (inboxAdapter.getCount() <= 1) {
            getContainer().onAcceptedUser(conversation);
        } else {
            visibleConversationId = getNextConversationId();
            getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this,
                                                                                          InboxLoadRequester.INBOX_DISMISS);
        }

    }

    private String getNextConversationId() {
        int currentPosition = inboxAdapter.getMainConnectRequestPosition();
        int nextPosition;
        if (currentPosition == 0) {
            if (inboxAdapter.getCount() > 1) {
                nextPosition = currentPosition + 1;
            } else {
                nextPosition = 0;
            }
        } else if (currentPosition == inboxAdapter.getCount() - 1) {
            nextPosition = currentPosition - 1;
        } else {
            nextPosition = currentPosition + 1;
        }
        return inboxAdapter.getItem(nextPosition).getId();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {
        getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this, InboxLoadRequester.INBOX_LOAD);
    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {

    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnInboxLoadedListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnectRequestInboxConversationsLoaded(List<IConversation> conversations, InboxLoadRequester inboxLoadRequester) {
        if (conversations.size() == 0) {
            return;
        }

        // Reset inbox, show first item
        if (inboxLoadRequester == InboxLoadRequester.CONNECT_REQUEST_NOTIFICATION ||
            inboxLoadRequester == InboxLoadRequester.INBOX_LOAD) {
            visibleConversationId = NO_ARGUMENT_PROVIDED_CONVERSATION_ID;
            setInboxConversations(conversations, 0);
            return;
        }

        if (LayoutSpec.isTablet(getActivity())) {
            setInboxConversations(conversations, -1);
            return;
        }

        // Set main connect request if value is initiated for first time or a new id value was provided as an argument
        //  -> swiping back & forth between conversation list and inbox does not change currently displayed connect request
        int newVisiblePosition = 0;
        for (int i = 0; i < conversations.size(); i++) {
            IConversation conversation = conversations.get(i);
            if (conversation.getId().equals(visibleConversationId)) {
                newVisiblePosition = i;
                break;
            }
        }

        if (inboxAdapter.getMainConnectRequestPosition() != newVisiblePosition ||
            inboxLoadRequester == InboxLoadRequester.INBOX_DISMISS) {
            setInboxConversations(conversations, newVisiblePosition);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  IncomingMessages.MessageListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onIncomingMessage(Message message) {
        if (message.getMessageType() == Message.Type.CONNECT_REQUEST) {
            getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this, InboxLoadRequester.CONNECT_REQUEST_NOTIFICATION);
        }
    }

    @Override
    public void onIncomingKnock(KnockingEvent knock) {

    }

    @Override
    public void onSyncError(ErrorsList.ErrorDescription error) {

    }

    private void setInboxConversations(List<IConversation> conversations, final int visiblePosition) {
        boolean shouldScroll = visiblePosition >= 0;
        inboxListView.setStackFromBottom(conversations.size() == 1);
        inboxAdapter.setMainConnectRequestPosition(shouldScroll ? visiblePosition : conversations.size() - 1);
        inboxAdapter.setConnectRequests(conversations);
        inboxAdapter.notifyDataSetChanged();

        if (shouldScroll) {
            // Scroll to a particular connect request
            // Added a delay to ensure that scrolling is not ignored
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getView() != null) {
                        inboxListView.setSelection(visiblePosition);
                    }
                }
            }, 100);
        }
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        inboxAdapter.setAccentColor(color);
        inboxAdapter.notifyDataSetChanged();
    }

    public interface Container {

        void dismissInboxFragment();

        void onAcceptedUser(IConversation conversation);
    }
}
