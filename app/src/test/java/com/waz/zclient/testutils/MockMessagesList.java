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
package com.waz.zclient.testutils;

import android.annotation.SuppressLint;
import android.os.Parcel;
import com.waz.api.Asset;
import com.waz.api.EphemeralExpiration;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.MessagesList;
import com.waz.api.SyncIndicator;
import com.waz.api.User;
import org.threeten.bp.Instant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MockMessagesList extends MockObservable implements MessagesList {

    private List<MockMessage> mockMessages = new ArrayList<>();
    private int lastReadIndex = -1;

    private MockConversation conversation;

    public MockMessagesList(MockConversation conversation) {
        super(conversation.id);
        this.conversation = conversation;
    }

    @Override
    public int getUnreadCount() {
        return lastReadIndex == -1 ? mockMessages.size() : mockMessages.size() - 1 - lastReadIndex;
    }

    @Override
    public int getLastReadIndex() {
        return lastReadIndex;
    }

    @Override
    public int getMessageIndex(Message m) {
        return mockMessages.indexOf(m);
    }

    @Override
    public Message getLastMessage() {
        return mockMessages.get(mockMessages.size() - 1);
    }

    @Override
    public SyncIndicator getSyncIndicator() {
        return null;
    }

    @Override
    public Message get(int position) {
        return mockMessages.get(position);
    }

    @Override
    public int size() {
        return mockMessages.size();
    }

    @Override
    public Iterator<Message> iterator() {
        return null;
    }

    @Override
    public void triggerInternalUpdate() {
        conversation.triggerInternalUpdate();
        super.triggerInternalUpdate();
    }

    public void setMessages(int numMessages) {
        mockMessages.clear();
        for (int i = 0; i < numMessages; i++) {
            mockMessages.add(new MockMessage(i));
        }
        triggerInternalUpdate();
    }

    public void addExtraMessage(boolean messageVisible) {
        mockMessages.add(new MockMessage(mockMessages.size()));
        if (messageVisible) {
            lastReadIndex++;
        }
        triggerInternalUpdate();
    }

    public void setLastReadMessageIndex(int index) {
        if (index < 0 || index >= mockMessages.size()) {
            throw new IllegalArgumentException("The last read message needs to exist!");
        }
        lastReadIndex = index;
    }

    @SuppressLint("ParcelCreator")
    public static class MockMessage extends MockObservable implements Message {

        public MockMessage(int id) {
            super(id);
        }

        @Override
        public Part[] getParts() {
            return new Part[0];
        }

        @Override
        public String getId() {
            return Integer.toString(id);
        }

        @Override
        public String getConversationId() {
            return null;
        }

        @Override
        public IConversation getConversation() {
            return null;
        }

        @Override
        public Type getMessageType() {
            return null;
        }

        @Override
        public Status getMessageStatus() {
            return null;
        }

        @Override
        public User getUser() {
            return null;
        }

        @Override
        public ImageAsset getImage() {
            return null;
        }

        @Override
        public ImageAsset getImage(int width, int height) {
            return null;
        }

        @Override
        public Asset getAsset() {
            return null;
        }

        @Override
        public String getBody() {
            return null;
        }

        @Override
        public Instant getTime() {
            return null;
        }

        @Override
        public Instant getEditTime() {
            return null;
        }

        @Override
        public boolean isDeleted() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean isHotKnock() {
            return false;
        }

        @Override
        public boolean isEdited() {
            return false;
        }

        @Override
        public boolean isCreateConversation() {
            return false;
        }
        
        @Override
        public boolean isFirstMessage() {
            return false;
        }

        @Override
        public boolean isLastMessageFromSelf() {
            return false;
        }

        @Override
        public boolean isLastMessageFromOther() {
            return false;
        }

        @Override
        public boolean isUserMentioned() {
            return false;
        }

        @Override
        public User[] getMentionedUsers() {
            return new User[0];
        }

        @Override
        public User[] getLikes() {
            return new User[0];
        }

        @Override
        public boolean isLikedByThisUser() {
            return false;
        }

        @Override
        public boolean isLiked() {
            return false;
        }

        @Override
        public void like() {

        }

        @Override
        public void unlike() {

        }

        @Override
        public boolean isEphemeral() {
            return false;
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public EphemeralExpiration getEphemeralExpiration() {
            return null;
        }

        @Override
        public Instant getExpirationTime() {
            return null;
        }

        @Override
        public void retry() {

        }

        @Override
        public void delete() {

        }

        @Override
        public void recall() {

        }

        @Override
        public void update(MessageContent.Text content) {

        }

        @Override
        public Instant getLocalTime() {
            return null;
        }

        @Override
        public MessageContent.Location getLocation() {
            return null;
        }

        @Override
        public String getNewConversationName() {
            return null;
        }

        @Override
        public int getImageWidth() {
            return 0;
        }

        @Override
        public int getImageHeight() {
            return 0;
        }

        @Override
        public User[] getMembers() {
            return new User[0];
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {

        }
    }
}
