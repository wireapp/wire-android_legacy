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
package com.waz.zclient.pages.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.singleimage.SingleImageObserver;
import com.waz.zclient.controllers.usernames.UsernamesControllerObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.backgroundmain.views.BackgroundFrameLayout;
import com.waz.zclient.pages.main.conversation.SingleImageFragment;
import com.waz.zclient.pages.main.conversation.SingleImageMessageFragment;
import com.waz.zclient.pages.main.conversation.SingleImageUserFragment;
import com.waz.zclient.pages.main.conversation.VideoPlayerFragment;
import com.waz.zclient.pages.main.inappnotification.InAppNotificationFragment;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.menus.ConfirmationMenu;


public class MainTabletFragment extends BaseFragment<MainTabletFragment.Container> implements
        OnBackPressedListener,
        InAppNotificationFragment.Container,
        RootFragment.Container,
        SingleImageObserver,
        SingleImageFragment.Container,
        ConfirmationObserver,
        AccentColorObserver,
        UsernamesControllerObserver {

    public static final String TAG = MainTabletFragment.class.getName();
    private static final String ARG_LOCK_EXPANDED = "ARG_LOCK_EXPANDED";

    private ConfirmationMenu confirmationMenu;
    private BackgroundFrameLayout backgroundLayout;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    @SuppressLint("MissingSuperCall")
    public void onConfigurationChanged(Configuration newConfig) {
        confirmationMenu.adjustLayout();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_landscape, container, false);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

            transaction.add(R.id.fl_fragment_main_in_app_notification,
                            InAppNotificationFragment.newInstance(),
                            InAppNotificationFragment.TAG);

            transaction.add(R.id.fl_fragment_main_root_container,
                            RootFragment.newInstance(),
                            RootFragment.TAG);

            transaction.commit();
        }

        backgroundLayout = ViewUtils.getView(view, R.id.bl__background);
        confirmationMenu = ViewUtils.getView(view, R.id.cm__confirm_action_light);
        confirmationMenu.setNoRoundBackground();
        confirmationMenu.setVisibility(View.GONE);

        if (savedInstanceState != null) {
            backgroundLayout.onScaleToMax(savedInstanceState.getBoolean(ARG_LOCK_EXPANDED));
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getSingleImageController().addSingleImageObserver(this);
        getControllerFactory().getConfirmationController().addConfirmationObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(backgroundLayout);
        getControllerFactory().getBackgroundController().addBackgroundObserver(backgroundLayout);
        getControllerFactory().getUsernameController().addUsernamesObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getConfirmationController().removeConfirmationObserver(this);
        getControllerFactory().getSingleImageController().removeSingleImageObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(backgroundLayout);
        getControllerFactory().getBackgroundController().removeBackgroundObserver(backgroundLayout);
        getControllerFactory().getUsernameController().removeUsernamesObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        confirmationMenu = null;
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl_fragment_main_root_container);
        if (fragment != null) {
            fragment.onActivityResult(requestCode,
                                      resultCode,
                                      data);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public boolean onBackPressed() {
        if (confirmationMenu.getVisibility() == View.VISIBLE) {
            confirmationMenu.animateToShow(false);
            return true;
        }

        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            Fragment topFragment = getChildFragmentManager().findFragmentByTag(getChildFragmentManager().getBackStackEntryAt(0).getName());
            if (topFragment instanceof SingleImageFragment) {
                return ((SingleImageFragment) topFragment).onBackPressed();
            }
        }

        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl_fragment_main_root_container);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        return getChildFragmentManager().popBackStackImmediate();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Stores
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowSingleImage(Message message) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      SingleImageMessageFragment.newInstance(message),
                                      SingleImageMessageFragment.TAG)
                                 .addToBackStack(SingleImageMessageFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.SINGLE_MESSAGE, TAG);
        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.IMAGE_CONTENT_CLICKS);
    }

    @Override
    public void onShowUserImage(User user) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      SingleImageUserFragment.newInstance(user),
                                      SingleImageUserFragment.TAG)
                                 .addToBackStack(SingleImageUserFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.SINGLE_MESSAGE, TAG);
        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.IMAGE_CONTENT_CLICKS);
    }

    @Override
    public void onHideSingleImage() {

    }

    @Override
    public void updateSingleImageReferences() {

    }

    @Override
    public void onShowVideo(Uri uri) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      VideoPlayerFragment.newInstance(uri),
                                      VideoPlayerFragment.TAG)
                                 .addToBackStack(VideoPlayerFragment.TAG)
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.SINGLE_MESSAGE, TAG);
    }

    @Override
    public void onHideVideo() {
        getChildFragmentManager().popBackStackImmediate(VideoPlayerFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(ARG_LOCK_EXPANDED, backgroundLayout.isExpanded());
    }

    @Override
    public void onRequestConfirmation(ConfirmationRequest confirmationRequest, @IConfirmationController.ConfirmationMenuRequester int requester) {
        if (requester != IConfirmationController.CONVERSATION) {
            return;
        }
        confirmationMenu.onRequestConfirmation(confirmationRequest);
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        confirmationMenu.setButtonColor(color);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  UsernamesObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onValidUsernameGenerated(String name, String generatedUsername) {
        RootFragment fragment = (RootFragment) getChildFragmentManager().findFragmentByTag(RootFragment.TAG);
        if (fragment != null) {
            fragment.showFirstTimeAssignUsername(name, generatedUsername);
        }
    }

    @Override
    public void onUsernameAttemptsExhausted(String name) {
        RootFragment fragment = (RootFragment) getChildFragmentManager().findFragmentByTag(RootFragment.TAG);
        if (fragment != null) {
            fragment.showFirstTimeAssignUsername(name, "");
        }
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
