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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.UpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;

public class ParticipantBackbarFragment extends BaseFragment<ParticipantBackbarFragment.Container> implements View.OnClickListener,
                                                                                                              UpdateListener {
    public static final String TAG = ParticipantBackbarFragment.class.getSimpleName();


    public static ParticipantBackbarFragment newInstance() {
        return new ParticipantBackbarFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_participants_backbar, container, false);
        view.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        if (getControllerFactory().getConversationScreenController().isShowingUser()) {
            getControllerFactory().getConversationScreenController().hideUser();
            return;
        }
    }

    @Override
    public void updated() {

    }

    public interface Container {

    }
}
