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

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.ui.text.LinkTextView;
import com.waz.zclient.ui.utils.TypefaceUtils;

public class TextMessageLinkTextView extends LinkTextView implements AccentColorObserver,
                                                                     View.OnLongClickListener {

    private boolean onLongClicked;
    private Callback callback;

    private MessageViewsContainer messageViewContainer;
    private final float textSizeRegular;
    private final float textSizeEmoji;
    private final Typeface defaultTypeface;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (messageViewContainer == null ||
                messageViewContainer.isTornDown()) {
                return;
            }

            String messageText;
            if (message.isEphemeral() && message.isExpired()) {
                setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getRedactedTypedaceName()));
            } else {
                setTypeface(defaultTypeface);
            }

            if (message.isDeleted()) {
                messageText = "";
            } else {
                messageText = message.getBody();
                messageText = messageText.replaceAll("\u2028", "\n");
            }

            if (message.getMessageType() == Message.Type.RECALLED) {
                setVisibility(GONE);
            } else {
                setVisibility(VISIBLE);
                if (message.isEphemeral() && message.isExpired()) {
                    setText(messageText);
                } else {
                    setTextLink(messageText);
                }
            }

            resizeIfEmoji(message);
        }
    };

    public TextMessageLinkTextView(Context context) {
        this(context, null);
    }

    public TextMessageLinkTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextMessageLinkTextView(final Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        textSizeRegular = context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__regular);
        textSizeEmoji = context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__emoji);

        setOnLongClickListener(this);
        defaultTypeface = getTypeface();
    }

    /*
     * This part (the methods onLongClick and onTouchEvent) of the Wire software uses source coded posted on the StackOverflow site.
     * (http://stackoverflow.com/a/19781716)
     *
     * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
     * (http://creativecommons.org/licenses/by-sa/2.5)
     *
     * Contributors on StackOverflow:
     *  - afei (http://stackoverflow.com/users/2954908/afei)
     */
    @Override
    public boolean onLongClick(View view) {
        onLongClicked = true;
        if (callback != null) {
            callback.onTextMessageLinkTextViewOnLongClicked(this);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            onLongClicked = false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP && onLongClicked) {
            onLongClicked = false;
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setMessage(final Message message) {
        messageModelObserver.setAndUpdate(message);
    }


    public void setMessageViewsContainer(MessageViewsContainer messageViewContainer) {
        this.messageViewContainer = messageViewContainer;
        messageViewContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        setLinkTextColor(color);
    }

    public void recycle() {
        if (!messageViewContainer.isTornDown()) {
            messageViewContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        messageModelObserver.pauseListening();
    }

    private void resizeIfEmoji(Message message) {
        if (message.getMessageType() == Message.Type.TEXT_EMOJI_ONLY) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeEmoji);
        } else {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizeRegular);
        }
    }

    public interface Callback {
        void onTextMessageLinkTextViewOnLongClicked(View view);
    }
}
