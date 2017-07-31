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
package com.waz.zclient.pages.main.participants;

import android.support.test.runner.AndroidJUnit4;
import com.waz.api.Fingerprint;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.api.UiSignal;
import com.waz.api.User;
import com.waz.api.Verification;
import com.waz.api.ZMessagingApi;
import com.waz.zclient.MainTestActivity;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.api.IZMessagingApiStore;
import com.waz.zclient.core.stores.profile.IProfileStore;
import com.waz.zclient.testutils.FragmentTest;
import com.waz.zclient.testutils.MockHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static com.waz.zclient.testutils.CustomMatchers.otrSwitchWithId;
import static com.waz.zclient.testutils.CustomViewAssertions.hasText;
import static com.waz.zclient.testutils.CustomViewAssertions.isGone;
import static com.waz.zclient.testutils.CustomViewAssertions.isVisible;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class SingleOtrClientFragmentTest extends FragmentTest<MainTestActivity> {

    private final static String DEFAULT_FINGERPRINT = "aabbccdd11223344";
    private final static String DEFAULT_DISPLAY_NAME = "Test User";

    public SingleOtrClientFragmentTest() {
        super(MainTestActivity.class);
    }

    @Test
    public void launchFragmentWithoutParameters_shouldDisplayCurrentOtrClient() {
        Self mockSelf = mock(Self.class);
        Fingerprint mockFingerprint = mock(Fingerprint.class);
        setupMocksForCurrentOtrClient(mockSelf, mockFingerprint);

        attachFragment(SingleOtrClientFragment.newInstance(), SingleOtrClientFragment.TAG);

        onView(withId(R.id.os__single_otr_client__verify)).check(isGone());
        onView(withId(R.id.gtv__single_otr_client__back)).check(isGone());
        onView(withId(R.id.ttv__single_otr_client__my_fingerprint)).check(isGone());
        onView(withId(R.id.gtv__single_otr_client__close)).check(isVisible());
        onView(withId(R.id.ttv__single_otr_client__my_devices)).check(isVisible());
    }

    @Test
    public void launchFragmentWithoutParameters_shouldShowFormatterFingerprint() {
        Self mockSelf = mock(Self.class);
        Fingerprint mockFingerprint = mock(Fingerprint.class);
        setupMocksForCurrentOtrClient(mockSelf, mockFingerprint);

        attachFragment(SingleOtrClientFragment.newInstance(), SingleOtrClientFragment.TAG);

        onView(withId(R.id.ttv__single_otr_client__fingerprint)).check(hasText("aa bb cc dd 11 22 33 44"));
    }

    @Test
    public void launchFragmentWithParameters_shouldDisplayOtherOtrClient() {
        User mockUser = mock(User.class);
        OtrClient mockOtrClient = mock(OtrClient.class);
        setupMocksForOtherOtrClient(mockUser, mockOtrClient);

        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);

        onView(withId(R.id.os__single_otr_client__verify)).check(isVisible());
        onView(withId(R.id.gtv__single_otr_client__back)).check(isVisible());
        onView(withId(R.id.ttv__single_otr_client__my_fingerprint)).check(isVisible());
        onView(withId(R.id.gtv__single_otr_client__close)).check(isGone());
        onView(withId(R.id.ttv__single_otr_client__my_devices)).check(isGone());
    }

    @Test
    public void launchFragmentWithoutParameters_shouldDisplayDefaultText() {
        Self mockSelf = mock(Self.class);
        Fingerprint mockFingerprint = mock(Fingerprint.class);
        setupMocksForCurrentOtrClient(mockSelf, mockFingerprint);

        attachFragment(SingleOtrClientFragment.newInstance(), SingleOtrClientFragment.TAG);

        String text = activity.getString(R.string.otr__participant__my_device__description);
        onView(withId(R.id.ttv__single_otr_client__description)).check(hasText(text));
    }

