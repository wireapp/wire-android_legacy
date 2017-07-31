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
import com.waz.api.ErrorType;
import com.waz.api.ErrorsList;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.inappnotification.IInAppNotificationStore;
import com.waz.zclient.core.stores.inappnotification.SyncErrorObserver;
import com.waz.zclient.testutils.FragmentTest;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AudioRecordingTest extends FragmentTest<MainTestActivity> {
    public AudioRecordingTest() {
        super(MainTestActivity.class);
    }
    /*
    @Test
    public void verifyLongPressingAudioMessageButtonShowsRecordingView() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_audio_message)).check(isVisible());
        onView(withId(R.id.cursor_menu_item_audio_message)).perform(ViewActions.longClick());

        onView(withId(R.id.amrv_audio_message_recording)).check(isVisible());
    }


    @Test
    public void verifyPlayButtonIsVisibleAfterLongPressiingAudioMessageButton() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_audio_message)).check(isVisible());
        onView(withId(R.id.cursor_menu_item_audio_message)).perform(ViewActions.longClick());

        String label = activity.getString(R.string.glyph__play);
        onView(withId(R.id.gtv__audido_message__recording__bottom_button)).check(hasText(label));
    }

    @Test
    public void verifyCancellingOfAudioMessageRecordingView() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_audio_message)).check(isVisible());
        onView(withId(R.id.cursor_menu_item_audio_message)).perform(ViewActions.longClick());

        onView(withId(R.id.fl__audio_message__recording__cancel_button_container)).check(isVisible());
        onView(withId(R.id.fl__audio_message__recording__cancel_button_container)).perform(ViewActions.click());

        onView(withId(R.id.amrv_audio_message_recording)).check(isInvisible());
    }
    */
    @Test
    @SuppressLint("NewApi")
    public void assertRecordingFailureWarningShowed() throws InterruptedException {
        final ErrorsList.ErrorDescription mockErrorDescription = mock(ErrorsList.ErrorDescription.class);
        when(mockErrorDescription.getType()).thenReturn(ErrorType.RECORDING_FAILURE);
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

        onView(withText(activity.getString(R.string.audio_message__recording__failure__title))).check(isVisible());
    }

}
