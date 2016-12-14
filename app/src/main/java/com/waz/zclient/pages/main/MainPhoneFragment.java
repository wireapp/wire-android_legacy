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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.giphy.GiphyObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.onboarding.OnboardingControllerObserver;
import com.waz.zclient.controllers.singleimage.SingleImageObserver;
import com.waz.zclient.conversation.CollectionFragment;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.backgroundmain.views.BackgroundFrameLayout;
import com.waz.zclient.pages.main.conversation.SingleImageFragment;
import com.waz.zclient.pages.main.conversation.SingleImageMessageFragment;
import com.waz.zclient.pages.main.conversation.SingleImageUserFragment;
import com.waz.zclient.pages.main.conversation.VideoPlayerFragment;
import com.waz.zclient.pages.main.conversationpager.ConversationPagerFragment;
import com.waz.zclient.pages.main.giphy.GiphySharingPreviewFragment;
import com.waz.zclient.pages.main.inappnotification.InAppNotificationFragment;
import com.waz.zclient.pages.main.onboarding.OnBoardingHintFragment;
import com.waz.zclient.pages.main.onboarding.OnBoardingHintType;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.menus.ConfirmationMenu;

public class MainPhoneFragment extends BaseFragment<MainPhoneFragment.Container> implements OnBackPressedListener,
                                                                                            ConversationPagerFragment.Container,
                                                                                            InAppNotificationFragment.Container,
                                                                                            OnBoardingHintFragment.Container,
                                                                                            OnboardingControllerObserver,
                                                                                            SingleImageObserver,
                                                                                            SingleImageFragment.Container,
                                                                                            GiphyObserver,
                                                                                            ConfirmationObserver,
                                                                                            AccentColorObserver {

    public static final String TAG = MainPhoneFragment.class.getName();
    private ConfirmationMenu confirmationMenu;
    private BackgroundFrameLayout backgroundLayout;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction().add(R.id.fl_fragment_main_in_app_notification,
                                                             InAppNotificationFragment.newInstance(),
                                                             InAppNotificationFragment.TAG).commit();
            getChildFragmentManager().beginTransaction().replace(R.id.fl_fragment_main_content,
                                                                 ConversationPagerFragment.newInstance(),
                                                                 ConversationPagerFragment.TAG).commit();
        }

        backgroundLayout = ViewUtils.getView(view, R.id.bl__background);
        confirmationMenu = ViewUtils.getView(view, R.id.cm__confirm_action_light);
        confirmationMenu.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getSingleImageController().addSingleImageObserver(this);
        getControllerFactory().getOnboardingController().addOnboardingControllerObserver(this);
        getControllerFactory().getGiphyController().addObserver(this);
        getControllerFactory().getConfirmationController().addConfirmationObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);

        getControllerFactory().getAccentColorController().addAccentColorObserver(backgroundLayout);
        getControllerFactory().getBackgroundController().addBackgroundObserver(backgroundLayout);

        OnBoardingHintFragment fragment = (OnBoardingHintFragment) getChildFragmentManager().findFragmentByTag(
            OnBoardingHintFragment.TAG);
        if (fragment != null) {
            getControllerFactory().getOnboardingController().setCurrentHintType(fragment.getOnBoardingHintType());
        }
    }

    @Override
    public void onStop() {
        getControllerFactory().getGiphyController().removeObserver(this);
        getControllerFactory().getSingleImageController().removeSingleImageObserver(this);
        getControllerFactory().getOnboardingController().removeOnboardingControllerObserver(this);
        getControllerFactory().getConfirmationController().removeConfirmationObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);

        getControllerFactory().getAccentColorController().removeAccentColorObserver(backgroundLayout);
        getControllerFactory().getBackgroundController().removeBackgroundObserver(backgroundLayout);
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
        getChildFragmentManager().findFragmentById(R.id.fl_fragment_main_content).onActivityResult(requestCode,
                                                                                                   resultCode,
                                                                                                   data);
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

        // Clear any overlays
        dismissOnBoardingHint(OnBoardingHintType.NONE);

        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            Fragment topFragment = getChildFragmentManager().findFragmentByTag(getChildFragmentManager().getBackStackEntryAt(
                0).getName());
            if (topFragment instanceof SingleImageFragment) {
                return ((SingleImageFragment) topFragment).onBackPressed();
            } else if (topFragment instanceof VideoPlayerFragment) {
                return ((VideoPlayerFragment) topFragment).onBackPressed();
            } else if (topFragment instanceof GiphySharingPreviewFragment) {
                if (!((GiphySharingPreviewFragment) topFragment).onBackPressed()) {
                    getChildFragmentManager().popBackStackImmediate(GiphySharingPreviewFragment.TAG,
                                                                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
                return true;
            }

        }

        // Back press is first delivered to the notification fragment, and if it's not consumed there,
        // it's then delivered to the main content.

        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl_fragment_main_in_app_notification);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl_fragment_main_content);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        fragment = getChildFragmentManager().findFragmentById(R.id.fl__overlay_container);
        if (fragment instanceof OnBackPressedListener &&
            ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        return getChildFragmentManager().popBackStackImmediate();
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnBoardingHintFragment
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissOnBoardingHint(OnBoardingHintType requestedType) {
        getControllerFactory().getOnboardingController().hideOnboardingHint(requestedType);
    }

    @Override
    public void onShowOnboardingHint(final OnBoardingHintType hintType, int delayMilSec) {
        if (hintType == OnBoardingHintType.NONE) {
            return;
        }

        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getContainer() == null || !isResumed()) {
                    return;
                }

                // Additional check if hint types match. Some animations go through conversation list and might trigger pull down hint
                Page currentPage = getControllerFactory().getNavigationController().getCurrentPage();
                IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();

                boolean currentConversationHasDraft = TextUtils.isEmpty(getStoreFactory().getDraftStore().getDraft(
                    getStoreFactory().getConversationStore().getCurrentConversation()));
                OnBoardingHintType currentHintType = getControllerFactory().getOnboardingController().getCurrentOnBoardingHint(
                    currentPage,
                    currentConversation,
                    currentConversationHasDraft);
                if (hintType != currentHintType) {
                    return;
                }

                getChildFragmentManager().popBackStackImmediate(OnBoardingHintFragment.TAG,
                                                                FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getChildFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(R.anim.fade_in,
                                         R.anim.slide_out_to_top,
                                         R.anim.slide_in_from_bottom,
                                         R.anim.fade_out)
                    .add(R.id.fl_fragment_main_onboarding,
                         OnBoardingHintFragment.newInstance(hintType),
                         OnBoardingHintFragment.TAG)
                    .addToBackStack(OnBoardingHintFragment.TAG)
                    .commit();
            }
        }, delayMilSec);
    }

    @Override
    public void onHideOnboardingHint(OnBoardingHintType type) {
        getChildFragmentManager().popBackStackImmediate(OnBoardingHintFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

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
    public void onSearch(String keyword) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      GiphySharingPreviewFragment.newInstance(keyword),
                                      GiphySharingPreviewFragment.TAG)
                                 .addToBackStack(GiphySharingPreviewFragment.TAG)
                                 .commit();
    }

    @Override
    public void onRandomSearch() {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      GiphySharingPreviewFragment.newInstance(),
                                      GiphySharingPreviewFragment.TAG)
                                 .addToBackStack(GiphySharingPreviewFragment.TAG)
                                 .commit();
    }

    @Override
    public void onTrendingSearch() {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      GiphySharingPreviewFragment.newInstance(),
                                      GiphySharingPreviewFragment.TAG)
                                 .addToBackStack(GiphySharingPreviewFragment.TAG)
                                 .commit();
    }

    @Override
    public void onCloseGiphy() {
        getChildFragmentManager().popBackStackImmediate(GiphySharingPreviewFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void onCancelGiphy() {
        getChildFragmentManager().popBackStackImmediate(GiphySharingPreviewFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public void openCollection() {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__overlay_container,
                                      CollectionFragment.newInstance(),
                                      CollectionFragment.TAG())
                                 .addToBackStack(CollectionFragment.TAG())
                                 .commit();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConfirmationObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRequestConfirmation(ConfirmationRequest confirmationRequest, @IConfirmationController.ConfirmationMenuRequester int requester) {
        confirmationMenu.onRequestConfirmation(confirmationRequest);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        if (getView() == null) {
            return;
        }
        confirmationMenu.setButtonColor(color);
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
