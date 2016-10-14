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
package com.waz.zclient.views.chathead;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ViewAnimator;
import com.waz.api.Message;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.common.views.ChatheadView;
import com.waz.zclient.ui.animation.WidthEvaluator;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.utils.ViewUtils;

public class MessageNotificationChatheadView extends FrameLayout implements UpdateListener {
    private static final double MAX_WIDTH_TO_SCREEN_RATION = 0.70;
    private static final int MIN_WIDTH_DP = 88;

    private User user;
    int widthMeasureSpec;
    int heightMeasureSpec;
    private int cornerRadius;
    private float backgroundDarkenPerc;
    private View backgroundView;
    private ViewAnimator labelViewAnimator;
    private TypefaceTextView username2TextView;
    private TypefaceTextView label2TextView;
    private TypefaceTextView usernameTextView;
    private TypefaceTextView labelTextView;
    private ViewAnimator avatarViewAnimator;
    private ChatheadView chatheadView;
    private ChatheadView chatheadView2;
    private View labelWrapper;
    private View label2Wrapper;

    public MessageNotificationChatheadView(Context context) {
        super(context);
        init(context);
    }

    public MessageNotificationChatheadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.rl_message_notification_chathead, this, true);

        avatarViewAnimator = ViewUtils.getView(this, R.id.va_message_notification_chathead__chathead_viewanimator);
        labelViewAnimator = ViewUtils.getView(this, R.id.va_message_notification_chathead__label_viewanimator);
        chatheadView = ViewUtils.getView(this, R.id.cv_message_notification_chathead__avatar1);
        chatheadView2 = ViewUtils.getView(this, R.id.cv_message_notification_chathead__avatar2);
        backgroundView = ViewUtils.getView(this, R.id.ll_message_notification_chathead__background);
        username2TextView = ViewUtils.getView(this, R.id.ttv_message_notification_chathead__username2);
        label2TextView = ViewUtils.getView(this, R.id.ttv_message_notification_chathead__label2);
        usernameTextView = ViewUtils.getView(this, R.id.ttv_message_notification_chathead__username1);
        labelTextView = ViewUtils.getView(this, R.id.ttv_message_notification_chathead__label1);
        labelWrapper = ViewUtils.getView(this, R.id.ll_message_notification_chathead__label_wrapper);
        label2Wrapper = ViewUtils.getView(this, R.id.ll_message_notification_chathead__label_wrapper2);

        cornerRadius = getResources().getDimensionPixelSize(R.dimen.message_notification_chathead__background__height);
        backgroundDarkenPerc = ResourceUtils.getResourceFloat(getResources(),
                                                              R.dimen.message_notification_chathead__background__darken_perc);

        avatarViewAnimator.setInAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in));
        avatarViewAnimator.setOutAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_out));

        Animation inAnim = AnimationUtils.loadAnimation(context, R.anim.slide_in_from_bottom);
        Animation outAnim = AnimationUtils.loadAnimation(context, R.anim.slide_out_to_top);
        inAnim.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        outAnim.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));

        labelViewAnimator.setInAnimation(inAnim);
        labelViewAnimator.setOutAnimation(outAnim);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        this.widthMeasureSpec = widthMeasureSpec;
        this.heightMeasureSpec = heightMeasureSpec;
    }


    public void setMessage(Message message) {
        if (this.user != null) {
            this.user.removeUpdateListener(this);
        }
        this.user = message.getUser();
        this.user.addUpdateListener(this);
        createBackgroundShape(user.getAccent().getColor());

        if (avatarViewAnimator.getDisplayedChild() == 0) {
            chatheadView2.setUser(user);
            username2TextView.setTransformedText(user.getName(),
                                         getResources().getString(R.string.message_notification_chathead__username__text_transform));
            setLabel(label2TextView, message);

            setBackgroundWidth(username2TextView, label2TextView, label2Wrapper);
        } else {
            chatheadView.setUser(user);
            usernameTextView.setTransformedText(user.getName(),
                                        getResources().getString(R.string.message_notification_chathead__username__text_transform));
            setLabel(labelTextView, message);

            setBackgroundWidth(usernameTextView, labelTextView, labelWrapper);
        }

        avatarViewAnimator.showNext();
        labelViewAnimator.showNext();
    }

    @Override
    public void updated() {
        TypefaceTextView usernameTextView;
        TypefaceTextView labelTextView;
        View labelWrapper;

        if (labelViewAnimator.getDisplayedChild() == 0) {
            usernameTextView = this.username2TextView;
            labelTextView = this.labelTextView;
            labelWrapper = this.labelWrapper;
        } else {
            usernameTextView = this.usernameTextView;
            labelTextView = this.label2TextView;
            labelWrapper = this.label2Wrapper;
        }

        usernameTextView.setTransformedText(user.getName(),
                                            getResources().getString(R.string.message_notification_chathead__username__text_transform));
        setBackgroundWidth(usernameTextView, labelTextView, labelWrapper);
    }

    private void setLabel(TypefaceTextView label, Message message) {
        String messageLabel;
        if (message.isEphemeral()) {
            label.setTypeface(getResources().getString(R.string.wire__typeface__redacted));
            messageLabel = message.getBody();
        } else {
            label.setTypeface(getResources().getString(R.string.message_notification_chathead__label__font));
            if (message.getMessageType() == Message.Type.KNOCK) {
                messageLabel = message.isHotKnock() ? getResources().getString(R.string.in_app_notification__footer__pinged_again)
                                                    : getResources().getString(R.string.in_app_notification__footer__ping);
            } else if (message.getMessageType() == Message.Type.ASSET) {
                messageLabel = getResources().getString(R.string.in_app_notification__footer__photo);
            } else if (message.getMessageType() == Message.Type.ANY_ASSET) {
                messageLabel = getResources().getString(R.string.in_app_notification__footer__file);
            } else if (message.getMessageType() == Message.Type.VIDEO_ASSET) {
                messageLabel = getResources().getString(R.string.in_app_notification__footer__video);
            } else if (message.getMessageType() == Message.Type.AUDIO_ASSET) {
                messageLabel = getResources().getString(R.string.in_app_notification__footer__audio);
            } else if (message.getMessageType() == Message.Type.LOCATION) {
                messageLabel = getResources().getString(R.string.in_app_notification__footer__location);
            } else {
                messageLabel = message.getBody();
            }
        }
        label.setTransformedText(messageLabel,
                                 getResources().getString(R.string.message_notification_chathead__label__text_transform));
    }

    /**
     * Animates background width based on measured width of text labels. Keeps width within min & max values.
     */
    private void setBackgroundWidth(View usernameView, View labelView, View wrapperView) {
        int displayWidth = ViewUtils.getOrientationIndependentDisplayWidth(getContext());
        int maxWidth =  (int) (MAX_WIDTH_TO_SCREEN_RATION * displayWidth);
        int minWidth = ViewUtils.toPx(getContext(), MIN_WIDTH_DP);

        // Measure views
        usernameView.measure(widthMeasureSpec, heightMeasureSpec);
        labelView.measure(widthMeasureSpec, heightMeasureSpec);
        wrapperView.measure(widthMeasureSpec, heightMeasureSpec);
        backgroundView.measure(widthMeasureSpec, heightMeasureSpec);

        // Keep background width within min & max values
        int defaultBackgroundWidth = backgroundView.getPaddingLeft() + avatarViewAnimator.getMeasuredWidth() + wrapperView.getMeasuredWidth() + backgroundView.getPaddingRight();
        int backgroundWidth = setMinMaxBackgroundWidth(defaultBackgroundWidth, maxWidth, minWidth);
        int duration = getResources().getInteger(R.integer.message_notification_chathead__background__animation_duration);
        ValueAnimator.ofObject(new WidthEvaluator(backgroundView), backgroundView.getWidth(), backgroundWidth).setDuration(duration).start();
    }

    /**
     * Returns background width within min and max values
     */
    private int setMinMaxBackgroundWidth(int defaultBackgroundWidth, int maxWidth, int minWidth) {
        int backgroundWidth = defaultBackgroundWidth;

        if (backgroundWidth > maxWidth) {
            backgroundWidth = maxWidth;
        }

        if (backgroundWidth < minWidth) {
            backgroundWidth = minWidth;
        }

        return backgroundWidth;
    }

    /**
     * Draws oval background shape with color
     */
    private void createBackgroundShape(int color) {
        Drawable roundedBackground = ViewUtils.getRoundedRect(cornerRadius, ColorUtils.adjustBrightness(color,
                                                                                                        backgroundDarkenPerc));
        backgroundView.setBackground(roundedBackground);
        setTextColor();
    }

    private void setTextColor() {
        TypefaceTextView username;
        TypefaceTextView label;

        // Find correct label views
        if (labelViewAnimator.getDisplayedChild() == 0) {
            username = username2TextView;
            label = label2TextView;
        } else {
            username = usernameTextView;
            label = labelTextView;
        }

        // Set text color
        username.setTextColor(getResources().getColor(R.color.message_notification__username_font_color));
        label.setTextColor(getResources().getColor(R.color.message_notification__font_color));
    }

    public void tearDown() {
        if (user != null) {
            user.removeUpdateListener(this);
        }
        user = null;
        removeAllViews();
    }
}
