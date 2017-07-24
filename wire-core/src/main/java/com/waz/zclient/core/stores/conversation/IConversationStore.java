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
package com.waz.zclient.core.stores.conversation;

import com.waz.api.AssetForUpload;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.MessageContent;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.zclient.core.stores.IStore;

public interface IConversationStore extends IStore {

    /**
     * adds an observer on this store
     * @param conversationStoreObserver
     */
    void addConversationStoreObserver(ConversationStoreObserver conversationStoreObserver);

    void addConversationStoreObserverAndUpdate(ConversationStoreObserver conversationStoreObserver);

    /**
     * removes an observer on this store
     * @param conversationStoreObserver
     */
    void removeConversationStoreObserver(ConversationStoreObserver conversationStoreObserver);

    void mute();

    /**
     * mute conversation
     * @param conversation
     * @param mute
     */
    void mute(IConversation conversation, boolean mute);

    /**
     * archive conversation
     * @param conversation
     * @param archive
     */
    void archive(IConversation conversation, boolean archive);

    /**
     * Leaves conversation
     * @param conversation
     */
    void leave(IConversation conversation);

    /**
     * Deletes conversation
     * @param conversation
     */
    void deleteConversation(IConversation conversation, boolean leaveConversation);


    /**
     * gets the current conversation
     * @return
     */
    IConversation getCurrentConversation();

    /**
     * sets the current conversation so that the message fragment gets informed
      * @param conversation
     * @param conversationChangerSender
     */
    void setCurrentConversation(IConversation conversation, ConversationChangeRequester conversationChangerSender);

    /**
     * Same as calling {@code setCurrentConversation(getNextConversation())}
     * @param requester
     */
    void setCurrentConversationToNext(ConversationChangeRequester requester);

    /**
     * For use when archiving a conversation - you need to set a new current conversation
     *
     * @return IConversation - if the below conversation is not archived this will be returned,
     * otherwise the conversation above
     */
    IConversation getNextConversation();

    IConversation getConversation(String conversationId);

    void sendMessage(String message);

    void sendMessage(IConversation conversation, String message);

    void sendMessage(byte[] jpegData);

    void sendMessage(ImageAsset imageAsset);

    void sendMessage(MessageContent.Location location);

    void sendMessage(AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler);

    void sendMessage(IConversation conversation, AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler);

    void sendMessage(IConversation conversation, ImageAsset imageAsset);


    void sendMessage(AudioAssetForUpload audioAssetForUpload, MessageContent.Asset.ErrorHandler errorHandler);

    void sendMessage(IConversation conversation, AudioAssetForUpload audioAssetForUpload, MessageContent.Asset.ErrorHandler errorHandler);

    void knockCurrentConversation();

    void createGroupConversation(Iterable<User> users, ConversationChangeRequester conversationChangerSender);

    void loadCurrentConversation(OnConversationLoadedListener onConversationLoadedListener);

    void loadConversation(String conversationId, OnConversationLoadedListener onConversationLoadedListener);

    void loadMenuConversation(String conversationId);

    int getNumberOfActiveConversations();

    SyncState getConversationSyncingState();

    void onLogout();
}
