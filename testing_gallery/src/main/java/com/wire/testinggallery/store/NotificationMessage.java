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
package com.wire.testinggallery.store;


import android.app.Notification;

import com.google.gson.Gson;

public class NotificationMessage {
    private int id;
    private String title;
    private String text;
    private String[] textLines;
    private transient Notification notification;

    public NotificationMessage(int id, String title, String text, CharSequence[] textLinesSequence, Notification notification) {
        this.id = id;
        this.title = title;
        this.text = text;
        this.notification = notification;
        if (textLinesSequence != null) {
            textLines = new String[textLinesSequence.length];
            for (int i = 0; i < textLinesSequence.length; i++) {
                textLines[i] = textLinesSequence[i].toString();
            }
        }
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getTitle() {
        return title;
    }

    public String[] getTextLines() {
        return textLines;
    }

    public Notification getNotification() {
        return notification;
    }

    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

}
