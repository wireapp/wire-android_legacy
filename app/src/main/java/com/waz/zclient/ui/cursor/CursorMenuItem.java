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
package com.waz.zclient.ui.cursor;

import com.waz.zclient.R;

/**
 * These are the cursor items displayed under the cursor.
 * IMPORTANT: the ordering is important as the order will
 * be preserved in the actual layout
 * <p/>
 * Each cursor item needs to have a resid to be testable;
 */
public enum CursorMenuItem {
    VIDEO_MESSAGE(R.string.glyph__record, R.string.glyph__record_timed, R.id.cursor_menu_item_video, R.string.tooltip_record),
    CAMERA(R.string.glyph__camera, R.string.glyph__camera_timed, R.id.cursor_menu_item_camera, R.string.tooltip_camera),
    SKETCH(R.string.glyph__paint, R.string.glyph__sketch_timed, R.id.cursor_menu_item_draw, R.string.tooltip_sketch),
    FILE(R.string.glyph__attachment, R.string.glyph__attachment_timed, R.id.cursor_menu_item_file, R.string.tooltip_file),
    GIF(R.string.glyph__gif, R.string.glyph__gif_timed, R.id.cursor_menu_item_gif, R.string.tooltip_gif),
    AUDIO_MESSAGE(R.string.glyph__microphone_on, R.string.glyph__microphone_on_timed, R.id.cursor_menu_item_audio_message, R.string.tooltip_audio_message),
    MORE(R.string.glyph__more, R.string.glyph__more, R.id.cursor_menu_item_more, R.string.tooltip_more),
    LESS(R.string.glyph__more, R.string.glyph__more, R.id.cursor_menu_item_less, R.string.tooltip_more),
    // DUMMY item is a blank icon just used to position icons when the row of icons is not full
    DUMMY(R.string.empty_string, R.string.empty_string, R.id.cursor_menu_item_dummy, R.string.tooltip_ping),
    LOCATION(R.string.glyph__location, R.string.glyph__location_timed, R.id.cursor_menu_item_location, R.string.tooltip_location),
    EMOJI(R.string.glyph__emoji, R.string.glyph__emoji_timed, R.id.cursor_menu_item_emoji, R.string.tooltip_emoji),
    KEYBOARD(R.string.glyph__keyboard, R.string.glyph__keyboard, R.id.cursor_menu_item_keyboard, R.string.tooltip_emoji),
    PING(R.string.glyph__ping, R.string.glyph__ping_timed, R.id.cursor_menu_item_ping, R.string.tooltip_ping);

    public int glyphResId;
    public int timedGlyphResId;
    public int resId;
    public int resTooltip;

    CursorMenuItem(int glyphResId, int timedGlyphResId, int resId, int resTooltip) {
        this.glyphResId = glyphResId;
        this.timedGlyphResId = timedGlyphResId;
        this.resId = resId;
        this.resTooltip = resTooltip;
    }
}
