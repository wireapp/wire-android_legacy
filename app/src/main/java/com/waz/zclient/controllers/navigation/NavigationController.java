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
package com.waz.zclient.controllers.navigation;

import android.content.Context;
import android.os.Bundle;
import timber.log.Timber;

import java.util.HashSet;
import java.util.Set;

public class NavigationController implements INavigationController {
    public static final String TAG = NavigationController.class.getName();
    public static final int FIRST_PAGE = 0;
    public static final int SECOND_PAGE = 1;
    private static final String SAVED_INSTANCE_CURRENT_PAGER_POSITION = "SAVED_INSTANCE_CURRENT_PAGER_POSITION";
    private static final String SAVED_INSTANCE_CURRENT_PAGE = "SAVED_INSTANCE_CURRENT_PAGE";
    private static final String SAVED_INSTANCE_CURRENT_LEFT_PAGE = "SAVED_INSTANCE_CURRENT_LEFT_PAGE";
    private static final String SAVED_INSTANCE_CURRENT_RIGHT_PAGE = "SAVED_INSTANCE_CURRENT_RIGHT_PAGE";
    private static final String SAVED_INSTANCE_PAGER_ENABLE_STATE = "SAVED_INSTANCE_PAGER_ENABLE_STATE";
    public static final String PAGER_TAG = "Pager";

    private Set<NavigationControllerObserver> navigationControllerObservers;
    private Set<PagerControllerObserver> pagerControllerObservers;

    private Page currentPage;
    private Page lastPageLeft;
    private Page lastPageRight;
    private int currentPagerPos;

    private boolean isPagerEnabled;
    private boolean isInLandscape;

    @Override
    public void addNavigationControllerObserver(NavigationControllerObserver navigationControllerObserver) {
        navigationControllerObservers.add(navigationControllerObserver);
        navigationControllerObserver.onPageVisible(currentPage);
    }

    @Override
    public void removeNavigationControllerObserver(NavigationControllerObserver navigationControllerObserver) {
        navigationControllerObservers.remove(navigationControllerObserver);
    }

    @Override
    public void addPagerControllerObserver(PagerControllerObserver pagerControllerObserver) {
        pagerControllerObservers.add(pagerControllerObserver);
    }

    @Override
    public void removePagerControllerObserver(PagerControllerObserver pagerControllerObserver) {
        pagerControllerObservers.remove(pagerControllerObserver);
    }

    public NavigationController(Context context) {
        navigationControllerObservers = new HashSet<>();
        pagerControllerObservers = new HashSet<>();

        currentPage = Page.START;
        lastPageLeft = Page.START;
        lastPageRight = Page.START;

        currentPagerPos = FIRST_PAGE;
    }

    @Override
    public void setVisiblePage(Page page, String sender) {
        Timber.i("Page: %s Sender: %s", page, sender);
        if (currentPage == page && !isInLandscape) {
            return;
        }

        currentPage = page;

        setPagerSettingForPage(page);
        for (NavigationControllerObserver navigationControllerObserver : navigationControllerObservers) {
            navigationControllerObserver.onPageVisible(page);
        }
    }

    @Override
    public void setPagerPosition(int position) {
        if (currentPagerPos == position) {
            return;
        }

        currentPagerPos = position;
        if (currentPagerPos == 0) {
            setVisiblePage(lastPageLeft, PAGER_TAG);
        } else {
            setVisiblePage(lastPageRight, PAGER_TAG);
        }
    }

    @Override
    public int getPagerPosition() {
        return currentPagerPos;
    }

    @Override
    public void resetPagerPositionToDefault() {
        currentPagerPos = FIRST_PAGE;
    }

    @Override
    public void setLeftPage(Page leftPage, String sender) {
        lastPageLeft = leftPage;
        setPage(leftPage, sender, FIRST_PAGE);
    }

    @Override
    public void setRightPage(Page rightPage, String sender) {
        lastPageRight = rightPage;
        setPage(rightPage, sender, SECOND_PAGE);
    }

