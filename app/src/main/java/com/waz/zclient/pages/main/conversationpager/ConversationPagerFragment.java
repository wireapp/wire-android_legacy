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
package com.waz.zclient.pages.main.conversationpager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.collection.controllers.CollectionController;
import com.waz.zclient.controllers.navigation.NavigationController;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.cursor.CursorController;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.utils.Callback;

public class ConversationPagerFragment extends BaseFragment<ConversationPagerFragment.Container> implements OnBackPressedListener,
                                                                                                            PagerControllerObserver,
                                                                                                            NavigationControllerObserver {
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
        conversationPagerAdapter = new ConversationPagerAdapter(getChildFragmentManager());
        conversationPager.setAdapter(conversationPagerAdapter);

        return conversationPager;
    }

    private final Callback callback = new Callback<ConversationController.ConversationChange>() {
        @Override
        public void callback(ConversationController.ConversationChange change) {
            onCurrentConversationHasChanged(change);
        }
    };

    @Override
    public void onStart() {
        super.onStart();

        conversationPager.setOnPageChangeListener(getControllerFactory().getNavigationController());

        conversationPager.setEnabled(getControllerFactory().getNavigationController().isPagerEnabled());

        getControllerFactory().getNavigationController().addPagerControllerObserver(this);
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);

        inject(ConversationController.class).addConvChangedCallback(callback);
    }

    @Override
    public void onStop() {
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        conversationPager.setOnPageChangeListener(null);
        inject(ConversationController.class).removeConvChangedCallback(callback);
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

    private void onCurrentConversationHasChanged(ConversationController.ConversationChange change) {
        switch (change.requester()) {
            case ARCHIVED_RESULT:
            case FIRST_LOAD:
            case UPDATER:
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
            case DELETE_CONVERSATION:
                conversationPagerAdapter.notifyDataSetChanged();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.FIRST_PAGE, false);

                    }
                }, PAGER_DELAY);
                break;
            case INVITE:
            case LEAVE_CONVERSATION:
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        conversationPager.setCurrentItem(NavigationController.FIRST_PAGE, false);

                    }
                }, PAGER_DELAY);
                break;
            case CONVERSATION_LIST_UNARCHIVED_CONVERSATION:
            case CONVERSATION_LIST:
            case INBOX:
            case ONGOING_CALL:
            case TRANSFER_CALL:
            case INCOMING_CALL:
            case INTENT:
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
        }
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
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

    @Override
    public void onPageSelected(int position) {
        conversationPager.setScrollDurationFactor(VIEW_PAGER_SCROLL_FACTOR_SCROLLING);
        getControllerFactory().getNavigationController().setPagerPosition(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING &&
            getControllerFactory().getGlobalLayoutController().isKeyboardVisible()) {
            getCursorController().notifyKeyboardVisibilityChanged(false);
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
            getCursorController().notifyKeyboardVisibilityChanged(false);

            if (getControllerFactory().getNavigationController()
                    .getPagerPosition() == NavigationController.SECOND_PAGE) {
                conversationPager.setCurrentItem(NavigationController.FIRST_PAGE);
            }
        }
    }

    private CollectionController getCollectionController() {
        return ((BaseActivity) getActivity()).injectJava(CollectionController.class);
    }

    private CursorController getCursorController() {
        return ((BaseActivity) getActivity()).injectJava(CursorController.class);
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
