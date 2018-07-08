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
package com.waz.zclient.ui.calling;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class RoundedLayout extends FrameLayout {

    private final Path path;
    private boolean circular = true;

    public RoundedLayout(Context context) {
        this(context, null);
    }

    public RoundedLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoundedLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        path = new Path();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        if (circular) {
            canvas.clipPath(path);
        }
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (circular) {
            path.reset();
            path.addCircle(w / 2, h / 2, w / 2, Path.Direction.CW);
            path.close();
        }
    }
}
