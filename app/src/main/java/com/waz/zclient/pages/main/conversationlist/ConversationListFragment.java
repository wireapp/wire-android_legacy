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
package com.waz.zclient.pages.main.conversationlist;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.waz.api.ActiveVoiceChannels;
import com.waz.api.ConversationsList;
import com.waz.api.CoreList;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.VoiceChannel;
import com.waz.zclient.BaseScalaActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.conversationlist.ConversationListObserver;
import com.waz.zclient.controllers.conversationlist.IConversationListController;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.controllers.streammediaplayer.StreamMediaPlayerObserver;
import com.waz.zclient.controllers.tracking.events.navigation.ClickedOnContactsHintEvent;
import com.waz.zclient.controllers.tracking.events.navigation.OpenedArchiveEvent;
import com.waz.zclient.controllers.tracking.events.navigation.OpenedContactsEvent;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.InboxLoadRequester;
import com.waz.zclient.core.stores.conversation.OnInboxLoadedListener;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback;
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView;
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView;
import com.waz.zclient.pages.main.conversationlist.views.row.ConversationListRow;
import com.waz.zclient.pages.main.conversationlist.views.row.RightIndicatorView;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.pullforaction.PullForActionContainer;
import com.waz.zclient.ui.pullforaction.PullForActionListener;
import com.waz.zclient.ui.pullforaction.PullForActionMode;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.utils.ViewUtils;

import net.hockeyapp.android.CrashManagerListener;
import net.hockeyapp.android.ExceptionHandler;

import java.util.List;

