package com.waz.zclient.pages.main.settings;

import android.support.design.widget.TextInputLayout;
import android.support.test.runner.AndroidJUnit4;

import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.profile.preferences.dialogs.ChangeUsernamePreferenceDialogFragment;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.ui.text.TypefaceTextView;

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
        CharSequence error = ((TextInputLayout) withId(R.id.til__change_username)).getError();
        verify(error != null && error.length() > 0);
    }

    @Test
    public void assertInvalidUsernameCantBeSet() throws InterruptedException {
        String currentUsername = "";
        attachFragment(ChangeUsernamePreferenceDialogFragment.newInstance(currentUsername, true), ChangeUsernamePreferenceDialogFragment.TAG);
        Thread.sleep(400);
        onView(withId(R.id.acet__change_username)).perform(typeText("1"));
        Thread.sleep(400);
        verify(!((TypefaceTextView) withId(R.id.tv__ok_button)).isEnabled());
    }
}
