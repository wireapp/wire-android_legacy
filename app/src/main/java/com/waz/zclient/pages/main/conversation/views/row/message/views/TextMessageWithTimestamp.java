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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.ui.text.LinkTextView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.ZTimeFormatter;
import org.threeten.bp.DateTimeUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

public class TextMessageWithTimestamp extends LinearLayout implements AccentColorObserver,
                                                                      GestureDetector.OnGestureListener {


    private final float textSizeRegular;
    private final float textSizeEmoji;

    private LinkTextView messageTextView;
    private TypefaceTextView timestampTextView;
    private MessageViewsContainer messageViewContainer;
    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (messageTextView == null ||
                timestampTextView == null ||
                messageViewContainer == null ||
                messageViewContainer.isTornDown()) {
                return;
            }

            resizeIfEmoji(message);

            String messageText;
            if (message.isDeleted()) {
                messageText = getResources().getString(R.string.content__system__message_deleted);
            } else if (message.getMessageType() == Message.Type.LOCATION) {
                messageText = getLocationMessageText(message.getLocation());
            } else {
                messageText = message.getBody();
                messageText = messageText.replaceAll("\u2028", "\n");
            }

            messageTextView.setLinkTextColor(messageViewContainer.getControllerFactory().getAccentColorController().getColor());
            messageTextView.setTextLink(messageText);

            String timestamp;
            Message.Status messageStatus = message.getMessageStatus();
            if (messageStatus == Message.Status.PENDING) {
                timestamp = getResources().getString(R.string.content_system_message_timestamp_pending);
            } else if (messageStatus == Message.Status.FAILED) {
                timestamp = getResources().getString(R.string.content_system_message_timestamp_failure);
            } else {
                timestamp = ZTimeFormatter.getSingleMessageTime(getContext(), DateTimeUtils.toDate(message.getTime()));
            }

            timestampTextView.setTransformedText(timestamp);
            timestampTextView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    collapseTimestamp();
                }
            });
        }
    };
    private GestureDetectorCompat gestureDetector;
    private int animationDuration;
    private String messageId;
    private OnLongClickListener longClickListener;

    public TextMessageWithTimestamp(Context context) {
        this(context, null);
    }

    public TextMessageWithTimestamp(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextMessageWithTimestamp(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context).inflate(R.layout.row_conversation_text_with_timestamp, this, true);
        setOrientation(VERTICAL);

        messageTextView = ViewUtils.getView(this, R.id.ltv__row_conversation__message);
        timestampTextView = ViewUtils.getView(this, R.id.ttv__row_conversation__timestamp);
        animationDuration = getResources().getInteger(R.integer.content__message_timestamp__animation_duration);

        textSizeRegular = context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__regular);
        textSizeEmoji = context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__emoji);

        messageTextView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        });

        gestureDetector = new GestureDetectorCompat(context, this);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        if (!gestureDetector.isLongpressEnabled()) {
            gestureDetector.setIsLongpressEnabled(l != null);
        }
        this.longClickListener = l;
    }

    public void setMessage(final Message message) {
        messageModelObserver.setAndUpdate(message);
        messageTextView.setVisibility(View.VISIBLE);
        messageId = message.getId();
        if (messageViewContainer.getTimestampShownSet().contains(messageId)) {
            messageViewContainer.setShownTimestampView(this);
            timestampTextView.setVisibility(VISIBLE);
        } else {
            timestampTextView.setVisibility(GONE);
        }
    }

    private void expandTimestamp() {
        if (messageViewContainer.getShownTimestampView() != null &&
            messageViewContainer.getShownTimestampView() != this) {
            messageViewContainer.getShownTimestampView().collapseTimestamp();
        }
        messageViewContainer.getTimestampShownSet().add(messageId);
        messageViewContainer.setShownTimestampView(this);
        timestampTextView.setVisibility(VISIBLE);

        View parent = (View) timestampTextView.getParent();
        final int widthSpec = MeasureSpec.makeMeasureSpec(parent.getMeasuredWidth()
                                                          - parent.getPaddingLeft()
                                                          - parent.getPaddingRight(),
                                                          MeasureSpec.AT_MOST);
        final int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        timestampTextView.measure(widthSpec, heightSpec);
        ValueAnimator animator = createHeightAnimator(timestampTextView, 0, timestampTextView.getMeasuredHeight());
        animator.start();
    }

    private void collapseTimestamp() {
        messageViewContainer.getTimestampShownSet().remove(messageId);
        int origHeight = timestampTextView.getHeight();

        ValueAnimator animator = createHeightAnimator(timestampTextView, origHeight, 0);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animator) {
                timestampTextView.setVisibility(View.GONE);
            }
        });
        animator.start();
    }

    public ValueAnimator createHeightAnimator(final View view, final int start, final int end) {
        ValueAnimator animator = ValueAnimator.ofInt(start, end);
        animator.setDuration(animationDuration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator valueAnimator) {
                ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                layoutParams.height = (Integer) valueAnimator.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }
        });
        return animator;
    }

    private boolean isClickInsideLink(MotionEvent event) {
        Object text = messageTextView.getText();
        if (text != null && text instanceof Spanned) {
            Spannable buffer = (Spannable) text;

            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= messageTextView.getTotalPaddingLeft();
            y -= messageTextView.getTotalPaddingTop();

            x += messageTextView.getScrollX();
            y += messageTextView.getScrollY();

            Layout layout = messageTextView.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

            return link.length > 0;

        }
        return false;
    }

    public void setMessageViewsContainer(MessageViewsContainer messageViewContainer) {
        this.messageViewContainer = messageViewContainer;
        messageViewContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        messageTextView.setLinkTextColor(color);
    }

    public void recycle() {
        if (!messageViewContainer.isTornDown()) {
            messageViewContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        messageModelObserver.pauseListening();
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
        if (isClickInsideLink(event)) {
            return false;
        }
        timestampTextView.clearAnimation();
        if (timestampTextView.getVisibility() == GONE) {
            expandTimestamp();
        } else {
            collapseTimestamp();
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        longClickListener.onLongClick(this);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    private void resizeIfEmoji(Message message) {
        if (message.getMessageType() == Message.Type.TEXT_EMOJI_ONLY) {
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeEmoji);
        } else {
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeRegular);
        }
    }

    private String getLocationMessageText(MessageContent.Location location) {
        if (location == null) {
            return "";
        }
        String urlEncodedName;
        try {
            urlEncodedName = URLEncoder.encode(location.getName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            urlEncodedName = location.getName();
        }
        return String.format(Locale.getDefault(),
                                    IntentUtils.GOOGLE_MAPS_WEB_LINK,
                                    location.getZoom(),
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    urlEncodedName);
    }
}
