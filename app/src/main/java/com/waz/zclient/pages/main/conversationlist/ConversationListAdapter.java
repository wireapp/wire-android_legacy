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
package com.waz.zclient.pages.main.conversationlist;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import com.waz.api.ConversationsList;
import com.waz.api.CoreList;
import com.waz.api.IConversation;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.connect.InboxLinkConversation;
import com.waz.zclient.pages.main.conversation.ConversationUtils;
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback;
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView;
import com.waz.zclient.pages.main.conversationlist.views.row.ConversationListArchivedBorderRow;
import com.waz.zclient.pages.main.conversationlist.views.row.RightIndicatorView;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.conversationlist.ConversationListRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationListAdapter extends BaseAdapter {
    public static final String TAG = ConversationListAdapter.class.getName();
    private static final int HACK_ANIMATION_FACTOR = 2;

    private static final int CONNECT_REQUEST_INBOX_POSITION = 0;
    private static final int CONNECT_REQUEST_INBOX_POSITION_NONE = -1;

    private int layoutHeight;
    private ConversationListFragment.Mode mode;
    private float maxAlpha;
    private int listViewPaddingTop;
    private int listViewPaddingBottom;
    private int sharingListViewPaddingTop;

    public enum ArchivedState {
        GONE,
        INVISIBLE,
        VISIBLE
    }

    // View types
    private static final int ARCHIVE_BORDER_ROW = 0;
    private static final int CONVERSATION_ROW = 1;
    private static final int VIEW_TYPE_COUNT = 2;
    private ConversationsList conversationList;
    private long translationDelay;

    private RightIndicatorView.ConversationActionCallback conversationActionCallback;

    private ConversationCallback conversationCallback;

    private int accentColor;

    private ListView listView;

    private boolean isSorting = false;
    private int sortingAnimationDuration;
    private ArchivedState archivedState = ArchivedState.GONE;


    private ConversationsList.SearchableConversationsList incomingConversations;
    private ConversationsList.SearchableConversationsList archivedConversations;

    private int posOfInbox;
    private int posOfInboxOld;

    private int fakeElementHeight;

    /**
     * CTOR - injecting ZClientApp
     */
    public ConversationListAdapter(ConversationCallback conversationCallback, Context context) {
        this.conversationCallback = conversationCallback;
        sortingAnimationDuration = context.getResources().getInteger(R.integer.list_sorting_duration);
        translationDelay = context.getResources().getInteger(R.integer.framework_animation_delay_short);
        listViewPaddingTop = context.getResources().getDimensionPixelSize(R.dimen.list_padding_top);
        listViewPaddingBottom = context.getResources().getDimensionPixelSize(R.dimen.list_padding_bottom);
        sharingListViewPaddingTop = context.getResources().getDimensionPixelSize(R.dimen.list_padding_top) + context.getResources().getDimensionPixelSize(R.dimen.sharing_indicator__expanded_height);
    }

    public void setConversationListMode(ConversationListFragment.Mode mode) {
        this.mode = mode;
    }

    public void setListView(SwipeListView listView) {
        this.listView = listView;
    }

    public void setLayoutHeight(final int layoutHeight) {
        this.layoutHeight = layoutHeight;
        fakeElementHeight = 0;
        listView.post(new Runnable() {
            @Override
            public void run() {
                fakeElementHeight = layoutHeight - listView.getMeasuredHeight();
                notifyDataSetChanged();
            }
        });
    }

    public void onActiveCallStateHasChanged() {
        if (conversationList != null) {
            setConversationList(conversationList);
        }
    }

    public void setConversationList(ConversationsList conversationList) {
        this.conversationList = conversationList;
        onConversationListHasChanged();
        if (prepareSorting()) {
            animateSortingOfConversation();
        } else {
            notifyDataSetChanged();
        }
    }

    private void onConversationListHasChanged() {
        if (this.conversationList == null) {
            return;
        }
        incomingConversations = this.conversationList.getIncomingConversations();
        archivedConversations = this.conversationList.getArchivedConversations();

        posOfInboxOld = posOfInbox;
        posOfInbox = CONNECT_REQUEST_INBOX_POSITION_NONE;

        final boolean inbox = hasInboxConversation();

        // pending conversations available
        if (inbox) {
            // always put in into first position
            posOfInbox = CONNECT_REQUEST_INBOX_POSITION;
        }
    }

    private boolean hasInboxConversation() {
        return incomingConversations.size() > 0;
    }

    public ArchivedState getArchivedState() {
        return archivedState;
    }

    public void setArchivedState(ArchivedState archivedState, int duration) {
        if (this.archivedState == archivedState) {
            return;
        }

        this.archivedState = archivedState;

        switch (archivedState) {
            case GONE:
                notifyDataSetChanged();
                break;
            case INVISIBLE:
                notifyDataSetChanged();
                break;
            case VISIBLE:
                int count = listView.getChildCount();
                for (int i = 0; i < count; i++) {
                    View row = listView.getChildAt(i);
                    if (row.getVisibility() == View.INVISIBLE) {
                        row.setVisibility(View.VISIBLE);
                        row.setAlpha(0);
                        row.animate().alpha(1).setDuration(duration).setInterpolator(new Quart.EaseOut());
                    }
                }
                break;
        }
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
        notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetChanged() {
        if (isSorting) {
            return;
        }
        super.notifyDataSetChanged();
    }

    public void setConversationActionCallback(RightIndicatorView.ConversationActionCallback conversationActionCallback) {
        this.conversationActionCallback = conversationActionCallback;
    }

    @Override
    public int getCount() {
        if (this.conversationList == null ||
            (conversationList.size() == 0 && incomingConversations.size() == 0)) {
            return 0;
        }
        switch (archivedState) {
            case GONE:
                // always show faked element
                return getUnarchivedCount() + 1;
            default:
                return getUnarchivedCount() + getArchivedCount();
        }
    }

    public int getArchivedCount() {
        if (archivedConversations.size() == 0) {
            return 0;
        }

        // return extra of 1 for the archived line
        return archivedConversations.size() + 1;
    }

    public int getUnarchivedCount() {
        int size = conversationList.size();
        if (posOfInbox != CONNECT_REQUEST_INBOX_POSITION_NONE) {
            size++;
        }
        return size;
    }

    @Override
    public IConversation getItem(int position) {
        // inbox available
        final boolean showInbox = posOfInbox != CONNECT_REQUEST_INBOX_POSITION_NONE;
        final int shiftBy = 1;

        if (showInbox) {
            // we are above inbox - return position
            if (position < posOfInbox) {
                return conversationList.get(position);
            }

            // return inbox item
            if (position == posOfInbox) {
                return new InboxLinkConversation(incomingConversations);
            }

            // return item shifted one up
            if (position - shiftBy < conversationList.size()) {
                return conversationList.get(position - shiftBy);
            }
        } else {
            // no inbox
            // return regular item
            if (position < conversationList.size()) {
                return conversationList.get(position);
            }
        }

        int count = getUnarchivedCount();

        // archive border
        if (position == count) {
            return null;
        }

        // shifting archived elements one down
        if (position > count) {
            return conversationList.getArchivedConversations().get(position - count - 1);
        }

        throw new RuntimeException("We should never get here, sth. wrong with the counting");
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getUnarchivedCount()) {
            return ARCHIVE_BORDER_ROW;
        }

        return CONVERSATION_ROW;
    }

    public boolean isArchived(int position) {
        return position >= getUnarchivedCount();
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        if (archivedState == ArchivedState.GONE && position == getCount() - 1) {
            return createFakeView(parent.getContext());
        }

        listView = (ListView) parent;

        if (getItemViewType(position) == ARCHIVE_BORDER_ROW) {
            return getArchiveBorderRow(parent.getContext(), position);
        }

        ConversationListRow conversationListRowItem;

        if (convertView == null || !(convertView instanceof ConversationListRow)) {
            conversationListRowItem = new ConversationListRow(parent.getContext());
        } else {
            conversationListRowItem = (ConversationListRow) convertView;

            // needs redraw due to animation changes
            if (conversationListRowItem.needsRedraw()) {
                conversationListRowItem = new ConversationListRow(parent.getContext());
            }
        }

        conversationListRowItem.setAlpha(1f);
        conversationListRowItem.setMaxAlpha(maxAlpha);
        conversationListRowItem.setId(position);

        // integrate model
        final IConversation conversation = getItem(position);

        conversationListRowItem.setSwipeable(mode == ConversationListFragment.Mode.NORMAL && !(conversation instanceof InboxLinkConversation));

        if (isArchived(position)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                //noinspection deprecation
                conversationListRowItem.setBackgroundColor(parent.getResources().getColor(R.color.list_archive_box__background_color));
            } else {
                conversationListRowItem.setBackgroundColor(parent.getResources().getColor(R.color.list_archive_box__background_color,
                                                           conversationListRowItem.getContext().getTheme()));
            }
        } else {
            conversationListRowItem.setBackgroundColor(Color.TRANSPARENT);
        }

        conversationListRowItem.setConversation(conversation);
        conversationListRowItem.setConversationCallback(conversationCallback);
        conversationListRowItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((ListView) parent).performItemClick(parent, position, position);
            }
        });

        final ConversationListRow anchorView = conversationListRowItem;
        conversationListRowItem.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (conversationCallback == null) {
                    return false;
                }
                conversationCallback.onConversationListRowLongClicked(conversation, anchorView);
                return true;
            }
        });

        return conversationListRowItem;
    }

    public View getArchiveBorderRow(Context context, int pos) {
        ConversationListArchivedBorderRow view = new ConversationListArchivedBorderRow(context);
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                       context.getResources().getDimensionPixelSize(R.dimen.list__archived_border_height));
        view.setLayoutParams(params);
        view.setId(pos);
        return view;
    }

    /**
     * Set top padding for entire list on first item, so list is not clipped off when list is scrolled
     * @param rowView
     */
    private void setListRowPaddingTopAndBottom(View rowView, int position) {
        int paddingTop;
        int paddingBottom;

        if (position == 0) {
            // First row
            if (mode == ConversationListFragment.Mode.SHARING) {
                paddingTop = sharingListViewPaddingTop;
            } else {
                paddingTop = listViewPaddingTop;
            }
            paddingBottom = 0;
        } else if (isLastRow(position)) {
            // Last row
            paddingTop = 0;
            paddingBottom = listViewPaddingBottom;
        } else {
            paddingTop = 0;
            paddingBottom = 0;
        }

        ViewUtils.setPaddingTop(rowView, paddingTop);
        ViewUtils.setPaddingBottom(rowView, paddingBottom);
    }

    private boolean isLastRow(int position) {
        if (archivedState == ArchivedState.GONE) {
            return position == getCount() - 2;
        } else {
            return position == getCount() - 1;
        }
    }

    /**
     * Creates a fake view at the bottom of the list to ensure that the list view
     * takes the whole screen height, otherwise the view would be too short and
     * pull for action isn't called.
     *
     * @param context
     * @return
     */
    private View createFakeView(Context context) {
        View view = new View(context);
        view.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fakeElementHeight));
        view.setBackgroundColor(Color.argb(0, 0, 0, 0));
        return view;
    }

    public void tearDown() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view instanceof ConversationListRow) {
                ((ConversationListRow) view).tearDown();
            }
        }
    }

    private void animateSortingOfConversation() {
        CoreList<IConversation> archivedConversations = conversationList.getArchivedConversations();

        boolean isArchiveVisible = archivedState == ArchivedState.VISIBLE;
        Map<Integer, View> currentViews = new HashMap<>();

        List<Animator> animators = new ArrayList<>();

        // Cache all displayed views and store the last and first to hack views that leave the screen
        View first = null;
        View last = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view instanceof ConversationListRow || view instanceof ConversationListArchivedBorderRow) {
                if (first == null) {
                    first = view;
                }
                last = view;
                currentViews.put(view.getId(), view);
            }
        }

        final View firstView = first;
        final View lastView = last;

        // collect all needed animators
        // run once through the list of all displayed items
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i) instanceof ConversationListRow) {
                final ConversationListRow row = (ConversationListRow) listView.getChildAt(i);
                // needs to be called - this view cant be used as a convertView no more
                row.redraw();

                int currPos = row.getId();
                final IConversation conversation = row.getConversation();

                int newPos;

                // it is the inbox
                if (currPos == posOfInboxOld) {
                    newPos = currPos;
                } else {
                    newPos = conversationList.getConversationIndex(conversation.getId());
                    // push one up for the inbox
                    if (posOfInbox != CONNECT_REQUEST_INBOX_POSITION_NONE &&
                        newPos >= posOfInbox) {
                        newPos++;
                    }
                }

                // the item is archived
                if (newPos == -1 && isArchiveVisible) {
                    for (int j = 0; j < archivedConversations.size(); j++) {
                        if (ConversationUtils.isConversationEqual(archivedConversations.get(j), conversation)) {
                            newPos = getUnarchivedCount() + 1 + j;
                        }
                    }
                }

                if (currPos != newPos) {
                    if (currentViews.containsKey(newPos)) {
                        float t = currentViews.get(newPos).getY() - row.getY();
                        animators.add(ObjectAnimator.ofFloat(row, View.TRANSLATION_Y, t));
                    } else {
                        // its archive target
                        if (newPos == -1) {
                            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(row,
                                                                                  View.ALPHA,
                                                                                  0);
                            alphaAnimator.setInterpolator(new Quart.EaseOut());
                            alphaAnimator.setDuration(sortingAnimationDuration);
                            alphaAnimator.start();
                            continue;
                        }

                        // NORTH
                        // this view goes far north - we need to hack a little around it
                        if (currPos > newPos) {
                            float targetY = row.getY();
                            targetY -= first.getY();
                            animators.add(ObjectAnimator.ofFloat(row, View.TRANSLATION_Y, -targetY));
                            animators.add(ObjectAnimator.ofFloat(row, View.ALPHA, 1, 0, 1));
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    IConversation changeConversation = getItem(firstView.getId());
                                    if (changeConversation != null) {
                                        row.setConversation(changeConversation);
                                    }
                                }
                            }, sortingAnimationDuration / HACK_ANIMATION_FACTOR);
                        }

                        // SOUTH
                        // this view goes far south - we need to hack a little around it
                        if (currPos < newPos) {
                            float targetY = row.getY() - last.getY();

                            boolean isOnlyArchive = conversationList.getArchivedConversations().size() == 1 && row.isArchiveTarget();

                            if (isOnlyArchive) {
                                targetY -= row.getMeasuredHeight();
                            }

                            animators.add(ObjectAnimator.ofFloat(row, View.TRANSLATION_Y, -targetY));
                            animators.add(ObjectAnimator.ofFloat(row, View.ALPHA, 1, 1, 1, 0, 1));
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (conversation != null && lastView != null && row != null) {

                                        IConversation changeConversation = getItem(lastView.getId());
                                        if (changeConversation != null) {
                                            row.setConversation(changeConversation);
                                        }
                                    }
                                }
                            }, sortingAnimationDuration / HACK_ANIMATION_FACTOR);
                        }

                    }
                }
            } else if (listView.getChildAt(i) instanceof ConversationListArchivedBorderRow &&
                       isArchiveVisible) {
                View row = listView.getChildAt(i);
                int newPos = getUnarchivedCount();

                if (currentViews.containsKey(newPos)) {
                    float t = currentViews.get(newPos).getY() - row.getY();
                    animators.add(ObjectAnimator.ofFloat(row, View.TRANSLATION_Y, t));
                } else {
                    animators.add(ObjectAnimator.ofFloat(row,
                                                         View.TRANSLATION_Y,
                                                         ViewUtils.getOrientationIndependentDisplayHeight(listView.getContext())));
                }
            }
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(sortingAnimationDuration);
        animatorSet.playTogether(animators);
        animatorSet.setInterpolator(new Expo.EaseOut());
        animatorSet.setStartDelay(translationDelay);
        animatorSet.start();

        cleanUpSorting();
    }

    private boolean prepareSorting() {

        // ensures that at least one run was done before otherwise the list would be null
        if (listView == null) {
            return false;
        }

        // no scrolling allowed
        listView.setEnabled(false);
        isSorting = true;

        return true;
    }

    private void cleanUpSorting() {
        // clean up animation
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                isSorting = false;
                fakeElementHeight = 0;
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        fakeElementHeight = layoutHeight - listView.getMeasuredHeight();
                        notifyDataSetChanged();
                    }
                });
                notifyDataSetChanged();
                listView.setEnabled(true);
            }
        }, sortingAnimationDuration);
    }

    public void setMaxAlpha(float maxAlpha) {
        this.maxAlpha = maxAlpha;
        notifyDataSetChanged();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
