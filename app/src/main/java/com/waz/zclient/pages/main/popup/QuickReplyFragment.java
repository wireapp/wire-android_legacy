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
package com.waz.zclient.pages.main.popup;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.api.MessageContent;
import com.waz.api.MessagesList;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.notifications.OpenedAppFromQuickReplyEvent;
import com.waz.zclient.core.controllers.tracking.events.notifications.SwitchedMessageInQuickReplyEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.text.TypefaceEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

public class QuickReplyFragment extends BaseFragment<QuickReplyFragment.Container> implements AccentColorObserver,
                                                                                              TextView.OnEditorActionListener {

    public static final String TAG = QuickReplyFragment.class.getSimpleName();

    private static final String EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID";
    private static final String STATE_CONVERSATION = "STATE_CONVERSATION";

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            if (name == null || counter == null || contentContainer == null) {
                return;
            }
            name.setText(model.getName());
            messagesListModelObserver.setAndUpdate(model.getMessages());
        }
    };
    private final ModelObserver<MessagesList> messagesListModelObserver = new ModelObserver<MessagesList>() {
        @Override
        public void updated(MessagesList model) {
            if (adapter == null) {
                adapter = new ContentAdapter(getContext(), model);
                contentContainer.setAdapter(adapter);
            }
            adapter.setLastRead(model.getLastReadIndex());
            updateScrolledItemText();
        }
    };

    private TextView name;
    private TextView counter;
    private RecyclerView contentContainer;
    private ContentAdapter adapter;
    private TypefaceEditText message;
    private LinearLayout openWire;
    private IConversation conversation;
    private LinearLayoutManager layoutManager;

    public static Fragment newInstance(@NonNull String conversationId) {
        QuickReplyFragment fragment = new QuickReplyFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_CONVERSATION_ID, conversationId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            final String conversationId = getArguments().getString(EXTRA_CONVERSATION_ID);
            conversation = getStoreFactory().getConversationStore().getConversation(conversationId);
        } else {
            conversation = savedInstanceState.getParcelable(STATE_CONVERSATION);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_CONVERSATION, conversation);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.layout_quick_reply, container, false);
        name = ViewUtils.getView(view, R.id.ttv__quick_reply__name);
        counter = ViewUtils.getView(view, R.id.ttv__quick_reply__counter);
        counter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SwitchedMessageInQuickReplyEvent());
                contentContainer.smoothScrollToPosition((layoutManager.findFirstVisibleItemPosition() + 1) % adapter.getItemCount());
            }
        });
        contentContainer = ViewUtils.getView(view, R.id.rv__quick_reply__content_container);
        contentContainer.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SwitchedMessageInQuickReplyEvent());
                    updateScrolledItemText();
                }
            }
        });
        layoutManager = new ViewPagerLikeLayoutManager(getContext());
        contentContainer.setLayoutManager(layoutManager);
        message = ViewUtils.getView(view, R.id.tet__quick_reply__message);
        message.setOnEditorActionListener(this);
        openWire = ViewUtils.getView(view, R.id.ll__quick_reply__open_external);
        openWire.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null || conversation == null || message == null) {
                    return;
                }

                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedAppFromQuickReplyEvent());

                Intent appLaunchIntent = IntentUtils.getAppLaunchIntent(getContext(), conversation.getId(), message.getText().toString());
                startActivity(appLaunchIntent);
                getActivity().finish();
            }
        });
        return view;
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND ||
            (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
            final String sendText = textView.getText().toString();
            if (TextUtils.isEmpty(sendText)) {
                return false;
            }
            conversation.sendMessage(new MessageContent.Text(sendText));

            TrackingUtils.onSentTextMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                            getStoreFactory().getConversationStore().getCurrentConversation());
            getActivity().finish();
            return true;
        }
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        conversationModelObserver.setAndUpdate(conversation);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        message.postDelayed(new Runnable() {
            @Override
            public void run() {
                message.requestFocus();
                message.setCursorVisible(true);
                KeyboardUtils.showKeyboard(getActivity());
            }
        }, 100);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        conversationModelObserver.clear();
        messagesListModelObserver.clear();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        name = null;
        if (counter != null) {
            counter.setOnClickListener(null);
            counter = null;
        }
        if (contentContainer != null) {
            contentContainer.setAdapter(null);
            contentContainer = null;
        }
        message = null;
        if (openWire != null) {
            openWire.setOnClickListener(null);
            openWire = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        if (message == null) {
            return;
        }
        message.setAccentColor(color);
    }

    private void updateScrolledItemText() {
        if (counter == null ||
            adapter == null ||
            layoutManager == null) {
            return;
        }

        if (adapter.getItemCount() <= 1) {
            counter.setVisibility(View.GONE);
        } else {
            counter.setVisibility(View.VISIBLE);
            counter.setText(getString(R.string.quick_reply__counter, Math.max(1, layoutManager.findFirstVisibleItemPosition() + 1), adapter.getItemCount()));
        }
    }

    public interface Container {
    }
}
