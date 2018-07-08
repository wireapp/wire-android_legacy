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
package com.waz.zclient.controllers.userpreferences;

import com.waz.zclient.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedList;
import java.util.List;

public class RecentEmojis {

    private static final int MAX_RECENT_EMOJIS = 12;

    private List<String> recentEmojis;

    public RecentEmojis(String json) {
        recentEmojis = new LinkedList<>();
        if (!StringUtils.isBlank(json)) {
            try {
                JSONArray jsonArray = new JSONArray(json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    recentEmojis.add(jsonArray.getString(i));
                }
            } catch (JSONException e) {
                // ignore
            }
        }
    }

    public String getJson() {
        JSONArray array = new JSONArray();
        for (String emoji : recentEmojis) {
            array.put(emoji);
        }
        return array.toString();
    }

    public void addRecentEmoji(String emoji) {
        if (recentEmojis.contains(emoji)) {
            recentEmojis.remove(emoji);
        }
        if (recentEmojis.size() >= MAX_RECENT_EMOJIS) {
            recentEmojis.remove(recentEmojis.size() - 1);
        }
        recentEmojis.add(0, emoji);
    }

    public List<String> getRecentEmojis() {
        return recentEmojis;
    }

}
