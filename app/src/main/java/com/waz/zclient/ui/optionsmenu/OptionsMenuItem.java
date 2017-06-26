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
package com.waz.zclient.ui.optionsmenu;


import com.waz.zclient.ui.R;

public enum OptionsMenuItem {

    /**
     * ORDER IS IMPORTANT! Collections.sort is used for priority in menus
     */

    /**
     * OptionsMenuItems for conversation
     */
    ARCHIVE(R.string.conversation__action__archive, R.string.glyph__archive),
    UNARCHIVE(R.string.conversation__action__unarchive, R.string.glyph__archive),
    LEAVE(R.string.conversation__action__leave, R.string.glyph__minus),
    BLOCK(R.string.confirmation_menu__confirm_block, R.string.glyph__block),
    UNBLOCK(R.string.connect_request__unblock__button__text, R.string.glyph__block),
    DELETE(R.string.conversation__action__delete, R.string.glyph__trash),
    SILENCE(R.string.conversation__action__silence, R.string.glyph__silence),
    UNSILENCE(R.string.conversation__action__unsilence, R.string.glyph__silence),
    RENAME(R.string.conversation__action__rename, R.string.glyph__edit),
    PICTURE(R.string.conversation__action__picture, R.string.glyph__camera),
    CALL(R.string.conversation__action__call, R.string.glyph__call),

    /**
     * OptionsMenuItems for settings
     */
    HELP(R.string.menu_help, R.string.glyph__info),
    SETTINGS(R.string.menu_settings, R.string.glyph__settings),
    AVS_SETTINGS(R.string.menu_avs_settings, R.string.glyph__settings),
    ABOUT(R.string.menu_about, R.string.glyph__wire);


    final int resTextId;
    final int resGlyphId;

    OptionsMenuItem(int resTextId, int resGlyphId) {
        this.resTextId = resTextId;
        this.resGlyphId = resGlyphId;
    }

    /**
     * items in the toggled state (aka not the original state) will
     * return true.
     * @return
     *      true if item is a toggled state item
     */
    public boolean isToggled() {
        switch (this) {
            case UNARCHIVE:
                return true;
            case UNSILENCE:
                return true;
            case UNBLOCK:
                return true;
            default:
                return false;
        }
    }
}
