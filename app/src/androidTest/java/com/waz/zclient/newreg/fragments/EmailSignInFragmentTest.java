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
package com.waz.zclient.newreg.fragments;

import android.support.test.espresso.PerformException;
import android.support.test.runner.AndroidJUnit4;
import com.waz.zclient.AppEntryTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.IAppEntryStore;
import com.waz.zclient.testutils.FragmentTest;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.waz.zclient.testutils.CustomMatchers.guidedEditTextWithId;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
public class EmailSignInFragmentTest extends FragmentTest<AppEntryTestActivity> {

    public EmailSignInFragmentTest() {
        super(AppEntryTestActivity.class);
    }

    @Test
    public void signInWithEmailAndPassword_ShouldCallAppEntrySignInWithEmail() {
        attachFragment(EmailSignInFragment.newInstance(), EmailSignInFragment.TAG);

        final String email = "android@wire.com";
        final String password = "aqa123456";

        onView(guidedEditTextWithId(R.id.get__sign_in__email)).perform(typeText(email));

        try {
            onView(guidedEditTextWithId(R.id.get__sign_in__password)).perform(typeText(password));
        } catch (PerformException e) {
            // ignore test on real devices, security exception
            return;
        }

        onView(withId(R.id.pcb__signin__email)).perform(click());

        verify(activity.getStoreFactory().appEntryStore()).signInWithEmail(eq(email), eq(password), any(IAppEntryStore.ErrorCallback.class));
    }
}

