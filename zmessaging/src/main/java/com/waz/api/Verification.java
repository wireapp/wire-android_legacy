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

/**
 * Verification state.
 * Used values mean:
 * - VERIFIED   - is verified
 * - UNKNOWN    - was never verified / "don't care about verification"
 * - UNVERIFIED - is no longer verified (it was verified before and got unverified)
 *
 * By default all items will be in UNKNOWN state, and can only transfer to VERIFIED state on user action or sync.
 * From VERIFIED state item usually goes only to UNVERIFIED and back.
 */
public enum Verification {
    UNKNOWN, VERIFIED, UNVERIFIED;

    /**
     * Returns updated value, updated accordingly to rules for state changes (see class description).
     *
     * @param verified
     *  - true if item is currently verified,
     *  - false means that it is not verified, but doesn't mean UNVERIFIED
     */
    public Verification updated(boolean verified) {
        return (verified) ? VERIFIED : ((this == VERIFIED) ? UNVERIFIED : this);
    }

    public Verification orElse(Verification v) {
        return (this == UNKNOWN) ? v : this;
    }
}
