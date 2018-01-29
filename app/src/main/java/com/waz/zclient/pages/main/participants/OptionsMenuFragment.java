/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.IConversation;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.model.ConvId;
import com.waz.model.ConversationData;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.common.controllers.ThemeController;
import com.waz.zclient.core.stores.singleparticipants.SingleParticipantStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.participants.OptionsMenuController;
import com.waz.zclient.ui.optionsmenu.OptionsMenu;
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class OptionsMenuFragment extends BaseFragment<OptionsMenuFragment.Container> implements SingleParticipantStoreObserver,
                                                                                                OptionsMenuControl.Callback,
                                                                                                OptionsMenu.Callback {
    public static final String TAG = OptionsMenuFragment.class.getName();
    private static final String ARGUMENT_IN_LIST = "ARGUMENT_IN_LIST ";
    private static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    private static final String ARGUMENT_WIRE_THEME = "ARGUMENT_WIRE_THEME";
    private OptionsMenu optionsMenu;
    private @IConversationScreenController.ConversationMenuRequester int requester;
    private User user;

    private OptionsMenuController ctrl;

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

        ctrl = inject(OptionsMenuController.class);
        ctrl.onMenuConversationHasChanged(new Callback<ConversationData>() {
            @Override
            public void callback(ConversationData conversationData) {
                onMenuConversationHasChanged(conversationData);
            }
        });

        String conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
        if (!TextUtils.isEmpty(conversationId)) {
            connectConversation(new ConvId(conversationId));
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

        ctrl.withConvId(new Callback<ConvId>() {
            @Override
            public void callback(ConvId convId) {
                getArguments().putString(ARGUMENT_CONVERSATION_ID, convId != null? convId.str():"");
            }
        });

    }

    @Override
    public void onPause() {
        optionsMenu.setCallback(null);
        super.onPause();
    }

    @Override
    public void onStop() {
        getContainer().getOptionsMenuControl().setCallback(null);
        ctrl.onMenuConversationHasChanged(null);
        ctrl = null;
        getStoreFactory().singleParticipantStore().removeSingleParticipantObserver(this);
        disconnectConversation();
        disconnectUser();
        super.onStop();
    }


    public void onMenuConversationHasChanged(final ConversationData conv) {
        ConversationController conversationController = inject(ConversationController.class);
        conversationController.isGroup(conv.id(), new Callback<Boolean>() {
            @Override public void callback(Boolean isGroup) {
                List<OptionsMenuItem> items = new ArrayList<>();

                if (isGroup) {
                    if (conv.isActive()) {
                        // silence/unsilence
                        if (conv.muted()) {
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
                    if (conv.archived()) {
                        items.add(OptionsMenuItem.UNARCHIVE);
                    } else {
                        items.add(OptionsMenuItem.ARCHIVE);
                    }

                    items.add(OptionsMenuItem.DELETE);

                    // leave
                    if (conv.isActive()) {
                        items.add(OptionsMenuItem.LEAVE);
                    }
                    optionsMenu.setMenuItems(items, optionsTheme);
                } else if (conv.convType() == IConversation.Type.ONE_TO_ONE) {
                    items.add(OptionsMenuItem.CALL);
                    items.add(OptionsMenuItem.PICTURE);
                    connectUser(ConversationController.getOtherParticipantForOneToOneConv(conv));
                } else if (conv.convType() == IConversation.Type.WAIT_FOR_CONNECTION) {
                    connectUser(ConversationController.getOtherParticipantForOneToOneConv(conv));
                }
            }
        });

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SingleParticipantsStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUserUpdated(final User user) {
        if (user == null) {
            return;
        }

        ctrl.withConv(new Callback<ConversationData>() {
            @Override
            public void callback(ConversationData conv) {
                final List<OptionsMenuItem> items = new ArrayList<>();
                switch (user.getConnectionStatus()) {
                    case ACCEPTED:
                    case BLOCKED:
                        if (conv.muted()) {
                            items.add(OptionsMenuItem.UNSILENCE);
                        } else {
                            items.add(OptionsMenuItem.SILENCE);
                        }
                        break;
                }

                if (requester != IConversationScreenController.USER_PROFILE_SEARCH &&
                    requester != IConversationScreenController.USER_PROFILE_PARTICIPANTS) {
                    if (conv.archived()) {
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
        });
    }

    @Override
    public void onOptionsMenuStateHasChanged(OptionsMenu.State state) {
        getContainer().onOptionMenuStateHasChanged(state);
    }

    @Override
    public void onOptionsMenuItemClicked(final OptionsMenuItem optionsMenuItem) {
        ctrl.withConvId(new Callback<ConvId>() {
            @Override
            public void callback(ConvId convId) {
            getContainer().onOptionsItemClicked(convId, user, optionsMenuItem);
            }
        });
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
    public void onCreateMenu(ConvId convId,
                             @IConversationScreenController.ConversationMenuRequester int requester,
                             OptionsTheme optionsTheme) {
        this.optionsTheme = optionsTheme;
        this.requester = requester;
        connectConversation(convId);
    }

    private void connectConversation(ConvId convId) {
        if (ctrl == null) {
            return;
        }

        ctrl.setConvId(convId);
        ctrl.withConv(new Callback<ConversationData>() {
            @Override
            public void callback(ConversationData conversationData) {
                optionsMenu.setTitle(conversationData.displayName());
                optionsMenu.setConversationDetails(conversationData);
                onMenuConversationHasChanged(conversationData);
            }
        });
    }

    private void disconnectConversation() {
        if (ctrl != null) {
            ctrl.setConvId(null);
        }
    }

    private void connectUser(UserId userId) {
        this.user = getUser(userId);
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

    private User getUser(UserId id) {
        return getStoreFactory().zMessagingApiStore().getApi().getUser(id.str());
    }

    public interface Container extends OnBackPressedListener {

        void onOptionMenuStateHasChanged(OptionsMenu.State state);

        void onOptionsItemClicked(ConvId convId, User user, OptionsMenuItem item);

        OptionsMenuControl getOptionsMenuControl();
    }

}
