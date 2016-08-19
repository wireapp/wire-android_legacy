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
package com.waz.zclient.pages.main.pickuser;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.waz.api.ContactDetails;
import com.waz.api.ContactMethod;
import com.waz.api.Contacts;
import com.waz.api.IConversation;
import com.waz.api.NetworkMode;
import com.waz.api.User;
import com.waz.api.UserSearchResult;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.tracking.events.connect.OpenedGenericInviteMenuEvent;
import com.waz.zclient.controllers.tracking.events.connect.SentConnectRequestEvent;
import com.waz.zclient.controllers.tracking.events.peoplepicker.PeoplePickerSelectSearchUser;
import com.waz.zclient.controllers.tracking.events.peoplepicker.PeoplePickerSelectTopUser;
import com.waz.zclient.controllers.tracking.screens.ApplicationScreen;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.InboxLoadRequester;
import com.waz.zclient.core.stores.conversation.OnInboxLoadedListener;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.core.stores.pickuser.PickUserStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.pages.main.participants.views.ChatheadWithTextFooter;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerSearchObserver;
import com.waz.zclient.pages.main.pickuser.controller.PickUserDataState;
import com.waz.zclient.pages.main.pickuser.views.ContactRowView;
import com.waz.zclient.pages.main.pickuser.views.SearchBoxView;
import com.waz.zclient.pages.main.pickuser.views.UserRowView;
import com.waz.zclient.ui.animation.fragment.FadeAnimation;
import com.waz.zclient.ui.startui.ConversationQuickMenu;
import com.waz.zclient.ui.startui.ConversationQuickMenuCallback;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.device.DeviceDetector;
import com.waz.zclient.views.DefaultPageTransitionAnimation;
import com.waz.zclient.views.LoadingIndicatorView;
import hugo.weaving.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PickUserFragment extends BaseFragment<PickUserFragment.Container> implements View.OnClickListener,
                                                                                          OnInboxLoadedListener,
                                                                                          PickUserStoreObserver,
                                                                                          KeyboardVisibilityObserver,
                                                                                          AccentColorObserver,
                                                                                          ConversationQuickMenuCallback,
                                                                                          OnBackPressedListener,
                                                                                          SearchResultOnItemTouchListener.Callback,
                                                                                          SearchResultAdapter.Callback,
                                                                                          PickUserControllerSearchObserver {
    public static final String TAG = PickUserFragment.class.getName();

    public static final String ARGUMENT_ADD_TO_CONVERSATION = "ARGUMENT_ADD_TO_CONVERSATION";
    public static final String ARGUMENT_GROUP_CONVERSATION = "ARGUMENT_GROUP_CONVERSATION";

    public static final int NUM_SEARCH_RESULTS_LIST = 30;
    public static final int NUM_SEARCH_RESULTS_TOP_USERS = 24;
    public static final int NUM_SEARCH_RESULTS_ADD_TO_CONV = 1000;

    private static final int DEFAULT_SELECTED_INVITE_METHOD = 0;
    private static final int SHOW_KEYBOARD_THRETHOLD = 10;
    private RecyclerView searchResultRecyclerView;
    private SearchResultAdapter searchResultAdapter;

    // Saves user from which a pending connect request is loaded
    private User pendingFromUser;
    private Toolbar toolbar;
    private TextView toolbarHeader;
    private View divider;
    private TypefaceTextView errorMessageViewHeader;
    private LinearLayout errorMessageViewSendInvite;
    private TypefaceTextView errorMessageViewBody;
    private LinearLayout errorMessageViewContainer;
    private ConversationQuickMenu conversationQuickMenu;
    private ZetaButton userSelectionConfirmationButton;
    private View userSelectionConfirmationContainer;
    private View genericInviteContainer;
    private ZetaButton genericInviteButton;
    private SearchBoxView searchBoxView;
    private boolean isKeyboardVisible;
    private boolean searchBoxIsEmpty = true;
    private long showLoadingBarDelay;
    private boolean lastInputIsKeyboardDoneAction;
    private String shareBody;
    private AlertDialog dialog;
    private static final boolean SHOW_INVITE = true;

    final public SearchBoxView.Callback searchBoxViewCallback = new SearchBoxView.Callback() {
        @Override
        public void onKeyboardDoneAction() {
            getControllerFactory().getPickUserController().notifyKeyboardDoneAction();
        }

        @Override
        public void onFocusChange(boolean hasFocus) {
            setFocusByCurrentPickerDestination();
        }

        @Override
        public void onClearButton() {
            closeStartUI();
        }

        @Override
        public void onRemovedTokenSpan(User user) {
            getControllerFactory().getPickUserController().removeUser(user);
            if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
                setConversationQuickMenuVisible(false);
            }
        }

        @Override
        public void afterTextChanged(String s) {
            if (getControllerFactory().isTornDown()) {
                return;
            }
            getControllerFactory().getPickUserController().setSearchFilter(searchBoxView.getSearchFilter());
            getControllerFactory().getTrackingController().searchedForPeople();
        }
    };

    private final ModelObserver<UserSearchResult> usersSearchModelObserver = new ModelObserver<UserSearchResult>() {
        @Override
        public void updated(UserSearchResult model) {
            if (getContainer() != null) {
                getContainer().getLoadingViewIndicator().hide();
            }
            if (model.getAll().length == 0) {
                PickUserDataState dataState = getDataState(getControllerFactory().getPickUserController().hasSelectedUsers());
                if (dataState == PickUserDataState.SHOW_ALL_USERS_TO_ADD_TO_CONVERSATION) {
                    handleEmptySearchResult(getString(R.string.people_picker__error_message__no_users_to_add_to_conversation), "", false);
                } else {
                    handleEmptySearchResult(getString(R.string.people_picker__error_message__no_results), "", false);
                }
            } else {
                hideErrorMessage();
                searchResultAdapter.setSearchResult(model.getAll(), null, null);
            }
        }
    };

    public static PickUserFragment newInstance(boolean addToConversation) {
        PickUserFragment fragment = new PickUserFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARGUMENT_ADD_TO_CONVERSATION, addToConversation);
        args.putBoolean(ARGUMENT_GROUP_CONVERSATION, false);
        fragment.setArguments(args);
        return fragment;
    }

    public static PickUserFragment newInstance(boolean addToConversation, boolean groupConversation) {
        PickUserFragment fragment = new PickUserFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARGUMENT_ADD_TO_CONVERSATION, addToConversation);
        args.putBoolean(ARGUMENT_GROUP_CONVERSATION, groupConversation);
        fragment.setArguments(args);
        return fragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim == 0 ||
            getContainer() == null ||
            getControllerFactory().isTornDown()) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }

        if (getControllerFactory().getPickUserController().isHideWithoutAnimations()) {
            return new DefaultPageTransitionAnimation(0, ViewUtils.getOrientationIndependentDisplayHeight(getActivity()), enter, 0, 0, 1f);
        }

        if (enter) {
            // Fade animation in participants dialog on tablet
            if (LayoutSpec.isTablet(getActivity()) && getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
                return new FadeAnimation(getResources().getInteger(R.integer.open_new_conversation__top_conversation__animation_duration), 0f, 1f);
            }
            return new DefaultPageTransitionAnimation(0,
                                         getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                         enter,
                                         getResources().getInteger(R.integer.framework_animation_duration_long),
                                         getResources().getInteger(R.integer.framework_animation_duration_medium),
                                         1f);
        }
        return new DefaultPageTransitionAnimation(0,
                                     getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                     enter,
                                     getResources().getInteger(R.integer.framework_animation_duration_medium),
                                     0,
                                     1f);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewContainer, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_pick_user, viewContainer, false);

        toolbarHeader = ViewUtils.getView(rootView, R.id.ttv__pickuser__add_header);
        toolbar = ViewUtils.getView(rootView, R.id.t_pickuser_toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeStartUI();
            }
        });
        divider = ViewUtils.getView(rootView, R.id.v__pickuser__divider);

        searchResultAdapter = new SearchResultAdapter(this);
        searchResultAdapter.setTopUsersOnItemTouchListener(new SearchResultOnItemTouchListener(getActivity(), this));

        searchResultRecyclerView = ViewUtils.getView(rootView, R.id.rv__pickuser__header_list_view);
        searchResultRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        searchResultRecyclerView.setAdapter(searchResultAdapter);
        searchResultRecyclerView.addOnItemTouchListener(new SearchResultOnItemTouchListener(getActivity(), this));
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            searchResultRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING &&
                        getControllerFactory().getGlobalLayoutController().isKeyboardVisible()) {
                        KeyboardUtils.hideKeyboard(getActivity());
                    }
                }
            });
        }
        searchBoxView = ViewUtils.getView(rootView, R.id.sbv__search_box);
        searchBoxView.applyLightTheme(!getControllerFactory().getThemeController().isDarkTheme());
        searchBoxView.setAccentColor(getControllerFactory().getAccentColorController().getColor());
        searchBoxView.setCallback(searchBoxViewCallback);

        conversationQuickMenu = ViewUtils.getView(rootView, R.id.cqm__pickuser__quick_menu);
        conversationQuickMenu.setCallback(this);
        conversationQuickMenu.setVisibility(View.GONE);

        userSelectionConfirmationContainer = ViewUtils.getView(rootView, R.id.fl__pickuser__confirmation_button_container);
        userSelectionConfirmationContainer.setVisibility(View.GONE);
        userSelectionConfirmationButton = ViewUtils.getView(rootView, R.id.zb__pickuser__confirmation_button);

        genericInviteContainer = ViewUtils.getView(rootView, R.id.fl__pickuser__generic_invite__container);
        genericInviteButton = ViewUtils.getView(rootView, R.id.zb__pickuser__generic_invite);
        genericInviteButton.setIsFilled(false);

        // Error message
        errorMessageViewContainer = ViewUtils.getView(rootView, R.id.fl_pickuser__error_message_container);
        errorMessageViewContainer.setVisibility(View.GONE);

        errorMessageViewHeader = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_header);
        errorMessageViewBody = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_body);
        errorMessageViewSendInvite = ViewUtils.getView(rootView, R.id.ll_pickuser__error_invite);

        showLoadingBarDelay = getResources().getInteger(R.integer.people_picker__loading_bar__show_delay);

        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            genericInviteContainer.setVisibility(View.GONE);
            searchBoxView.setHintText(getString(R.string.pick_user_search__add_to_conversation));
            searchBoxView.showClearButton(false);
            divider.setVisibility(View.VISIBLE);
            toolbar.setVisibility(View.VISIBLE);
            toolbarHeader.setText(getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION) ?
                                  getString(R.string.people_picker__toolbar_header__group) :
                                  getString(R.string.people_picker__toolbar_header__one_to_one));
            userSelectionConfirmationButton.setText(getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION) ?
                                                    getString(R.string.people_picker__confirm_button_title__add_to_conversation) :
                                                    getString(R.string.people_picker__confirm_button_title__create_conversation));
            ViewUtils.setHeight(searchBoxView, getResources().getDimensionPixelSize(R.dimen.searchbox__height__with_toolbar));
        } else {
            // Use constant style for left side start ui
            int textColor = getResources().getColor(R.color.text__primary_dark);
            errorMessageViewHeader.setTextColor(textColor);
            errorMessageViewBody.setTextColor(textColor);
            TextView errorMessageIcon = ViewUtils.getView(rootView, R.id.gtv_pickuser__error_icon);
            errorMessageIcon.setTextColor(textColor);
            TextView errorMessageSublabel = ViewUtils.getView(rootView, R.id.ttv_pickuser__error_sublabel);
            errorMessageSublabel.setTextColor(textColor);
            searchResultAdapter.setDarkTheme(true);
            searchBoxView.forceDarkTheme();
            searchBoxView.setHintText(getString(R.string.pick_user_search_focus));
            searchBoxView.showClearButton(true);
            toolbar.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();

        getStoreFactory().getPickUserStore().addPickUserStoreObserver(this);
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getPickUserController().addPickUserSearchControllerObserver(this);
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION) && !getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION)) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
                        return;
                    }
                    for (User user : getControllerFactory().getPickUserController().getSelectedUsers()) {
                        searchBoxView.addUser(user);
                    }
                }
            });
        } else {
            getControllerFactory().getPickUserController().setSearchFilter("");
        }

        loadStartUi();
        usersSearchModelObserver.resumeListening();
        usersSearchModelObserver.forceUpdate();
    }

    @Override
    public void onResume() {
        super.onResume();
        genericInviteButton.setOnClickListener(this);
        userSelectionConfirmationButton.setOnClickListener(this);
        errorMessageViewSendInvite.setOnClickListener(this);
        if (!getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
                        return;
                    }
                    final int numberOfActiveConversations = getStoreFactory().getConversationStore().getNumberOfActiveConversations();
                    if (searchBoxView == null ||
                        numberOfActiveConversations <= SHOW_KEYBOARD_THRETHOLD) {
                        return;
                    }
                    searchBoxView.setFocus();
                    KeyboardUtils.showKeyboard(getActivity());
                }
            }, getResources().getInteger(R.integer.people_picker__keyboard__show_delay));
        }
    }

    @Override
    public void onPause() {
        genericInviteButton.setOnClickListener(null);
        userSelectionConfirmationButton.setOnClickListener(null);
        errorMessageViewSendInvite.setOnClickListener(null);
        super.onPause();

    }

    @Override
    public void onStop() {
        getContainer().getLoadingViewIndicator().hide();
        getStoreFactory().getPickUserStore().removePickUserStoreObserver(this);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getPickUserController().removePickUserSearchControllerObserver(this);
        usersSearchModelObserver.pauseListening();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        errorMessageViewHeader = null;
        errorMessageViewSendInvite = null;
        errorMessageViewBody = null;
        errorMessageViewContainer = null;
        searchResultAdapter.reset();
        searchResultRecyclerView = null;
        conversationQuickMenu = null;
        userSelectionConfirmationContainer = null;
        userSelectionConfirmationButton = null;
        genericInviteButton = null;
        genericInviteContainer = null;
        searchBoxView = null;
        toolbar = null;
        toolbarHeader = null;
        super.onDestroyView();
    }

    @Override
    public boolean onBackPressed() {
        if (isKeyboardVisible) {
            KeyboardUtils.hideKeyboard(getActivity());
        } else if (getControllerFactory().getPickUserController().isShowingUserProfile()) {
            getControllerFactory().getPickUserController().hideUserProfile();
            return true;
        }
        return isKeyboardVisible;
    }

    @Override
    public void onConnectRequestInboxConversationsLoaded(List<IConversation> conversations,
                                                         InboxLoadRequester inboxLoadRequester) {
        for (IConversation conversation : conversations) {
            if (conversation.getId().equals(pendingFromUser.getConversation().getId())) {
                getContainer().showIncomingPendingConnectRequest(conversation);
                return;
            }
        }
    }

    @Override
    public void onTopUsersUpdated(User[] users) {
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            return;
        }
        getContainer().getLoadingViewIndicator().hide();
        PickUserDataState dataState = getDataState(getControllerFactory().getPickUserController().hasSelectedUsers());
        switch (dataState) {
            case SHOW_ALL_USERS_TO_ADD_TO_CONVERSATION:
            case SHOW_TOP_USERS_AS_LIST:
                // Show results as list
                if (users == null) {
                    handleEmptySearchResult(getString(R.string.people_picker__error_message__no_results), "", false);
                } else if (users.length == 0) {
                    if (dataState == PickUserDataState.SHOW_ALL_USERS_TO_ADD_TO_CONVERSATION) {
                        handleEmptySearchResult(getString(R.string.people_picker__error_message__no_users_to_add_to_conversation), "", false);
                    } else {
                        handleEmptySearchResult(getString(R.string.people_picker__error_message__no_results), "", false);
                    }
                } else {
                    hideErrorMessage();
                    searchResultAdapter.setSearchResult(users, null, null);
                }
                break;
            case SHOW_TOP_USERS_AND_RECOMMENDED:
                searchResultAdapter.setTopUsers(users);
                getStoreFactory().getPickUserStore().loadContacts();
                break;
        }
    }

    @Override
    public void onSearchResultsUpdated(User[] connectedUsers, User[] otherUsers, IConversation[] conversations) {
        getContainer().getLoadingViewIndicator().hide();

        String errorHeader = getString(R.string.people_picker__error_message__no_results);
        String errorBody;
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            errorBody = getString(R.string.people_picker__error_message__no_results__shared_contacts__add_to_conversation);
        } else {
            errorBody = getControllerFactory().getPickUserController().searchInputIsInvalidEmail() ?
                        getString(R.string.people_picker__error_message__no_results__invalid_email__no_shared_contacts) :
                        getString(R.string.people_picker__error_message__no_results__no_shared_contacts);
        }

        switch (getDataState(getControllerFactory().getPickUserController().hasSelectedUsers())) {
            case SHOW_SEARCH_RESULTS_TO_ADD_TO_CONVERSATION:
                if (connectedUsers.length == 0) {
                    handleEmptySearchResult(errorHeader, errorBody, !lastInputIsKeyboardDoneAction);
                } else {
                    hideErrorMessage();
                    searchResultAdapter.setSearchResult(connectedUsers, null, null);
                }
                break;
            case SHOW_SEARCH_RESULTS:
                if (getControllerFactory().getPickUserController().hasSelectedUsers()) {
                    // Hide group conversations if there are selected users
                    if (connectedUsers.length == 0) {
                        handleEmptySearchResult(errorHeader, errorBody, !lastInputIsKeyboardDoneAction);
                    } else {
                        hideErrorMessage();
                        searchResultAdapter.setSearchResult(connectedUsers, null, null);
                    }
                } else {
                    if (connectedUsers.length == 0 &&
                        otherUsers.length == 0 &&
                        conversations.length == 0 &&
                        !searchResultAdapter.hasContacts()) {
                        handleEmptySearchResult(errorHeader, errorBody, !lastInputIsKeyboardDoneAction);
                    } else {
                        hideErrorMessage();
                        searchResultAdapter.setSearchResult(connectedUsers, otherUsers, conversations);
                    }
                }
                break;
        }
    }

    @Override
    public void onContactsUpdated(Contacts contacts) {
        if (contacts == null ||
            contacts.size() == 0) {
            return;
        }
        hideErrorMessage();
        searchResultAdapter.setContacts(contacts);
    }

    @Override
    public void onSearchContactsUpdated(Contacts contacts) {
        if (contacts == null) {
            return;
        }
        searchResultAdapter.setContacts(contacts);
    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        int color;
        isKeyboardVisible = keyboardIsVisible;
        if (keyboardIsVisible || !searchBoxIsEmpty) {
            color = getResources().getColor(R.color.people_picker__loading__color);
        } else {
            color = getControllerFactory().getAccentColorController().getColor();
        }
        getContainer().getLoadingViewIndicator().setColor(color);

        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            return;
        }

        int inviteVisibility = keyboardIsVisible ||  getControllerFactory().getPickUserController().hasSelectedUsers() ? View.GONE : View.VISIBLE;
        genericInviteContainer.setVisibility(inviteVisibility);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        conversationQuickMenu.setAccentColor(color);
        userSelectionConfirmationButton.setAccentColor(color);
        searchResultAdapter.setAccentColor(color);
        genericInviteButton.setAccentColor(color, true);
        searchBoxView.setAccentColor(color);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // ConversationQuickMenuCallback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationButtonClicked() {
        KeyboardUtils.hideKeyboard(getActivity());
        List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
        getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION);
    }

    @Override
    public void onVideoCallButtonClicked() {
        KeyboardUtils.hideKeyboard(getActivity());
        List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
        if (users.size() > 1) {
            throw new IllegalStateException("A video call cannot be started with more than one user. The button should not be visible " +
                                            "if multiple users are selected.");
        }
        getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL);
    }

    @Override
    public void onCallButtonClicked() {
        KeyboardUtils.hideKeyboard(getActivity());
        List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
        getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION_FOR_CALL);
        // TODO: Uncomment when call issue is resolved with https://wearezeta.atlassian.net/browse/CM-675
        //getStoreFactory().getGroupCallingStore().startCall();
    }

    @Override
    public void onCameraButtonClicked() {
        KeyboardUtils.hideKeyboard(getActivity());
        List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
        getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserControllerSearchObserver - Search actions
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSearchBoxIsEmpty() {
        searchBoxIsEmpty = true;
        lastInputIsKeyboardDoneAction = false;
        setConversationQuickMenuVisible(false);
        loadStartUi();
    }

    @Override
    public void onSearchBoxHasNewSearchFilter(String filter) {
        searchBoxIsEmpty = false;
        lastInputIsKeyboardDoneAction = false;
        loadStartUi();
    }

    @Override
    public void onKeyboardDoneAction() {
        lastInputIsKeyboardDoneAction = true;

        List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
        int minUsers = 1;
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION) && !getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION)) {
            minUsers = 2;
        }
        if (users.size() >= minUsers) {
            KeyboardUtils.hideKeyboard(getActivity());
            getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION);
        }

        if (searchResultRecyclerView != null &&
            searchResultRecyclerView.getVisibility() != View.VISIBLE &&
            errorMessageViewContainer.getVisibility() != View.VISIBLE) {
            showErrorMessage();
        }
    }

    @Override
    public void onSelectedUserAdded(List<User> selectedUsers, User addedUser) {
        changeUserSelectedState(addedUser, true);
        updateConversationButtonLabel();
        conversationQuickMenu.showVideoCallButton(shouldVideoCallButtonBeDisplayed());
        searchBoxView.addUser(addedUser);
    }

    private boolean shouldVideoCallButtonBeDisplayed() {
        return getControllerFactory().getPickUserController().getSelectedUsers().size() == 1 &&
               DeviceDetector.isVideoCallingEnabled();
    }

    @Override
    public void onSelectedUserRemoved(List<User> selectedUsers, User removedUser) {
        changeUserSelectedState(removedUser, false);
        updateConversationButtonLabel();
        conversationQuickMenu.showVideoCallButton(shouldVideoCallButtonBeDisplayed());
        searchBoxView.removeUser(removedUser);
    }

    private void changeUserSelectedState(User user, boolean selected) {

        changeUserSelectedState(searchResultRecyclerView, user, selected);

        if (!searchResultAdapter.hasTopUsers()) {
            return;
        }
        for (int i = 0; i < searchResultRecyclerView.getChildCount(); i++) {
            View rowView = searchResultRecyclerView.getChildAt(i);
            if (rowView instanceof RecyclerView) {
                changeUserSelectedState(((RecyclerView) rowView), user, selected);
            }
        }
    }

    private void changeUserSelectedState(RecyclerView rv, User user, boolean selected) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View rowView = rv.getChildAt(i);
            if (rowView instanceof UserRowView) {
                UserRowView userRowView = (UserRowView) rowView;
                if (userRowView.getUser() != null &&
                    userRowView.getUser().equals(user)) {
                    userRowView.setSelected(selected);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SearchResultOnItemTouchListener.Callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onUserClicked(User user, int position, View anchorView) {
        if (user == null ||
            user.isMe() ||
            getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }

        if (anchorView instanceof ChatheadWithTextFooter) {
            getControllerFactory().getTrackingController().tagEvent(new PeoplePickerSelectTopUser());
        } else {
            int rowType = searchResultAdapter.getItemViewType(position);
            switch (rowType) {
                case SearchResultAdapter.ITEM_TYPE_CONNECTED_USER:
                case SearchResultAdapter.ITEM_TYPE_OTHER_USER:
                    getControllerFactory().getTrackingController().tagEvent(new PeoplePickerSelectSearchUser(position));
                    break;
            }
        }

        // Selecting user from search results toggles user token and confirmation button
        if (user.getConnectionStatus() == User.ConnectionStatus.ACCEPTED) {
            if (anchorView.isSelected()) {
                getControllerFactory().getPickUserController().addUser(user);
            } else {
                getControllerFactory().getPickUserController().removeUser(user);
            }
            setConversationQuickMenuVisible(getControllerFactory().getPickUserController().hasSelectedUsers());
            return;
        }

        if (anchorView instanceof ContactRowView &&
            user.getConnectionStatus() == User.ConnectionStatus.UNCONNECTED) {
            return;
        }

        showUser(user, anchorView);

    }

    @Override
    public void onUserDoubleClicked(User user, int position, View anchorView) {
        if (!(anchorView instanceof ChatheadWithTextFooter)) {
            return;
        }

        if (user == null ||
            user.isMe() ||
            user.getConnectionStatus() != User.ConnectionStatus.ACCEPTED ||
            getControllerFactory().getPickUserController().hasSelectedUsers()) {
            return;
        }

        getControllerFactory().getTrackingController().tagEvent(new PeoplePickerSelectTopUser());
        getStoreFactory().getConversationStore().setCurrentConversation(user.getConversation(),
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    @Override
    public void onConversationClicked(IConversation conversation) {
        KeyboardUtils.hideKeyboard(getActivity());
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.START_CONVERSATION);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  SearchResultAdapter.Callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Set<User> getSelectedUsers() {
        if (getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return null;
        }
        return new HashSet<>(getControllerFactory().getPickUserController().getSelectedUsers());
    }

    @Override
    public void onContactListUserClicked(User user) {
        if (getStoreFactory() == null ||
            getStoreFactory().isTornDown() ||
            user == null) {
            return;
        }

        // ACCEPTED user connection status handled by onUserClicked callback
        switch (user.getConnectionStatus()) {
            case UNCONNECTED:
                User me = getStoreFactory().getProfileStore().getSelfUser();
                String myName = me != null ? me.getName() : "";
                String message = getString(R.string.connect__message, user.getName(), myName);
                user.connect(message);
                getControllerFactory().getTrackingController().tagEvent(new SentConnectRequestEvent(SentConnectRequestEvent.EventContext.INVITE_CONTACT_LIST, user.getCommonConnections().getTotalCount()));
                break;
        }
    }

    @Override
    public void onContactListContactClicked(final ContactDetails contactDetails) {
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                final int contactMethodsCount = contactDetails.getContactMethods().size();
                final ContactMethod[] contactMethods = contactDetails.getContactMethods().toArray(new ContactMethod[contactMethodsCount]);

                if (contactMethodsCount == 1 &&
                    contactMethods[0].getKind() == ContactMethod.Kind.SMS) {
                    // Launch SMS app directly if contact only has phone numner
                    final String number = contactMethods[0].getStringRepresentation();
                    sendSMSInvite(number);
                    getControllerFactory().getTrackingController().tagEvent(new OpenedGenericInviteMenuEvent(OpenedGenericInviteMenuEvent.EventContext.STARTUI_CONTACT));
                    return;
                }

                final CharSequence[] itemNames = new CharSequence[contactMethodsCount];
                for (int i = 0; i < contactMethodsCount; i++) {
                    ContactMethod contactMethod = contactMethods[i];
                    itemNames[i] = contactMethod.getStringRepresentation();
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                builder.setTitle(getResources().getString(R.string.people_picker__contact_list__invite_dialog__title))
                       .setPositiveButton(getResources().getText(R.string.confirmation_menu__confirm_done),
                                          new DialogInterface.OnClickListener() {
                                              @Override
                                              public void onClick(DialogInterface dialogInterface, int i) {
                                                  ListView lv = dialog.getListView();
                                                  int selected = lv.getCheckedItemPosition();
                                                  ContactMethod selectedContactMethod = null;
                                                  if (selected >= 0) {
                                                      selectedContactMethod = contactMethods[selected];
                                                  }
                                                  if (selectedContactMethod == null) {
                                                      return;
                                                  }


                                                  if (selectedContactMethod.getKind() == ContactMethod.Kind.SMS) {
                                                      final String number = String.valueOf(itemNames[selected]);
                                                      sendSMSInvite(number);
                                                      getControllerFactory().getTrackingController().tagEvent(new OpenedGenericInviteMenuEvent(OpenedGenericInviteMenuEvent.EventContext.STARTUI_CONTACT));
                                                  } else {
                                                      selectedContactMethod.invite(" ", null);
                                                      Toast.makeText(getActivity(),
                                                                     getResources().getString(R.string.people_picker__invite__sent_feedback),
                                                                     Toast.LENGTH_LONG).show();
                                                      boolean fromSearch = TextUtils.isEmpty(getControllerFactory().getPickUserController().getSearchFilter());
                                                      TrackingUtils.tagSentInviteToContactEvent(getControllerFactory().getTrackingController(),
                                                                                                selectedContactMethod.getKind(),
                                                                                                contactDetails.hasBeenInvited(),
                                                                                                fromSearch);
                                                  }
                                              }
                                          })
                       .setNegativeButton(getResources().getText(R.string.confirmation_menu__cancel),
                                          new DialogInterface.OnClickListener() {
                                              @Override
                                              public void onClick(DialogInterface dialogInterface, int i) {
                                                  dialogInterface.cancel();
                                              }
                                          })
                       .setSingleChoiceItems(itemNames,
                                             DEFAULT_SELECTED_INVITE_METHOD,
                                             null);

                dialog = builder.create();
                dialog.show();

                getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.SEND_PERSONAL_INVITE_MENU);
            }
        });
    }

    private void sendSMSInvite(String number) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", number, ""));
        intent.putExtra("sms_body", getString(R.string.people_picker__invite__share_text__body));
        startActivity(intent);
    }

    @Override
    public int getDestination() {
        return IPickUserController.STARTUI;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  List and grid helpers
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @DebugLog
    public void loadStartUi() {
        if (searchResultAdapter.getItemCount() == 0) {
            getContainer().getLoadingViewIndicator().show(LoadingIndicatorView.SPINNER);
        } else {
            getContainer().getLoadingViewIndicator().show(LoadingIndicatorView.INFINITE_LOADING_BAR, showLoadingBarDelay);
        }

        String filter = getControllerFactory().getPickUserController().getSearchFilter();
        boolean hasSelectedUsers = getControllerFactory().getPickUserController().hasSelectedUsers();
        switch (getDataState(hasSelectedUsers)) {
            case SHOW_ALL_USERS_TO_ADD_TO_CONVERSATION:
                filter = "";
            case SHOW_SEARCH_RESULTS_TO_ADD_TO_CONVERSATION:
                String[] excludedUsers = getStoreFactory().getPickUserStore().getExcludedUsers();
                UserSearchResult usersSearchResult = getStoreFactory().getZMessagingApiStore()
                                                                       .getApi()
                                                                       .search()
                                                                       .getConnectionsByName(filter, NUM_SEARCH_RESULTS_ADD_TO_CONV, excludedUsers);
                usersSearchModelObserver.setAndUpdate(usersSearchResult);
                break;
            case SHOW_SEARCH_RESULTS:
                getStoreFactory().getPickUserStore().loadSearchByFilter(filter, NUM_SEARCH_RESULTS_LIST, false);
                if (!hasSelectedUsers) {
                    getStoreFactory().getPickUserStore().searchContacts(filter);
                }
                break;
            case SHOW_TOP_USERS_AS_LIST:
            case SHOW_TOP_USERS_AND_RECOMMENDED:
                boolean excludeConversationParticipants = getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION) &&
                                                          getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION);
                getStoreFactory().getPickUserStore().loadTopUserList(NUM_SEARCH_RESULTS_TOP_USERS, excludeConversationParticipants);
                break;
        }
    }

    private void handleEmptySearchResult(String errorHeader, String errorBody, boolean showBlankScreen) {
        setErrorMessage(errorHeader, errorBody);
        if (showBlankScreen) {
            errorMessageViewContainer.setVisibility(View.INVISIBLE);
            searchResultRecyclerView.setClickable(false);
            searchResultRecyclerView.setVisibility(View.GONE);
        } else {
            showErrorMessage();
        }
    }
    private void setErrorMessage(String header, String body) {
        errorMessageViewHeader.setText(header);
        if (body != null) {
            errorMessageViewBody.setText(body);
            errorMessageViewBody.setVisibility(View.VISIBLE);
        } else {
            errorMessageViewBody.setVisibility(View.GONE);
        }

        if (SHOW_INVITE && !getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            errorMessageViewSendInvite.setVisibility(View.VISIBLE);
        } else {
            errorMessageViewSendInvite.setVisibility(View.GONE);
        }
    }

    private void showErrorMessage() {
        errorMessageViewContainer.setVisibility(View.VISIBLE);
        // Set isClickable as ListView continues to receive click events with GONE visibility
        searchResultRecyclerView.setClickable(false);
        searchResultRecyclerView.setVisibility(View.GONE);
    }

    private void hideErrorMessage() {
        errorMessageViewContainer.setVisibility(View.GONE);
        searchResultRecyclerView.setClickable(true);
        searchResultRecyclerView.setVisibility(View.VISIBLE);

        errorMessageViewHeader.setText("");
    }

    private IPickUserController.Destination getCurrentPickerDestination() {
        return getContainer().getCurrentPickerDestination();
    }

    private void sendGenericInvite(final boolean fromSearch) {
        if (getControllerFactory() == null ||
            getControllerFactory().isTornDown() ||
            getStoreFactory() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        shareBody = getString(R.string.people_picker__invite__share_text__body);

        String name = "";
        if (getStoreFactory().getProfileStore().getSelfUser() != null &&
            getStoreFactory().getProfileStore().getSelfUser().getDisplayName() != null) {
            name = getStoreFactory().getProfileStore().getSelfUser().getDisplayName();
        }
        String shareSubject = getString(R.string.people_picker__invite__share_text__header, name);
        String shareChooserMessage = getString(R.string.people_picker__invite__share_details_dialog);
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, shareChooserMessage));
        OpenedGenericInviteMenuEvent.EventContext eventContext = fromSearch ?
                                                                 OpenedGenericInviteMenuEvent.EventContext.STARTUI_SEARCH :
                                                                 OpenedGenericInviteMenuEvent.EventContext.STARTUI_BANNER;
        getControllerFactory().getTrackingController().tagEvent(new OpenedGenericInviteMenuEvent(eventContext));
        getControllerFactory().getTrackingController().onApplicationScreen(ApplicationScreen.SEND_GENERIC_INVITE_MENU);
    }

    private void showUser(User user, View anchorView) {
        switch (user.getConnectionStatus()) {
            case ACCEPTED:
                if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
                    ArrayList<User> users = new ArrayList<>();
                    users.add(user);
                    getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION);
                } else {
                    // Go to 1:1 conversation
                    IConversation conversation = user.getConversation();
                    if (conversation != null) {
                        KeyboardUtils.hideKeyboard(getActivity());
                        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                                        ConversationChangeRequester.START_CONVERSATION);
                    }
                }
                break;
            case PENDING_FROM_USER:
            case BLOCKED:
            case IGNORED:
            case CANCELLED:
            case UNCONNECTED:
                KeyboardUtils.hideKeyboard(getActivity());
                getControllerFactory().getConversationScreenController().setPopoverLaunchedMode(DialogLaunchMode.SEARCH);
                getControllerFactory().getPickUserController().showUserProfile(user, anchorView);
                break;
            case PENDING_FROM_OTHER:
                // Show inbox
                KeyboardUtils.hideKeyboard(getActivity());
                // Load inbox requests to retrieve conversation
                pendingFromUser = user;
                getStoreFactory().getConversationStore().loadConnectRequestInboxConversations(this,
                                                                                              InboxLoadRequester.INBOX_SHOW_SPECIFIC);
                break;
        }
    }

    private void setConversationQuickMenuVisible(boolean show) {
        if (getView() == null ||
            getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }
        if (getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION)) {
            int numberOfSelectedUsers = getControllerFactory().getPickUserController().getSelectedUsers().size();
            boolean visible = getArguments().getBoolean(ARGUMENT_GROUP_CONVERSATION) ? numberOfSelectedUsers > 0 : numberOfSelectedUsers > 1;
            userSelectionConfirmationContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            boolean visible = show || getControllerFactory().getPickUserController().hasSelectedUsers();
            conversationQuickMenu.setVisibility(visible ? View.VISIBLE : View.GONE);
            genericInviteContainer.setVisibility(visible || isKeyboardVisible ? View.GONE : View.VISIBLE);

        }
    }

    private void updateConversationButtonLabel() {
        String label = getControllerFactory().getPickUserController().getSelectedUsers().size() > 1 ?
                       getString(R.string.conversation_quick_menu__conversation_button__group_label) :
                       getString(R.string.conversation_quick_menu__conversation_button__single_label);
        conversationQuickMenu.setConversationButtonText(label);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zb__pickuser__confirmation_button:
                KeyboardUtils.hideKeyboard(getActivity());
                List<User> users = getControllerFactory().getPickUserController().getSelectedUsers();
                getContainer().onSelectedUsers(users, ConversationChangeRequester.START_CONVERSATION);
                break;
            case R.id.zb__pickuser__generic_invite:
                sendGenericInvite(false);
                break;
            case R.id.ll_pickuser__error_invite:
                sendGenericInvite(true);
                break;
        }
    }

    private void setFocusByCurrentPickerDestination() {
        // Don't trigger setting focus in closed split mode on tablet portrait, search is not visible then
        if (LayoutSpec.isTablet(getActivity()) &&
            ViewUtils.isInPortrait(getActivity()) &&
            getControllerFactory().getNavigationController().getPagerPosition() == NavigationController.SECOND_PAGE) {
            return;
        }

        if (getCurrentPickerDestination() == IPickUserController.Destination.CONVERSATION_LIST &&
            (LayoutSpec.isTablet(getActivity()) || getControllerFactory().getNavigationController().getPagerPosition() == NavigationController.FIRST_PAGE)) {
            getControllerFactory().getFocusController().setFocus(IFocusController.CONVERSATION_LIST_SEARCHBOX);
        } else if (getCurrentPickerDestination() == IPickUserController.Destination.PARTICIPANTS) {
            getControllerFactory().getFocusController().setFocus(IFocusController.PARTICIPANTS_SEARCHBOX);
        }
    }

    private PickUserDataState getDataState(boolean hasSelectedUsers) {
        PickUserDataState dataState = PickUserDataState.SHOW_TOP_USERS_AND_RECOMMENDED;

        boolean isAddingToConversation = getArguments().getBoolean(ARGUMENT_ADD_TO_CONVERSATION);
        if (!TextUtils.isEmpty(getControllerFactory().getPickUserController().getSearchFilter())) {
            if (isAddingToConversation) {
                dataState = PickUserDataState.SHOW_SEARCH_RESULTS_TO_ADD_TO_CONVERSATION;
            } else {
                dataState = PickUserDataState.SHOW_SEARCH_RESULTS;
            }
        } else if (isAddingToConversation) {
            dataState = PickUserDataState.SHOW_ALL_USERS_TO_ADD_TO_CONVERSATION;
        } else if (hasSelectedUsers) {
            dataState = PickUserDataState.SHOW_TOP_USERS_AS_LIST;
        }

        return dataState;
    }

    private void closeStartUI() {
        KeyboardUtils.hideKeyboard(getActivity());
        getStoreFactory().getInAppNotificationStore().setUserLookingAtPeoplePicker(false);
        getControllerFactory().getPickUserController().setSearchFilter("");
        getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), true);
    }

    public interface Container {

        void showIncomingPendingConnectRequest(IConversation conversation);

        void onSelectedUsers(List<User> users, ConversationChangeRequester requester);

        LoadingIndicatorView getLoadingViewIndicator();

        IPickUserController.Destination getCurrentPickerDestination();
    }
}