public class ConversationListFragment extends BaseFragment<ConversationListFragment.Container> implements
                                                                                               PullForActionListener,
                                                                                               OnBackPressedListener,
                                                                                               AdapterView.OnItemClickListener,
                                                                                               OnInboxLoadedListener,
                                                                                               ConversationStoreObserver,
                                                                                               PagerControllerObserver,
                                                                                               InAppNotificationStoreObserver,
                                                                                               View.OnClickListener,
                                                                                               AccentColorObserver,
                                                                                               ConversationListObserver,
                                                                                               StreamMediaPlayerObserver,
                                                                                               VoiceChannel.JoinCallback {
    public static final String TAG = ConversationListFragment.class.getName();
    private static final int LIST_VIEW_POSITION_OFFSET = 3;
    private static final String ARG_MODE = "arg_mode";
    private float maxSwipeAlpha;
    private ActiveVoiceChannels activeVoiceChannels;

    public enum Mode {
        NORMAL, SHARING
    }

    private final UpdateListener callUpdateListener  = new UpdateListener() {
        public void updated() {
            conversationsListAdapter.onActiveCallStateHasChanged();
        }
    };

    private Mode mode;

    // conversation adapter - is updated by observers
    private ConversationListAdapter conversationsListAdapter;

    private PullForActionContainer pullForActionContainer;
    private SwipeListView swipeListView;
    private int pebbleViewX;

    private View hintContainer;
    private TypefaceTextView hintHeader;
    private ListActionsView listActionsView;
    private LinearLayout archiveBox;
    private int initialArchivedBoxOffset;

    private IConversation pendingCurrentConversation;
    private boolean scrollToConversation;
    private View layoutNoConversations;

    private final ConversationCallback conversationCallback = new ConversationCallback() {

        @Override
        public void onConversationListRowSwiped(IConversation conversation, View conversationListRowView) {
            swipeListView.resetListRowALpha();
            if (conversation.getType() != IConversation.Type.GROUP &&
                conversation.getType() != IConversation.Type.ONE_TO_ONE &&
                conversation.getType() != IConversation.Type.WAIT_FOR_CONNECTION) {
                return;
            }

            getControllerFactory().getConversationScreenController().showConversationMenu(IConversationScreenController.CONVERSATION_LIST_SWIPE,
                                                                                          conversation,
                                                                                          conversationListRowView);
        }

        @Override
        public void onConversationListRowLongClicked(IConversation conversation, View conversationListRowView) {
            swipeListView.resetListRowALpha();
            if (conversation.getType() != IConversation.Type.GROUP &&
                conversation.getType() != IConversation.Type.ONE_TO_ONE &&
                conversation.getType() != IConversation.Type.WAIT_FOR_CONNECTION) {
                return;
            }
            getControllerFactory().getConversationScreenController().showConversationMenu(IConversationScreenController.CONVERSATION_LIST_LONG_PRESS,
                                                                                          conversation,
                                                                                          conversationListRowView);
        }
    };
    private final ModelObserver<CoreList<OtrClient>> addedDevicesModelObserver = new ModelObserver<CoreList<OtrClient>>() {
        @Override
        public void updated(CoreList<OtrClient> model) {
            listActionsView.showIndicatorDot(model.size() > 0);
        }
    };

    public static ConversationListFragment newInstance(Mode mode) {
        ConversationListFragment conversationListFragment = new ConversationListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode.ordinal());
        conversationListFragment.setArguments(args);
        return conversationListFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim == 0 ||
            getContainer() == null ||
            getControllerFactory().isTornDown()) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
        if (getControllerFactory().getPickUserController().isHideWithoutAnimations()) {
            return new ConversationListAnimation(0,
                                                 getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                 enter,
                                                 0,
                                                 0,
                                                 false,
                                                 1f);
        }

        if (enter) {
            return new ConversationListAnimation(0,
                                                 getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                 enter,
                                                 getResources().getInteger(R.integer.framework_animation_duration_long),
                                                 getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                 false,
                                                 1f);
        }
        return new ConversationListAnimation(0,
                                             getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                             enter,
                                             getResources().getInteger(R.integer.framework_animation_duration_medium),
                                             0,
                                             false,
                                             1f);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (getArguments() != null) {
            mode = Mode.values()[args.getInt(ARG_MODE)];
        } else {
            mode = Mode.NORMAL;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation_list, c, false);

        layoutNoConversations = ViewUtils.getView(view, R.id.ll__conversation_list__no_contacts);
        layoutNoConversations.setVisibility(View.GONE);

        conversationsListAdapter = new ConversationListAdapter(conversationCallback, getActivity());
        conversationsListAdapter.setConversationListMode(mode);

        swipeListView = new SwipeListView(getActivity());
        FrameLayout.LayoutParams paramsSticky = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                             ViewGroup.LayoutParams.WRAP_CONTENT);
        paramsSticky.topMargin = getResources().getDimensionPixelSize(R.dimen.conversation_list__sticky_view__top_margin);

        pullForActionContainer = ViewUtils.getView(view, R.id.pfac__conversation_list);

        swipeListView.setAllowSwipeAway(true);
        swipeListView.setAdapter(conversationsListAdapter);
        swipeListView.setOnItemClickListener(this);
        swipeListView.setDivider(null);
        swipeListView.setVerticalScrollBarEnabled(false);
        swipeListView.setSelector(R.drawable.empty_list_view_selector);
        swipeListView.setScrollingCacheEnabled(false);
        swipeListView.setAnimationCacheEnabled(false);

        conversationsListAdapter.setListView(swipeListView);

        archiveBox = ViewUtils.getView(view, R.id.ll__archiving_container);
        archiveBox.setVisibility(View.INVISIBLE);

        swipeListView.post(new Runnable() {
            @Override
            public void run() {
                if (swipeListView != null) {
                    swipeListView.setOffsetRight(swipeListView.getMeasuredWidth() -
                                                 getResources().getDimensionPixelOffset(R.dimen.list_menu_distance_threshold)); // right side offset
                }
            }
        });

        swipeListView.setOnScrollListener(new ConversationListViewOnScrollListener());
        maxSwipeAlpha = ResourceUtils.getResourceFloat(getResources(), R.dimen.list__swipe_max_alpha);
        conversationsListAdapter.setMaxAlpha(maxSwipeAlpha);
        pullForActionContainer.setPullForActionView(swipeListView, PullForActionContainer.FillType.WRAP);
        pullForActionContainer.setPullToActionListener(this);
        pullForActionContainer.setPullForActionMode(PullForActionMode.BOTTOM);

        pebbleViewX = getResources().getDimensionPixelSize(R.dimen.framework__general__left_padding);
        if (mode != Mode.SHARING) {
            pebbleViewX += getResources().getDimensionPixelSize(R.dimen.list_extra_padding_when_unread);
        }

        hintContainer = ViewUtils.getView(view, R.id.ll__conversation_list__hint_container);
        hintHeader = ViewUtils.getView(view, R.id.chttv__conversation_list__hint_header);

        listActionsView = ViewUtils.getView(view, R.id.lav__conversation_list_actions);
        if (mode == Mode.SHARING) {
            listActionsView.setVisibility(View.GONE);
        } else {
            listActionsView.setCallback(new ListActionsView.Callback() {
                @Override
                public void onAvatarPress() {
                    getControllerFactory().getPickUserController().showPickUser(IPickUserController.Destination.CONVERSATION_LIST, null);
                    boolean hintVisible = hintContainer != null && hintContainer.getVisibility() == View.VISIBLE;
                    ((BaseScalaActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedContactsEvent(hintVisible));
                    getControllerFactory().getOnboardingController().hideConversationListHint();
                }

                @Override
                public void onSettingsPress() {
                    startActivity(ZetaPreferencesActivity.getDefaultIntent(getContext()));
                }
            });
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pullForActionContainer.post(new Runnable() {
            @Override
            public void run() {
                if (conversationsListAdapter == null || pullForActionContainer == null) {
                    return;
                }
                conversationsListAdapter.setLayoutHeight(pullForActionContainer.getMeasuredHeight());
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        activeVoiceChannels = getStoreFactory().getZMessagingApiStore().getApi().getActiveVoiceChannels();
        activeVoiceChannels.addUpdateListener(callUpdateListener);
        conversationsListAdapter.setStreamMediaPlayerController(getControllerFactory().getStreamMediaPlayerController());
        conversationsListAdapter.setConversationActionCallback(new RightIndicatorView.ConversationActionCallback() {
            @Override
            public void startCall(IConversation conversation) {
                if (getStoreFactory() != null && !getStoreFactory().isTornDown()) {
                    getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                                    ConversationChangeRequester.CONVERSATION_LIST);
                    getControllerFactory().getCallingController().startCall(false);
                }
            }
        });
        conversationsListAdapter.setNetworkStore(getStoreFactory().getNetworkStore());
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        if (mode == Mode.NORMAL) {
            getStoreFactory().getInAppNotificationStore().addInAppNotificationObserver(this);
            getControllerFactory().getStreamMediaPlayerController().addStreamMediaObserver(this);
            getControllerFactory().getNavigationController().addPagerControllerObserver(this);
        }
        getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        getControllerFactory().getConversationListController().addConversationListObserver(this);

        if (mode == Mode.SHARING || !getControllerFactory().getOnboardingController().shouldShowConversationListHint()) {
            hintContainer.setVisibility(View.GONE);
            hintContainer.setOnClickListener(null);
        } else {
            hintContainer.setOnClickListener(this);
        }

        // post after performTransversal (Layout cycle)
        archiveBox.post(new Runnable() {
            @Override
            public void run() {
                if (archiveBox == null) {
                    return;
                }
                initialArchivedBoxOffset = archiveBox.getMeasuredHeight();
                archiveBox.setTranslationY(initialArchivedBoxOffset);
                archiveBox.setVisibility(View.VISIBLE);
            }
        });
        addedDevicesModelObserver.setAndUpdate(getStoreFactory().getZMessagingApiStore().getApi().getSelf().getIncomingOtrClients());
    }

    @Override
    public void onStop() {
        addedDevicesModelObserver.clear();
        activeVoiceChannels.removeUpdateListener(callUpdateListener);
        getControllerFactory().getConversationListController().removeConversationListObserver(this);
        getControllerFactory().getStreamMediaPlayerController().removeStreamMediaObserver(this);
        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getStoreFactory().getInAppNotificationStore().removeInAppNotificationObserver(this);
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        conversationsListAdapter.tearDown();
        hintContainer.setOnClickListener(null);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        layoutNoConversations = null;
        pullForActionContainer.setPullToActionListener(null);
        pullForActionContainer = null;
        swipeListView = null;
        hintContainer = null;
        archiveBox = null;
    }

    private void maybeShowNoContactsLabel(boolean showNoContactsLabel) {
        if (showNoContactsLabel) {
            layoutNoConversations.setVisibility(View.VISIBLE);
        } else {
            layoutNoConversations.setVisibility(View.GONE);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationListUpdated(final @NonNull ConversationsList conversationsList) {
        ConversationsList conversations = mode == Mode.SHARING ? conversationsList.getEstablishedConversations()
                                                               : conversationsList;
        conversationsListAdapter.setConversationList(conversations);
        // We post this to notify that we are ready after the items are instantiated
        swipeListView.post(new Runnable() {
            @Override
            public void run() {
                if (getContainer() == null) {
                    return;
                }
            }
        });

        if (pendingCurrentConversation != null) {
            final int pos = getStoreFactory().getConversationStore().getPositionInList(pendingCurrentConversation);
            if (pos > 0) {

                // set selection after updating the conversationlist
                new Handler().post(new Runnable() {
                    @SuppressLint("NewApi")
                    @Override
                    public void run() {
                        int selectionPos = pos - LIST_VIEW_POSITION_OFFSET < 0 ? 0 : pos - LIST_VIEW_POSITION_OFFSET;
                        swipeListView.setSelectionFromTop(selectionPos, 0);
                        pendingCurrentConversation = null;
                    }
                });
            }
        }

        // sync state
        onConversationSyncingStateHasChanged(getStoreFactory().getConversationStore().getConversationSyncingState());

        CoreList<IConversation> archivedConversations = conversationsList.getArchivedConversations();
        if (archivedConversations != null && archivedConversations.size() > 0) {
            archiveBox.setAlpha(1f);
        } else {
            archiveBox.setAlpha(0f);
        }

        if (conversationsList.isReady()) {
            maybeShowNoContactsLabel(conversationsList.size() == 0);
        }
    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {
        if (toConversation == null ||
            conversationsListAdapter == null) {
            return;
        }
        if (fromConversation != null &&
            fromConversation.getId().equals(toConversation.getId())) {
            return;
        }
        switch (conversationChangerSender) {
            case CONVERSATION_LIST_SELECT_TO_SHARE:
            case CONVERSATION_LIST:
                break;
            default:
                if (toConversation.isArchived()) {
                    pendingCurrentConversation = toConversation;
                } else {
                    // Don't autoscroll to selected conversation if list is just loaded
                    if (conversationChangerSender == ConversationChangeRequester.FIRST_LOAD) {
                        break;
                    } else if (conversationChangerSender == ConversationChangeRequester.UPDATER && !scrollToConversation) {
                        break;
                    }

                    // set selection after updating the conversationlist
                    final IConversation finalToConversation = toConversation;
                    new Handler().post(new Runnable() {
                        @Override
                        public void run() {
                            if (swipeListView == null || getStoreFactory().isTornDown()) {
                                return;
                            }
                            int pos = getStoreFactory().getConversationStore().getPositionInList(finalToConversation);
                            int selectionPos =
                                pos - LIST_VIEW_POSITION_OFFSET < 0 ? 0 : pos - LIST_VIEW_POSITION_OFFSET;
                            swipeListView.setSelectionFromTop(selectionPos, 0);
                        }
                    });
                }
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
    public void onReleasedTop(int offset) {

    }

    @Override
    public void onReleasedBottom() {
        if (conversationsListAdapter.getArchivedState() != ConversationListAdapter.ArchivedState.GONE) {
            return;
        }
        conversationsListAdapter.setArchivedState(ConversationListAdapter.ArchivedState.INVISIBLE,
                                                  getResources().getInteger(R.integer.framework_animation_duration_medium));
        final int smoothScrollPosition = conversationsListAdapter.getUnarchivedCount() - 1;
        swipeListView.customSmoothScrollToPosition(smoothScrollPosition);

        // The list view needs to be rearranged due to archive state
        // we post it to the end of this rearrangement
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                int offset = 0;
                for (int i = 0; i < swipeListView.getChildCount(); i++) {
                    View view = swipeListView.getChildAt(i);
                    int id = view.getId();

                    if (id == smoothScrollPosition) {
                        offset = view.getTop() + view.getMeasuredHeight() + getResources().getDimensionPixelSize(R.dimen.list__archived_border_height) / 2;
                    }
                }
                getControllerFactory().getConversationListController().onReleasedPullDownFromBottom(offset);
            }
        });

        // post to set archived to visible from invisible
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (conversationsListAdapter != null) {
                    conversationsListAdapter.setArchivedState(ConversationListAdapter.ArchivedState.VISIBLE,
                                                              getResources().getInteger(R.integer.framework_animation_duration_medium));
                    if (getControllerFactory() == null ||
                        getControllerFactory().isTornDown()) {
                        return;
                    }
                    ((BaseScalaActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedArchiveEvent());
                }
            }
        }, getResources().getInteger(R.integer.list__show_archived_delay));
    }

    @Override
    public void onListViewOffsetChanged(int offset) {
        if (getContainer() == null) {
            return;
        }

        getControllerFactory().getConversationListController().onListViewOffsetChanged(offset);
    }

    @Override
    public void setAlpha(float alpha) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final IConversation conversation = conversationsListAdapter.getItem(position);
        if (conversation == null) {
            return;
        }

        if (mode == Mode.NORMAL) {
            handleNormalModeItemClick(position, conversation);
        } else if (mode == Mode.SHARING) {
            handleSharingModeItemClick(conversation);
        }

    }

    private void handleNormalModeItemClick(int position,
                                           IConversation conversation) {
        getControllerFactory().getLoadTimeLoggerController().clickConversationInList();

        ConversationChangeRequester conversationChangeRequester = ConversationChangeRequester.CONVERSATION_LIST;
        if (conversationsListAdapter.isArchived(position) && mode == Mode.NORMAL) {
            conversationChangeRequester = ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION;
        }

        getStoreFactory().getConversationStore().setCurrentConversation(conversation, conversationChangeRequester);

        // archived should not be shown after conversation was clicked.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getContainer() == null) {
                    return;
                }
                conversationsListAdapter.setArchivedState(ConversationListAdapter.ArchivedState.GONE,
                                                          getResources().getInteger(R.integer.framework_animation_duration_medium));
            }
        }, getResources().getInteger(R.integer.framework_animation_duration_medium));
    }

    private void handleSharingModeItemClick(IConversation conversation) {
        if (conversation.isMe()) {
            return;
        }
        getControllerFactory().getSharingController().setDestination(conversation);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        conversationsListAdapter.setAccentColor(color);
        listActionsView.setAccentColor(color);
        hintHeader.setTextColor(color);
    }

    @Override
    public boolean onBackPressed() {
        if (getChildFragmentManager().popBackStackImmediate()) {
            ObjectAnimator.ofFloat(pullForActionContainer, View.TRANSLATION_Y, 0).start();
            return true;
        }
        return false;
    }

    public void setScrollToConversation(boolean scrollToConversation) {
        this.scrollToConversation = scrollToConversation;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        swipeListView.onPagerOffsetChanged(0);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onIncomingMessage(Message message) {

    }

    @Override
    public void onIncomingKnock(final KnockingEvent knockingEvent) {
        if (mode == Mode.SHARING) {
            return;
        }
        if (conversationsListAdapter != null) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (swipeListView == null) {
                        return;
                    }
                    for (int i = 0; i < swipeListView.getChildCount(); i++) {
                        if (swipeListView.getChildAt(i) instanceof ConversationListRow) {
                            ConversationListRow row = (ConversationListRow) swipeListView.getChildAt(i);
                            if (knockingEvent.getConversationId().equals(row.getConversation().getId())) {
                                row.knock(knockingEvent);
                            }
                        }
                    }
                }
            }, getResources().getInteger(R.integer.framework_animation_duration_medium));
        }
    }

    @Override
    public void onSyncError(ErrorsList.ErrorDescription error) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  StreamMediaPlayer
    //
    //////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onPlay(Message message) {
        conversationsListAdapter.notifyDataSetChanged();
    }

    // CHECKSTYLE:OFF
    @Override
    public void onPause(Message message) {
        conversationsListAdapter.notifyDataSetChanged();
    }
    // CHECKSTYLE:ON

    // CHECKSTYLE:OFF
    @Override
    public void onStop(Message message) {
        conversationsListAdapter.notifyDataSetChanged();
    }
    // CHECKSTYLE:ON

    @Override
    public void onPrepared(Message message) {
        conversationsListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onError(Message message) {

    }

    @Override
    public void onComplete(Message message) {
        conversationsListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onTrackChanged(Message newMessage) {
        conversationsListAdapter.notifyDataSetChanged();
    }

    private class ConversationListViewOnScrollListener implements AbsListView.OnScrollListener {
        private boolean userHasScrolled;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (scrollState != SCROLL_STATE_TOUCH_SCROLL) {
                return;
            }
            userHasScrolled = true;
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (totalItemCount == 0 ||
                !userHasScrolled ||
                swipeListView == null) {
                return;
            }

            int scrolledToBottom;
            try {
                if (swipeListView.getLastVisiblePosition() == swipeListView.getAdapter().getCount() - 1 &&
                                   swipeListView.getChildAt(swipeListView.getChildCount() - 1).getBottom() <= swipeListView.getHeight()) {
                    scrolledToBottom = IConversationListController.SCROLLED_TO_BOTTOM;
                } else {
                    scrolledToBottom = IConversationListController.NOT_SCROLLED_TO_BOTTOM;
                }
            } catch (Exception e) {
                scrolledToBottom = IConversationListController.UNDEFINED;
                ExceptionHandler.saveException(e, new CrashManagerListener() {
                    @Override
                    public String getDescription() {
                        return "Logging try/catch, non fatal";
                    }
                });
            }

            getControllerFactory().getConversationListController().notifyScrollOffsetChanged(swipeListView.computeVerticalScrollOffset(), scrolledToBottom);

            // Sticky profile link header
            View firstListRow = swipeListView.getChildAt(0);
            if (firstListRow == null) {
                return;
            }
        }
    }

    @Override
    public void onVoiceChannelFull(int maxJoined) {
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.calling__voice_channel_full__title),
                                  getResources().getQuantityString(R.plurals.calling__voice_channel_full__message,
                                                                   maxJoined,
                                                                   maxJoined),
                                  getString(R.string.alert_dialog__confirmation),
                                  null,
                                  false);
    }

    @Override
    public void onCallJoined() {

    }

    @Override
    public void onAlreadyJoined() {

    }

    @Override
    public void onCallJoinError(String message) {

    }

    @Override
    public void onConversationTooBig(int memberCount, int maxMembers) {
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.calling__conversation_full__title),
                                  getResources().getQuantityString(R.plurals.calling__conversation_full__message,
                                                                   maxMembers,
                                                                   maxMembers),
                                  getString(R.string.alert_dialog__confirmation),
                                  null,
                                  false);
    }

    @Override
    public void onListViewScrollOffsetChanged(int offset, @IConversationListController.ListScrollPosition int scrolledToBottom) {
        listActionsView.setScrolledToBottom(scrolledToBottom == IConversationListController.SCROLLED_TO_BOTTOM);
    }

    @Override
    public void onListViewPullOffsetChanged(int offset) {
        if (getControllerFactory().getPickUserController().isShowingPickUser(IPickUserController.Destination.CONVERSATION_LIST)) {
            return;
        }
        int archiveOffset = Math.max(0, offset + initialArchivedBoxOffset);
        archiveBox.setTranslationY(archiveOffset);
    }

    @Override
    public void onReleasedPullDownFromBottom(int offset) {
        archiveBox.setTranslationY(initialArchivedBoxOffset);
    }

    @Override
    public void onConnectRequestInboxConversationsLoaded(List<IConversation> conversations,
                                                         InboxLoadRequester inboxLoadRequester) {

    }

    @Override
    public void onPagerEnabledStateHasChanged(boolean enabled) {

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.ll__conversation_list__hint_container:
                getControllerFactory().getPickUserController().showPickUser(IPickUserController.Destination.CONVERSATION_LIST, null);
                getControllerFactory().getOnboardingController().hideConversationListHint();
                ((BaseScalaActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new ClickedOnContactsHintEvent());
                break;
        }
    }

    public interface Container {
    }
}
