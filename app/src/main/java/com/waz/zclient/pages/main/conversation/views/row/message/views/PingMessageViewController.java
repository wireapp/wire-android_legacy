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
package com.waz.zclient.pages.main.conversation.views.row.message.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode;
import com.waz.zclient.ui.animation.LeftPaddingReverseAnimation;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.chathead.ChatheadImageView;
import org.threeten.bp.DateTimeUtils;

import java.util.Locale;


public class PingMessageViewController extends MessageViewController implements UpdateListener,
                                                                                View.OnClickListener {
    public static final String TAG = PingMessageViewController.class.getName();
    private static final long APPROXIMATE_MESSAGE_EVALUATION_DURATION = 1000L;

    private TypefaceTextView textViewMessage;
    private GlyphTextView glyphTextView;
    private ChatheadImageView userChatheadImageView;
    private Locale locale;

    private View view;
    private User user;

    private LeftPaddingReverseAnimation knockingAnimation;
    int originalLeftPadding;

    @SuppressLint("InflateParams")
    public PingMessageViewController(Context context, MessageViewsContainer messageViewsContainer) {
        super(context, messageViewsContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_knock, null);

        textViewMessage = ViewUtils.getView(view, R.id.ttv__row_conversation__ping_message);
        textViewMessage.setOnLongClickListener(this);
        glyphTextView = ViewUtils.getView(view, R.id.gtv__knock_icon);
        glyphTextView.setOnLongClickListener(this);
        userChatheadImageView = ViewUtils.getView(view, R.id.civ__row_conversation__ping_chathead);

        locale = context.getResources().getConfiguration().locale;

        originalLeftPadding = context.getResources().getDimensionPixelSize(R.dimen.content__padding_left);
    }

    @Override
    protected void onSetMessage(Separator separator) {
        user = message.getUser();
        user.addUpdateListener(this);
        message.addUpdateListener(this);
        userChatheadImageView.setUser(user);
        userChatheadImageView.setOnClickListener(this);

        updated();
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void updated() {
        textViewMessage.setText(getPingMessage());
        final int textColor = user.getAccent().getColor();
        glyphTextView.setTextColor(textColor);
        textViewMessage.setTextColor(textColor);
        TextViewUtils.boldText(textViewMessage);

        if (DateTimeUtils.toDate(message.getLocalTime()).getTime() + APPROXIMATE_MESSAGE_EVALUATION_DURATION > System.currentTimeMillis()) {
            startKnockAnimation();
        }
    }

    private void startKnockAnimation() {
        if (message == null || messageViewsContainer == null) {
            return;
        }

        // ping if not already shown
        boolean successfulPing = messageViewsContainer.ping(message.isHotKnock(),
                                                            message.getId(),
                                                            textViewMessage.getText().toString(),
                                                            message.getUser().getAccent().getColor());
        if (!successfulPing) {
            return;
        }

        showPingMessage(false);
        // save left padding
        final int leftPadding = view.getPaddingLeft();
        knockingAnimation = new LeftPaddingReverseAnimation(leftPadding,
                                                            context.getResources().getDimensionPixelSize(R.dimen.list__ping_label_distance),
                                                            view,
                                                            context.getResources().getInteger(R.integer.framework_animation_duration_ages));
        knockingAnimation.setInterpolator(new Expo.EaseOut());
        knockingAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                stopKnockAnimation();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        knockingAnimation.setFillAfter(false);
        view.setAnimation(knockingAnimation);
        knockingAnimation.start();
    }

    private String getPingMessage() {
        final String pingMessage;
        if (message.isHotKnock()) {
            if (user.isMe()) {
                pingMessage = context.getString(R.string.content__you_pinged_again);
            } else {
                pingMessage = context.getString(R.string.content__xxx_pinged_again, user.getDisplayName().toUpperCase(locale));
            }
        } else if (user.isMe()) {
            pingMessage = context.getString(R.string.content__you_pinged);
        } else {
            pingMessage = context.getString(R.string.content__xxx_pinged, user.getDisplayName().toUpperCase(locale));
        }
        return pingMessage;
    }

    private void stopKnockAnimation() {
        showPingMessage(true);
    }

    private void showPingMessage(boolean show) {
        if (glyphTextView == null) {
            return;
        }

        if (show) {
            ViewUtils.setPaddingLeft(view, originalLeftPadding);
            glyphTextView.animate().alpha(1);
        } else {
            glyphTextView.animate().alpha(0).setDuration(0);
        }
    }

    @Override
    public void recycle() {
        if (user != null) {
            user.removeUpdateListener(this);
        }
        if (message != null) {
            message.removeUpdateListener(this);
        }
        userChatheadImageView.setOnClickListener(null);
        user = null;
        super.recycle();
    }

    @Override
    public void onClick(View v) {
        if (messageViewsContainer.isTornDown()) {
            return;
        }
        messageViewsContainer.getControllerFactory()
                             .getConversationScreenController()
                             .setPopoverLaunchedMode(DialogLaunchMode.AVATAR);
        if (!messageViewsContainer.isPhone()) {
            messageViewsContainer.getControllerFactory()
                                 .getPickUserController()
                                 .showUserProfile(user, userChatheadImageView);
        } else {
            messageViewsContainer.getControllerFactory()
                                 .getConversationScreenController()
                                 .showUser(user);
        }
    }

}
