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
package com.waz.zclient.controllers.tracking.events.conversation;

import android.support.annotation.NonNull;
import com.waz.api.Message;
import com.waz.zclient.core.controllers.tracking.attributes.Attribute;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.controllers.tracking.events.Event;
import org.threeten.bp.Instant;

public class EditedMessageEvent extends Event {

    public EditedMessageEvent(Message message) {
        attributes.put(Attribute.TYPE, message.getMessageType().name());

        // message.getEditTime() is not immediately available for tracking purpose
        int timeElapsed = (int) (Instant.now().getEpochSecond() - message.getLocalTime().getEpochSecond());
        rangedAttributes.put(RangedAttribute.MESSAGE_ACTION_TIME_ELAPSED, timeElapsed);
    }

    @NonNull
    @Override
    public String getName() {
        return "conversation.edited_message";
    }
}
