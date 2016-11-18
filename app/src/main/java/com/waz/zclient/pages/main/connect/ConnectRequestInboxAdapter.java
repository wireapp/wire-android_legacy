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
package com.waz.zclient.pages.main.connect;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.connect.views.CommonUsersCallback;
import com.waz.zclient.pages.main.connect.views.ConnectRequestInboxRow;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;

public class ConnectRequestInboxAdapter extends BaseAdapter {

    public static final String TAG = ConnectRequestInboxAdapter.class.getName();
    public static final int MAIN_CONNECT_REQUEST_NOT_SPECIFIED_POS = -1;

    private List<IConversation> connectRequests;
    private int mainVisibleRequestPosition = MAIN_CONNECT_REQUEST_NOT_SPECIFIED_POS;
    private int accentColor;
    private Context context;
    private ConnectActionsCallback connectActionsCallback;
    private CommonUsersCallback commonUsersCallback;

    public ConnectRequestInboxAdapter(Context context, ConnectActionsCallback connectActionsCallback, CommonUsersCallback commonUsersCallback) {
        this.context = context;
        this.connectActionsCallback = connectActionsCallback;
        this.commonUsersCallback = commonUsersCallback;
    }

    public void reset() {
        connectRequests = null;
        notifyDataSetChanged();
    }

    public void setConnectRequests(List<IConversation> connectRequests) {
        this.connectRequests = connectRequests;
    }

    public int getMainConnectRequestPosition() {
        return mainVisibleRequestPosition;
    }

    public void setMainConnectRequestPosition(int position) {
        mainVisibleRequestPosition = position;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    @Override
    public int getCount() {
        if (connectRequests == null) {
            return 0;
        }
        return connectRequests.size();
    }

    @Override
    public IConversation getItem(int position) {
        if (connectRequests != null && position < getCount()) {
            return connectRequests.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            convertView = new ConnectRequestInboxRow(parent.getContext());

            viewHolder = new ViewHolder();
            viewHolder.nameView = ViewUtils.getView(convertView, R.id.taet__participants__header);
            viewHolder.subheaderView = ViewUtils.getView(convertView, R.id.ttv__participants__user_name);
            viewHolder.separatorView = ViewUtils.getView(convertView, R.id.v__connect_request__separator_line);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // Set valuesCon
        IConversation request = getItem(position);
        viewHolder.nameView.setText(context.getString(R.string.connect_request__inbox__header, request.getName()));
        TextViewUtils.boldText(viewHolder.nameView);

        ((ConnectRequestInboxRow) convertView).setConnectActionCallback(connectActionsCallback);
        ((ConnectRequestInboxRow) convertView).setCommonUsersCallback(commonUsersCallback);
        ((ConnectRequestInboxRow) convertView).setAccentColor(accentColor);
        ((ConnectRequestInboxRow) convertView).loadUser(request.getOtherParticipant());

        if (LayoutSpec.isPhone(context)) {
            if (position == 0) {
                viewHolder.separatorView.setVisibility(View.GONE);
            } else {
                viewHolder.separatorView.setVisibility(View.VISIBLE);
            }
        }

        return convertView;
    }

    public static class ViewHolder {
        TextView nameView;
        TextView subheaderView;
        View separatorView;
    }
}
