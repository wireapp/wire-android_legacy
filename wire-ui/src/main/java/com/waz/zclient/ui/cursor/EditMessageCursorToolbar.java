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
package com.waz.zclient.ui.cursor;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.zclient.ui.R;
import com.waz.zclient.utils.ViewUtils;


public class EditMessageCursorToolbar extends FrameLayout implements View.OnClickListener {

    private View closeButton;
    private TextView approveButton;
    private TextView resetButton;

    private Callback callback;

    private int enabledTextColor;
    private int disabledTextColor;

    public EditMessageCursorToolbar(Context context) {
        this(context, null);
    }

    public EditMessageCursorToolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditMessageCursorToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void enableEditControls(boolean enable) {
        if (enable) {
            resetButton.setTextColor(enabledTextColor);
            approveButton.setTextColor(enabledTextColor);
        } else {
            resetButton.setTextColor(disabledTextColor);
            approveButton.setTextColor(disabledTextColor);
        }
        resetButton.setClickable(enable);
        approveButton.setClickable(enable);
    }

    @Override
    public void onClick(View view) {
        if (callback == null) {
            return;
        }
        if (view.getId() == R.id.gtv__edit_message__close) {
            callback.onCloseEditMessage();
        } else if (view.getId() == R.id.gtv__edit_message__approve) {
            callback.onApproveEditMessage();
        } else if (view.getId() == R.id.gtv__edit_message__reset) {
            callback.onResetEditMessage();
        }
    }


    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.edit_message_cursor_toolbar, this, true);
        closeButton = ViewUtils.getView(this, R.id.gtv__edit_message__close);
        approveButton = ViewUtils.getView(this, R.id.gtv__edit_message__approve);
        resetButton = ViewUtils.getView(this, R.id.gtv__edit_message__reset);

        enabledTextColor = ContextCompat.getColor(getContext(), R.color.graphite);
        disabledTextColor = ContextCompat.getColor(getContext(), R.color.light_graphite);

        closeButton.setOnClickListener(this);
        closeButton.setClickable(true);
        approveButton.setOnClickListener(this);
        resetButton.setOnClickListener(this);
    }

    public interface Callback {
        void onCloseEditMessage();

        void onResetEditMessage();

        void onApproveEditMessage();
    }
}
