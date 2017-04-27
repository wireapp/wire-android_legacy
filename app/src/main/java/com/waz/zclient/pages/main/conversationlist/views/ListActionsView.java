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
package com.waz.zclient.pages.main.conversationlist.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.views.CircleView;
import com.waz.zclient.utils.ViewUtils;

public class ListActionsView extends FrameLayout implements View.OnClickListener {

    private GlyphTextView avatar;
    private GlyphTextView settings;
    private CircleView indicatorDot;
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
        settings = ViewUtils.getView(this, R.id.gtv__list_actions__settings);
        settings.setOnClickListener(this);
        bottomBorder = ViewUtils.getView(this, R.id.v_conversation_list_bottom_border);
        indicatorDot = ViewUtils.getView(this, R.id.cv__list_actions__settings_indicator);
        indicatorDot.setVisibility(GONE);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setAccentColor(int color) {
        indicatorDot.setAccentColor(color);
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
                callback.onSettingsPress();
                break;
        }
    }

    public void setScrolledToBottom(boolean scrolledToBottom) {
        bottomBorder.setVisibility(scrolledToBottom ? GONE : VISIBLE);
    }

    public void showIndicatorDot(boolean show) {
        indicatorDot.setVisibility(show ? VISIBLE : GONE);
    }

    public interface Callback {
        void onAvatarPress();

        void onSettingsPress();
    }
}
