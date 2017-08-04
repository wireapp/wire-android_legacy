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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.test.runner.AndroidJUnit4;
import com.waz.api.AssetForUpload;
import com.waz.api.ErrorType;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.MessageContent;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.conversation.IConversationStore;
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore;
import com.waz.zclient.core.stores.inappnotification.SyncErrorObserver;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.testutils.MockHelper;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.intent.Intents.intending;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasType;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class FileUploadTest extends FragmentTest<MainTestActivity> {

    public FileUploadTest() {
        super(MainTestActivity.class);
    }

    @Test
    @SuppressLint("NewApi")
    public void assertFileUploadIntentInOneToOneConversation() throws InterruptedException {
        // Mock conversation
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);
        MockHelper.setupConversationMocks(mockConversation, activity);
        IConversationStore mockConversationStore = activity.getStoreFactory().conversationStore();

        // Mock intent result
        String action;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            action = Intent.ACTION_OPEN_DOCUMENT;
        } else {
            action = Intent.ACTION_GET_CONTENT;
        }
        Matcher<Intent> expectedIntent = allOf(hasAction(action), hasType("*/*"));
        Intent intent = new Intent();
        intent.setData(Uri.parse("file:///tmp/whatever.txt"));
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_OK, intent);
        intending(expectedIntent).respondWith(result);

        // attach fragment
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        // verify stuff
        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_more)).perform(click());
        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_file)).perform(click());
        Thread.sleep(200);

        verify(mockConversationStore).sendMessage(any(AssetForUpload.class), any(MessageContent.Asset.ErrorHandler.class));
    }

    @Test
    @SuppressLint("NewApi")
    public void assertFileTooBigWarningShowed() throws InterruptedException {
        final ErrorsList.ErrorDescription mockErrorDescription = mock(ErrorsList.ErrorDescription.class);
        when(mockErrorDescription.getType()).thenReturn(ErrorType.CANNOT_SEND_ASSET_TOO_LARGE);
        IInAppNotificationStore mockInAppNotificationStore = activity.getStoreFactory().inAppNotificationStore();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                SyncErrorObserver u = (SyncErrorObserver) args[0];
                u.onSyncError(mockErrorDescription);
                return null;
            }
        }).when(mockInAppNotificationStore).addInAppNotificationObserver(any(SyncErrorObserver.class));

        // attach fragment
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withText(activity.getString(R.string.asset_upload_error__file_too_large__title))).check(isVisible());
    }

    @Test
    @SuppressLint("NewApi")
    public void assertFileNotFoundWarningShowed() throws InterruptedException {
        final ErrorsList.ErrorDescription mockErrorDescription = mock(ErrorsList.ErrorDescription.class);
        when(mockErrorDescription.getType()).thenReturn(ErrorType.CANNOT_SEND_ASSET_FILE_NOT_FOUND);
        IInAppNotificationStore mockInAppNotificationStore = activity.getStoreFactory().inAppNotificationStore();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                SyncErrorObserver u = (SyncErrorObserver) args[0];
                u.onSyncError(mockErrorDescription);
                return null;
            }
        }).when(mockInAppNotificationStore).addInAppNotificationObserver(any(SyncErrorObserver.class));

        // attach fragment
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withText(activity.getString(R.string.asset_upload_error__not_found__title))).check(isVisible());
    }

}
