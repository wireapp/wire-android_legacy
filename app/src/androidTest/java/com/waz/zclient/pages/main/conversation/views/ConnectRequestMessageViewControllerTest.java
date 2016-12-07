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
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.User;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ConnectRequestMessageViewController;
import com.waz.zclient.testutils.ViewTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static com.waz.zclient.testutils.MockHelper.createMockConversation;
import static com.waz.zclient.testutils.MockHelper.createMockMessage;
import static com.waz.zclient.testutils.MockHelper.createMockSeparator;
import static com.waz.zclient.testutils.MockHelper.createMockUser;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ConnectRequestMessageViewControllerTest extends ViewTest<MainTestActivity> {
    public ConnectRequestMessageViewControllerTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertUserDetailsAndImageAreShown() throws InterruptedException {
        Message message = createMockMessage(Message.Type.CONNECT_REQUEST, Message.Status.DELIVERED, false);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        User mockUser = createMockUser("Brian", "123");
        when(conversation.getOtherParticipant()).thenReturn(mockUser);
        when(message.getConversation()).thenReturn(conversation);

        ConnectRequestMessageViewController messageAndSeparatorViewController = createConnectRequestMessageViewController();
        messageAndSeparatorViewController.setMessage(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.udv__row_conversation__connect_request__user_details)).check(isVisible());
        onView(withId(R.id.cv__row_conversation__connect_request__chat_head)).check(isVisible());
    }

    private ConnectRequestMessageViewController createConnectRequestMessageViewController() {
        MessageViewsContainer messageViewsContainer = ViewControllerMockHelper.getMockMessageViewsContainer(activity);
        return new ConnectRequestMessageViewController(activity,
                                                       messageViewsContainer);
    }
}
