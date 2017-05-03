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
package com.waz.zclient.controllers.navigation;

import android.content.Context;
import android.os.Bundle;
import android.util.Property;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.calling.enums.VoiceBarAppearance;
import com.waz.zclient.utils.LayoutSpec;
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
    private static final String SAVED_INSTANCE_VOICE_BAR_APPEARANCE_CONVERSATION_LIST = "SAVED_INSTANCE_VOICE_BAR_APPEARANCE_CONVERSATION_LIST";
    private static final String SAVED_INSTANCE_VOICE_BAR_APPEARANCE_MESSAGE_STREAM = "SAVED_INSTANCE_VOICE_BAR_APPEARANCE_MESSAGE_STREAM";
    private static final String SAVED_INSTANCE_SCREEN_OFFSET_X = "SAVED_INSTANCE_SCREEN_OFFSET_X";
    private static final String SAVED_INSTANCE_SCREEN_OFFSET_Y = "SAVED_INSTANCE_SCREEN_OFFSET_Y";
    private static final String SAVED_INSTANCE_PAGER_ENABLE_STATE = "SAVED_INSTANCE_PAGER_ENABLE_STATE";
    public static final Property<INavigationController, Integer> SCREEN_OFFSET_Y = new Property<INavigationController, Integer>(Integer.class, "screenOffsetY") {
        @Override
        public Integer get(INavigationController object) {
            return object.getScreenOffsetY();
        }

        @Override
        public void set(INavigationController object, Integer value) {
            object.setScreenOffsetY(value);
        }
    };
    public static final String PAGER_TAG = "Pager";

    private final int maxOffsetX;
    private final int maxOffsetY;
    private int screenOffsetX;
    private int screenOffsetY;

    private Set<NavigationControllerObserver> navigationControllerObservers;
    private Set<PagerControllerObserver> pagerControllerObservers;
    private Set<ScreenPositionObserver> screenPositionObservers;

    private Page currentPage;
    private Page lastPageLeft;
    private Page lastPageRight;
    private int currentPagerPos;
    private VoiceBarAppearance voiceBarAppearanceConversationList;
    private VoiceBarAppearance voiceBarAppearanceMessageStream;

    private boolean isPagerEnabled;
    private boolean isInLandscape;
    private boolean isPhone;
    private boolean wasPaused;

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

    @Override
    public void addScreenPositionObserver(ScreenPositionObserver screenPositionObserver) {
        screenPositionObservers.add(screenPositionObserver);
        screenPositionObserver.onScreenPositionChanged(screenOffsetX, screenOffsetY);
    }

    @Override
    public void removeScreenPositionObserver(ScreenPositionObserver screenPositionObserver) {
        screenPositionObservers.remove(screenPositionObserver);
    }

    public NavigationController(Context context) {
        isPhone = LayoutSpec.isPhone(context);

        maxOffsetX = context.getResources().getDimensionPixelSize(R.dimen.background__max_offset_x);
        maxOffsetY = context.getResources().getDimensionPixelSize(R.dimen.background__max_offset_y);

        navigationControllerObservers = new HashSet<>();
        pagerControllerObservers = new HashSet<>();
        screenPositionObservers = new HashSet<>();

        currentPage = Page.START;
        lastPageLeft = Page.START;
        lastPageRight = Page.START;

        currentPagerPos = FIRST_PAGE;

        voiceBarAppearanceConversationList = VoiceBarAppearance.MICRO;
        voiceBarAppearanceMessageStream = VoiceBarAppearance.FULL;
    }

    @Override
    public VoiceBarAppearance getVoiceBarAppearanceConversationList() {
        return voiceBarAppearanceConversationList;
    }

    @Override
    public VoiceBarAppearance getVoiceBarAppearanceMessageStream() {
        return voiceBarAppearanceMessageStream;
    }

    @Override
    public void setConversationListState(VoiceBarAppearance voiceBarAppearance) {
        if (voiceBarAppearanceConversationList == voiceBarAppearance) {
            return;
        }
        voiceBarAppearanceConversationList = voiceBarAppearance;

        if (currentPage == Page.CONVERSATION_LIST) {
            for (NavigationControllerObserver navigationControllerObserver : navigationControllerObservers) {
                navigationControllerObserver.onPageStateHasChanged(Page.CONVERSATION_LIST);
            }
        }
    }

    @Override
    public void setMessageStreamState(VoiceBarAppearance voiceBarAppearance) {
        if (voiceBarAppearanceMessageStream == voiceBarAppearance) {
            return;
        }

        voiceBarAppearanceMessageStream = voiceBarAppearance;

        if (currentPage == Page.MESSAGE_STREAM) {
            for (NavigationControllerObserver navigationControllerObserver : navigationControllerObservers) {
                navigationControllerObserver.onPageStateHasChanged(Page.MESSAGE_STREAM);
            }
        }
    }

    @Override
    public void setVisiblePage(Page page, String sender) {
        Timber.i("Page: %s Sender: %s", page, sender);
        if (currentPage == page && !isInLandscape) {
            return;
        }

        currentPage = page;

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

        if (isPhone) {
            if (currentPagerPos == FIRST_PAGE) {
                setVisiblePage(leftPage, sender);
            }
        } else {
            if (currentPagerPos == FIRST_PAGE || isInLandscape) {
                setVisiblePage(leftPage, sender);
            }
        }
    }

    @Override
    public void setRightPage(Page rightPage, String sender) {
        lastPageRight = rightPage;

        if (isPhone) {
            if (currentPagerPos == SECOND_PAGE) {
                setVisiblePage(rightPage, sender);
            }
        } else {
            if (currentPagerPos == SECOND_PAGE || isInLandscape) {
                setVisiblePage(rightPage, sender);
            }
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
        voiceBarAppearanceMessageStream = VoiceBarAppearance.values()[savedInstanceState.getInt(
            SAVED_INSTANCE_VOICE_BAR_APPEARANCE_MESSAGE_STREAM)];
        voiceBarAppearanceConversationList = VoiceBarAppearance.values()[savedInstanceState.getInt(
            SAVED_INSTANCE_VOICE_BAR_APPEARANCE_CONVERSATION_LIST)];
        screenOffsetX = savedInstanceState.getInt(SAVED_INSTANCE_SCREEN_OFFSET_X);
        screenOffsetY = savedInstanceState.getInt(SAVED_INSTANCE_SCREEN_OFFSET_Y);
        isPagerEnabled = savedInstanceState.getBoolean(SAVED_INSTANCE_PAGER_ENABLE_STATE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVED_INSTANCE_CURRENT_PAGER_POSITION, currentPagerPos);
        outState.putInt(SAVED_INSTANCE_CURRENT_PAGE, currentPage.ordinal());
        outState.putInt(SAVED_INSTANCE_CURRENT_LEFT_PAGE, lastPageLeft.ordinal());
        outState.putInt(SAVED_INSTANCE_CURRENT_RIGHT_PAGE, lastPageRight.ordinal());
        outState.putInt(SAVED_INSTANCE_VOICE_BAR_APPEARANCE_MESSAGE_STREAM, voiceBarAppearanceMessageStream.ordinal());
        outState.putInt(SAVED_INSTANCE_VOICE_BAR_APPEARANCE_CONVERSATION_LIST,
                        voiceBarAppearanceConversationList.ordinal());
        outState.putInt(SAVED_INSTANCE_SCREEN_OFFSET_X, screenOffsetX);
        outState.putInt(SAVED_INSTANCE_SCREEN_OFFSET_Y, screenOffsetY);
        outState.putBoolean(SAVED_INSTANCE_PAGER_ENABLE_STATE, isPagerEnabled);
    }

    @Override
    public void setScreenOffsetX(int offset) {
        if (offset > maxOffsetX) {
            offset = maxOffsetX;
        }
        screenOffsetX = offset;
        notifyScreenPositionHasChanged();
    }

    @Override
    public void setScreenOffsetY(int offset) {
        if (offset > maxOffsetY) {
            offset = maxOffsetY;
        }
        screenOffsetY = offset;
        notifyScreenPositionHasChanged();
    }

    @Override
    public int getScreenOffsetX() {
        return screenOffsetX;
    }

    @Override
    public int getScreenOffsetY() {
        return screenOffsetY;
    }

    @Override
    public void setScreenOffsetYFactor(float factorY) {
        if (factorY > 1) {
            factorY = 1.0f;
        }

        setScreenOffsetY((int) (factorY * maxOffsetY));
    }

    @Override
    public int getMaxScreenOffsetY() {
        return maxOffsetY;
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
                if (isPhone) {
                    // Handled in ConversationListManagerFragment
                    return;
                }
                setPagerEnabled(true);
                break;
            case SELF_PROFILE_OVERLAY:
            case CAMERA:
            case CONFIRMATION_DIALOG:
            case SINGLE_MESSAGE:
            case DRAWING:
            case SHARE_LOCATION:
            case COLLECTION:
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
                if (isPhone) {
                    setPagerEnabled(false);
                }
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
    public boolean isActivityResuming() {
        return wasPaused;
    }

    @Override
    public void markActivityPaused() {
        wasPaused = true;
    }

    @Override
    public void markActivityResumed() {
        wasPaused = false;
    }

    @Override
    public void tearDown() {
        navigationControllerObservers.clear();
        pagerControllerObservers.clear();
        screenPositionObservers.clear();

        currentPage = Page.START;
        lastPageLeft = Page.START;
        lastPageRight = Page.START;
        currentPagerPos = FIRST_PAGE;

        voiceBarAppearanceConversationList = VoiceBarAppearance.MICRO;
        voiceBarAppearanceMessageStream = VoiceBarAppearance.FULL;
    }

    private void notifyScreenPositionHasChanged() {
        for (ScreenPositionObserver screenPositionObserver : screenPositionObservers) {
            screenPositionObserver.onScreenPositionChanged(screenOffsetX, screenOffsetY);
        }
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

        float offset = 0;
        switch (position) {
            case 0:
                offset = -(positionOffset * maxOffsetX);
                break;
            case 1:
                offset = -maxOffsetX + (positionOffset * maxOffsetX);
                break;
        }

        screenOffsetX = (int) offset;
        notifyScreenPositionHasChanged();
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

    private void log() {
        Timber.i("leftPage: %s", lastPageLeft);
        Timber.i("rightPage: %s", lastPageRight);
        Timber.i("currentPage: %s", currentPage);
        Timber.i("voiceBarAppearanceConversationList: %s", voiceBarAppearanceConversationList);
        Timber.i("voiceBarAppearanceMessageStream: %s", voiceBarAppearanceMessageStream);
    }
}
