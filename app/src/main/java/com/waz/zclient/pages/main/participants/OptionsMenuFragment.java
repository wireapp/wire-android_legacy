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
package com.waz.zclient.pages.main.participants;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.ThemeController;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.singleparticipants.SingleParticipantStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.optionsmenu.OptionsMenu;
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class OptionsMenuFragment extends BaseFragment<OptionsMenuFragment.Container> implements SingleParticipantStoreObserver,
                                                                                                ConversationStoreObserver,
                                                                                                OptionsMenuControl.Callback,
                                                                                                OptionsMenu.Callback {
    public static final String TAG = OptionsMenuFragment.class.getName();
    private static final String ARGUMENT_IN_LIST = "ARGUMENT_IN_LIST ";
    private static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    private static final String ARGUMENT_WIRE_THEME = "ARGUMENT_WIRE_THEME";
    private OptionsMenu optionsMenu;
    private @IConversationScreenController.ConversationMenuRequester int requester;
    private IConversation conversation;
    private User user;

    private boolean inConversationList;
    private OptionsTheme optionsTheme;

    public static OptionsMenuFragment newInstance(boolean inConversationList) {
        OptionsMenuFragment fragment = new OptionsMenuFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARGUMENT_IN_LIST, inConversationList);
        args.putString(ARGUMENT_CONVERSATION_ID, "");
        fragment.setArguments(args);
        return fragment;
    }

    public static OptionsMenuFragment newInstance(boolean inConversationList, String conversationId) {
        OptionsMenuFragment fragment = new OptionsMenuFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARGUMENT_IN_LIST, inConversationList);
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inConversationList = getArguments().getBoolean(ARGUMENT_IN_LIST);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_conversation_option_menu, container, false);
        optionsMenu = ViewUtils.getView(view, R.id.om__participant);

        if (savedInstanceState != null) {
            switch (OptionsTheme.Type.values()[savedInstanceState.getInt(ARGUMENT_WIRE_THEME)]) {
                case DARK:
                    optionsTheme = ((BaseActivity) getActivity()).injectJava(ThemeController.class).optionsDarkTheme();
                    break;
                case LIGHT:
                    optionsTheme = ((BaseActivity) getActivity()).injectJava(ThemeController.class).optionsLightTheme();
                    break;
            }
        } else {
            optionsTheme = ((BaseActivity) getActivity()).injectJava(ThemeController.class).optionsLightTheme();
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        getStoreFactory().conversationStore().addConversationStoreObserver(this);

        String conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);

        if (!TextUtils.isEmpty(conversationId)) {
            getStoreFactory()
                .zMessagingApiStore()
                .getApi()
                .getConversations()
                .getConversation(conversationId,
                                 new ConversationsList.ConversationCallback() {
                                     @Override
                                     public void onConversationsFound(Iterable<IConversation> iterable) {
                                         // use only the first one
                                         connectConversation(iterable.iterator().next());
                                     }
                                 });
            getStoreFactory().conversationStore().loadMenuConversation(conversationId);
        }

        getContainer().getOptionsMenuControl().setCallback(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        optionsMenu.setCallback(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARGUMENT_WIRE_THEME, optionsTheme.getType().ordinal());

        if (conversation != null) {
            getArguments().putString(ARGUMENT_CONVERSATION_ID, conversation.getId());
        } else {
            getArguments().putString(ARGUMENT_CONVERSATION_ID, "");
        }
    }

    @Override
    public void onPause() {
        optionsMenu.setCallback(null);
        super.onPause();
    }

    @Override
    public void onStop() {
        getContainer().getOptionsMenuControl().setCallback(null);
        getStoreFactory().conversationStore().removeConversationStoreObserver(this);
        getStoreFactory().singleParticipantStore().removeSingleParticipantObserver(this);
        disconnectConversation();
        disconnectUser();
        super.onStop();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConversationStoreStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

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
    public void onMenuConversationHasChanged(final IConversation conversation) {
        List<OptionsMenuItem> items = new ArrayList<>();
        switch (conversation.getType()) {
            case GROUP:
                if (conversation.isMemberOfConversation()) {
                    // silence/unsilence
                    if (conversation.isMuted()) {
                        items.add(OptionsMenuItem.UNSILENCE);
                    } else {
                        items.add(OptionsMenuItem.SILENCE);
                    }

                    if (inConversationList) {
                        items.add(OptionsMenuItem.CALL);
                        items.add(OptionsMenuItem.PICTURE);
                    } else { //in ParticipantsFragment
                        items.add(OptionsMenuItem.RENAME);
                    }
                }

                // archive
                if (conversation.isArchived()) {
                    items.add(OptionsMenuItem.UNARCHIVE);
                } else {
                    items.add(OptionsMenuItem.ARCHIVE);
                }

                items.add(OptionsMenuItem.DELETE);

                // leave
                if (conversation.isMemberOfConversation()) {
                    items.add(OptionsMenuItem.LEAVE);
                }
                optionsMenu.setMenuItems(items, optionsTheme);
                break;
            case ONE_TO_ONE:
                items.add(OptionsMenuItem.CALL);
                items.add(OptionsMenuItem.PICTURE);
                connectUser(conversation.getOtherParticipant());
                break;
            case WAIT_FOR_CONNECTION:
                connectUser(conversation.getOtherParticipant());
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SingleParticipantsStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUserUpdated(final User user) {
        if (user == null ||
            conversation == null) {
            return;
        }
        List<OptionsMenuItem> items = new ArrayList<>();

        switch (user.getConnectionStatus()) {
            case ACCEPTED:
            case BLOCKED:
                if (conversation.isMuted()) {
                    items.add(OptionsMenuItem.UNSILENCE);
                } else {
                    items.add(OptionsMenuItem.SILENCE);
                }
                break;
        }

        if (requester != IConversationScreenController.USER_PROFILE_SEARCH &&
            requester != IConversationScreenController.USER_PROFILE_PARTICIPANTS) {
            if (conversation.isArchived()) {
                items.add(OptionsMenuItem.UNARCHIVE);
            } else {
                items.add(OptionsMenuItem.ARCHIVE);
            }
        }

        switch (user.getConnectionStatus()) {
            case ACCEPTED:
                items.add(OptionsMenuItem.DELETE);
                items.add(OptionsMenuItem.BLOCK);
                if (inConversationList) {
                    items.add(OptionsMenuItem.CALL);
                    items.add(OptionsMenuItem.PICTURE);
                }
                break;
            case BLOCKED:
                items.add(OptionsMenuItem.UNBLOCK);
                break;
            case PENDING_FROM_USER:
                items.add(OptionsMenuItem.BLOCK);
                break;
        }

        optionsMenu.setMenuItems(items, optionsTheme);
    }

    @Override
    public void onOptionsMenuStateHasChanged(OptionsMenu.State state) {
        getContainer().onOptionMenuStateHasChanged(state);
    }

    @Override
    public void onOptionsMenuItemClicked(OptionsMenuItem optionsMenuItem) {
        getContainer().onOptionsItemClicked(conversation, user, optionsMenuItem);

        if (getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }
        TrackingUtils.tagOptionsMenuSelectedEvent(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                  optionsMenuItem,
                                                  conversation.getType(),
                                                  inConversationList,
                                                  (requester == IConversationScreenController.CONVERSATION_LIST_SWIPE));

    }

    @Override
    public boolean onOptionsMenuItemLongClicked(OptionsMenuItem optionsMenuItem) {
        return false;
    }

    @Override
    public void onOpenRequest() {
        optionsMenu.open();
    }

    @Override
    public boolean onCloseRequest() {
        return optionsMenu.close();
    }

    @Override
    public void onCreateMenu(IConversation conversation,
                             @IConversationScreenController.ConversationMenuRequester int requester,
                             OptionsTheme optionsTheme) {
        this.optionsTheme = optionsTheme;
        this.requester = requester;
        connectConversation(conversation);
    }

    private void connectConversation(IConversation conversation) {
        this.conversation = conversation;
        this.conversation.addUpdateListener(conversationUpdateListener);
        conversationUpdateListener.updated();
        optionsMenu.setTitle(conversation.getName());
        optionsMenu.setConversationDetails(conversation);
    }

    private void disconnectConversation() {
        if (this.conversation != null) {
            this.conversation.removeUpdateListener(conversationUpdateListener);
            this.conversation = null;
        }
    }

    private void connectUser(User user) {
        this.user = user;
        this.user.addUpdateListener(userUpdateListener);
        userUpdateListener.updated();
    }

    private void disconnectUser() {
        if (this.user != null) {
            this.user.removeUpdateListener(userUpdateListener);
            this.user = null;
        }
    }

    private UpdateListener userUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            if (user != null) {
                onUserUpdated(user);
            }
        }
    };

    private UpdateListener conversationUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            if (conversation != null) {
                onMenuConversationHasChanged(conversation);
            }
        }
    };

    public interface Container extends OnBackPressedListener {

        void onOptionMenuStateHasChanged(OptionsMenu.State state);

        void onOptionsItemClicked(IConversation conversation, User user, OptionsMenuItem item);

        OptionsMenuControl getOptionsMenuControl();
    }
}
