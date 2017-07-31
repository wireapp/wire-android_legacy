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
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.ErrorsList;
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
import com.waz.zclient.core.stores.inappnotification.SyncErrorObserver;
import com.waz.zclient.fragments.ImageFragment;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.SingleImageUserFragment;
import com.waz.zclient.pages.main.conversationlist.ConfirmationFragment;
import com.waz.zclient.utils.SyncErrorUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.BackgroundFrameLayout;
import com.waz.zclient.views.menus.ConfirmationMenu;
import net.hockeyapp.android.ExceptionHandler;


public class MainTabletFragment extends BaseFragment<MainTabletFragment.Container> implements
                                                                                   OnBackPressedListener,
                                                                                   RootFragment.Container,
                                                                                   SingleImageObserver,
                                                                                   SingleImageUserFragment.Container,
                                                                                   ConfirmationObserver,
                                                                                   AccentColorObserver,
                                                                                   ConfirmationFragment.Container,
    SyncErrorObserver {

    public static final String TAG = MainTabletFragment.class.getName();

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_landscape, container, false);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();

            transaction.add(R.id.fl_fragment_main_root_container,
                            RootFragment.newInstance(),
                            RootFragment.TAG);

            transaction.commit();
        }

        backgroundLayout = ViewUtils.getView(view, R.id.bl__background);
        confirmationMenu = ViewUtils.getView(view, R.id.cm__confirm_action_light);
        confirmationMenu.setNoRoundBackground();
        confirmationMenu.setVisibility(View.GONE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().inAppNotificationStore().addInAppNotificationObserver(this);
        getControllerFactory().getSingleImageController().addSingleImageObserver(this);
        getControllerFactory().getConfirmationController().addConfirmationObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getConfirmationController().removeConfirmationObserver(this);
        getControllerFactory().getSingleImageController().removeSingleImageObserver(this);
        getStoreFactory().inAppNotificationStore().removeInAppNotificationObserver(this);
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
            if (topFragment instanceof SingleImageUserFragment) {
                return ((SingleImageUserFragment) topFragment).onBackPressed();
            } else if (topFragment instanceof ConfirmationFragment) {
                return ((ConfirmationFragment) topFragment).onBackPressed();
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
                                      ImageFragment.newInstance(message.getId()),
                                     ImageFragment.TAG())
                                 .addToBackStack(ImageFragment.TAG())
                                 .commit();
        getControllerFactory().getNavigationController().setRightPage(Page.SINGLE_MESSAGE, TAG);
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
    }

    @Override
    public void onHideSingleImage() {

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

    @Override
    public void onSyncError(ErrorsList.ErrorDescription error) {
        if (getActivity() == null) {
            return;
        }

        switch (error.getType()) {
            case CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION:
            case CANNOT_ADD_USER_TO_FULL_CONVERSATION:
            case CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER:
                getChildFragmentManager().beginTransaction()
                                         .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                                         .replace(R.id.fl_dialog_container,
                                                  ConfirmationFragment.newMessageOnlyInstance(getResources().getString(R.string.in_app_notification__sync_error__create_group_convo__title),
                                                                                              SyncErrorUtils.getGroupErrorMessage(getContext(), error),
                                                                                              getResources().getString(R.string.in_app_notification__sync_error__create_convo__button),
                                                                                              error.getId()),
                                                  ConfirmationFragment.TAG
                                                 )
                                         .addToBackStack(ConfirmationFragment.TAG)
                                         .commit();
                break;
            case CANNOT_ADD_USER_TO_FULL_CALL:
            case CANNOT_CALL_CONVERSATION_WITH_TOO_MANY_MEMBERS:
            case CANNOT_SEND_VIDEO:
            case PLAYBACK_FAILURE:
                ExceptionHandler.saveException(new RuntimeException("Unhandled error " + error.getType()), null);
                error.dismiss();
                break;
            case CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION:
            case RECORDING_FAILURE:
            case CANNOT_SEND_ASSET_FILE_NOT_FOUND:
            case CANNOT_SEND_ASSET_TOO_LARGE:
                // Handled in ConversationFragment
                break;
            default:
                ExceptionHandler.saveException(new RuntimeException("Unexpected error " + error.getType()), null);

        }
    }

    @Override
    public void onDialogConfirm(String dialogId) {
        getChildFragmentManager().popBackStackImmediate(ConfirmationFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
        dismissError(dialogId);
    }

    @Override
    public void onDialogCancel(String dialogId) {
        getChildFragmentManager().popBackStackImmediate(ConfirmationFragment.TAG,
                                                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
        dismissError(dialogId);
    }

    private void dismissError(String errorId) {
        if (getActivity() == null ||
            getStoreFactory().isTornDown()) {
            return;
        }
        getStoreFactory().inAppNotificationStore().dismissError(errorId);
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
