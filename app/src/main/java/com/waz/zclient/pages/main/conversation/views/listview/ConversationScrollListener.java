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
package com.waz.zclient.pages.main.conversation.views.listview;

import android.widget.AbsListView;
import android.widget.ListAdapter;
import com.waz.api.Message;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ConversationScrollListener implements AbsListView.OnScrollListener {

    public static final int UNSET_ITEM_POSITION = -1;
    List<ScrolledToBottomListener> scrolledToBottomListeners = new LinkedList<>();
    List<VisibleMessagesChangesListener> visibleMessagesChangedListeners = new LinkedList<>();
    List<ScrollStateChangeListener> scrollStateChangeListeners = new LinkedList<>();

    private static final int MAX_OFFSET = 400;

    private int scrollState;

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
        notifyScrollStateChanged(scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
    }

    @Override
    public void onScroll(AbsListView listView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (scrollState == SCROLL_STATE_IDLE || listView.getChildCount() == 0 || totalItemCount == 0) {
            return;
        }

        notifyOffsetFromFirstElement(listView, totalItemCount);
        notifyScrollPosition(listView, totalItemCount);
        notifyVisibleMessagesChanged(listView, firstVisibleItem, firstVisibleItem + visibleItemCount - 1);
    }

    public void registerScrollStateChangeListener(ScrollStateChangeListener listener) {
        scrollStateChangeListeners.add(listener);
    }

    public void unregisterScrollStateChangeListener(ScrollStateChangeListener listener) {
        scrollStateChangeListeners.remove(listener);
    }

    public void registerScrolledToBottomListener(ScrolledToBottomListener listener) {
        scrolledToBottomListeners.add(listener);
    }

    public void unregisterScrolledToBottomListener(ScrolledToBottomListener listener) {
        scrolledToBottomListeners.remove(listener);
    }

    public void registerVisibleMessagesChangedListener(VisibleMessagesChangesListener listener) {
        visibleMessagesChangedListeners.add(listener);
    }

    public void unregisterVisibleMessagesChangedListener(VisibleMessagesChangesListener listener) {
        visibleMessagesChangedListeners.remove(listener);
    }

    private void notifyScrollStateChanged(boolean idle) {
        for (ScrollStateChangeListener listener : scrollStateChangeListeners) {
            listener.onScrollStateChanged(idle);
        }
    }

    private void notifyScrollPosition(AbsListView listView, int totalItemCount) {
        if (isLastItemVisible(listView, totalItemCount) && getBottomOfLastItemView(listView) <= listView.getHeight()) {
            triggerScrolledToBottomNotifications();
        } else {
            triggerScrolledAwayFromBottomNotifications();
        }
    }

    private void notifyOffsetFromFirstElement(AbsListView listView, int totalItemCount) {
        int offset = !isLastItemVisible(listView, totalItemCount) ? MAX_OFFSET :
                     getBottomOfLastItemView(listView) - listView.getHeight();

        for (ScrolledToBottomListener scrolledToBottomListener : scrolledToBottomListeners) {
            scrolledToBottomListener.onScrollOffsetFromFirstElement(offset);
        }
    }

    private boolean isLastItemVisible(AbsListView listView, int totalItemCount) {
        return listView.getLastVisiblePosition() == totalItemCount - 1;
    }

    private int getBottomOfLastItemView(AbsListView listView) {
        return listView.getChildAt(listView.getChildCount() - 1).getBottom();
    }

    private int lastFirstVisibleItem = UNSET_ITEM_POSITION;
    private int lastLastVisibleItem = UNSET_ITEM_POSITION;

    private void notifyVisibleMessagesChanged(AbsListView listView, int firstVisibleItem, int lastVisibleItem) {
        if (firstVisibleItem == -1 ||
            visibleMessagesChangedListeners.size() == 0 ||
            listView == null ||
            listView.getAdapter() == null) {
            return;
        }
        if (lastFirstVisibleItem != UNSET_ITEM_POSITION &&
            lastFirstVisibleItem == firstVisibleItem &&
            lastLastVisibleItem != UNSET_ITEM_POSITION &&
            lastLastVisibleItem == lastVisibleItem) {
            return;
        }

        lastFirstVisibleItem = firstVisibleItem;
        lastLastVisibleItem = lastVisibleItem;

        List<String> messageIds = new ArrayList<>();
        ListAdapter adapter = listView.getAdapter();
        Message message;
        for (int i = firstVisibleItem; i <= lastVisibleItem; i++) {
            message = (Message) adapter.getItem(i);
            if (message != null) {
                messageIds.add(message.getId());
            }
        }
        if (messageIds.size() == 0) {
            return;
        }
        for (VisibleMessagesChangesListener listener : visibleMessagesChangedListeners) {
            listener.onVisibleMessagesChanged(messageIds);
        }
    }

    private void triggerScrolledToBottomNotifications() {
        for (ScrolledToBottomListener listener : scrolledToBottomListeners) {
            listener.onScrolledToBottom();
        }
    }

    private void triggerScrolledAwayFromBottomNotifications() {
        for (ScrolledToBottomListener listener : scrolledToBottomListeners) {
            listener.onScrolledAwayFromBottom();
        }
    }

    public interface ScrollStateChangeListener {
        void onScrollStateChanged(boolean idle);
    }

    public interface ScrolledToBottomListener {
        void onScrolledToBottom();

        void onScrolledAwayFromBottom();

        void onScrollOffsetFromFirstElement(int offset);
    }

    public interface VisibleMessagesChangesListener {
        void onVisibleMessagesChanged(List<String> visibleMessageIds);
    }

}
