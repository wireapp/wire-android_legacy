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
import com.waz.api.Asset;
import com.waz.api.AssetStatus;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.User;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.pages.main.conversation.views.row.footer.FooterViewController;
import com.waz.zclient.pages.main.conversation.views.row.footer.FooterViewControllerFactory;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageAndSeparatorViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewControllerFactory;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.testutils.ViewTest;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.ZTimeFormatter;
import com.waz.zclient.views.chathead.ChatheadImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.threeten.bp.DateTimeUtils;
import org.threeten.bp.Duration;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.doubleClick;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.waz.zclient.testutils.CustomViewAssertions.containsText;
import static com.waz.zclient.testutils.CustomViewAssertions.doesNotContainText;
import static com.waz.zclient.testutils.CustomViewAssertions.hasText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static com.waz.zclient.testutils.MockHelper.createMockConversation;
import static com.waz.zclient.testutils.MockHelper.createMockMessage;
import static com.waz.zclient.testutils.MockHelper.createMockSeparator;
import static com.waz.zclient.testutils.MockHelper.createMockUser;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class FooterViewControllerTest extends ViewTest<MainTestActivity> {

    public FooterViewControllerTest() {
        super(MainTestActivity.class);
    }

    /**
     * Likes
     */

    @Test
    public void verifyICanLikeTextMessage() throws InterruptedException {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).perform(click());

        verify(message).like();
    }

    @Test
    public void verifyICanLikeTextMessageByDoubleTap() throws InterruptedException {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(doubleClick());

        verify(message).like();
    }

    @Test
    public void verifyICanLikeLocationMessagebyDoubleTap() throws InterruptedException {
        MessageContent.Location mockLocation = mock(MessageContent.Location.class);
        when(mockLocation.getName()).thenReturn("Berlin");

        Message message = createMockMessage(Message.Type.LOCATION, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        when(message.getLocation()).thenReturn(mockLocation);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.fl__row_conversation__map_image_container)).check(isVisible());
        onView(withId(R.id.fl__row_conversation__map_image_container)).perform(doubleClick());

        verify(message).like();
    }

    @Test
    public void verifyICanLikeEmojiMessage() throws InterruptedException {
        Message message = createMockMessage(Message.Type.TEXT_EMOJI_ONLY, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).perform(click());

        verify(message).like();
    }

    @Test
    public void verifyICanLikeAudioMessage() throws InterruptedException {
        Asset mockAsset = mock(Asset.class);
        when(mockAsset.getStatus()).thenReturn(AssetStatus.DOWNLOAD_DONE);
        when(mockAsset.getDuration()).thenReturn(Duration.ZERO);

        Message message = createMockMessage(Message.Type.AUDIO_ASSET, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        when(message.getAsset()).thenReturn(mockAsset);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tfll__audio_message_container)).check(isVisible());
        onView(withId(R.id.tfll__audio_message_container)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).perform(click());

        verify(message).like();
    }

    @Test
    public void verifyICanLikeMessageThatIsLikedByOthers() {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(true);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).perform(click());

        verify(message).like();
    }

    @Test
    public void verifyICanUnlikeMessage() throws InterruptedException {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(true);
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);
        when(message.getConversation()).thenReturn(conversation);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).perform(click());

        verify(message).unlike();
    }

    @Test
    public void verifyICannotLikeSystemMessage() {
        MessageViewsContainer messageViewsContainer = ViewControllerMockHelper.getMockMessageViewsContainer(activity);

        Message memberLeaveMessage = createMockMessage(Message.Type.MEMBER_LEAVE, Message.Status.DELIVERED, false);
        FooterViewController memberLeaveFooterViewController = FooterViewControllerFactory.create(activity, memberLeaveMessage, messageViewsContainer);
        assertTrue(memberLeaveFooterViewController  == null);

        Message memberJoinMessage = createMockMessage(Message.Type.MEMBER_JOIN, Message.Status.DELIVERED, false);
        FooterViewController memberJoinFooterViewController = FooterViewControllerFactory.create(activity, memberJoinMessage, messageViewsContainer);
        assertTrue(memberJoinFooterViewController  == null);

        Message connectAcceptedMessage = createMockMessage(Message.Type.CONNECT_ACCEPTED, Message.Status.DELIVERED, false);
        FooterViewController connectAcceptedFooterViewController = FooterViewControllerFactory.create(activity, connectAcceptedMessage, messageViewsContainer);
        assertTrue(connectAcceptedFooterViewController == null);
    }

    @Test
    public void verifyICanSeeIfSomeoneElseLikedMessage() {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(true);
        User likedUser = createMockUser("Barry", "345");
        User[] likes = {likedUser};
        when(message.getLikes()).thenReturn(likes);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).check(hasText(activity.getString(R.string.glyph__like)));

        onView(withId(R.id.tv__footer__like__description)).check(isVisible());
        onView(withId(R.id.tv__footer__like__description)).check(hasText(likedUser.getDisplayName()));
    }

    @Test
    public void verifyICanSeeThatILikedMessage() {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLikedByThisUser()).thenReturn(true);
        when(message.isLiked()).thenReturn(true);
        User likedUser = createMockUser("Barry", "345");
        User[] likes = {likedUser};
        when(message.getLikes()).thenReturn(likes);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).check(hasText(activity.getString(R.string.glyph__liked)));

        onView(withId(R.id.tv__footer__like__description)).check(isVisible());
        onView(withId(R.id.tv__footer__like__description)).check(hasText(likedUser.getDisplayName()));
    }

    @Test
    public void verifyICanTapMessageToRevealLikeButtonIfMessageHasNoLikes() throws InterruptedException {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
    }

    @Test
    public void verifyLastReceivedMessageAlwaysShowsLikeButton() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
    }

    @Test
    public void verifyLastReceivedMessageAlwaysShowsLikeButtonInGroups() {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
    }

    @Test
    public void verifyICanSeeLikersAvatarsIfMoreThanThreeLikers() {
        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, false);
        when(message.isLikedByThisUser()).thenReturn(true);
        when(message.isLiked()).thenReturn(true);

        User lastLiker = createMockUser("Otto", "345");
        User secondLastLiker = createMockUser("Susan", "234");
        User[] likes = {createMockUser("Barry", "456"), createMockUser("Jack", "123"), secondLastLiker, lastLiker};
        when(message.getLikes()).thenReturn(likes);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.ll__like_chathead_container)).check(isVisible());
        onView(withText(activity.getString(R.string.glyph__more))).check(isVisible());
        ChatheadImageView lastLikerAvatar =  ViewUtils.getView(activity, R.id.cv__first_like_chathead);
        assertTrue(lastLikerAvatar.getUser().getId().equals(lastLiker.getId()));
        ChatheadImageView secondLastLikerAvatar =  ViewUtils.getView(activity, R.id.cv__second_like_chathead);
        assertTrue(secondLastLikerAvatar.getUser().getId().equals(secondLastLiker.getId()));
    }

    /**
     * Message status of received messages in 1:1
     */

    @Test
    public void verifyLastReceivedMessageShowsTimestampButNotMessageStatus() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(true);

        IUserPreferencesController userPreferencesController = activity.getControllerFactory().getUserPreferencesController();
        when(userPreferencesController.hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(doesNotContainText(getMessageStatusKeyword(R.string.message_footer__status__delivered)));
    }

    @Test
    public void verifyReceivedMessageShowsTimestampButNotMessageStatus() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(false);

        IUserPreferencesController userPreferencesController = activity.getControllerFactory().getUserPreferencesController();
        when(userPreferencesController.hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(doesNotContainText(getMessageStatusKeyword(R.string.message_footer__status__delivered)));
    }

    /**
     * Message status of received messages in groups
     */

    @Test
    public void verifyLastReceivedMessageShowsTimestampButNotMessageStatusInGroups() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(true);

        IUserPreferencesController userPreferencesController = activity.getControllerFactory().getUserPreferencesController();
        when(userPreferencesController.hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(doesNotContainText(getMessageStatusKeyword(R.string.message_footer__status__delivered)));
    }

    @Test
    public void verifyReceivedMessageShowsTimestampButNotMessageStatusinGroups() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, false);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromOther()).thenReturn(false);

        IUserPreferencesController userPreferencesController = activity.getControllerFactory().getUserPreferencesController();
        when(userPreferencesController.hasPerformedAction(IUserPreferencesController.LIKED_MESSAGE)).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(doesNotContainText(getMessageStatusKeyword(R.string.message_footer__status__delivered)));
    }

    /**
     * Sent messages in 1:1 conversations
     */

    @Test
    public void verifyMySendingMessageHasCorrectMessageStatus() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.PENDING, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        onView(withId(R.id.tv__footer__message_status)).check(hasText(activity.getString(R.string.message_footer__status__sending)));
    }

    @Test
    public void verifyMySentMessageHasCorrectMessageStatus() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(getMessageStatusKeyword(R.string.message_footer__status__sent)));
    }

    @Test
    public void verifyMyDeliveredMessageHasCorrectMessageStatus() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(getMessageStatusKeyword(R.string.message_footer__status__delivered)));
    }

    @Test
    public void verifyMyFailedMessageHasCorrectMessageStatus() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.FAILED, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String keyword = activity.getString(R.string.message_footer__status__failed).replace("_", "");
        onView(withId(R.id.tv__footer__message_status)).check(containsText(keyword));
    }

    @Test
    public void verifyMyLastMessageThatHasNoLikesShowsMessageStatus() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.fldl_like_details)).check(isGone());
    }

    @Test
    public void verifyICanTapOnMyLastMessageThatHasNoLikesToRevealLikeButton() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
    }

    @Test
    public void verifyMyLastMessageThatHasLikesShowsLikeDetails() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(true);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isGone());
        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.fldl_like_details)).check(isVisible());
    }

    @Test
    public void verifyMyMessageThatIsNotLastAndHasNoLikesHidesMessageStatusAndLikeButton() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isGone());
        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.fldl_like_details)).check(isGone());
    }

    @Test
    public void verifyICanTapOnMyMessageThatIsNotLastAndHasNoLikesToRevealDeliveryStatusAndLikeButton() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isGone());
        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.fldl_like_details)).check(isGone());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.fldl_like_details)).check(isGone());
    }

    @Test
    public void verifyMyMessageThatIsNotLastAndHasLikesShowsLikeDetails() {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(true);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isGone());
        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.fldl_like_details)).check(isVisible());
    }

    @Test
    public void verifyICannotLikeFailedMessage() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.FAILED, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
    }

    @Test
    public void verifyICannotLikeSendingMessage() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.ONE_TO_ONE);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.PENDING, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLikedByThisUser()).thenReturn(false);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
    }

    /**
     * Sent messages in group conversations
     */

    @Test
    public void verifyMyLastMessageThatHasNoLikesHidesMessageStatusInGroupConversation() {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(true);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isGone());
        onView(withId(R.id.gtv__footer__like__button)).check(isGone());
        onView(withId(R.id.fldl_like_details)).check(isGone());
    }

    @Test
    public void verifyICanTapOnMyMessageThatIsNotLastToRevealMessageStatusInGroup() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        onView(withId(R.id.gtv__footer__like__button)).check(isVisible());
        onView(withId(R.id.fldl_like_details)).check(isGone());
    }

    @Test
    public void verifyMySendingMessageHasCorrectMessageStatusInGroupConversation() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.PENDING, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        onView(withId(R.id.tv__footer__message_status)).check(hasText(activity.getString(R.string.message_footer__status__sending)));
    }

    @Test
    public void verifyMySentMessageHasCorrectMessageStatusInGroupConversation() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.SENT, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(getMessageStatusKeyword(R.string.message_footer__status__sent)));
    }

    @Test
    public void verifyMyDeliveredMessageHasCorrectMessageStatusInGroupConversation() throws InterruptedException {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.DELIVERED, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tmltv__row_conversation__message)).check(isVisible());
        onView(withId(R.id.tmltv__row_conversation__message)).perform(click());

        Thread.sleep(400);

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String timestamp = ZTimeFormatter.getSingleMessageTime(activity, DateTimeUtils.toDate(message.getTime()));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(timestamp));
        onView(withId(R.id.tv__footer__message_status)).check(containsText(getMessageStatusKeyword(R.string.message_footer__status__sent)));
    }

    @Test
    public void verifyMyFailedMessageHasCorrectMessageStatusInGroupConversation() {
        IConversation conversation = createMockConversation(IConversation.Type.GROUP);

        Message message = createMockMessage(Message.Type.TEXT, Message.Status.FAILED, true);
        when(message.getConversation()).thenReturn(conversation);
        when(message.isLiked()).thenReturn(false);
        when(message.isLastMessageFromSelf()).thenReturn(false);

        MessageAndSeparatorViewController messageAndSeparatorViewController = createMessageAndSeparatorViewController(message);
        messageAndSeparatorViewController.setModel(message, createMockSeparator());

        setView(messageAndSeparatorViewController.getView());

        onView(withId(R.id.tv__footer__message_status)).check(isVisible());
        String keyword = activity.getString(R.string.message_footer__status__failed).replace("_", "");
        onView(withId(R.id.tv__footer__message_status)).check(hasText(keyword));
    }

    private MessageAndSeparatorViewController createMessageAndSeparatorViewController(Message message) {
        MessageViewsContainer messageViewsContainer = ViewControllerMockHelper.getMockMessageViewsContainer(activity);
        MessageViewController viewController = MessageViewControllerFactory.create(activity,
                                                                                   message,
                                                                                   messageViewsContainer);

        FooterViewController footerViewController = FooterViewControllerFactory.create(activity, message, messageViewsContainer);

        return new MessageAndSeparatorViewController(viewController,
                                                     footerViewController,
                                                     messageViewsContainer,
                                                     activity);
    }

    /**
     * Extracts message status string from string of format "%1$s \u30FB Sent";
     * @param resId
     * @return
     */
    private String getMessageStatusKeyword(int resId) {
        String messageStatusStr = activity.getString(resId);
        int i = messageStatusStr.indexOf("\u30FB");
        if (i < 0) {
            i = 0;
        }
        return messageStatusStr.substring(i);
    }
}
