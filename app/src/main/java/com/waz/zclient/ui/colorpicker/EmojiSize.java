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
package com.waz.zclient.ui.colorpicker;


import android.content.Context;
import com.waz.zclient.R;

public enum EmojiSize {
    SMALL(R.dimen.sketch__emoji__icon_size__small, R.dimen.sketch__emoji__size__small, R.dimen.sketch__color_picker_emoji__icon_size__small),
    MEDIUM(R.dimen.sketch__emoji__icon_size__medium, R.dimen.sketch__emoji__size__medium, R.dimen.sketch__color_picker_emoji__icon_size__medium),
    LARGE(R.dimen.sketch__emoji__icon_size__large, R.dimen.sketch__emoji__size__large, R.dimen.sketch__color_picker_emoji__icon_size__large),
    NONE(R.dimen.sketch__color_picker_emoji__icon_size__unselected, 0, R.dimen.sketch__color_picker_emoji__icon_size__unselected);

    private final int iconSizeResId;
    private final int emojiSizeResId;
    private final int colorPickerIconSizeResId;

    EmojiSize(int iconSizeResId, int emojiSizeResId, int colorPickerIconSizeResId) {
        this.iconSizeResId = iconSizeResId;
        this.emojiSizeResId = emojiSizeResId;
        this.colorPickerIconSizeResId = colorPickerIconSizeResId;
    }

    public int getIconSize(Context context) {
        return context.getResources().getDimensionPixelSize(iconSizeResId);
    }

    public int getIconSizeResId() {
        return iconSizeResId;
    }

    public int getEmojiSize(Context context) {
        return context.getResources().getDimensionPixelSize(emojiSizeResId);
    }

    public int getColorPickerIconResId() {
        return colorPickerIconSizeResId;
    }

}
