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
package com.waz.zclient.core.stores.stub;

import com.waz.api.AssetForUpload;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.MessageContent;
import com.waz.api.SyncState;
import com.waz.api.User;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.conversation.OnConversationLoadedListener;

public class StubConversationStore implements IConversationStore {
  @Override
  public void sendMessage(AudioAssetForUpload audioAssetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {

  }

  @Override
  public void sendMessage(String message) {

  }

  @Override
  public void mute() {

  }

  @Override
  public void loadMenuConversation(String conversationId) {

  }

  @Override
  public void leave(IConversation conversation) {

  }

  @Override
  public void setCurrentConversationToNext(ConversationChangeRequester requester) {

  }

  @Override
  public void sendMessage(byte[] jpegData) {

  }

  @Override
  public void sendMessage(IConversation conversation, AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {

  }

  @Override
  public void sendMessage(IConversation conversation, ImageAsset imageAsset) {

  }

  @Override
  public void sendMessage(IConversation conversation, AudioAssetForUpload audioAssetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {

  }

  @Override
  public int getNumberOfActiveConversations() {
    return 0;
  }

  @Override
  public void addConversationStoreObserverAndUpdate(ConversationStoreObserver conversationStoreObserver) {

  }

  @Override
  public void mute(IConversation conversation, boolean mute) {

  }

  @Override
  public void sendMessage(IConversation conversation, String message) {

  }

  @Override
  public void removeConversationStoreObserver(ConversationStoreObserver conversationStoreObserver) {

  }

  @Override
  public void addConversationStoreObserver(ConversationStoreObserver conversationStoreObserver) {

  }

  @Override
  public void sendMessage(AssetForUpload assetForUpload, MessageContent.Asset.ErrorHandler errorHandler) {

  }

  @Override
  public IConversation getNextConversation() {
    return null;
  }

  @Override
  public void loadCurrentConversation(OnConversationLoadedListener onConversationLoadedListener) {

  }

  @Override
  public void deleteConversation(IConversation conversation, boolean leaveConversation) {

  }

  @Override
  public void createGroupConversation(Iterable<User> users, ConversationChangeRequester conversationChangerSender) {

  }

  @Override
  public void setCurrentConversation(IConversation conversation, ConversationChangeRequester conversationChangerSender) {

  }

  @Override
  public void sendMessage(MessageContent.Location location) {

  }

  @Override
  public void tearDown() {

  }

  @Override
  public void archive(IConversation conversation, boolean archive) {

  }

  @Override
  public void sendMessage(ImageAsset imageAsset) {

  }

  @Override
  public void knockCurrentConversation() {

  }

  @Override
  public SyncState getConversationSyncingState() {
    return null;
  }

  @Override
  public IConversation getConversation(String conversationId) {
    return null;
  }

  @Override
  public void onLogout() {

  }

  @Override
  public IConversation getCurrentConversation() {
    return null;
  }

  @Override
  public void loadConversation(String conversationId, OnConversationLoadedListener onConversationLoadedListener) {

  }
}
