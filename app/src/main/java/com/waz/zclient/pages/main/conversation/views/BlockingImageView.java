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
package com.waz.zclient.pages.main.conversation.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * If you know the size of the image, you can set the size of the view independently. In that case we want to block the
 * requestLayout call while setting the image to the view.
 *
 * {@see <a href="https://plus.google.com/+JorimJaggi/posts/iTk4PjgeAWX">Blog post by Jorim Jaggi</a>}
 */
public class BlockingImageView extends ImageView {
    private boolean blockLayout;

    public BlockingImageView(Context context) {
        super(context);
    }

    public BlockingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BlockingImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void requestLayout() {
        if (!blockLayout) {
            super.requestLayout();
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        blockLayout = true;
        super.setImageBitmap(bm);
        blockLayout = false;
    }

    @Override
    public void setImageResource(int resId) {
        blockLayout = true;
        super.setImageResource(resId);
        blockLayout = false;
    }

    @Override
    public void setImageURI(Uri uri) {
        blockLayout = true;
        super.setImageURI(uri);
        blockLayout = false;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        blockLayout = true;
        super.setImageDrawable(drawable);
        blockLayout = false;
    }
}
