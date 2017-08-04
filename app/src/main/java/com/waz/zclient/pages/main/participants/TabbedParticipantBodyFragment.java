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
package com.waz.zclient.pages.main.participants;

import android.os.Build;
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
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.UserAccountsController;
import com.waz.zclient.controllers.ThemeController;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.group.OpenedGroupActionEvent;
import com.waz.zclient.controllers.tracking.events.otr.ViewedOtherOtrClientsEvent;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.views.ParticipantDetailsTab;
import com.waz.zclient.pages.main.participants.views.ParticipantOtrDeviceAdapter;
import com.waz.zclient.pages.main.participants.views.TabbedParticipantPagerAdapter;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.views.tab.TabIndicatorLayout;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.menus.FooterMenuCallback;

public class TabbedParticipantBodyFragment extends BaseFragment<TabbedParticipantBodyFragment.Container> implements
                                                                                               ParticipantsStoreObserver,
                                                                                               AccentColorObserver,
                                                                                               ParticipantOtrDeviceAdapter.ViewHolder.ViewHolderClicks,
                                                                                               TabbedParticipantPagerAdapter.Callback,
                                                                                               ViewPager.OnPageChangeListener,
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                color = getResources().getColor(R.color.text__secondary_dark);
            } else {
                color = getResources().getColor(R.color.text__secondary_dark, getContext().getTheme());
            }
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                color = getResources().getColor(R.color.text__secondary_light);
            } else {
                color = getResources().getColor(R.color.text__secondary_light, getContext().getTheme());
            }
        }
        if (tabIndicatorLayout != null) {
            tabIndicatorLayout.setPrimaryColor(color);
        }
        viewPager = ViewUtils.getView(view, R.id.vp_single_participant_viewpager);
        viewPager.setAdapter(new TabbedParticipantPagerAdapter(getActivity(), participantOtrDeviceAdapter, this));
        viewPager.addOnPageChangeListener(this);
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
        viewPager.removeOnPageChangeListener(this);
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
        userModelObserverForTabs.forceUpdate();
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
            ParticipantDetailsTab tab = (ParticipantDetailsTab) view;
            tab.setUser(user);

            final IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
            if (conversation != null) {
                if (conversation.getType() == IConversation.Type.ONE_TO_ONE && permissionToCreate) {
                    tab.updateFooterMenu(R.string.glyph__add_people,
                                         R.string.conversation__action__create_group,
                                         R.string.glyph__more,
                                         R.string.empty_string,
                                         callbacks);
                } else if (conversation.getType() == IConversation.Type.GROUP && permissionToRemove) {
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
    }

    private void updateUser() {
        IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
        if (conversation == null) {
            userModelObserver.clear();
            userModelObserverForTabs.clear();
            otrClientsModelObserver.clear();
            return;
        }
        permissionToRemove = ((BaseActivity) getActivity()).injectJava(UserAccountsController.class).hasRemoveConversationMemberPermission(new ConvId(conversation.getId()));
        permissionToCreate = ((BaseActivity) getActivity()).injectJava(UserAccountsController.class).hasCreateConversationPermission();
        final User updatedUser;
        if (conversation.getType() == IConversation.Type.ONE_TO_ONE) {
            updatedUser = conversation.getOtherParticipant();
        } else {
            updatedUser = getStoreFactory().singleParticipantStore().getUser();
        }
        userModelObserver.setAndUpdate(updatedUser);
        userModelObserverForTabs.setAndUpdate(updatedUser);
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
    public void onConversationLoaded() {

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
                                       IConversation conversation,
                                       View anchorView) {

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
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (position == 0 || getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ViewedOtherOtrClientsEvent());
    }

    @Override
    public void onPageScrollStateChanged(int state) {

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
            IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
            if (conversation == null) {
                return;
            }
            if (conversation.getType() == IConversation.Type.ONE_TO_ONE && permissionToCreate) {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedGroupActionEvent());
                getControllerFactory().getConversationScreenController().addPeopleToConversation();
            } else {
                getControllerFactory().getConversationScreenController().hideParticipants(true, false);
                BaseActivity activity = (BaseActivity) getActivity();
                activity.injectJava(UserAccountsController.class).createAndOpenConversation(
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
            IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
            if (conversation == null) {
                return;
            }
            if (conversation.getType() == IConversation.Type.ONE_TO_ONE) {
                getControllerFactory().getConversationScreenController().showConversationMenu(
                    IConversationScreenController.CONVERSATION_DETAILS,
                    conversation,
                    null);

            } else if (permissionToRemove) {
                getContainer().showRemoveConfirmation(user);
            }
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
