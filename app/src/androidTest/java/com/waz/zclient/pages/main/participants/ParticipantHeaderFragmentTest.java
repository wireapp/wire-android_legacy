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

import android.support.test.runner.AndroidJUnit4;
import com.waz.api.IConversation;
import com.waz.api.User;
import com.waz.api.Verification;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.network.INetworkStore;
import com.waz.zclient.pages.main.participants.ParticipantHeaderFragment;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.testutils.MockHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.waz.zclient.testutils.CustomViewAssertions.hasText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ParticipantHeaderFragmentTest extends FragmentTest<MainTestActivity>  {
    public ParticipantHeaderFragmentTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertOneToOneConversationCanNotBeEdited() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        User otherUser = MockHelper.createMockUser("James", "988");
        when(otherUser.getVerified()).thenReturn(Verification.UNVERIFIED);
        when(mockConversation.getOtherParticipant()).thenReturn(otherUser);

        MockHelper.setupParticipantsMocks(mockConversation, activity);

        attachFragment(ParticipantHeaderFragment.newInstance(IConnectStore.UserRequester.CONVERSATION), ParticipantHeaderFragment.TAG);

        Thread.sleep(400);

        onView(withId(R.id.gtv__participants_header__pen_icon)).check(isGone());
        onView(withId(R.id.taet__participants__header__editable)).check(isGone());
    }

    @Test
    public void assertUserDetailsIsVisibleInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        User otherUser = MockHelper.createMockUser("James", "988");
        when(otherUser.getVerified()).thenReturn(Verification.UNVERIFIED);
        when(mockConversation.getOtherParticipant()).thenReturn(otherUser);

        MockHelper.setupParticipantsMocks(mockConversation, activity);

        attachFragment(ParticipantHeaderFragment.newInstance(IConnectStore.UserRequester.CONVERSATION), ParticipantHeaderFragment.TAG);

        Thread.sleep(400);

        onView(withId(R.id.udv__participants__user_details)).check(isVisible());
    }

    @Test
    public void assertGroupConversationNameCanBeEdited() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        String conversationName = "A group";
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.getName()).thenReturn(conversationName);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        MockHelper.setupParticipantsMocks(mockConversation, activity);

        attachFragment(ParticipantHeaderFragment.newInstance(IConnectStore.UserRequester.CONVERSATION), ParticipantHeaderFragment.TAG);

        onView(withId(R.id.gtv__participants_header__pen_icon)).check(isVisible());
        onView(withId(R.id.gtv__participants_header__pen_icon)).perform(click());

        Thread.sleep(500);

        onView(withId(R.id.gtv__participants_header__pen_icon)).check(isGone());
        onView(withId(R.id.taet__participants__header__editable)).check(hasText(conversationName));
    }

    @Test
    public void assertGroupMembersSummaryIsVisibleInGroup() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        MockHelper.setupParticipantsMocks(mockConversation, activity);

        attachFragment(ParticipantHeaderFragment.newInstance(IConnectStore.UserRequester.CONVERSATION), ParticipantHeaderFragment.TAG);

        Thread.sleep(400);

        onView(withId(R.id.ttv__participants__sub_header)).check(isVisible());
    }


}
