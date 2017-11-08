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

import com.waz.api.AccentColor;
import com.waz.api.IConversation;
import com.waz.api.NetworkMode;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.api.UiObservable;
import com.waz.api.UiSignal;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.TestActivity;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.network.INetworkStore;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.participants.IParticipantsStore;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MockHelper {

    @SuppressWarnings("unchecked")
    public static <T> UiSignal<T> mockUiSignal() {
        return mock(UiSignal.class);
    }

    public static <T> void mockSubscription(UiSignal<T> uiSignal, final Object mock) {
        when(uiSignal.subscribe(any(Subscriber.class))).thenAnswer(new Answer<Subscription>() {
            @Override
            public Subscription answer(InvocationOnMock invocationOnMock) throws Throwable {
                Object[] args = invocationOnMock.getArguments();
                Subscriber s = (Subscriber) args[0];
                if (s == null) {
                    return null;
                }
                s.next(mock);
                return mock(Subscription.class);
            }
        });
    }

    public static void setupParticipantsMocks(final IConversation mockConversation, final TestActivity activity) {
        IParticipantsStore mockParticipantsStore = activity.getStoreFactory().participantsStore();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                UpdateListener u = (UpdateListener) args[0];
                u.updated();
                return null;
            }
        }).when(mockConversation).addUpdateListener(any(UpdateListener.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                ParticipantsStoreObserver o = (ParticipantsStoreObserver) args[0];
                o.conversationUpdated(mockConversation);
                return null;
            }
        }).when(mockParticipantsStore).addParticipantsStoreObserver(any(ParticipantsStoreObserver.class));


        INetworkStore mockNetworkStore = activity.getStoreFactory().networkStore();
        when(mockNetworkStore.hasInternetConnection()).thenReturn(true);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                NetworkAction a = (NetworkAction) args[0];
                a.execute(NetworkMode.WIFI);
                return null;
            }
        }).when(mockNetworkStore).doIfHasInternetOrNotifyUser(any(NetworkAction.class));
    }

    public static void setupConversationMocks(final IConversation mockConversation, final TestActivity activity) {
        IConversationStore mockConversationStore = activity.getStoreFactory().conversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                UpdateListener u = (UpdateListener) args[0];
                u.updated();
                return null;
            }
        }).when(mockConversation).addUpdateListener(any(UpdateListener.class));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                ConversationStoreObserver o = (ConversationStoreObserver) args[0];
                o.onCurrentConversationHasChanged(null, mockConversation, ConversationChangeRequester.UPDATER);
                return null;
            }
        }).when(mockConversationStore).addConversationStoreObserverAndUpdate(any(ConversationStoreObserver.class));

        doAnswer(new Answer<Void>() {
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                ConversationStoreObserver o = (ConversationStoreObserver) args[0];
                o.onCurrentConversationHasChanged(null, mockConversation, ConversationChangeRequester.UPDATER);
                return null;
            }
        }).when(mockConversationStore).addConversationStoreObserver(any(ConversationStoreObserver.class));
    }

    public static void setupObservableMocks(final UiObservable observable, final TestActivity activity) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                UpdateListener u = (UpdateListener) args[0];
                u.updated();
                return null;
            }
        }).when(observable).addUpdateListener(any(UpdateListener.class));

    }

    public static User createMockUser(String name, String id) {
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(id);
        when(mockUser.getDisplayName()).thenReturn(name);
        AccentColor mockAccent = mock(AccentColor.class);
        when(mockAccent.getColor()).thenReturn(3);
        when(mockUser.getAccent()).thenReturn(mockAccent);
        return mockUser;
    }
}
