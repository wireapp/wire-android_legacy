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
package com.waz.zclient.pages.main.conversationlist.views.row;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.api.UpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.connect.InboxLinkConversation;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback;
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.text.TypefaceFactory;
import com.waz.zclient.ui.views.properties.MoveToAnimateable;
import com.waz.zclient.utils.ViewUtils;

public class ConversationListRow extends FrameLayout implements SwipeListView.SwipeListRow,
                                                                UpdateListener,
                                                                MoveToAnimateable {
    private ConversationCallback conversationCallback;

    // current menuOpenSpring animation
    private boolean isArchiveTarget;
    private int normalTextColor;
    private float maxAlpha;
    private boolean open;
    private int menuOpenOffset;

    public IConversation getConversation() {
        return conversation;
    }

    boolean needsRedraw;

    public boolean needsRedraw() {
        return needsRedraw;
    }

    public void redraw() {
        disconnectCurrentConversation();
        needsRedraw = true;
    }

    public void setConversationCallback(ConversationCallback conversationCallback) {
        this.conversationCallback = conversationCallback;
    }

    // the view hidden by the front view. Visible after swiping
    private MenuIndicatorView menuIndicatorView;
    // the unread. voice channel or knocking indicator
    private LeftIndicatorView leftIndicatorView;
    // the muteButton indicator on the right
    private RightIndicatorView rightIndicatorView;
    public TextView textView;
    private int accentColor;

    // the open state of this view in px:  0 - closed, maxOffset - opened
    private float moveTo;
    private float maxOffset;
    // Swipe to reveal menu is disable for some conversations
    private boolean isSwipeable;
    // bind the view to the model
    private IConversation conversation;

    private ObjectAnimator moveToAnimator;

    @Override
    public void swipeAway() {
        close();
        conversationCallback.onConversationListRowSwiped(conversation, this);
    }

    public boolean isArchiveTarget() {
        return isArchiveTarget;
    }

    /**
     * Can be used by the object animator.
     */
    @Override
    public float getMoveTo() {
        return moveTo;
    }

    /**
     * the max offset is set by the SwipeListView. It needs to calculate its own width first.
     */
    @Override
    public void setMaxOffset(float maxOffset) {
        this.maxOffset = maxOffset;
    }

    /**
     * The color can be changed from outside. Each view needs to be notified.
     */
    public void setAccentColor(int myColor) {
        accentColor = myColor;
        leftIndicatorView.setAccentColor(myColor);
    }

    /**
     * View is created in getView, we don't necesseraly need to inflate.
     * The extra padding is passed because it doesn't affect all items in a row.
     */
    public ConversationListRow(Context context) {
        super(context);
        this.isSwipeable = true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            normalTextColor = getResources().getColor(R.color.list_font_color);
        } else {
            normalTextColor = getResources().getColor(R.color.list_font_color, getContext().getTheme());
        }
        menuOpenOffset = getResources().getDimensionPixelSize(R.dimen.list__menu_indicator__max_swipe_offset);

        menuIndicatorView = new MenuIndicatorView(getContext());
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.conversation_list__left_icon_width),
                                                                         ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.gravity = Gravity.LEFT;
        menuIndicatorView.setLayoutParams(layoutParams);
        menuIndicatorView.setMaxOffset(menuOpenOffset);
        menuIndicatorView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                close();
                conversationCallback.onConversationListRowSwiped(conversation, ConversationListRow.this);
            }
        });

        leftIndicatorView = new LeftIndicatorView(getContext(), Color.RED);

        textView = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.conv_list_item_front, this, false);
        textView.setTypeface(TypefaceFactory.getInstance().getTypeface(getResources().getString(R.string.wire__typeface__light)));

        rightIndicatorView = new RightIndicatorView(getContext());


        addView(menuIndicatorView);
        addView(leftIndicatorView);
        addView(textView);
        addView(rightIndicatorView);
    }

    /**
     * Overrides the click listener we don't need it for the
     * overall view. Only for the text view.
     */
    @Override
    public void setOnClickListener(final OnClickListener l) {
        textView.setOnClickListener(l);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        textView.setOnLongClickListener(l);
    }

    /* update data */

    /**
     * Binds the conversation model to this views. Any views in
     * the hierarchy are notified as well.
     */
    public void setConversation(IConversation conversation) {
        isArchiveTarget = false;

        // close open menu immediate if different conversation
        if (this.conversation != null && !this.conversation.getId().equals(conversation.getId())) {
            if (moveToAnimator != null) {
                moveToAnimator.cancel();
            }

            closeImmediate();
            if (this.conversation instanceof InboxLinkConversation) {
                this.conversation.clear();
            }
        }

        disconnectCurrentConversation();
        connectConversation(conversation);

        if (conversation instanceof InboxLinkConversation) {
            isSwipeable = false;
        }

        setTextViewState();
    }

    private void connectConversation(IConversation conversation) {
        if (conversation != null) {
            this.conversation = conversation;
            this.conversation.addUpdateListener(this);

            updated();
        }
    }

    private void disconnectCurrentConversation() {
        if (this.conversation != null) {
            this.conversation.removeUpdateListener(this);
        }
    }

    public boolean isSwipeable() {
        return isSwipeable;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void setSwipeable(boolean isSwipeable) {
        this.isSwipeable = isSwipeable;
    }

    /**
     * Sets the text view state depending on conversation state.
     */
    private void setTextViewState() {
        // text color and typeface
        if (conversation != null &&
            conversation.isSelected()) {
            textView.setTextColor(accentColor);
        } else {
            textView.setTextColor(normalTextColor);
        }

        if (conversation instanceof InboxLinkConversation) {
            textView.setText(getInboxName((InboxLinkConversation) conversation));
        } else {
            textView.setText(conversation.getName());
        }
    }

    private String getInboxName(InboxLinkConversation conversation) {
        int size = conversation.getSize();
        return getResources().getQuantityString(R.plurals.connect_inbox__link__name, size);
    }

    /**
     * Somebody knocks on this conversation.
     * - animateMenu text view
     * - animateMenu left indicator
     *
     * @param knockingEvent
     */
    public void knock(final KnockingEvent knockingEvent) {
        // drawer is open
        if (moveTo > 0) {
            return;
        }
        leftIndicatorView.knock(knockingEvent);
    }

    public void open() {
        if (open) {
            return;
        }
        animateMenu(menuOpenOffset);
        open = true;
    }

    /**
     * Dims views when other list row's menu is swiped
     *
     * @param alpha
     */
    public void dimOnListRowMenuSwiped(float alpha) {
        alpha = Math.max(alpha, maxAlpha);
        menuIndicatorView.setAlpha(alpha);
        leftIndicatorView.setAlpha(alpha);
        rightIndicatorView.setAlpha(alpha);
        textView.setAlpha(alpha);
    }

    public void close() {
        if (open) {
            open = false;
        }
        animateMenu(0);
    }

    private void closeImmediate() {
        if (open) {
            open = false;
        }
        setMoveTo(0);
    }

    /**
     * Animates the open/closing behaviour of this item.
     */
    private void animateMenu(int moveTo) {
        final float moveFrom = getMoveTo();
        moveToAnimator = ObjectAnimator.ofFloat(this, MOVE_TO, moveFrom, moveTo);
        moveToAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
        moveToAnimator.setInterpolator(new Expo.EaseOut());
        moveToAnimator.start();
    }

    /**
     * Used during scrolling to open/close the drawer until the finger is on it.
     */
    public void setOffset(float offset) {
        int openOffset = open ? menuOpenOffset : 0;
        float moveTo = openOffset + offset;

        if (moveTo < 0) {
            moveTo = 0;
        }

        if (moveTo > maxOffset) {
            float overshoot = moveTo - maxOffset;
            moveTo = maxOffset + overshoot / 2;
        }

        setMoveTo(moveTo);
    }

    /**
     * Used by animation thread to move drawer
     */
    public void setMoveTo(float moveTo) {
        this.moveTo = moveTo;
        int rightPadding = (int) moveTo + rightIndicatorView.getTotalWidth();
        textView.setPadding(textView.getPaddingLeft(),
                            textView.getPaddingTop(),
                            rightPadding,
                            textView.getPaddingBottom());
        textView.setTranslationX(moveTo);
        menuIndicatorView.setClipX((int) moveTo);
        leftIndicatorView.setTranslationX((int) moveTo);
    }

    /**
     * Called from conversation. The view is set again.
     */
    @Override
    public void updated() {
        leftIndicatorView.setConversation(conversation);
        rightIndicatorView.setConversation(conversation);

        // get behind the layout cycle of the right indicator view
        rightIndicatorView.post(new Runnable() {
            @Override
            public void run() {
                int rightPadding = (int) (rightIndicatorView.getTotalWidth() + moveTo);
                if (textView.getPaddingRight() != rightPadding) {
                    ViewUtils.setPaddingRight(textView, rightPadding);
                    textView.invalidate();
                }
            }
        });

        setTextViewState();
    }


    public void setPagerOffset(float pagerOffset) {
        if (conversation == null ||
            conversation.isSelected()) {
            return;
        }

        float alpha = (float) Math.pow(1 - pagerOffset, 4);
        alpha = Math.max(alpha, maxAlpha);
        setAlpha(alpha);
    }

    public void setConversationActionCallback(RightIndicatorView.ConversationActionCallback callback) {
        rightIndicatorView.setCallback(callback);
    }

    public void tearDown() {
        disconnectCurrentConversation();
    }

    public void showIndicatorView(boolean show) {
        if (show) {
            leftIndicatorView.setVisibility(VISIBLE);
        } else {
            leftIndicatorView.setVisibility(GONE);
        }
    }

    public void setMaxAlpha(float maxAlpha) {
        this.maxAlpha = maxAlpha;
    }
}
