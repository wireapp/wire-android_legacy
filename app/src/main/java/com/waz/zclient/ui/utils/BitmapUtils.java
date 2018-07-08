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
package com.waz.zclient.ui.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import com.waz.zclient.R;

public class BitmapUtils {

    private static final int VIGNETTE_WIDTH = 50;
    private static final int VIGNETTE_HEIGHT = 50;

    private BitmapUtils(){}

    /**
     * Helper function to create a bitmap that serves as a vignette overlay.
     *
     * @return
     */
    public static Bitmap getVignetteBitmap(Resources resources) {
        double radiusFactor = ResourceUtils.getResourceFloat(resources, R.dimen.background__vignette_radius_factor);
        int radius = (int) (VIGNETTE_WIDTH * radiusFactor);

        int baseColor = resources.getColor(R.color.black_80);
        int colorCenter = resources.getColor(R.color.black);
        int colorEdge = resources.getColor(R.color.transparent);

        Bitmap dest = Bitmap.createBitmap(VIGNETTE_WIDTH, VIGNETTE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        Bitmap tempBitmap = Bitmap.createBitmap(dest.getWidth(),
                                                dest.getHeight(),
                                                Bitmap.Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(tempBitmap);
        tempCanvas.drawColor(baseColor);
        RadialGradient gradient = new RadialGradient(VIGNETTE_WIDTH / 2,
                                                                      VIGNETTE_HEIGHT / 2,
                                                                      radius,
                                                                      colorCenter,
                                                                      colorEdge,
                                                                      android.graphics.Shader.TileMode.CLAMP);
        Paint p = new Paint();
        p.setShader(gradient);
        p.setColor(0xFF000000);
        p.setAntiAlias(true);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        tempCanvas.drawCircle(VIGNETTE_WIDTH / 2, VIGNETTE_HEIGHT / 2, radius, p);
        canvas.drawBitmap(tempBitmap, 0, 0, null);

        return dest;
    }


    public static Bitmap getUnreadMarker(int width, int radius, int color) {
        if (width <= 0) {
            return null;
        }
        Bitmap dest = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        Paint p = new Paint();
        p.setColor(color);
        p.setAntiAlias(true);
        canvas.drawCircle(width / 2, width / 2, radius, p);
        return dest;
    }
}
