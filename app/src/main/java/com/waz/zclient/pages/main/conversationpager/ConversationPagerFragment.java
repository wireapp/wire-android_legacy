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
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.SyncState;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.conversation.CollectionController;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.utils.LayoutSpec;

public class ConversationPagerFragment extends BaseFragment<ConversationPagerFragment.Container> implements ConversationStoreObserver,
                                                                                                            OnBackPressedListener,
                                                                                                            PagerControllerObserver,
                                                                                                            NavigationControllerObserver,
                                                                                                            FirstPageFragment.Container,
                                                                                                            SecondPageFragment.Container {
    public static final String TAG = ConversationPagerFragment.class.getName();
    private static final int PAGER_DELAY = 150;

    private static final int OFFSCREEN_PAGE_LIMIT = 2;

    public static final double VIEW_PAGER_SCROLL_FACTOR_SCROLLING = 1;


    // The adapter that reacts on the type of conversation.
    private ConversationPagerAdapter conversationPagerAdapter;

    private ConversationViewPager conversationPager;

    public static ConversationPagerFragment newInstance() {
        return new ConversationPagerFragment();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        conversationPager = new ConversationViewPager(getActivity());
        conversationPager.setScrollDurationFactor(VIEW_PAGER_SCROLL_FACTOR_SCROLLING);
        conversationPager.setId(R.id.conversation_pager);
        conversationPager.setOffscreenPageLimit(OFFSCREEN_PAGE_LIMIT);
        conversationPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        conversationPager.setPageTransformer(false, new CustomPagerTransformer(CustomPagerTransformer.SLIDE_IN));
        conversationPagerAdapter = new ConversationPagerAdapter(getActivity(),
                                                                getChildFragmentManager(),
                                                                LayoutSpec.get(getActivity()),
                                                                ResourceUtils.getResourceFloat(getResources(),
                                                                                               R.dimen.framework__first_page__percentage));
        conversationPager.setAdapter(conversationPagerAdapter);

        if (this.getControllerFactory().getUserPreferencesController().showContactsDialog()) {
            conversationPager.setCurrentItem(NavigationController.FIRST_PAGE);
        }

        return conversationPager;
    }

    @Override
    public void onStart() {
        super.onStart();

        getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        conversationPager.setOnPageChangeListener(getControllerFactory().getNavigationController());

        conversationPager.setEnabled(getControllerFactory().getNavigationController().isPagerEnabled());

        getControllerFactory().getNavigationController().addPagerControllerObserver(this);
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        conversationPager.setOnPageChangeListener(null);
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Fragment fragment = conversationPagerAdapter.getFragment(conversationPager.getCurrentItem());

        if (fragment != null) {
            fragment.onActivityResult(requestCode, resultCode, data);
        } else {
            for (Fragment loadedFragment : getChildFragmentManager().getFragments()) {
                loadedFragment.onActivityResult(requestCode, resultCode, data);
            }
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
                                                ConversationChangeRequester conversationChangeRequester) {
        switch (conversationChangeRequester) {
            case ARCHIVED_RESULT:
            case FIRST_LOAD:
                break;
            case START_CONVERSATION_FOR_CALL:
            case START_CONVERSATION_FOR_VIDEO_CALL:
            case START_CONVERSATION_FOR_CAMERA:
            case START_CONVERSATION:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.SECOND_PAGE, false);
                    }
                }, PAGER_DELAY);
                break;
            case INVITE:
            case DELETE_CONVERSATION:
            case LEAVE_CONVERSATION:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.FIRST_PAGE, false);
                    }
                }, PAGER_DELAY);
                break;
            case UPDATER:
                break;
            case CONVERSATION_LIST_UNARCHIVED_CONVERSATION:
            case CONVERSATION_LIST:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.SECOND_PAGE);
                    }
                }, PAGER_DELAY);
                break;
            case INBOX:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.SECOND_PAGE);
                    }
                }, PAGER_DELAY);
                break;
            case BLOCK_USER:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.FIRST_PAGE);
                    }
                }, PAGER_DELAY);
                break;
            case ONGOING_CALL:
            case TRANSFER_CALL:
            case INCOMING_CALL:
            case SHARING:
            case NOTIFICATION:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.SECOND_PAGE);
                    }
                }, PAGER_DELAY);
                break;
        }
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public boolean onBackPressed() {
        // ask children if they want it
        Fragment fragment = getCurrentPagerFragment();
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        // at least back to first page
        if (conversationPager.getCurrentItem() > 0) {
            conversationPager.setCurrentItem(conversationPager.getCurrentItem() - 1);
            return true;
        }

        return false;
    }

    private Fragment getCurrentPagerFragment() {
        return conversationPagerAdapter.getFragment(conversationPager.getCurrentItem());
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (position == 1) {
            getControllerFactory().getLoadTimeLoggerController().conversationPageVisible();
        }
    }

    @Override
    public void onPageSelected(int position) {
        conversationPager.setScrollDurationFactor(VIEW_PAGER_SCROLL_FACTOR_SCROLLING);
        getControllerFactory().getNavigationController().setPagerPosition(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING &&
            getControllerFactory().getGlobalLayoutController().isKeyboardVisible()) {
            KeyboardUtils.hideKeyboard(getActivity());
        }
    }

    @Override
    public void onPagerEnabledStateHasChanged(boolean enabled) {
        conversationPager.setEnabled(enabled);
    }

    @Override
    public void onPageVisible(Page page) {
        if (page == Page.CONVERSATION_LIST) {
            getCollectionController().clearSearch();
        }
        if (page == Page.CONVERSATION_LIST &&
            getControllerFactory().getNavigationController()
                                  .getPagerPosition() == NavigationController.SECOND_PAGE) {
            conversationPager.setCurrentItem(NavigationController.FIRST_PAGE);
        }
    }

    private CollectionController getCollectionController() {
        return ((BaseActivity) getActivity()).injectJava(CollectionController.class);
    }

    @Override
    public void onPageStateHasChanged(Page page) {

    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
