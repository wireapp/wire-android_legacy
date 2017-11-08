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
package com.waz.zclient.core.api.scala;

import com.waz.api.IConversation;
import com.waz.zclient.core.stores.draft.DraftStore;

import java.util.HashMap;
import java.util.Map;

public class ScalaDraftStore extends DraftStore {
    public static final String TAG = ScalaDraftStore.class.getName();

    Map<String, String> draftMap = new HashMap<>();

    public ScalaDraftStore() {

    }

    @Override
    public void setDraft(IConversation conversation, String text) {
        if (conversation == null) {
            return;
        }
        draftMap.put(conversation.getId(), text);
    }

    @Override
    public String getDraft(IConversation conversation) {

        if (conversation != null &&
            draftMap.containsKey(conversation.getId())) {
            return draftMap.get(conversation.getId());
        }
        return "";
    }

    @Override
    public void tearDown() {
        if (draftMap != null) {
            draftMap.clear();
        }
    }
}
