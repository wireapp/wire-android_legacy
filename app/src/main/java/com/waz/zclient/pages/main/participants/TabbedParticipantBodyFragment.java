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
package com.waz.zclient.pages.main.participants;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import com.waz.api.CoreList;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.model.ConvId;
import com.waz.model.ConversationData;
import com.waz.model.IntegrationId;
import com.waz.model.ProviderId;
import com.waz.model.UserData;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.ThemeController;
import com.waz.zclient.common.controllers.UserAccountsController;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.conversation.ConversationController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.pages.main.participants.views.ParticipantOtrDeviceAdapter;
import com.waz.zclient.pages.main.participants.views.TabbedParticipantPagerAdapter;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.ContextUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.conversation.ParticipantDetailsTab;
import com.waz.zclient.views.menus.FooterMenuCallback;

import java.util.Collection;


public class TabbedParticipantBodyFragment extends BaseFragment<TabbedParticipantBodyFragment.Container> implements
                                                                                               ParticipantsStoreObserver,
                                                                                               AccentColorObserver,
                                                                                               ParticipantOtrDeviceAdapter.ViewHolder.ViewHolderClicks,
                                                                                               TabbedParticipantPagerAdapter.Callback,
                                                                                               ConversationScreenControllerObserver {

    public static final String TAG = TabbedParticipantBodyFragment.class.getName();
    private static final String ARG__FIRST__PAGE = "ARG__FIRST__PAGE";
    public static final int USER_PAGE = 0;
    public static final int DEVICE_PAGE = 1;

    private ViewPager viewPager;
    private final ParticipantOtrDeviceAdapter participantOtrDeviceAdapter;

    private CallbackImpl callbacks;
    private boolean permissionToRemove = true;
    private boolean permissionToCreate = true;

    private final ModelObserver<User> userModelObserver = new ModelObserver<User>() {
        @Override
        public void updated(User user) {
            otrClientsModelObserver.setAndUpdate(user.getOtrClients());
            participantOtrDeviceAdapter.setUserDisplayName(user != null ? user.getDisplayName() : null);
            participantOtrDeviceAdapter.notifyDataSetChanged();
        }
    };

    private final ModelObserver<CoreList<OtrClient>> otrClientsModelObserver = new ModelObserver<CoreList<OtrClient>>() {
        @Override
        public void updated(CoreList<OtrClient> model) {
            participantOtrDeviceAdapter.setOtrClients(model);
        }
    };

    private final ModelObserver<User> userModelObserverForTabs = new ModelObserver<User>() {
        @Override
        public void updated(User user) {
            callbacks = new CallbackImpl(user);
            updateViews(user);
        }
    };

    public static TabbedParticipantBodyFragment newInstance(int firstPage) {
        TabbedParticipantBodyFragment fragment = new TabbedParticipantBodyFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(ARG__FIRST__PAGE, firstPage);
        fragment.setArguments(bundle);
        return fragment;
    }

    public TabbedParticipantBodyFragment() {
        participantOtrDeviceAdapter = new ParticipantOtrDeviceAdapter(this);
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            Animation doNothingAnim = new AlphaAnimation(1, 1);
            doNothingAnim.setDuration(ViewUtils.getNextAnimationDuration(parent));
            return doNothingAnim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_participants_single_tabbed, viewGroup, false);
        TabIndicatorLayout tabIndicatorLayout = ViewUtils.getView(view, R.id.til_single_participant_tabs);
        int color;
        if (((BaseActivity) getActivity()).injectJava(ThemeController.class).isDarkTheme()) {
            color = ContextUtils.getColorWithTheme(R.color.text__secondary_dark, getContext());
        } else {
            color = ContextUtils.getColorWithTheme(R.color.text__secondary_light, getContext());
        }
        if (tabIndicatorLayout != null) {
            tabIndicatorLayout.setPrimaryColor(color);
        }
        viewPager = ViewUtils.getView(view, R.id.vp_single_participant_viewpager);
        viewPager.setAdapter(new TabbedParticipantPagerAdapter(getActivity(), participantOtrDeviceAdapter, this));
        tabIndicatorLayout.setViewPager(viewPager);
        if (savedInstanceState == null) {
            if (getControllerFactory().getConversationScreenController().shouldShowDevicesTab()) {
                viewPager.setCurrentItem(1);
                getControllerFactory().getConversationScreenController().setShowDevicesTab(null);
            } else {
                int firstPage = getArguments().getInt(ARG__FIRST__PAGE);
                viewPager.setCurrentItem(firstPage);
            }
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().participantsStore().addParticipantsStoreObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        updateUser();
    }

    @Override
    public void onStop() {
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().participantsStore().removeParticipantsStoreObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        viewPager = null;
        super.onDestroyView();
    }

    @Override
    public void onOtrClientClick(OtrClient otrClient) {
        if (callbacks == null) {
            return;
        }
        callbacks.showOtrDetails(otrClient);
    }

    @Override
    public void onOtrHeaderClick() {
        getContainer().onOpenUrl(getResources().getString(R.string.url_otr_learn_why));
    }

    @Override
    public void finishUpdate() {
    }

    @Override
    public void conversationUpdated(IConversation conversation) {
        updateUser();
    }

    @Override
    public void participantsUpdated(UsersList participants) {
        updateUser();
    }

    @Override
    public void otherUserUpdated(User otherUser) {
        updateUser();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        participantOtrDeviceAdapter.setAccentColor(color);
    }

    private void updateViews(User user) {
        if (viewPager == null) {
            return;
        }

        View view = viewPager.findViewWithTag(TabbedParticipantPagerAdapter.ParticipantTabs.DETAILS);
        if (view instanceof ParticipantDetailsTab) {
            final ParticipantDetailsTab tab = (ParticipantDetailsTab) view;
            tab.setUser(user);

            boolean isGroup = inject(ConversationController.class).getIsCurrentConvGroup();

            if (!isGroup && permissionToCreate) {
                tab.updateFooterMenu(R.string.glyph__add_people,
                    R.string.conversation__action__create_group,
                    R.string.glyph__more,
                    R.string.empty_string,
                    callbacks);
            } else if (isGroup  && permissionToRemove) {
                tab.updateFooterMenu(R.string.glyph__conversation,
                    R.string.empty_string,
                    R.string.glyph__minus,
                    R.string.empty_string,
                    callbacks);
            } else {
                tab.updateFooterMenu(R.string.glyph__conversation,
                    R.string.empty_string,
                    R.string.empty_string,
                    R.string.empty_string,
                    callbacks);
            }

        }
    }

    private void updateUser() {
        final ConversationController ctrl = inject(ConversationController.class);

        ctrl.withCurrentConv(new Callback<ConversationData>() {
            @Override
            public void callback(ConversationData conv) {
                UserAccountsController userAccountsController = inject(UserAccountsController.class);
                permissionToRemove = userAccountsController.hasRemoveConversationMemberPermission(conv.id());
                permissionToCreate = userAccountsController.hasCreateConversationPermission();

                if (!ctrl.getIsCurrentConvGroup() && !ctrl.getIsCurrentConvWithBot()) {
                    if (conv.convType() == IConversation.Type.ONE_TO_ONE) {
                        UserId id = ConversationController.getOtherParticipantForOneToOneConv(conv);
                        User updatedUser = getStoreFactory().zMessagingApiStore().getApi().getUser(id.str());
                        userModelObserver.setAndUpdate(updatedUser);
                        userModelObserverForTabs.setAndUpdate(updatedUser);
                    } else {
                        ctrl.withCurrentConvMembers(new Callback<Collection<UserData>>() {
                            @Override
                            public void callback(Collection<UserData> users) {
                                for(UserData user: users) {
                                    if (!user.isSelf()) {
                                        User updatedUser = getStoreFactory().zMessagingApiStore().getApi().getUser(user.id().str());
                                        getStoreFactory().singleParticipantStore().setUser(updatedUser);
                                        userModelObserver.setAndUpdate(updatedUser);
                                        userModelObserverForTabs.setAndUpdate(updatedUser);
                                    }
                                }
                            }
                        });
                    }
                } else {
                    User updatedUser = getStoreFactory().singleParticipantStore().getUser();
                    userModelObserver.setAndUpdate(updatedUser);
                    userModelObserverForTabs.setAndUpdate(updatedUser);
                }


            }
        });
    }

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {
        if (getControllerFactory().getConversationScreenController().shouldShowDevicesTab()) {
            viewPager.setCurrentItem(1);
            getControllerFactory().getConversationScreenController().setShowDevicesTab(null);
        }

        if (isSingleConversation && showDeviceTabIfSingle) {
            viewPager.setCurrentItem(1);
        }
    }

    @Override
    public void onHideParticipants(boolean backOrButtonPressed,
                                   boolean hideByConversationChange,
                                   boolean isSingleConversation) {

    }

    @Override
    public void onShowEditConversationName(boolean show) {

    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {

    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

    }

    @Override
    public void onShowUser(User user) {

    }

    @Override
    public void onHideUser() {

    }

    @Override
    public void onAddPeopleToConversation() {

    }

    @Override
    public void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester,
                                       ConvId convId) {

    }

    @Override
    public void onShowOtrClient(OtrClient otrClient, User user) {

    }

    @Override
    public void onShowCurrentOtrClient() {

    }

    @Override
    public void onHideOtrClient() {

    }

    @Override
    public void onShowLikesList(Message message) {

    }

    @Override
    public void onShowIntegrationDetails(ProviderId providerId, IntegrationId integrationId) {

    }

    public interface MenuActions {
        void showOtrDetails(OtrClient otrClient);
    }

    public interface Container {
        void showRemoveConfirmation(User user);
        void onOpenUrl(String url);
    }

    private class CallbackImpl implements FooterMenuCallback,
                                          MenuActions {
        final User user;

        CallbackImpl(User user) {
            this.user = user;
        }

        @Override
        public void onLeftActionClicked() {
            if (getStoreFactory() == null || getStoreFactory().isTornDown() ||
                getControllerFactory() == null || getControllerFactory().isTornDown()) {
                return;
            }

            boolean isGroup = inject(ConversationController.class).getIsCurrentConvGroup();

            if (!isGroup && permissionToCreate) {
                getControllerFactory().getConversationScreenController().addPeopleToConversation();
            } else {
                getControllerFactory().getConversationScreenController().hideParticipants(true, false);
                BaseActivity activity = (BaseActivity) getActivity();
                inject(UserAccountsController.class).createAndOpenConversation(
                    new UserId[]{new UserId(user.getId())},
                    ConversationChangeRequester.START_CONVERSATION,
                    activity);
            }
        }

        @Override
        public void onRightActionClicked() {
            if (getStoreFactory() == null || getStoreFactory().isTornDown() ||
                getControllerFactory() == null || getControllerFactory().isTornDown()) {
                return;
            }

            final ConversationController ctrl = inject(ConversationController.class);
            ctrl.withCurrentConv(new Callback<ConversationData>() {
                @Override
                public void callback(ConversationData conv) {
                    if (!ctrl.getIsCurrentConvGroup()) {
                        getControllerFactory().getConversationScreenController().showConversationMenu(
                            IConversationScreenController.CONVERSATION_DETAILS,
                            conv.id()
                        );
                    } else if (permissionToRemove) {
                        getContainer().showRemoveConfirmation(user);
                    }
                }
            });
        }

        @Override
        public void showOtrDetails(OtrClient otrClient) {
            if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
                return;
            }
            getControllerFactory().getConversationScreenController().showOtrClient(otrClient, user);
        }
    }
}
