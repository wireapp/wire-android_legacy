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
package com.waz.zclient.pages.main.conversationlist.views.row;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;
import com.waz.zclient.R;

public class ConversationListArchivedBorderRow extends View {

    private Paint paint;

    public ConversationListArchivedBorderRow(Context context) {
        super(context);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            paint.setColor(getResources().getColor(R.color.list_archive_box__background_color));
        } else {
            paint.setColor(getResources().getColor(R.color.list_archive_box__background_color, getContext().getTheme()));
        }
        setBackgroundColor(Color.TRANSPARENT);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int centerY = canvas.getHeight() / 2;
        canvas.drawRect(0, centerY, canvas.getWidth(), canvas.getHeight(), paint);
    }

}
