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
package com.waz.zclient.ui.views;


import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.ContactDetails;
import com.waz.api.User;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.ui.R;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import java.util.Locale;


public class UserDetailsView extends LinearLayout {

    private TextView userNameTextView;
    private TextView userInfoTextView;
    private User user;


    private final ModelObserver<ContactDetails> contactDetailsModelObserver = new ModelObserver<ContactDetails>() {
        @Override
        public void updated(ContactDetails contactDetails) {
            if (user == null) {
                return;
            }
            String userInfo;
            if (user.getDisplayName().toLowerCase(Locale.getDefault()).equals(contactDetails.getDisplayName().toLowerCase(Locale.getDefault()))) {
                // User's Wire name is same as in address book
                userInfo = getContext().getResources().getString(R.string.content__message__connect_request__user_info, "");
            } else {
                userInfo = getContext().getResources().getString(R.string.content__message__connect_request__user_info,
                                                                      contactDetails.getDisplayName());
            }
            userInfoTextView.setText(userInfo);
        }
    };

    private final ModelObserver<User> userModelObserver = new ModelObserver<User>() {
        @Override
        public void updated(User user) {
            if (TextUtils.isEmpty(user.getUsername())) {
                userNameTextView.setVisibility(GONE);
                userNameTextView.setText("");
            } else {
                userNameTextView.setVisibility(VISIBLE);
                userNameTextView.setText(StringUtils.formatHandle(user.getUsername()));
            }
            userInfoTextView.setText("");
            contactDetailsModelObserver.setAndUpdate(user.getFirstContact());
        }
    };

    public UserDetailsView(Context context) {
        this(context, null);
    }

    public UserDetailsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UserDetailsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setUser(User user) {
        this.user = user;
        if (user == null) {
            userModelObserver.clear();
            userNameTextView.setText("");
            userInfoTextView.setText("");
            return;
        }
        userModelObserver.setAndUpdate(user);
    }

    public void recycle() {
        userModelObserver.clear();
        contactDetailsModelObserver.clear();
        user = null;
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.user__details, this, true);
        userNameTextView = ViewUtils.getView(this, R.id.ttv__user_details__user_name);
        userInfoTextView = ViewUtils.getView(this, R.id.ttv__user_details__user_info);
    }
}
