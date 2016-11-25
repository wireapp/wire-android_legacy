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
package com.waz.zclient.pages.main.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.IConversation;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.utils.ViewUtils;

public class ConnectRequestInboxManagerFragment extends BaseFragment<ConnectRequestInboxManagerFragment.Container> implements ConnectRequestInboxFragment.Container {
    public static final String TAG = ConnectRequestInboxManagerFragment.class.getName();
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";

    public static ConnectRequestInboxManagerFragment  newInstance(String conversationId) {
        ConnectRequestInboxManagerFragment  newFragment = new ConnectRequestInboxManagerFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_request_inbox_manager, container, false);
        if (savedInstanceState == null) {

            String conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
            getChildFragmentManager()
                    .beginTransaction()
                    .add(R.id.fl__connect_request_inbox, ConnectRequestInboxFragment.newInstance(conversationId), ConnectRequestInboxFragment.TAG)
                    .commit();
        }

        return view;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Fragment transitions
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    public void setVisibleConnectRequest(Bundle arguments) {
        ConnectRequestInboxFragment fragment = (ConnectRequestInboxFragment) getChildFragmentManager().findFragmentByTag(ConnectRequestInboxFragment.TAG);
        fragment.setVisibleConnectRequest(arguments);
    }

    private void restoreConnectRequestInboxFragment() {
        animateInboxContainerWithCommonUserProfile(true);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConnectRequestInboxFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissInboxFragment() {
        getContainer().dismissInboxFragment();
    }

    @Override
    public void onAcceptedUser(IConversation conversation) {
        getContainer().onAcceptedUser(conversation);
    }

    private void animateInboxContainerWithCommonUserProfile(boolean show) {
        View inboxContainer = ViewUtils.getView(getView(), R.id.fl__connect_request_inbox);
        if (show) {
            inboxContainer.animate()
                          .alpha(1)
                          .scaleY(1)
                          .scaleX(1)
                          .setInterpolator(new Expo.EaseOut())
                          .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                          .setStartDelay(getResources().getInteger(R.integer.reopen_profile_source__delay))
                          .start();
        } else {
            inboxContainer.animate()
                          .alpha(0)
                          .scaleY(2)
                          .scaleX(2)
                          .setInterpolator(new Expo.EaseIn())
                          .setDuration(getResources().getInteger(R.integer.reopen_profile_source__animation_duration))
                          .setStartDelay(0)
                          .start();
        }
    }

    public interface Container {

        void dismissInboxFragment();

        void onAcceptedUser(IConversation conversation);
    }
}
