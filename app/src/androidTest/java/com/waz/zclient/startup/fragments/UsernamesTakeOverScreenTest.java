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
package com.waz.zclient.startup.fragments;

import android.support.test.runner.AndroidJUnit4;

import com.waz.api.User;
import com.waz.api.impl.Self;
import com.waz.zclient.AppEntryTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.newreg.fragments.FirstTimeAssignUsername;
import com.waz.zclient.testutils.FragmentTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.mockito.Mockito.when;


@RunWith(AndroidJUnit4.class)
public class UsernamesTakeOverScreenTest extends FragmentTest<AppEntryTestActivity> {
    public UsernamesTakeOverScreenTest() {
        super(AppEntryTestActivity.class);
    }

    @Test
    public void verifyNotPossibleToSkip() throws InterruptedException {
        attachFragment(FirstTimeAssignUsername.newInstance("", ""), FirstTimeAssignUsername.TAG);
        pressBack();
        Thread.sleep(500);
        verify(activity.getFragmentManager().findFragmentByTag(FirstTimeAssignUsername.TAG) != null);
    }

    @Test
    public void verifyConversationListIsOpenedAfterAcceptingUsername() {
        attachFragment(FirstTimeAssignUsername.newInstance("", ""), FirstTimeAssignUsername.TAG);
        onView(withId(R.id.zb__username_first_assign__keep)).perform(click());
    }

    @Test
    public void verifyUserHasTakeOverScreenWithUsername() {
        Self mockSelf = mock(Self.class);
        when(mockSelf.getName()).thenReturn("Test User");
        when(mockSelf.getUsername()).thenReturn("");
        when(activity.getStoreFactory().getZMessagingApiStore().getApi().getSelf()).thenReturn(mockSelf);
        attachFragment(FirstTimeAssignUsername.newInstance(mockSelf.getName(), mockSelf.getUsername()), FirstTimeAssignUsername.TAG);

        verify(onView(withId(R.id.ttv__name)).check(matches(withText(mockSelf.getName()))));
        verify(onView(withId(R.id.ttv__username)).check(matches(isDisplayed())));
    }
}