    private void setPage(Page page, String sender, int pageIndex) {
        if (currentPagerPos == pageIndex) {
            setVisiblePage(page, sender);
        }
    }

    @Override
    public Page getCurrentPage() {
        return currentPage;
    }

    @Override
    public Page getCurrentLeftPage() {
        return lastPageLeft;
    }

    @Override
    public Page getCurrentRightPage() {
        return lastPageRight;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        currentPagerPos = savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_PAGER_POSITION);
        currentPage = Page.values()[savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_PAGE)];
        lastPageLeft = Page.values()[savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_LEFT_PAGE)];
        lastPageRight = Page.values()[savedInstanceState.getInt(SAVED_INSTANCE_CURRENT_RIGHT_PAGE)];
        isPagerEnabled = savedInstanceState.getBoolean(SAVED_INSTANCE_PAGER_ENABLE_STATE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVED_INSTANCE_CURRENT_PAGER_POSITION, currentPagerPos);
        outState.putInt(SAVED_INSTANCE_CURRENT_PAGE, currentPage.ordinal());
        outState.putInt(SAVED_INSTANCE_CURRENT_LEFT_PAGE, lastPageLeft.ordinal());
        outState.putInt(SAVED_INSTANCE_CURRENT_RIGHT_PAGE, lastPageRight.ordinal());
        outState.putBoolean(SAVED_INSTANCE_PAGER_ENABLE_STATE, isPagerEnabled);
    }

    @Override
    public void setPagerEnabled(boolean enabled) {
        Timber.i("setPagerEnabled(%b)", enabled);
        if (enabled && getCurrentRightPage() == Page.PARTICIPANT) {
            Timber.i("ignoring setPagerEnabled()");
            return;
        }
        isPagerEnabled = enabled;
        for (PagerControllerObserver pagerControllerObserver : pagerControllerObservers) {
            pagerControllerObserver.onPagerEnabledStateHasChanged(enabled);
        }
    }

    @Override
    public void setPagerSettingForPage(Page page) {
        switch (page) {
            case CONVERSATION_LIST:
                setPagerEnabled(false);
                return;
            case SELF_PROFILE_OVERLAY:
            case CAMERA:
            case CONFIRMATION_DIALOG:
            case SINGLE_MESSAGE:
            case DRAWING:
            case SHARE_LOCATION:
            case COLLECTION:
            case ARCHIVE:
                setPagerEnabled(false);
                break;
            case CONVERSATION_MENU_OVER_CONVERSATION_LIST:
            case PARTICIPANT:
            case PARTICIPANT_USER_PROFILE:
            case PICK_USER:
            case COMMON_USER_PROFILE:
            case SEND_CONNECT_REQUEST:
            case PENDING_CONNECT_REQUEST:
            case BLOCK_USER:
            case PICK_USER_ADD_TO_CONVERSATION:
            case INTEGRATION_DETAILS:
                setPagerEnabled(false);
                break;
            default:
                setPagerEnabled(true);
        }
    }

    @Override
    public boolean isPagerEnabled() {
        return isPagerEnabled;
    }

    @Override
    public void setIsLandscape(boolean isLandscape) {
        this.isInLandscape = isLandscape;
    }

    @Override
    public void tearDown() {
        navigationControllerObservers.clear();
        pagerControllerObservers.clear();

        currentPage = Page.START;
        lastPageLeft = Page.START;
        lastPageRight = Page.START;
        currentPagerPos = FIRST_PAGE;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Pager
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        for (PagerControllerObserver pagerControllerObserver : pagerControllerObservers) {
            pagerControllerObserver.onPageScrolled(position, positionOffset, positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        for (PagerControllerObserver pagerControllerObserver : pagerControllerObservers) {
            pagerControllerObserver.onPageSelected(position);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        for (PagerControllerObserver pagerControllerObserver : pagerControllerObservers) {
            pagerControllerObserver.onPageScrollStateChanged(state);
        }
    }

}
