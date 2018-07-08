/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversationlist.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.utils.ViewUtils;

public class ListActionsView extends FrameLayout implements View.OnClickListener {

    private GlyphTextView avatar;
    private GlyphTextView archive;
    private View bottomBorder;

    private Callback callback;

    public ListActionsView(Context context) {
        this(context, null);
    }

    public ListActionsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.list_actions_view, this, true);
        avatar = ViewUtils.getView(this, R.id.gtv__list_actions__avatar);
        avatar.setOnClickListener(this);
        archive = ViewUtils.getView(this, R.id.gtv__list_actions__settings);
        archive.setOnClickListener(this);
        bottomBorder = ViewUtils.getView(this, R.id.v_conversation_list_bottom_border);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(View view) {
        if (view == null || callback == null) {
            return;
        }
        switch (view.getId()) {
            case R.id.gtv__list_actions__avatar:
                callback.onAvatarPress();
                break;
            case R.id.gtv__list_actions__settings:
                callback.onArchivePress();
                break;
        }
    }

    public void setScrolledToBottom(boolean scrolledToBottom) {
        bottomBorder.setVisibility(scrolledToBottom ? GONE : VISIBLE);
    }

    public void setArchiveEnabled(boolean enabled) {
        archive.setVisibility(enabled ? VISIBLE : GONE);
    }

    public void setContactsCentered(boolean centered) {
        LayoutParams params = ((LayoutParams) avatar.getLayoutParams());
        params.gravity =
            Gravity.CENTER_VERTICAL | (centered ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        invalidate();
    }

    public interface Callback {
        void onAvatarPress();

        void onArchivePress();
    }
}
