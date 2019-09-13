/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.api;

public interface IConversation extends UiObservable {

    enum Type {
        UNKNOWN(-1), GROUP(0), SELF(1), ONE_TO_ONE(2), WAIT_FOR_CONNECTION(3), INCOMING_CONNECTION(4);

        // conversation type backend id - this value is received in json
        public int id;

        Type(int id) {
            this.id = id;
        }

        public static Type withId(int id) {
            switch(id) {
                case -1: return UNKNOWN;
                case 0: return GROUP;
                case 1: return SELF;
                case 2: return ONE_TO_ONE;
                case 3: return WAIT_FOR_CONNECTION;
                case 4: return INCOMING_CONNECTION;
                default: return UNKNOWN;
            }
        }
    }

    /**
     * https://github.com/wireapp/architecture/blob/master/topics/conversations/access%20modes.md
     *
     * Access:
     * Specifies the means by which a user (with the correct access role) may join a conversation
     */
    enum Access {
        INVITE,  //possible to join only via invite from another conv member
        CODE,    //possible to join conversation via a "link" (confusingly enough)
        LINK,    //possible to join if the convId is known
        PRIVATE; //for 1:1 conversations
    }

    /**
     * Specifies what types of users may join a conversation
     */
    enum AccessRole {
        TEAM,          //only team members may join the conversation
        ACTIVATED,     //only users with activated account may join the conversation
        NON_ACTIVATED, //"wireless" users may join the conversation
        PRIVATE        //for 1:1 conversations
    }
}
