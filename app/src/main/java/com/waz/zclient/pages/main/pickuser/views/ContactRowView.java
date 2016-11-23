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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.api.Contact;
import com.waz.api.ContactDetails;
import com.waz.api.User;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.common.views.ChatheadView;

public class ContactRowView extends FrameLayout implements UserRowView {

    private ContactDetails contactDetails;
    private User user;
    private ChatheadView chathead;
    private TextView nameView;
    private TextView subLabelView;
    private ZetaButton contactInviteButton;
    private Callback callback;

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
            redraw();
        }
    };

    public ContactRowView(Context context) {
        this(context, null);
    }

    public ContactRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setContact(Contact contact) {
        if (contact == null) {
            return;
        }
        userModelObserver.clear();
        contactDetailsModelObserver.clear();
        contactDetails = null;
        user = null;
        clearDraw();
        contactDetailsModelObserver.setAndUpdate(contact.getDetails());
        userModelObserver.setAndUpdate(contact.getUser());
    }

    public void setAccentColor(int color) {
        contactInviteButton.setAccentColor(color);
    }

    public void applyDarkTheme() {
        nameView.setTextColor(getResources().getColor(R.color.text__primary_dark));
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.list_row_contactlist_user, this, true);
        chathead = ViewUtils.getView(this, R.id.cv__contactlist__user__chathead);
        nameView = ViewUtils.getView(this, R.id.ttv__contactlist__user__name);
        subLabelView = ViewUtils.getView(this, R.id.ttv__contactlist__user__username_and_address_book);
        contactInviteButton = ViewUtils.getView(this, R.id.zb__contactlist__user_selected_button);
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public void onClicked() {
        if (user != null && user.getConnectionStatus() == User.ConnectionStatus.ACCEPTED) {
            setSelected(!isSelected());
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        chathead.setSelected(selected);
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
            if (commonContacts > 0) {
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

    private void redraw() {
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
        chathead.setUser(user);
        switch (user.getConnectionStatus()) {
            case CANCELLED:
            case UNCONNECTED:
                contactInviteButton.setVisibility(VISIBLE);
                contactInviteButton.setText(getResources().getText(R.string.people_picker__contact_list__contact_selection_button__label));
                break;
            case ACCEPTED:
                setSelected(callback.isUserSelected(user));
                contactInviteButton.setVisibility(GONE);
                break;
            case PENDING_FROM_USER:
                contactInviteButton.setVisibility(GONE);
                break;
        }
        contactInviteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (callback == null) {
                    return;
                }
                callback.onContactListUserClicked(user);
            }
        });
    }

    private void drawContact() {
        if (contactDetails == null) {
            return;
        }
        nameView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        nameView.setText(contactDetails.getDisplayName());
        subLabelView.setVisibility(GONE);
        chathead.setContactDetails(contactDetails);
        if (contactDetails.hasBeenInvited()) {
            contactInviteButton.setVisibility(GONE);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (callback == null) {
                        return;
                    }
                    callback.onContactListContactClicked(contactDetails);
                }
            });
            return;
        }
        contactInviteButton.setVisibility(VISIBLE);
        contactInviteButton.setText(getResources().getText(R.string.people_picker__contact_list__contact_selection_button__label));
        contactInviteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (callback == null) {
                    return;
                }
                callback.onContactListContactClicked(contactDetails);
            }
        });
    }

    private void clearDraw() {
        nameView.setText("");
        subLabelView.setVisibility(GONE);
    }

    public interface Callback {
        void onContactListUserClicked(User user);

        void onContactListContactClicked(ContactDetails contactDetails);

        @IPickUserController.ContactListDestination int getDestination();

        boolean isUserSelected(User user);
    }
}
