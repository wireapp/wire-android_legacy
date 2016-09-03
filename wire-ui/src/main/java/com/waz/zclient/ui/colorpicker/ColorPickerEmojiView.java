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
package com.waz.zclient.ui.colorpicker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;
import com.waz.zclient.ui.R;

public class ColorPickerEmojiView extends TextView implements ColorPickerView {

    private final int iconSizeUnselected = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__icon_size__unselected);
    private final int iconSizeSmall = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__icon_size__small);
    private final int iconSizeMedium = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__icon_size__medium);
    private final int iconSizeLarge = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__icon_size__large);

    private final int emojiSizeSmall = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__size__small);
    private final int emojiSizeMedium = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__size__medium);
    private final int emojiSizeLarge = getResources().getDimensionPixelSize(R.dimen.sketch__emoji__size__large);

    private boolean selected;
    private int iconSize = iconSizeUnselected;
    private String emoji;

    public ColorPickerEmojiView(Context context) {
        this(context, null);
    }

    public ColorPickerEmojiView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerEmojiView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setGravity(Gravity.CENTER);
        setTextSize(iconSize);
    }

    @Override
    public void setSelected(int size) {
        if (selected) {
            iconSize = getNextSize();
        } else {
            iconSize = size;
            selected = true;
        }
        setTextSize(iconSize);
        invalidate();
    }

    @Override
    public void setUnselected() {
        selected = false;
        iconSize = iconSizeUnselected;
        setTextSize(iconSize);
        invalidate();
    }

    private int getNextSize() {
        if (iconSize == iconSizeLarge || iconSize == iconSizeUnselected) {
            return iconSizeSmall;
        } else if (iconSize == iconSizeMedium) {
            return iconSizeLarge;
        } else {
            return iconSizeMedium;
        }
    }

    @Override
    public int getSize() {
        return iconSize;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
        setText(emoji);
    }

    public String getEmoji() {
        return emoji;
    }

    public int getEmojiSize() {
        if (iconSize == iconSizeLarge) {
            return emojiSizeLarge;
        } else if (iconSize == iconSizeMedium) {
            return emojiSizeMedium;
        }
        return emojiSizeSmall;
    }
}
