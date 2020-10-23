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
package com.waz.model;

public enum Relation {
    Other(0),
    First(1),
    Second(2), // friend of friend
    Third(3); // friend of friend of friend

    public final int id;

    private Relation(int id) {
        this.id = id;
    }

    public static Relation withId(int id) {
        switch (id) {
            case 1: return First;
            case 2: return Second;
            case 3: return Third;
            default: return Other;
        }
    }
}
