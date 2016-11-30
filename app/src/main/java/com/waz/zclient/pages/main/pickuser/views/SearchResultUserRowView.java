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
package com.waz.zclient.pages.main.pickuser.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.common.views.ChatheadView;

public class SearchResultUserRowView extends FrameLayout implements UserRowView,
                                                                    UpdateListener {

    private User user;
    private ChatheadView chathead;
    private ContactListItemTextView contactListItemTextView;

    public SearchResultUserRowView(Context context) {
        this(context, null);
    }

    public SearchResultUserRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultUserRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setUser(User user) {
        if (this.user != null) {
            this.user.removeUpdateListener(this);
        }
        this.user = user;
        if (this.user != null) {
            this.user.addUpdateListener(this);
        }
        updated();
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public void onClicked() {
        setSelected(!isSelected());
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        chathead.setSelected(selected);
    }

    @Override
    public void updated() {
        if (user == null) {
            return;
        }
        contactListItemTextView.setUser(user);
        chathead.setUser(user);
    }

    public void applyDarkTheme() {
        contactListItemTextView.applyDarkTheme();
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.list_row_pickuser_searchuser, this, true);
        chathead = ViewUtils.getView(this, R.id.cv_pickuser__searchuser_chathead);
        contactListItemTextView = ViewUtils.getView(this, R.id.clitv__contactlist__user__text_view);
    }
}
