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
package com.waz.zclient.pages.main.inappnotification;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.ErrorType;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversationlist.ConfirmationFragment;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.chathead.MessageNotificationChatheadView;

public class InAppNotificationFragment extends BaseFragment<InAppNotificationFragment.Container> implements InAppNotificationStoreObserver,
                                                                                                            ConfirmationFragment.Container,
                                                                                                            OnBackPressedListener {
    public static final String TAG = InAppNotificationFragment.class.getName();

    private Handler hideChatheadHandler;
    private boolean chatheadNotificationIsVisible;
    private NotificationDisplayPrioritizer notificationDisplayPrioritizer;
    private MessageNotificationChatheadView chatheadView;
    private int chatheadVisibleDuration;

    public static InAppNotificationFragment newInstance() {
        return new InAppNotificationFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    protected void onPostAttach(Activity activity) {
        super.onPostAttach(activity);
        hideChatheadHandler = new Handler(Looper.getMainLooper());
        notificationDisplayPrioritizer = new NotificationDisplayPrioritizer();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notification, container, false);
        chatheadView = ViewUtils.getView(view, R.id.mncv__notifications__chathead);
        chatheadVisibleDuration = getResources().getInteger(R.integer.notification__chathead__show_duration);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().getInAppNotificationStore().addInAppNotificationObserver(this);
    }

    @Override
    public void onStop() {
        getStoreFactory().getInAppNotificationStore().removeInAppNotificationObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (chatheadView != null) {
            chatheadView.tearDown();
        }
        chatheadView = null;
        if (hideChatheadHandler != null) {
            hideChatheadHandler.removeCallbacks(null);
            hideChatheadHandler = null;
        }
        notificationDisplayPrioritizer = null;
        super.onDestroyView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onIncomingMessage(final Message message) {
        if (message.getMessageType() == Message.Type.KNOCK ||
            message.getMessageType() == Message.Type.MEMBER_JOIN ||
            message.getMessageType() == Message.Type.MEMBER_LEAVE ||
            message.getMessageType() == Message.Type.RENAME ||
            message.getMessageType() == Message.Type.MISSED_CALL) {
            return;
        }

        IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (getStoreFactory().getInAppNotificationStore().shouldShowChatheads(currentConversation, message)) {
            notificationDisplayPrioritizer.onNewIncomingMessage(message);
        }

        // Play sound for incoming connect request
        if (message.getMessageType() == Message.Type.CONNECT_REQUEST &&
            message.getConversation().getType() == IConversation.Type.INCOMING_CONNECTION) {
            getStoreFactory().getMediaStore().playSound(R.raw.first_message);
        } else if (message.getMessageType() == Message.Type.TEXT ||
                   message.getMessageType() == Message.Type.ASSET ||
                   message.getMessageType() == Message.Type.RICH_MEDIA) {
            if (message.isFirstMessage()) {
                getStoreFactory().getMediaStore().playSound(R.raw.first_message);
            } else {
                getStoreFactory().getMediaStore().playSound(R.raw.new_message);
            }
        }
        getControllerFactory().getVibratorController().vibrate(R.array.new_message);
    }

    @Override
    public void onIncomingKnock(KnockingEvent knock) {
        getStoreFactory().getMediaStore().playSound(R.raw.ping_from_them);
        getControllerFactory().getVibratorController().vibrate(R.array.ping_from_them);
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
                                         .replace(R.id.fl__main_content,
                                                  ConfirmationFragment.newMessageOnlyInstance(getResources().getString(R.string.in_app_notification__sync_error__create_group_convo__title),
                                                                                              getGroupErrorMessage(error),
                                                                                              getResources().getString(R.string.in_app_notification__sync_error__create_convo__button),
                                                                                              error.getId()),
                                                  ConfirmationFragment.TAG
                                                 )
                                         .addToBackStack(ConfirmationFragment.TAG)
                                         .commit();
                break;
        }
    }

    private String getGroupErrorMessage(ErrorsList.ErrorDescription error) {
        int userCount = getUserCount(error);
        switch (error.getType()) {
            case CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION:
                if (userCount == 1) {
                    return getResources().getString(R.string.in_app_notification__sync_error__add_user__body,
                                                    error.getUsers().iterator().next().getName());
                } else {
                    return getResources().getString(R.string.in_app_notification__sync_error__add_multiple_user__body);
                }
            case CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER:
                return getResources().getString(R.string.in_app_notification__sync_error__create_group_convo__body,
                                                error.getConversation().getName());
            default:
                return getResources().getString(R.string.in_app_notification__sync_error__unknown__body);
        }
    }

    private int getUserCount(ErrorsList.ErrorDescription error) {
        int userCount = 0;
        for (User user : error.getUsers()) {
            userCount++;
        }
        return userCount;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  OnBackPressedListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fl__main_content);

        if (fragment instanceof OnBackPressedListener) {
            return ((OnBackPressedListener) fragment).onBackPressed();
        }

        return false;
    }

    private void showChathead(Message message) {
        if (isDetached()) {
            return;
        }
        if (chatheadNotificationIsVisible || chatheadView == null) {
            return;
        }

        chatheadView.setMessage(message);

        final IConversation conversation = message.getConversation();
        chatheadView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (chatheadNotificationIsVisible) {
                    getStoreFactory().getConversationStore().setCurrentConversation(conversation,
                                                                                    ConversationChangeRequester.CHAT_HEAD);
                }
            }
        });

        hideChatheadHandler.removeCallbacksAndMessages(null);

        if (MathUtils.floatEqual(chatheadView.getAlpha(), 1f)) {
            scheduleChatheadHide();
        } else {
            if (isDetached()) {
                return;
            }
            chatheadView.animate()
                        .alpha(1)
                        .translationX(0)
                        .setDuration(getResources().getInteger(R.integer.notification__chathead_animation__duration))
                        .withStartAction(new Runnable() {
                            @Override
                            public void run() {
                                chatheadNotificationIsVisible = true;
                                chatheadView.setTranslationX(-chatheadView.getMeasuredWidth());
                            }
                        })
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                scheduleChatheadHide();
                            }
                        })
                        .start();
        }
    }

    private void scheduleChatheadHide() {
        if (isDetached() || hideChatheadHandler == null) {
            return;
        }
        hideChatheadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isDetached() || getActivity() == null) {
                    return;
                }
                chatheadView.animate()
                            .alpha(0)
                            .translationXBy(-chatheadView.getMeasuredWidth())
                            .setDuration(getResources().getInteger(R.integer.notification__chathead_animation__duration))
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    chatheadNotificationIsVisible = false;
                                }
                            })
                            .start();
            }
        }, chatheadVisibleDuration);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConfirmationFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDialogConfirm(String dialogId) {
        switchConversationIfFailedCreating(dialogId);
        getStoreFactory().getInAppNotificationStore().dismissError(dialogId);
        getChildFragmentManager().popBackStack();
    }

    @Override
    public void onDialogCancel(String dialogId) {
        switchConversationIfFailedCreating(dialogId);
        getStoreFactory().getInAppNotificationStore().dismissError(dialogId);
        getChildFragmentManager().popBackStack();
    }

    private void switchConversationIfFailedCreating(String dialogId) {
        ErrorsList.ErrorDescription error = getStoreFactory().getInAppNotificationStore().getError(dialogId);
        if (error != null && error.getType() == ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER) {
            getStoreFactory().getConversationStore().setCurrentConversationToNext(ConversationChangeRequester.LEAVE_CONVERSATION);
        }
    }

    public interface Container {
    }

    /**
     * Helper class to bundle messages that arrive within a given time span, at the end one is shown
     * Priority order: 1:1 conversation messages over group, newer messages over older ones.
     */
    private class NotificationDisplayPrioritizer {
        private Message currentNextMessage;
        private Message newIncomingMessage;
        private IConversation.Type currentNextMessageConversationType;
        private Handler showMessageHandler;

        private Runnable showChatheadRunnable = new Runnable() {
            public void run() {
                showChathead(currentNextMessage);
                reset();
            }
        };

        private OnConversationLoadedListener currentNextMessageConversationListener = new OnConversationLoadedListener() {
            @Override
            public void onConversationLoaded(IConversation conversation) {
                currentNextMessageConversationType = conversation.getType();
                getStoreFactory().getConversationStore().loadConversation(newIncomingMessage.getConversation().getId(),
                                                                  newMessageConversationListener);
            }
        };

        private OnConversationLoadedListener newMessageConversationListener = new OnConversationLoadedListener() {
            @Override
            public void onConversationLoaded(IConversation conversation) {
                IConversation.Type newIncomingMessageConversationType = conversation.getType();
                if (currentNextMessageConversationType != IConversation.Type.ONE_TO_ONE ||
                    newIncomingMessageConversationType != IConversation.Type.GROUP) {
                    currentNextMessage = newIncomingMessage;
                }
            }
        };

        private NotificationDisplayPrioritizer() {
            showMessageHandler = new Handler();
        }

        public void onNewIncomingMessage(Message newIncomingMessage) {
            if (currentNextMessage == null) {
                currentNextMessage = newIncomingMessage;
                showMessageHandler.removeCallbacksAndMessages(null);
                showMessageHandler.postDelayed(showChatheadRunnable,
                                               getResources().getInteger(R.integer.notification__timespan_messages_considered_arriving_simultaneously));
            } else {
                this.newIncomingMessage = newIncomingMessage;
                getStoreFactory().getConversationStore().loadConversation(currentNextMessage.getConversation().getId(),
                                                                  currentNextMessageConversationListener);
            }
        }

        public void reset() {
            currentNextMessage = null;
            currentNextMessageConversationType = null;
            newIncomingMessage = null;
        }
    }
}
