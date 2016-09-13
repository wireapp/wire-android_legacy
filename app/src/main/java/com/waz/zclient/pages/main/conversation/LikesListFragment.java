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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.utils.ViewUtils;

public class LikesListFragment extends BaseFragment<LikesListFragment.Container> {

    public static final String TAG = LikesListFragment.class.getName();
    public static final String ARGUMENT_LIKED_MESSAGE = "ARGUMENT_LIKED_MESSAGE";

    private Toolbar toolbar;
    private RecyclerView likersListView;
    private LikesAdapter likesAdapter;

    ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message model) {
            likesAdapter.setLikes(model.getLikes());
        }
    };

    public static LikesListFragment newInstance(Message message) {
        LikesListFragment fragment = new LikesListFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARGUMENT_LIKED_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup viewContainer,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_likes_list, viewContainer, false);
        toolbar = ViewUtils.getView(rootView, R.id.t__likes_list__toolbar);
        likersListView = ViewUtils.getView(rootView, R.id.rv__likes_list);

        likesAdapter = new LikesAdapter();
        likersListView.setLayoutManager(new LinearLayoutManager(getActivity()));
        likersListView.setAdapter(likesAdapter);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().closeLikesList();
            }
        });

        Message likedMessage = getArguments().getParcelable(ARGUMENT_LIKED_MESSAGE);
        messageModelObserver.setAndUpdate(likedMessage);
        return rootView;
    }

    @Override
    public void onDestroyView() {
        messageModelObserver.pauseListening();
        super.onDestroyView();
    }

    public interface Container {
        void closeLikesList();
    }
}
