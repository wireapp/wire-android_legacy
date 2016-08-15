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
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.RetryMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.utils.ViewUtils;


public class TextMessageViewController extends RetryMessageViewController {

    private View view;
    private TextMessageWithTimestamp textWithTimestamp;

    @SuppressLint("InflateParams")
    public TextMessageViewController(Context context, final MessageViewsContainer messageViewContainer) {
        super(context, messageViewContainer);
        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.row_conversation_text_message, null);
        textWithTimestamp = ViewUtils.getView(view, R.id.tmwt__message_and_timestamp);
        textWithTimestamp.setMessageViewsContainer(messageViewContainer);
        afterInit();
    }

    @Override
    public void onSetMessage(Separator separator) {
        super.onSetMessage(separator);
        textWithTimestamp.setMessage(message);
    }

    @Override
    protected void onHeaderClick() {
        if (message.getMessageType() == Message.Type.RECALLED) {
            textWithTimestamp.toggleTimestamp();
        }
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public void recycle() {
        textWithTimestamp.recycle();
        super.recycle();
    }
}
