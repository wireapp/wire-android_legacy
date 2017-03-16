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
package com.waz.zclient.pages.main.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.IConversation;
import com.waz.api.NetworkMode;
import com.waz.api.User;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.confirmation.TwoButtonConfirmationCallback;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.tracking.events.conversation.ArchivedConversationEvent;
import com.waz.zclient.controllers.tracking.events.conversation.UnarchivedConversationEvent;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.controllers.tracking.events.connect.BlockingEvent;
import com.waz.zclient.media.SoundController;
import com.waz.zclient.pages.main.participants.OptionsMenuControl;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.participants.OptionsMenuFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.optionsmenu.OptionsMenu;
import com.waz.zclient.ui.optionsmenu.OptionsMenuItem;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

public class PendingConnectRequestManagerFragment extends BaseFragment<PendingConnectRequestManagerFragment.Container> implements PendingConnectRequestFragment.Container,
                                                                                                                                  OptionsMenuFragment.Container {

    public static final String TAG = PendingConnectRequestManagerFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    public static final String ARGUMENT_LOAD_MODE = "ARGUMENT_LOAD_MODE";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";

    private IConnectStore.UserRequester userRequester;
    private OptionsMenuControl optionsMenuControl;
    private boolean isShowingCommonUserProfile = false;

    public static PendingConnectRequestManagerFragment newInstance(String userId, String conversationId, ConnectRequestLoadMode loadMode, IConnectStore.UserRequester userRequester) {
        PendingConnectRequestManagerFragment newFragment = new PendingConnectRequestManagerFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        args.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_request_pending_manager, container, false);

        optionsMenuControl = new OptionsMenuControl();
        if (savedInstanceState == null) {
            String userId = getArguments().getString(ARGUMENT_USER_ID);
            String conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
            ConnectRequestLoadMode loademode = ConnectRequestLoadMode.valueOf(getArguments().getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));

            getChildFragmentManager()
                    .beginTransaction()
                    .add(R.id.fl__pending_connect_request, PendingConnectRequestFragment.newInstance(userId, conversationId, loademode, userRequester), PendingConnectRequestFragment.TAG)
                    .commit();

            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__pending_connect_request__settings_box,
                                          OptionsMenuFragment.newInstance(false),
                                          OptionsMenuFragment.TAG)
                                     .commit();
        }

        return view;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // UserProfileContainer
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissUserProfile() {
        getContainer().dismissUserProfile();
    }

    @Override
    public void dismissSingleUserProfile() {
        if (LayoutSpec.isPhone(getActivity()) &&
            getChildFragmentManager().popBackStackImmediate()) {
            isShowingCommonUserProfile = false;

            restoreCurrentPageAfterClosingOverlay();
        }
    }

    @Override
    public void showRemoveConfirmation(final User user) {
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                getContainer().showRemoveConfirmation(user);
            }

            @Override
            public void onNoNetwork() {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.alert_dialog__no_network__header,
                                          R.string.remove_from_conversation__no_network__message,
                                          R.string.alert_dialog__confirmation,
                                          null, true);
            }
        });
    }

    @Override
    public void onConversationUpdated(IConversation conversation) {
        if (conversation != null && conversation.getType() == IConversation.Type.ONE_TO_ONE) {
            getContainer().onAcceptedPendingOutgoingConnectRequest(conversation);
        }
    }

    private void restoreCurrentPageAfterClosingOverlay() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }

        IConnectStore.UserRequester userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
        if (userRequester == IConnectStore.UserRequester.CONVERSATION) {
            getControllerFactory().getNavigationController().setRightPage(Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION, TAG);
        } else {
            getControllerFactory().getNavigationController().setRightPage(Page.PENDING_CONNECT_REQUEST, TAG);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // PendingConnectRequestFragment
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAcceptedConnectRequest(IConversation conversation) {
        getContainer().onAcceptedConnectRequest(conversation);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OptionsMenuFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public OptionsMenuControl getOptionsMenuControl() {
        return optionsMenuControl;
    }

    @Override
    public void onOptionMenuStateHasChanged(OptionsMenu.State state) {

    }

    @Override
    public void onOptionsItemClicked(IConversation conversation, User user, OptionsMenuItem item) {
        switch (item) {
            case BLOCK:
                showBlockUserConfirmation(user);
                break;
            case UNBLOCK:
                user.unblock();
                break;
            case ARCHIVE:
                getStoreFactory().getConversationStore().archive(conversation, true);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ArchivedConversationEvent(conversation.getType().toString()));
                break;
            case UNARCHIVE:
                getStoreFactory().getConversationStore().archive(conversation, false);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new UnarchivedConversationEvent(conversation.getType().toString()));
                break;
            case SILENCE:
                conversation.setMuted(true);
                break;
            case UNSILENCE:
                conversation.setMuted(false);
                break;
        }

        optionsMenuControl.close();
    }

    private void showBlockUserConfirmation(final User user) {
        getControllerFactory().getNavigationController().setRightPage(Page.CONFIRMATION_DIALOG, TAG);

        ConfirmationCallback callback = new TwoButtonConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {

                getStoreFactory().getConnectStore().blockUser(user);

                final IConnectStore.UserRequester userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
                getStoreFactory().getConnectStore().blockUser(user);
                switch (userRequester) {
                    case CONVERSATION:
                        getStoreFactory().getConversationStore().setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER);
                        break;
                    case SEARCH:
                    case POPOVER:
                        getControllerFactory().getPickUserController().hideUserProfile();
                        break;
                }
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new BlockingEvent(BlockingEvent.ConformationResponse.BLOCK));

            }
            @Override
            public void negativeButtonClicked() {
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {
                restoreCurrentPageAfterClosingOverlay();
            }
        };
        String header = getString(R.string.confirmation_menu__block_header);
        String text = getString(R.string.confirmation_menu__block_text_with_name, user.getDisplayName());
        String confirm = getString(R.string.confirmation_menu__confirm_block);
        String cancel = getString(R.string.confirmation_menu__cancel);

        ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(text)
            .withPositiveButton(confirm)
            .withNegativeButton(cancel)
            .withConfirmationCallback(callback)
            .withWireTheme(getControllerFactory().getThemeController().getThemeDependentOptionsTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request, IConfirmationController.USER_PROFILE);

        SoundController ctrl = inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playAlert();
        }
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    public interface Container extends UserProfileContainer {
        void onAcceptedConnectRequest(IConversation conversation);

        void onAcceptedPendingOutgoingConnectRequest(IConversation conversation);

    }
}
