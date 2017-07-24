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
package com.waz.zclient.api.scala;

import android.os.Handler;

import com.waz.api.AssetForUpload;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.api.MessageContent;
import com.waz.api.SyncIndicator;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.controllers.global.SelectionController;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import timber.log.Timber;

public class ScalaConversationStore implements IConversationStore, SelectionController.ConversationChangedListener {
    public static final String TAG = ScalaConversationStore.class.getName();
    private static final int ARCHIVE_DELAY = 500;

    // observers attached to a IConversationStore
    private Set<ConversationStoreObserver> conversationStoreObservers = new HashSet<>();

    private ConversationsList conversationsList;
    private SelectionController selectionController;
    private ConversationsList establishedConversationsList;

    private SyncIndicator syncIndicator;
    private IConversation menuConversation;

    private final UpdateListener syncStateUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            notifySyncChanged(syncIndicator.getState());
        }
    };

    private final UpdateListener menuConversationUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            notifyMenuConversationUpdated();
        }
    };

    @Override
    public IConversation getCurrentConversation() {
        return selectionController.getSelectedConversation();
    }

    private final UpdateListener conversationListUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            if (conversationsList.size() == 0 &&
                conversationsList.isReady()) {
                conversationsList.setSelectedConversation(null);
            }

            notifyConversationListUpdated();
        }
    };

    public ScalaConversationStore(ZMessagingApi zMessagingApi, SelectionController selectionController) {
        conversationsList = zMessagingApi.getConversations();
        this.selectionController = selectionController;
        establishedConversationsList = conversationsList.getEstablishedConversations();

        selectionController.setOnConversationChangeCallback(this);

        conversationsList.addUpdateListener(conversationListUpdateListener);
        conversationListUpdateListener.updated();

        syncIndicator = conversationsList.getSyncIndicator();
        syncIndicator.addUpdateListener(syncStateUpdateListener);
    }

    @Override
    public void tearDown() {
        if (syncIndicator != null) {
            syncIndicator.removeUpdateListener(syncStateUpdateListener);
            syncIndicator = null;
        }

        if (conversationsList != null) {
            conversationsList.removeUpdateListener(conversationListUpdateListener);
            conversationsList.setSelectedConversation(null);
            conversationsList = null;
        }

        if (menuConversation != null) {
            menuConversation.removeUpdateListener(menuConversationUpdateListener);
            menuConversation = null;
        }

        establishedConversationsList = null;
    }

    @Override
    public void onLogout() {
        conversationsList.setSelectedConversation(null);
    }

    @Override
    public IConversation getConversation(String conversationId) {
        if (conversationId == null || conversationsList == null) {
            return null;
        }
        return conversationsList.getConversation(conversationId);
    }

    @Override
    public void loadConversation(String conversationId, final OnConversationLoadedListener onConversationLoadedListener) {
        conversationsList.getConversation(conversationId, new ConversationsList.ConversationCallback() {
            @Override
            public void onConversationsFound(Iterable<IConversation> conversations) {
                onConversationLoadedListener.onConversationLoaded(conversations.iterator().next());
            }
        });
    }

    @Override
    public void setCurrentConversation(IConversation conversation,
                                       ConversationChangeRequester conversationChangerSender) {
        if (conversation != null) {
            conversation.setArchived(false);
        }
        if (conversation != null) {
            Timber.i("Set current conversation to %s, requester %s", conversation.getName(), conversationChangerSender);
        } else {
            Timber.i("Set current conversation to null, requester %s", conversationChangerSender);
        }
        IConversation oldConversation = conversationChangerSender == ConversationChangeRequester.FIRST_LOAD ? null : getCurrentConversation();
        conversationsList.setSelectedConversation(conversation);

        if (oldConversation == null || conversation != null && oldConversation.getId().equals(conversation.getId())) {
            // Notify explicitly if the conversation doesn't change, the UiSginal notifies only when the conversation changes
            notifyCurrentConversationHasChanged(oldConversation, conversation, conversationChangerSender);
        }
    }

    @Override
    public void loadCurrentConversation(OnConversationLoadedListener onConversationLoadedListener) {
        if (conversationsList != null && getCurrentConversation() != null) {
            onConversationLoadedListener.onConversationLoaded(getCurrentConversation());
        }
    }

    @Override
    public void setCurrentConversationToNext(ConversationChangeRequester requester) {
        IConversation nextConv = getNextConversation();
        if (nextConv != null) {
            setCurrentConversation(nextConv, requester);
        }
    }

    @Override
    public IConversation getNextConversation() {
        if (conversationsList == null || conversationsList.size() == 0) {
            return null;
        }

        for (int i = 0; i < conversationsList.size(); i++) {
            IConversation previousConversation = i >= 1 ? conversationsList.get(i - 1) : null;
            IConversation conversation = conversationsList.get(i);
            IConversation nextConversation = i == (conversationsList.size() - 1) ? null : conversationsList.get(i + 1);
            if (getCurrentConversation().equals(conversation)) {
                if (nextConversation != null) {
                    return nextConversation;
                }
                return previousConversation;
            }
        }
        return null;
    }

    @Override
    public void loadMenuConversation(String conversationId) {
        menuConversation = conversationsList.getConversation(conversationId);
        menuConversation.removeUpdateListener(menuConversationUpdateListener);
        menuConversation.addUpdateListener(menuConversationUpdateListener);
        menuConversationUpdateListener.updated();
    }

    @Override
    public int getNumberOfActiveConversations() {
        if (establishedConversationsList == null) {
            return 0;
        }
        return establishedConversationsList.size();
    }

    @Override
    public SyncState getConversationSyncingState() {
        return syncIndicator.getState();
    }

    @Override
    public void addConversationStoreObserver(ConversationStoreObserver conversationStoreObserver) {
        // Prevent concurrent modification (if this add was executed by one of current observers during notify* callback)
        Set<ConversationStoreObserver> observers = new HashSet<>(conversationStoreObservers);
        observers.add(conversationStoreObserver);
        conversationStoreObservers = observers;
    }

    @Override
    public void addConversationStoreObserverAndUpdate(ConversationStoreObserver conversationStoreObserver) {
        addConversationStoreObserver(conversationStoreObserver);
        if (getCurrentConversation() != null) {
            conversationStoreObserver.onCurrentConversationHasChanged(null,
                                                                      getCurrentConversation(),
                                                                      ConversationChangeRequester.UPDATER);
            conversationStoreObserver.onConversationSyncingStateHasChanged(getConversationSyncingState());
        }
        if (conversationsList != null) {
            conversationStoreObserver.onConversationListUpdated(conversationsList);
        }
    }

    @Override
    public void removeConversationStoreObserver(ConversationStoreObserver conversationStoreObserver) {
        // Prevent concurrent modification
        if (conversationStoreObservers.contains(conversationStoreObserver)) {
            Set<ConversationStoreObserver> observers = new HashSet<>(conversationStoreObservers);
            observers.remove(conversationStoreObserver);
            conversationStoreObservers = observers;
        }
    }

    @Override
    public void createGroupConversation(Iterable<User> users,
                                        final ConversationChangeRequester conversationChangerSender) {
        conversationsList.createGroupConversation(
            scala.collection.JavaConversions.iterableAsScalaIterable(users).toSeq(),
            new ConversationsList.ConversationCallback() {
                @Override
                public void onConversationsFound(Iterable<IConversation> iterable) {
                    Iterator<IConversation> iterator = iterable.iterator();
                    if (!iterator.hasNext()) {
                        return;
                    }
                    ConversationChangeRequester conversationChangeRequester = conversationChangerSender;
                    if (conversationChangeRequester != ConversationChangeRequester.START_CONVERSATION_FOR_CALL &&
                        conversationChangeRequester != ConversationChangeRequester.START_CONVERSATION_FOR_VIDEO_CALL &&
                        conversationChangeRequester != ConversationChangeRequester.START_CONVERSATION_FOR_CAMERA) {
                        conversationChangeRequester = ConversationChangeRequester.START_CONVERSATION;
                    }
                    setCurrentConversation(iterator.next(),
                                           conversationChangeRequester);
                }
            }
        );
    }

    @Override
    public void sendMessage(final String message) {
        sendMessage(getCurrentConversation(), message);
    }

    @Override
    public void sendMessage(IConversation conversation, String message) {
        if (conversation != null) {
            conversation.sendMessage(new MessageContent.Text(message));
        }
    }

    @Override
    public void sendMessage(final byte[] jpegData) {
        IConversation current = getCurrentConversation();
        if (current != null) {
            current.sendMessage(new MessageContent.Image(ImageAssetFactory.getImageAsset(jpegData)));
        }
    }

    @Override
    public void sendMessage(ImageAsset imageAsset) {
        sendMessage(getCurrentConversation(), imageAsset);
    }

    @Override
    public void sendMessage(MessageContent.Location location) {
        if (getCurrentConversation() == null) {
            return;
        }
        getCurrentConversation().sendMessage(location);
    }

    @Override
    public void sendMessage(AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {
        sendMessage(getCurrentConversation(), assetForUpload, errorHandler);
    }

    @Override
    public void sendMessage(IConversation conversation, AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {
        if (conversation != null) {
            Timber.i("Send file to %s", conversation.getName());
           conversation.sendMessage(new MessageContent.Asset(assetForUpload, errorHandler));
        }
    }

    @Override
    public void sendMessage(IConversation conversation, ImageAsset imageAsset) {
        if (conversation != null) {
            conversation.sendMessage(new MessageContent.Image(imageAsset));
        }
    }

    @Override
    public void sendMessage(AudioAssetForUpload audioAssetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {
        sendMessage(getCurrentConversation(), audioAssetForUpload, errorHandler);
    }

    @Override
    public void sendMessage(IConversation conversation,
                            AudioAssetForUpload audioAssetForUpload,
                            MessageContent.Asset.ErrorHandler errorHandler) {
        if (conversation != null) {
            Timber.i("Send audio file to %s", conversation.getName());
            conversation.sendMessage(new MessageContent.Asset(audioAssetForUpload, errorHandler));
        }
    }

    @Override
    public void knockCurrentConversation() {
        if (getCurrentConversation() != null) {
            getCurrentConversation().knock();
        }
    }

    @Override
    public void mute() {
        mute(getCurrentConversation(), !getCurrentConversation().isMuted());
    }

    @Override
    public void mute(IConversation conversation, boolean mute) {
        conversation.setMuted(mute);
    }

    @Override
    public void archive(IConversation conversation, boolean archive) {
        if (conversation.isSelected()) {
            final IConversation nextConversation = getNextConversation();
            if (nextConversation != null) {
                // don't want to change selected item immediately
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (conversationsList != null) {
                            setCurrentConversation(nextConversation, ConversationChangeRequester.ARCHIVED_RESULT);
                        }
                    }
                }, ARCHIVE_DELAY);
            }
        }

        conversation.setArchived(archive);

        // Set current conversation to unarchived
        if (!archive) {
            setCurrentConversation(conversation, ConversationChangeRequester.CONVERSATION_LIST_UNARCHIVED_CONVERSATION);
        }
    }

    @Override
    public void leave(IConversation conversation) {
        conversation.leave();
    }

    @Override
    public void deleteConversation(IConversation conversation, boolean leaveConversation) {
        if (leaveConversation) {
            conversation.leave();
        } else {
            conversation.clear();
        }
    }

    private void notifyConversationListUpdated() {
        for (ConversationStoreObserver conversationStoreObserver : conversationStoreObservers) {
            conversationStoreObserver.onConversationListUpdated(conversationsList);
        }
    }

    private void notifyMenuConversationUpdated() {
        for (ConversationStoreObserver conversationStoreObserver : conversationStoreObservers) {
            conversationStoreObserver.onMenuConversationHasChanged(menuConversation);
        }
    }

    private void notifyCurrentConversationHasChanged(IConversation fromConversation,
                                                     IConversation toConversation,
                                                     ConversationChangeRequester conversationChangerSender) {
        for (ConversationStoreObserver conversationStoreObserver : conversationStoreObservers) {
            conversationStoreObserver.onCurrentConversationHasChanged(fromConversation,
                                                                      toConversation,
                                                                      conversationChangerSender);
        }
    }

    private void notifySyncChanged(SyncState syncState) {
        for (ConversationStoreObserver observer : conversationStoreObservers) {
            observer.onConversationSyncingStateHasChanged(syncState);
        }
    }

    @Override
    public void onConversationChanged(IConversation prev, IConversation current) {
        notifyCurrentConversationHasChanged(prev, current, ConversationChangeRequester.UPDATER);
    }
}
