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
package com.waz.zclient.pages.main.settings;

import android.support.design.widget.TextInputLayout;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewAssertion;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.profile.preferences.dialogs.ChangeUsernamePreferenceDialogFragment;
import com.waz.zclient.testutils.FragmentTest;

import junit.framework.AssertionFailedError;

import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.core.deps.guava.base.Verify.verify;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class UsernameEditFragmentTest extends FragmentTest<MainTestActivity> {
    public UsernameEditFragmentTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void assertInvalidCharactersDoNotAppear() throws InterruptedException {
        String currentUsername = "current_username";
        attachFragment(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, true), ChangeUsernamePreferenceDialogFragment.TAG);
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).perform(typeText("?"));
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).check(matches(withText(currentUsername)));
    }

    @Test
    public void assertLongUsernameShowsError() throws InterruptedException {
        String currentUsername = "current_username";
        attachFragment(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, true), ChangeUsernamePreferenceDialogFragment.TAG);
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).perform(typeText("12345678912345678912345"));
        Thread.sleep(400);
        CharSequence error = ((TextInputLayout) withId(R.id.til__change_username)).getError();
        verify(error != null && error.length() > 0);
    }

    @Test
    public void assertShortUsernameShowsError() throws InterruptedException {
        String currentUsername = "";
        attachFragment(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, true), ChangeUsernamePreferenceDialogFragment.TAG);
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).perform(typeText("1"));
        Thread.sleep(400);
        onView(withId(R.id.til__change_username)).check(new ViewAssertion() {
            @Override
            public void check(View view, NoMatchingViewException noViewFoundException) {
                CharSequence error = ((TextInputLayout) view).getError();
                if (error == null || error.length() == 0) {
                    throw new AssertionFailedError("Error field is empty");
                }
            }
        });
    }

    @Test
    public void assertInvalidUsernameCantBeSet() throws InterruptedException {
        String currentUsername = "";
        attachFragment(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, true), ChangeUsernamePreferenceDialogFragment.TAG);
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).perform(typeText("1"));
        Thread.sleep(400);
        onView(withId(R.id.til__change_username)).check(new ViewAssertion() {
            @Override
            public void check(View view, NoMatchingViewException noViewFoundException) {
                if (!view.isEnabled()) {
                    throw new AssertionFailedError("View is enabled");
                }
            }
        });
    }
}
