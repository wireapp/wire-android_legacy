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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.waz.api.Contact;
import com.waz.api.ContactDetails;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;

public class ContactListItemTextView extends LinearLayout {

    private ContactDetails contactDetails;
    private User user;
    private TextView nameView;
    private TextView subLabelView;

    private final ModelObserver<ContactDetails> contactDetailsModelObserver = new ModelObserver<ContactDetails>() {
        @Override
        public void updated(ContactDetails model) {
            contactDetails = model;
            redraw();
        }
    };

    private final ModelObserver<User> userModelObserver = new ModelObserver<User>() {
        @Override
        public void updated(User model) {
            user = model;
            if (user.isContact()) {
                contactDetailsModelObserver.setAndUpdate(user.getFirstContact());
            }
            redraw();
        }
    };

    public ContactListItemTextView(Context context) {
        this(context, null);
    }

    public ContactListItemTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactListItemTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setUser(User user) {
        if (user == null) {
            return;
        }
        recycle();
        clearDraw();
        userModelObserver.setAndUpdate(user);
    }

    public void setContact(Contact contact) {
        if (contact == null) {
            return;
        }
        recycle();
        clearDraw();
        userModelObserver.setAndUpdate(contact.getUser());
        contactDetailsModelObserver.setAndUpdate(contact.getDetails());
    }

    public void recycle() {
        userModelObserver.clear();
        contactDetailsModelObserver.clear();
        this.user = null;
        this.contactDetails = null;
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.contact_list_item_text_layout, this, true);
        nameView = ViewUtils.getView(this, R.id.ttv__contactlist__user__name);
        subLabelView = ViewUtils.getView(this, R.id.ttv__contactlist__user__username_and_address_book);
    }

    public void redraw() {
        if (user != null) {
            drawUser();
        } else if (contactDetails != null) {
            drawContact();
        }
    }

    private void drawUser() {
        if (user == null) {
            return;
        }
        String sublabel = getFormattedSubLabel();
        if (TextUtils.isEmpty(sublabel)) {
            nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        } else {
            nameView.setGravity(Gravity.START | Gravity.BOTTOM);
            subLabelView.setVisibility(VISIBLE);
            subLabelView.setText(sublabel);
        }
        nameView.setText(user.getName());
    }

    public void drawContact() {
        if (contactDetails == null) {
            return;
        }
        nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        nameView.setText(contactDetails.getDisplayName());
        subLabelView.setVisibility(GONE);
    }

    public void clearDraw() {
        nameView.setText("");
        subLabelView.setVisibility(GONE);
    }


    private String getFormattedSubLabel() {
        String username = user.getUsername();
        String addressBookName = contactDetails != null ? contactDetails.getDisplayName().trim() : "";
        String name = user.getName().trim();
        int commonContacts = user.getCommonConnectionsCount();

        String usernameString = "";
        if (!TextUtils.isEmpty(username)) {
            usernameString = StringUtils.formatUsername(username);
        }

        String otherString = "";
        if (TextUtils.isEmpty(addressBookName)) {
            if (commonContacts > 0 && !user.isConnected()) {
                otherString = getResources().getQuantityString(R.plurals.people_picker__contact_list_contact_sub_label_common_friends, commonContacts, commonContacts);
            }
        } else {
            if (name.equalsIgnoreCase(addressBookName)) {
                otherString = getContext().getString(R.string.people_picker__contact_list_contact_sub_label_address_book_identical);
            } else {
                otherString = getContext().getString(R.string.people_picker__contact_list_contact_sub_label_address_book, addressBookName);
            }
        }

        if (TextUtils.isEmpty(username)) {
            return otherString;
        } else if (TextUtils.isEmpty(otherString)) {
            return usernameString;
        }
        return usernameString + " - " + otherString;
    }

    public void applyDarkTheme() {
        nameView.setTextColor(getResources().getColor(R.color.text__primary_dark));
    }
}
