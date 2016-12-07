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
package com.waz.zclient.pages.main.conversation.views;

import android.support.test.runner.AndroidJUnit4;
import com.waz.api.ContactDetails;
import com.waz.api.User;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.testutils.MockHelper;
import com.waz.zclient.testutils.ViewTest;
import com.waz.zclient.ui.views.UserDetailsView;
import com.waz.zclient.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.waz.zclient.testutils.CustomViewAssertions.doesNotContainText;
import static com.waz.zclient.testutils.CustomViewAssertions.hasText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class UserDetailsViewTest extends ViewTest<MainTestActivity> {
    public UserDetailsViewTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertExistingUserHandleIsVisible() {
        String handle = "red_panda";
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getUsername()).thenReturn(handle);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        onView(withId(R.id.ttv__user_details__user_name)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_name)).check(hasText(StringUtils.formatUsername(handle)));
    }

    @Test
    public void assertEmptyUserHandleIsNotShown() {
        String handle = "";
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getUsername()).thenReturn(handle);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        onView(withId(R.id.ttv__user_details__user_name)).check(isGone());
    }

    @Test
    public void assertAddressBookNameThatIsDifferentThanWireNameIsShown() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        ContactDetails mockContactDetails = mock(ContactDetails.class);

        String addressBookName = "Mom";
        when(mockContactDetails.getDisplayName()).thenReturn(addressBookName);
        when(mockUser.getFirstContact()).thenReturn(mockContactDetails);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String addressBookInfo = activity.getString(com.waz.zclient.ui.R.string.content__message__connect_request__user_info,
                                                   addressBookName);

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(addressBookInfo));
    }

    @Test
    public void assertAddressBookNameThatIsSameThanWireNameIsShown() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        ContactDetails mockContactDetails = mock(ContactDetails.class);
        String addressBookName = "Anna";
        when(mockContactDetails.getDisplayName()).thenReturn(addressBookName);
        when(mockUser.getFirstContact()).thenReturn(mockContactDetails);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String addressBookInfo = activity.getString(com.waz.zclient.ui.R.string.content__message__connect_request__user_info,
                                                   "");

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(addressBookInfo));
    }

    @Test
    public void assertCommonUsersIsShownForUnconnectedUserThatIsNotInAddressBook() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getConnectionStatus()).thenReturn(User.ConnectionStatus.UNCONNECTED);
        int commonUsersCount = 3;
        when(mockUser.getCommonConnectionsCount()).thenReturn(commonUsersCount);

        when(mockUser.getFirstContact()).thenReturn(null);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String commonUsersSummary = activity.getResources().getQuantityString(com.waz.zclient.ui.R.plurals.connect_request__common_users__summary,
                                                                     commonUsersCount,
                                                                     commonUsersCount);

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(commonUsersSummary));
    }

    @Test
    public void assertCommonUsersIsShownForPendingIncomingUserThatIsNotInAddressBook() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getConnectionStatus()).thenReturn(User.ConnectionStatus.PENDING_FROM_OTHER);
        int commonUsersCount = 3;
        when(mockUser.getCommonConnectionsCount()).thenReturn(commonUsersCount);

        when(mockUser.getFirstContact()).thenReturn(null);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String commonUsersSummary = activity.getResources().getQuantityString(com.waz.zclient.ui.R.plurals.connect_request__common_users__summary,
                                                                                    commonUsersCount,
                                                                                    commonUsersCount);

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(commonUsersSummary));
    }

    @Test
    public void assertCommonUsersIsShownForPendingOutgoingUserThatIsNotInAddressBook() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getConnectionStatus()).thenReturn(User.ConnectionStatus.PENDING_FROM_USER);
        int commonUsersCount = 3;
        when(mockUser.getCommonConnectionsCount()).thenReturn(commonUsersCount);

        when(mockUser.getFirstContact()).thenReturn(null);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String commonUsersSummary = activity.getResources().getQuantityString(com.waz.zclient.ui.R.plurals.connect_request__common_users__summary,
                                                                                    commonUsersCount,
                                                                                    commonUsersCount);

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(commonUsersSummary));
    }

    @Test
    public void assertCommonUsersIsNotShownForUnconnectedUserThatIsInAddressBook() {
        User mockUser = MockHelper.createMockUser("Anna", "123");
        when(mockUser.getConnectionStatus()).thenReturn(User.ConnectionStatus.UNCONNECTED);
        int commonUsersCount = 3;
        when(mockUser.getCommonConnectionsCount()).thenReturn(commonUsersCount);

        String addressBookName = "Mom";
        ContactDetails mockContactDetails = mock(ContactDetails.class);
        when(mockContactDetails.getDisplayName()).thenReturn(addressBookName);
        when(mockUser.getFirstContact()).thenReturn(mockContactDetails);

        UserDetailsView userDetailsView = new UserDetailsView(activity);
        userDetailsView.setUser(mockUser);

        setView(userDetailsView);

        final String commonUsersSummary = activity.getResources().getQuantityString(com.waz.zclient.ui.R.plurals.connect_request__common_users__summary,
                                                                                    commonUsersCount,
                                                                                    commonUsersCount);

        final String addressBookInfo = activity.getString(com.waz.zclient.ui.R.string.content__message__connect_request__user_info,
                                                   addressBookName);

        onView(withId(R.id.ttv__user_details__user_info)).check(isVisible());
        onView(withId(R.id.ttv__user_details__user_info)).check(doesNotContainText(commonUsersSummary));
        onView(withId(R.id.ttv__user_details__user_info)).check(hasText(addressBookInfo));
    }
}
