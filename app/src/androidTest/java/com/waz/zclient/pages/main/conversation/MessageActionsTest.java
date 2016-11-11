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
package com.waz.zclient.pages.main.conversation;

import android.support.test.runner.AndroidJUnit4;
import com.waz.api.Asset;
import com.waz.api.AssetStatus;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.User;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.FileMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.TextMessageViewController;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.testutils.MockHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.threeten.bp.Instant;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.waz.zclient.testutils.CustomViewAssertions.hasText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isNull;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class MessageActionsTest extends FragmentTest<MainTestActivity> {

    public MessageActionsTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertTextMessageByMeIsDeletableLocallyInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_local)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_local)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).delete();
    }

    @Test
    public void assertTextMessageBySomeoneElseIsDeletableLocallyInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_local)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_local)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).delete();
    }

    @Test
    public void assertTextMessageByMeIsDeletableEverywhereInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    @Test
    public void assertTextMessageByMeIsDeletableEverywhereInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    @Test
    public void assertTextMessageByMeIsNotDeletableEverywhereInGroupConversationWhereIAmNotAMember() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(false);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isNull());
    }

    @Test
    public void assertTextMessageBySomeoneElseIsNotDeletableEverywhere() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isNull());
    }

    @Test
    public void assertImageByMeIsDeletableEverywhere() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.ASSET);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    @Test
    public void assertFileByMeIsDeletableEverywhere() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.ANY_ASSET);
        when(mockMessage.getUser()).thenReturn(mockUser);
        final Asset mockAsset = mock(Asset.class);
        when(mockAsset.getStatus()).thenReturn(AssetStatus.DOWNLOAD_DONE);
        when(mockMessage.getAsset()).thenReturn(mockAsset);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    public void assertLocationByMeIsDeletableEverywhere() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.LOCATION);
        when(mockMessage.getUser()).thenReturn(mockUser);
        final Asset mockAsset = mock(Asset.class);
        when(mockAsset.getStatus()).thenReturn(AssetStatus.DOWNLOAD_DONE);
        when(mockMessage.getAsset()).thenReturn(mockAsset);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    @Test
    public void assertRichMediaByMeIsDeletableEverywhere() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.RICH_MEDIA);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_delete_global)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_delete_global)).perform(click());

        Thread.sleep(500);
        onView(withText(R.string.conversation__message_action__delete__dialog__ok)).perform(click());

        verify(mockMessage).recall();
    }

    @Test
    public void assertTextMessageByMeIsEditableInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);


        final String originalMessageText = "Original text";
        final String editedMessageText = "Some text";
        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.getLocalTime()).thenReturn(Instant.now());
        when(mockMessage.getBody()).thenReturn(originalMessageText);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(800);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
        onView(withId(R.id.cet__cursor)).check(hasText(originalMessageText));

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                MessageContent.Text messageContent = (MessageContent.Text) args[0];
                assertTrue(messageContent.getContent().equals(editedMessageText));
                return null;
            }
        }).when(mockMessage).update(any(MessageContent.Text.class));

        onView(withId(R.id.cet__cursor)).perform(replaceText(editedMessageText));
        Thread.sleep(200);
        onView(withId(R.id.gtv__edit_message__approve)).perform(click());
        verify(mockMessage).update(any(MessageContent.Text.class));
    }

    @Test
    public void assertICanCancelEditingOfTextMessageViaCancelButton() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.getLocalTime()).thenReturn(Instant.now());

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(800);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(500);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
        onView(withId(R.id.gtv__edit_message__close)).check(isVisible());
        onView(withId(R.id.gtv__edit_message__close)).perform(click());
        Thread.sleep(500);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isGone());
    }

    @Test
    public void assertICanCancelEditingOfTextMessageViaTappingOnCallingButton() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.getLocalTime()).thenReturn(Instant.now());

        MockHelper.setupConversationMocks(mockConversation, activity);
        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(800);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(500);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
        onView(withId(R.id.action_audio_call)).check(isVisible());
        onView(withId(R.id.action_audio_call)).perform(click());
        Thread.sleep(500);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isGone());
    }

    @Test
    public void assertTextMessageByMeIsEditableInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(800);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
    }

    @Test
    public void assertTextMessageBySomeoneElseIsNotEditable() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isNull());
    }

    @Test
    public void assertImageByMeIsNotEditable() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.ASSET);
        when(mockMessage.getUser()).thenReturn(mockUser);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isNull());
    }

    @Test
    public void assertICanUndoEditingOfTextMessage() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final String originalMessageText = "Original text";
        final String editedMessageText = "Some text";
        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.getBody()).thenReturn(originalMessageText);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(800);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());

        onView(withId(R.id.cet__cursor)).perform(replaceText(editedMessageText));
        Thread.sleep(200);
        onView(withId(R.id.gtv__edit_message__reset)).perform(click());
        Thread.sleep(200);
        onView(withId(R.id.cet__cursor)).check(hasText(originalMessageText));
    }

    @Test
    public void assertICanSwitchToEditingAnotherMessageWhileEditingAMessage() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(true);

        final String firstMessageText = "First message";
        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.getBody()).thenReturn(firstMessageText);

        final String secondMessageText = "Second message";
        final Message secondMockMessage = mock(Message.class);
        when(secondMockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(secondMockMessage.getUser()).thenReturn(mockUser);
        when(secondMockMessage.getBody()).thenReturn(secondMessageText);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(800);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
        onView(withId(R.id.cet__cursor)).check(hasText(firstMessageText));

        // Edit another message
        fragment.onItemLongClick(secondMockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_edit)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_edit)).perform(click());

        Thread.sleep(800);
        onView(withId(R.id.emct__edit_message__toolbar)).check(isVisible());
        onView(withId(R.id.cet__cursor)).check(hasText(secondMessageText));
    }

    @Test
    public void assertReceivedTextMessageCanBeLiked() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.isLikedByThisUser()).thenReturn(false);
        when(mockMessage.isLiked()).thenReturn(false);
        when(mockMessage.getConversation()).thenReturn(mockConversation);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_like)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_like)).perform(click());

        verify(mockMessage).like();
    }

    @Test
    public void assertReceivedAndLikedTextMessageCanBeUnliked() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationStore mockConversationStore = activity.getStoreFactory().getConversationStore();
        when(mockConversationStore.getCurrentConversation()).thenReturn(mockConversation);

        User mockUser = mock(User.class);
        when(mockUser.isMe()).thenReturn(false);

        final Message mockMessage = mock(Message.class);
        when(mockMessage.getMessageType()).thenReturn(Message.Type.TEXT);
        when(mockMessage.getUser()).thenReturn(mockUser);
        when(mockMessage.isLikedByThisUser()).thenReturn(true);
        when(mockMessage.isLiked()).thenReturn(false);
        when(mockMessage.getConversation()).thenReturn(mockConversation);

        ConversationFragment fragment = ConversationFragment.newInstance();
        attachFragment(fragment, ConversationFragment.TAG);
        Thread.sleep(500);
        fragment.onItemLongClick(mockMessage, getMockMessageViewController(mockMessage));

        Thread.sleep(500);
        onView(withId(R.id.message_bottom_menu_item_unlike)).check(isVisible());
        onView(withId(R.id.message_bottom_menu_item_unlike)).perform(click());

        verify(mockMessage).unlike();
    }

    private MessageViewController getMockMessageViewController(Message message) {
        if (message.getMessageType() == Message.Type.ANY_ASSET) {
            return mock(FileMessageViewController.class);
        }
        return mock(TextMessageViewController.class);
    }


}
