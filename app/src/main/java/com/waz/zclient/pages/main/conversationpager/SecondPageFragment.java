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
package com.waz.zclient.pages.main.conversationpager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.model.UserId;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.connect.ConnectRequestFragment;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestLoadMode;
import com.waz.zclient.pages.main.connect.PendingConnectRequestManagerFragment;
import com.waz.zclient.pages.main.conversation.ConversationManagerFragment;
import com.waz.zclient.ui.utils.MathUtils;

import timber.log.Timber;

public class SecondPageFragment extends BaseFragment<SecondPageFragment.Container> implements OnBackPressedListener,
                                                                                              ConversationStoreObserver,
                                                                                              ConversationManagerFragment.Container,
                                                                                              PagerControllerObserver,
                                                                                              PendingConnectRequestManagerFragment.Container,
                                                                                              UpdateListener,
                                                                                              ConnectRequestFragment.Container {
    public static final String TAG = SecondPageFragment.class.getName();
    private static final String SECOND_PAGE_POSITION = "SECOND_PAGE_POSITION";
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";


    private Page currentPage;
    private IConversation selectedConversation;
    private IConversation.Type selectedConversationType;

    public static SecondPageFragment newInstance() {
        return new SecondPageFragment();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isAdded()) {
            Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__second_page_container);
            if (fragment != null) {
                fragment.setUserVisibleHint(isVisibleToUser);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            currentPage = Page.NONE;
        } else {
            int pos = savedInstanceState.getInt(SECOND_PAGE_POSITION);
            currentPage = Page.values()[pos];
        }
        return inflater.inflate(R.layout.fragment_pager_second, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        getControllerFactory().getNavigationController().addPagerControllerObserver(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SECOND_PAGE_POSITION, currentPage.ordinal());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (selectedConversation != null) {
            selectedConversation.removeUpdateListener(this);
        }
        selectedConversation = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__second_page_container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                final IConversation toConversation,
                                                final ConversationChangeRequester conversationChangerSender) {
        if (selectedConversation != null) {
            selectedConversation.removeUpdateListener(this);
        }
        selectedConversation = null;

        if (toConversation == null) {
            return;
        }

        if (fromConversation != null &&
            fromConversation.getId().equals(toConversation.getId())) {
            return;
        }

        selectedConversation = toConversation;
        selectedConversation.addUpdateListener(this);
        selectedConversationType = toConversation.getType();

        Timber.i("Conversation: %s type: %s requester: %s",
                 toConversation,
                 toConversation.getType(),
                 conversationChangerSender);
        // either starting from beginning or switching fragment
        final boolean switchingToPendingConnectRequest = (toConversation.getType() == IConversation.Type.WAIT_FOR_CONNECTION);

        final boolean switchingToConnectRequestInbox = toConversation.getType() == IConversation.Type.INCOMING_CONNECTION;


        // This must be posted because onCurrentConversationHasChanged()
        // might still be running and iterating over the observers -
        // while the posted call triggers things to register/unregister
        // from the list of observers, causing ConcurrentModificationException
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (switchingToConnectRequestInbox) {
                    Bundle arguments = new Bundle();
                    arguments.putString(ARGUMENT_CONVERSATION_ID, toConversation.getId());
                    openPage(Page.CONNECT_REQUEST_INBOX, arguments);
                } else if (switchingToPendingConnectRequest) {
                    Bundle arguments = new Bundle();
                    arguments.putString(ARGUMENT_CONVERSATION_ID, toConversation.getId());
                    openPage(Page.CONNECT_REQUEST_PENDING, arguments);
                } else {
                    openPage(Page.MESSAGE_STREAM, new Bundle());
                }
            }
        });
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    private void openPage(Page page, Bundle arguments) {
        if (getContainer() == null || !isResumed()) {
            return;
        }
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__second_page_container);

        if (currentPage != null && currentPage.equals(page)) {
            // Scroll to a certain connect request in inbox
            if (fragment instanceof ConnectRequestFragment) {
                //TODO set a preference or something
                ((ConnectRequestFragment) fragment).setVisibleConnectRequest(new UserId(arguments.getString(ARGUMENT_CONVERSATION_ID)));
            }

            if (page != Page.CONNECT_REQUEST_PENDING) {
                return;
            }
        }
        currentPage = page;

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        switch (getControllerFactory().getNavigationController().getCurrentPage()) {
            case CONVERSATION_LIST:
                transaction.setCustomAnimations(R.anim.message_fade_in,
                                                R.anim.message_fade_out,
                                                R.anim.message_fade_in,
                                                R.anim.message_fade_out);
                break;
            case CONNECT_REQUEST_INBOX:
            case CONNECT_REQUEST_PENDING:
                transaction.setCustomAnimations(R.anim.fragment_animation_second_page_slide_in_from_right,
                                                R.anim.fragment_animation_second_page_slide_out_to_left);
                break;
        }

        Fragment pageFragment;
        String tag;
        switch (page) {
            case CONNECT_REQUEST_PENDING:
                getControllerFactory().getNavigationController().setRightPage(Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION,
                                                                              TAG);
                pageFragment = PendingConnectRequestManagerFragment.newInstance(null,
                                                                                arguments.getString(
                                                                                    ARGUMENT_CONVERSATION_ID),
                                                                                ConnectRequestLoadMode.LOAD_BY_CONVERSATION_ID,
                                                                                IConnectStore.UserRequester.CONVERSATION);
                tag = PendingConnectRequestManagerFragment.TAG;
                break;
            case CONNECT_REQUEST_INBOX:
                getControllerFactory().getNavigationController().setRightPage(Page.CONNECT_REQUEST_INBOX, TAG);
                pageFragment = ConnectRequestFragment.newInstance(arguments.getString(ARGUMENT_CONVERSATION_ID));
                tag = ConnectRequestFragment.FragmentTag();
                break;
            case MESSAGE_STREAM:
                getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
                pageFragment = ConversationManagerFragment.newInstance();
                tag = ConversationManagerFragment.TAG;
                break;
            case NONE:
            default:
                return;
        }

        transaction.replace(R.id.fl__second_page_container, pageFragment, tag).commit();
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__second_page_container);
        return fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //   PagerControllerObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (position == 0 || MathUtils.floatEqual(positionOffset, 0f)) {
            getView().setAlpha(1f);
        } else {
            getView().setAlpha((float) Math.pow(positionOffset, 4));
        }
    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UserProfile
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAcceptedConnectRequest(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.CONVERSATION_LIST);
    }

    @Override
    public void onAcceptedPendingOutgoingConnectRequest(IConversation conversation) {
        getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                        ConversationChangeRequester.CONNECT_REQUEST_ACCEPTED);
    }

    @Override
    public void dismissInboxFragment() {
        getControllerFactory().getNavigationController().setVisiblePage(Page.CONVERSATION_LIST, TAG);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UpdateListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void updated() {
        if ((selectedConversationType == IConversation.Type.INCOMING_CONNECTION ||
             selectedConversationType == IConversation.Type.WAIT_FOR_CONNECTION) &&
            selectedConversation.getType() == IConversation.Type.ONE_TO_ONE) {
            openPage(Page.MESSAGE_STREAM, new Bundle());
        }
        selectedConversationType = selectedConversation.getType();
    }

    @Override
    public void onPagerEnabledStateHasChanged(boolean enabled) {

    }

    @Override
    public void dismissUserProfile() {

    }

    @Override
    public void dismissSingleUserProfile() {

    }

    @Override
    public void showRemoveConfirmation(User user) {

    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
