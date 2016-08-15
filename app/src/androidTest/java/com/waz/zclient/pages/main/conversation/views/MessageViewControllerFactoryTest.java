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

import com.waz.api.Message;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewControllerFactory;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ErrorMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.FileMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ImageMessageViewController;
import com.waz.zclient.testutils.ViewTest;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageViewControllerFactoryTest extends ViewTest<MainTestActivity> {

    public MessageViewControllerFactoryTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void verifyAssetMessageViewReturned() {
        Message message = mock(Message.class);
        when(message.getMessageType()).thenReturn(Message.Type.ANY_ASSET);

        MessageViewController viewController = MessageViewControllerFactory.create(activity, message, ViewControllerMockHelper.getMockMessageViewsContainer(activity));
        assertTrue(viewController instanceof FileMessageViewController);
    }

    @Test
    public void verifyImageMessageViewReturned() {
        Message message = mock(Message.class);
        when(message.getMessageType()).thenReturn(Message.Type.ASSET);

        MessageViewController viewController = MessageViewControllerFactory.create(activity, message, ViewControllerMockHelper.getMockMessageViewsContainer(activity));
        assertTrue(viewController instanceof ImageMessageViewController);
    }

    @Test
    public void verifyNoErrorMessageViewReturned() {
        Message message = mock(Message.class);
        when(message.getParts()).thenReturn(new Message.Part[0]);
        for (Message.Type type : Message.Type.values()) {
            if (type == Message.Type.CONNECT_ACCEPTED ||
                type == Message.Type.INCOMING_CALL ||
                type == Message.Type.UNKNOWN) {
                continue;
            }
            when(message.getMessageType()).thenReturn(type);
            MessageViewController viewController = MessageViewControllerFactory.create(activity, message, ViewControllerMockHelper.getMockMessageViewsContainer(activity));
            assertFalse(type + " returned ErrorMessageViewController", viewController instanceof ErrorMessageViewController);
        }
    }
}
