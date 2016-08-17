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
import com.waz.zclient.core.controllers.tracking.attributes.Attribute;
import com.waz.zclient.core.controllers.tracking.events.Event;

public class OpenedMessageActionEvent extends Event {

    private enum Target {
        DELETE_FOR_ME("delete_for_me"),
        DELETE_FOR_EVERYONE("delete_for_everyone"),
        COPY("copy"),
        EDIT("edit"),
        FORWARD("forward"),
        ;

        public final String name;
        Target(String name) {
            this.name = name;
        }
    }

    public OpenedMessageActionEvent(Target target, String messageType) {
        attributes.put(Attribute.ACTION, target.name());
        attributes.put(Attribute.TYPE, messageType);
    }

    public static OpenedMessageActionEvent deleteForMe(String messageType) {
        return new OpenedMessageActionEvent(Target.DELETE_FOR_ME, messageType);
    }

    public static OpenedMessageActionEvent deleteForEveryone(String messageType) {
        return new OpenedMessageActionEvent(Target.DELETE_FOR_EVERYONE, messageType);
    }

    public static OpenedMessageActionEvent edit(String messageType) {
        return new OpenedMessageActionEvent(Target.EDIT, messageType);
    }

    public static OpenedMessageActionEvent copy(String messageType) {
        return new OpenedMessageActionEvent(Target.COPY, messageType);
    }

    public static OpenedMessageActionEvent forward(String messageType) {
        return new OpenedMessageActionEvent(Target.FORWARD, messageType);
    }

    @NonNull
    @Override
    public String getName() {
        return "conversation.opened_message_action";
    }
}
