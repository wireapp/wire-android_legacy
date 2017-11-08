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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.waz.api.AccentColor;
import com.waz.api.IConversation;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.IAccentColorController;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.testutils.CustomViewAssertions;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.testutils.MockHelper;

import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.intent.Intents.intending;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasType;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ConversationFragmentTest extends FragmentTest<MainTestActivity> {

    public ConversationFragmentTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertToolbarVisibleInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);

        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.t_conversation_toolbar)).check(isVisible());
    }

    @Test
    public void assertToolbarVisibleInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);

        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.t_conversation_toolbar)).check(isVisible());
    }

    @Test
    public void assertToolbarOneToOneParticipants() {
        String conversationName = "ConversationName";
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getName()).thenReturn(conversationName);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        IConversationScreenController mockScreenController = activity.getControllerFactory().getConversationScreenController();

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withText(conversationName)).perform(click());
        verify(mockScreenController).showParticipants(any(View.class), anyBoolean());
    }

    @Test
    public void assertNoVideoCallButtonInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.action_video_call)).check(CustomViewAssertions.isNull());
    }

//    @Test
//    public void assertNoVideoCallButtonInNonActiveConversation() throws InterruptedException {
//        IConversation mockConversation = mock(IConversation.class);
//        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
//        when(mockConversation.isMemberOfConversation()).thenReturn(false);
//
//        MockHelper.setupConversationMocks(mockConversation, activity);
//        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);
//
//        onView(withId(R.id.action_video_call)).check(CustomViewAssertions.isNull());
//    }

    @Test
    public void assertNoAudioCallButtonInNonActiveConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(false);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.action_audio_call)).check(CustomViewAssertions.isNull());
    }

    @Test
    public void assertFileUploadIconVisibleInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_more)).perform(click());
        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_file)).check(isVisible());
    }
/*
    @Test
    public void assertFileUploadIconVisibleInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_more)).perform(click());
        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_file)).check(isVisible());
    }

    @Test
    public void assertAudioMessageIconVisibleInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_audio_message)).check(isVisible());
    }*/

    @Test
    public void assertAudioMessageIconVisibleInGroupConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_audio_message)).check(isVisible());
    }
/*
    @Test
    public void assertCursorImagesVisible() throws Exception {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_camera)).check(isVisible());
        onView(withId(R.id.cursor_menu_item_camera)).perform(click());

        Thread.sleep(500);
        onView(withId(R.id.rv__cursor_images)).check(isVisible());
    }
*/

//    @Test
//    public void assertCursorImagesBackButtonVisible() throws Exception {
//        IConversation mockConversation = mock(IConversation.class);
//        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
//        when(mockConversation.isMemberOfConversation()).thenReturn(true);
//        when(mockConversation.isActive()).thenReturn(true);
//
//        MockHelper.setupConversationMocks(mockConversation, activity);
//        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);
//
//        onView(withId(R.id.cursor_menu_item_camera)).check(isVisible());
//        onView(withId(R.id.cursor_menu_item_camera)).perform(click());
//        Thread.sleep(500);
//
//        onView(withId(R.id.rv__cursor_images)).check(isVisible());
//        onView(withId(R.id.gtv__cursor_image__nav_camera_back)).check(isGone());
//
//        onView(withId(R.id.rv__cursor_images)).perform(swipeLeft());
//        Thread.sleep(200);
//
//        onView(withId(R.id.gtv__cursor_image__nav_camera_back)).check(isVisible());
//    }

    @Test
    public void assertCursorImagesBackButtonShowsCamera() throws Exception {
        // wait for camera changes
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void assertCursorImagesGalleryButton() throws Exception {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.GROUP);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);
        IAccentColorController mockAccentColorController = activity.getControllerFactory().getAccentColorController();
        AccentColor mockAccentColor = mock(AccentColor.class);
        when(mockAccentColor.getColor()).thenReturn(Color.RED);
        when(mockAccentColorController.getAccentColor()).thenReturn(mockAccentColor);

        String action = Intent.ACTION_OPEN_DOCUMENT;
        Matcher<Intent> expectedIntent = allOf(hasAction(action), hasType("image/*"));
        Intent intent = new Intent();
        intent.setData(Uri.parse("file:///tmp/whatever.txt"));
        Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_OK, intent);
        intending(expectedIntent).respondWith(result);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        onView(withId(R.id.cursor_menu_item_camera)).check(isVisible());
        onView(withId(R.id.cursor_menu_item_camera)).perform(click());
        Thread.sleep(500);

        onView(withId(R.id.rv__cursor_images)).check(isVisible());
        onView(withId(R.id.gtv__cursor_image__nav_open_gallery)).check(isVisible());

//        onView(withId(R.id.gtv__cursor_image__nav_open_gallery)).perform(click());
    }
/*
    @Test
    public void assertGIFIconVisibleInOneToOneConversation() throws InterruptedException {
        IConversation mockConversation = mock(IConversation.class);
        when(mockConversation.getType()).thenReturn(IConversation.Type.ONE_TO_ONE);
        when(mockConversation.isMemberOfConversation()).thenReturn(true);
        when(mockConversation.isActive()).thenReturn(true);

        MockHelper.setupConversationMocks(mockConversation, activity);
        attachFragment(ConversationFragment.newInstance(), ConversationFragment.TAG);

        Thread.sleep(500);
        onView(withId(R.id.cursor_menu_item_gif)).check(isVisible());
    }
*/
}