//    @Test
//    public void launchFragmentWithParameters_shouldDisplayDisplayNameInText() {
//        User mockUser = mock(User.class);
//        OtrClient mockOtrClient = mock(OtrClient.class);
//        setupMocksForOtherOtrClient(mockUser, mockOtrClient);
//
//        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);
//
//        String text = activity.getString(R.string.otr__participant__single_device__description, DEFAULT_DISPLAY_NAME);
//        onView(withId(R.id.ttv__single_otr_client__description)).check(hasText(text));
//    }

    @Test
    public void launchFragmentWithParametersAndClickVerify_shouldUpdateOtrClient() {
        User mockUser = mock(User.class);
        OtrClient mockOtrClient = mock(OtrClient.class);
        setupMocksForOtherOtrClient(mockUser, mockOtrClient);
        when(mockOtrClient.getVerified()).thenReturn(Verification.UNVERIFIED);

        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);

        onView(otrSwitchWithId(R.id.os__single_otr_client__verify)).perform(click());

        verify(mockOtrClient).setVerified(true);
    }

    //@Test
    // Pending AN-XXX
    public void launchFragmentWithParametersAndClickResetSession_shouldCallOtrClientResetSession() {
        User mockUser = mock(User.class);
        OtrClient mockOtrClient = mock(OtrClient.class);
        setupMocksForOtherOtrClient(mockUser, mockOtrClient);
        when(mockOtrClient.getVerified()).thenReturn(Verification.UNVERIFIED);

        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);

        onView(withId(R.id.ttv__single_otr_client__reset)).perform(click());

        verify(mockOtrClient).resetSession(any(OtrClient.ResetCallback.class));
    }

    @Test
    public void launchFragmentWithParametersAndClickShowMyFingerprint_shouldCallController() {
        User mockUser = mock(User.class);
        OtrClient mockOtrClient = mock(OtrClient.class);
        setupMocksForOtherOtrClient(mockUser, mockOtrClient);
        when(mockOtrClient.getVerified()).thenReturn(Verification.UNVERIFIED);

        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);

        onView(withId(R.id.ttv__single_otr_client__my_fingerprint)).perform(click());

        verify(activity.getControllerFactory().getConversationScreenController()).showCurrentOtrClient();
    }

    @Test
    public void launchFragmentWithParametersAndClickBack_shouldCallController() {
        User mockUser = mock(User.class);
        OtrClient mockOtrClient = mock(OtrClient.class);
        setupMocksForOtherOtrClient(mockUser, mockOtrClient);
        when(mockOtrClient.getVerified()).thenReturn(Verification.UNVERIFIED);

        attachFragment(SingleOtrClientFragment.newInstance(mockOtrClient, mockUser), SingleOtrClientFragment.TAG);

        onView(withId(R.id.gtv__single_otr_client__back)).perform(click());

        verify(activity.getControllerFactory().getConversationScreenController()).hideOtrClient();
    }

    private static void setupMocksForOtherOtrClient(User mockUser, OtrClient mockOtrClient) {
        when(mockUser.getDisplayName()).thenReturn(DEFAULT_DISPLAY_NAME);
        Fingerprint mockFingerprint = mock(Fingerprint.class);
        UiSignal<Fingerprint> fingerprintUiSignal = MockHelper.mockUiSignal();
        when(mockOtrClient.getFingerprint()).thenReturn(fingerprintUiSignal);
        MockHelper.mockSubscription(fingerprintUiSignal, mockFingerprint);
        when(mockFingerprint.getRawBytes()).thenReturn(DEFAULT_FINGERPRINT.getBytes());
    }

    private void setupMocksForCurrentOtrClient(Self mockSelf, Fingerprint mockFingerprint) {
        IZMessagingApiStore mockZMessagingApiStore = activity.getStoreFactory().zMessagingApiStore();
        ZMessagingApi mockZMessagingApi = mock(ZMessagingApi.class);
        when(mockZMessagingApiStore.getApi()).thenReturn(mockZMessagingApi);
        when(mockZMessagingApi.getSelf()).thenReturn(mockSelf);
        UiSignal<OtrClient> otrClientUiSignal = MockHelper.mockUiSignal();
        when(mockSelf.getOtrClient()).thenReturn(otrClientUiSignal);
        final OtrClient mockOtrClient = mock(OtrClient.class);
        MockHelper.mockSubscription(otrClientUiSignal, mockOtrClient);
        UiSignal<Fingerprint> fingerprintUiSignal = MockHelper.mockUiSignal();
        when(mockOtrClient.getFingerprint()).thenReturn(fingerprintUiSignal);
        MockHelper.mockSubscription(fingerprintUiSignal, mockFingerprint);
        when(mockFingerprint.getRawBytes()).thenReturn(DEFAULT_FINGERPRINT.getBytes());

        // Not really needed, as user is not used for current device
        IProfileStore mockProfileStore = activity.getStoreFactory().profileStore();
        User mockUser = mock(User.class);
        when(mockProfileStore.getSelfUser()).thenReturn(mockUser);
    }

}
