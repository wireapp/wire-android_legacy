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
package com.waz.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.nio.ByteBuffer;

@SuppressWarnings("deprecation")
public class Deprecated {
    public static final int MODE_WORLD_READABLE = Context.MODE_WORLD_READABLE;
    public static final int INFO_OUTPUT_BUFFERS_CHANGED = MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED;

    public static ByteBuffer[] inputBuffersOf(MediaCodec codec) {
        return codec.getInputBuffers();
    }

    public static ByteBuffer[] outputBuffersOf(MediaCodec codec) {
        return codec.getOutputBuffers();
    }

    public static int numberOfCodecs() {
        return MediaCodecList.getCodecCount();
    }

    public static MediaCodecInfo codecInfoAtIndex(int n) {
        return MediaCodecList.getCodecInfoAt(n);
    }

    public static Drawable getDrawable(Context context, int resId) {
        return context.getResources().getDrawable(resId);
    }
}
