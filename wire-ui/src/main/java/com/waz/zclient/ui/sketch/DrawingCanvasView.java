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
package com.waz.zclient.ui.sketch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import com.waz.zclient.ui.R;
import com.waz.zclient.utils.SquareOrientation;
import net.hockeyapp.android.ExceptionHandler;

import java.util.LinkedList;

public class DrawingCanvasView extends View {

    private Bitmap bitmap;
    private Bitmap backgroundBitmap;
    private Canvas canvas;
    private Path path;
    private Paint bitmapPaint;
    private Paint drawingPaint;
    private Paint emojiPaint;
    private Paint whitePaint;
    private SquareOrientation backgroundImageRotation = SquareOrientation.NONE;
    private DrawingCanvasCallback drawingCanvasCallback;

    //used for drawing path
    private float currentX;
    private float currentY;

    private boolean includeBackgroundImage;
    private boolean isBackgroundBitmapLandscape = false;
    private boolean isPaintedOn = false;
    private boolean touchMoved = false;
    private static final float TOUCH_TOLERANCE = 2;
    private Bitmap.Config bitmapConfig;

    private int trimBuffer;
    private final int defaultStrokeWidth = getResources().getDimensionPixelSize(R.dimen.color_picker_small_dot_radius) * 2;
    private String emoji;
    private boolean drawEmoji;

    private LinkedList<HistoryItem> historyItems; // NOPMD

    public DrawingCanvasView(Context context) {
        this(context, null);
    }

    public DrawingCanvasView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawingCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        path = new Path();
        bitmapConfig = Bitmap.Config.ARGB_8888;
        historyItems = new LinkedList<>();
        bitmapPaint = new Paint(Paint.DITHER_FLAG);
        drawingPaint = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        drawingPaint.setColor(Color.BLACK);
        drawingPaint.setStyle(Paint.Style.STROKE);
        drawingPaint.setStrokeJoin(Paint.Join.ROUND);
        drawingPaint.setStrokeCap(Paint.Cap.ROUND);
        drawingPaint.setStrokeWidth(defaultStrokeWidth);
        whitePaint = new Paint(Paint.DITHER_FLAG);
        whitePaint.setColor(Color.WHITE);
        emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setStrokeWidth(1);
        emoji = null;

        trimBuffer = getResources().getDimensionPixelSize(R.dimen.draw_image_trim_buffer);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        try {
            clearBitmapSpace(w, h);
            bitmap = Bitmap.createBitmap(w, h, bitmapConfig);
            canvas = new Canvas(bitmap);
        } catch (OutOfMemoryError outOfMemoryError) {
            ExceptionHandler.saveException(outOfMemoryError, null);
            // Fallback to non-alpha canvas if in memory trouble
            if (bitmapConfig == Bitmap.Config.ARGB_8888) {
                bitmapConfig = Bitmap.Config.RGB_565;
                clearBitmapSpace(w, h);
                bitmap = Bitmap.createBitmap(w, h, bitmapConfig);
                canvas = new Canvas(bitmap);
            }
        }
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        //needed for tablet view switching
        drawBackgroundBitmap();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT);
        canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
        if (drawEmoji) {
            canvas.drawText(emoji, currentX, currentY, emojiPaint);
        } else {
            canvas.drawPath(path, drawingPaint);
        }
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        if (bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return;
        }
        backgroundBitmap = bitmap;
        if (backgroundBitmap.getWidth() > backgroundBitmap.getHeight()) {
            // Flip the image in landscape
            isBackgroundBitmapLandscape = true;
            switch (backgroundImageRotation) {
                case LANDSCAPE_RIGHT:
                    backgroundImageRotation = SquareOrientation.LANDSCAPE_RIGHT;
                    break;
                case LANDSCAPE_LEFT:
                    backgroundImageRotation = SquareOrientation.LANDSCAPE_LEFT;
                    break;
                default:
                    //if we dont have a side, set one
                    backgroundImageRotation = SquareOrientation.LANDSCAPE_LEFT;
                    break;
            }
        }
        drawBackgroundBitmap();
    }

    public void reset() {
        paintedOn(false);
        historyItems.clear();
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        drawBackgroundBitmap();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event) && backgroundBitmap == null) {
            invalidate();
            return true;
        }
        if (backgroundBitmap == null &&
            historyItems.isEmpty() &&
            drawingPaint.getColor() == getResources().getColor(R.color.draw_white)) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(x, y);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up();
                invalidate();
                break;
        }
        return true;
    }

    private final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        public void onLongPress(MotionEvent e) {
            if (backgroundBitmap != null) {
                return;
            }
            drawingPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), drawingPaint);
            historyItems.add(new FilledScreen(bitmap.getWidth(), bitmap.getHeight(), new Paint(drawingPaint)));
            paintedOn(true);
            drawingPaint.setStyle(Paint.Style.STROKE);
            invalidate();
        }
    });

    private void touch_start(float x, float y) {
        if (emoji == null) {
            path.reset();
            path.moveTo(x, y);
            currentX = x;
            currentY = y;
        } else {
            drawEmoji = true;
            currentX = x - emojiPaint.getTextSize() / 2;
            currentY = y;
        }
    }

    private void touch_move(float x, float y) {
        float dx = Math.abs(x - currentX);
        float dy = Math.abs(y - currentY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            if (drawEmoji) {
                currentX = x - emojiPaint.getTextSize() / 2;
                currentY = y;
            } else {
                path.quadTo(currentX, currentY, (x + currentX) / 2, (y + currentY) / 2);
                currentX = x;
                currentY = y;
            }
            paintedOn(true);
            touchMoved = true;
        }
    }

    private void touch_up() {
        if (drawEmoji) {
            drawEmoji = false;
            canvas.drawText(emoji, currentX, currentY, emojiPaint);
            historyItems.add(new Emoji(emoji, currentX, currentY, new Paint(emojiPaint)));
            paintedOn(true);
        } else {
            path.lineTo(currentX, currentY);
            canvas.drawPath(path, drawingPaint);
            if (touchMoved) {
                touchMoved = false;
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                historyItems.add(new Stroke(new Path(path), new Paint(drawingPaint), bounds));
            }
            path.reset();
        }
    }

    public int getTopTrimValue(boolean isLandscape) {
        if (includeBackgroundImage) {
            return 0;
        }

        int topTrimValue = isLandscape ? bitmap.getWidth() : bitmap.getHeight();

        for (HistoryItem historyItem: historyItems) {
            if (historyItem instanceof FilledScreen) {
                topTrimValue = 0;
                break;
            } else if (historyItem instanceof Stroke) {
                RectF bounds = ((Stroke) historyItem).getBounds();
                if (isLandscape) {
                    topTrimValue = Math.min(topTrimValue, (int) bounds.left);
                } else {
                    topTrimValue = Math.min(topTrimValue, (int) bounds.top);
                }
            } else if (historyItem instanceof Emoji) {
                Emoji emoji = (Emoji) historyItem;
                topTrimValue = (int) Math.min(topTrimValue, emoji.y - emoji.paint.getTextSize());
            }
        }
        return Math.max(topTrimValue - trimBuffer, 0);
    }

    public int getBottomTrimValue(boolean isLandscape) {
        if (includeBackgroundImage) {
            return isLandscape ? bitmap.getWidth() : bitmap.getHeight();
        }
        int bottomTrimValue = 0;
        for (HistoryItem historyItem: historyItems) {
            if (historyItem instanceof FilledScreen) {
                bottomTrimValue = isLandscape ? bitmap.getWidth() : bitmap.getHeight();
                break;
            } else if (historyItem instanceof Stroke) {
                RectF bounds = ((Stroke) historyItem).getBounds();
                if (isLandscape) {
                    bottomTrimValue = Math.max(bottomTrimValue, (int) bounds.right);
                } else {
                    bottomTrimValue = Math.max(bottomTrimValue, (int) bounds.bottom);
                }
            } else if (historyItem instanceof Emoji) {
                bottomTrimValue = (int) Math.max(bottomTrimValue, ((Emoji) historyItem).y);
            }
        }
        return Math.min(bottomTrimValue + trimBuffer, isLandscape ? bitmap.getWidth() : bitmap.getHeight());
    }

    public boolean isBackgroundImageLandscape() {
        return isBackgroundBitmapLandscape;
    }

    public void setDrawingColor(int color) {
        drawingPaint.setColor(color);
        emoji = null;
    }

    public void setStrokeSize(int strokeSize) {
        drawingPaint.setStrokeWidth(strokeSize);
    }

    public void setEmoji(String emoji, float size) {
        this.emoji = emoji;
        emojiPaint.setTextSize(size);
    }

    public boolean undo() {
        if (historyItems.size() == 0) {
            return false;
        }
        if (historyItems.size() == 1) {
            paintedOn(false);
        }
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        historyItems.removeLast();
        if (includeBackgroundImage) {
            drawBackgroundBitmap();
        }
        for (HistoryItem item : historyItems) {
            item.draw(canvas);
        }
        invalidate();
        return true;
    }

    private void paintedOn(boolean isPaintedOn) {
        if (this.isPaintedOn == isPaintedOn) {
            return;
        }
        this.isPaintedOn =  isPaintedOn;
        if (isPaintedOn) {
            drawingCanvasCallback.drawingAdded();
        } else {
            drawingCanvasCallback.drawingCleared();
        }
    }

    public void setDrawingCanvasCallback(DrawingCanvasCallback drawingCanvasCallback) {
        this.drawingCanvasCallback = drawingCanvasCallback;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void drawBackgroundBitmap() {
        if (backgroundBitmap == null || canvas == null) {
            return;
        }
        includeBackgroundImage = true;
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);

        RectF src;
        RectF dest;
        int horizontalMargin;
        int imageHeight;
        int imageWidth;
        float ratio;

        if (isBackgroundBitmapLandscape) {
            ratio = (float) canvas.getHeight() / backgroundBitmap.getWidth();
            imageHeight = canvas.getHeight();
            imageWidth = (int) (backgroundBitmap.getHeight() * ratio);
            horizontalMargin = (canvas.getWidth() / 2) - (imageWidth / 2);
            src = new RectF(0, 0, backgroundBitmap.getHeight() - 1, backgroundBitmap.getWidth() - 1);
            dest = new RectF(0, 0, imageWidth, imageHeight);
        } else {
            ratio = (float) canvas.getHeight() / backgroundBitmap.getHeight();
            imageWidth = (int) (backgroundBitmap.getWidth() * ratio);
            imageHeight = canvas.getHeight();
            horizontalMargin = (canvas.getWidth() / 2) - (imageWidth / 2);
            src = new RectF(0, 0, backgroundBitmap.getWidth() - 1, backgroundBitmap.getHeight() - 1);
            dest = new RectF(0, 0, imageWidth, imageHeight);
        }

        Matrix matrix = new Matrix();
        matrix.setRectToRect(src, dest, Matrix.ScaleToFit.CENTER);

        if (isBackgroundBitmapLandscape) {
            matrix.postTranslate(-imageHeight / 2, -imageWidth / 2); // Centers image
            matrix.postRotate(-backgroundImageRotation.orientation);
            matrix.postTranslate(imageWidth / 2, imageHeight / 2);
        }
        matrix.postTranslate(horizontalMargin, 0);

        canvas.drawBitmap(backgroundBitmap, matrix, null);
        for (HistoryItem item : historyItems) {
            item.draw(canvas);
        }
        drawingCanvasCallback.setRotation(backgroundImageRotation.orientation);
        invalidate();
    }

    public void removeBackgroundBitmap() {
        includeBackgroundImage = false;
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        for (HistoryItem item : historyItems) {
            item.draw(canvas);
        }
        invalidate();
    }

    public void setConfigOrientation(SquareOrientation configOrientation) {
        if (configOrientation.equals(backgroundImageRotation) || !isBackgroundBitmapLandscape || backgroundBitmap == null) {
            return;
        }
        if (configOrientation == SquareOrientation.LANDSCAPE_LEFT || configOrientation == SquareOrientation.LANDSCAPE_RIGHT) {
            this.backgroundImageRotation = configOrientation;
            if (includeBackgroundImage) {
                drawBackgroundBitmap();
            }
        }
    }

    public boolean isEmpty() {
        return historyItems.size() == 0;
    }

    public void clearBitmapSpace(int width, int height) {
        bitmap = null;
        canvas = null;
        if (drawingCanvasCallback != null) {
            drawingCanvasCallback.reserveBitmapMemory(width, height);
        }
    }

    private class Stroke implements HistoryItem {
        private final Path path;
        private final Paint paint;
        private RectF bounds;

        Stroke(Path path, Paint paint, RectF bounds) {
            this.path = path;
            this.paint = paint;
            this.bounds = bounds;
        }

        public RectF getBounds() {
            return bounds;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }

    private class Emoji implements HistoryItem {
        private final float x;
        private final float y;
        private final String emoji;
        private final Paint paint;

        Emoji(String emoji, float currentX, float currentY, Paint paint) {
            this.emoji = emoji;
            this.x = currentX;
            this.y = currentY;
            this.paint = paint;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawText(emoji, x, y, paint);
        }
    }

    private class FilledScreen implements HistoryItem {
        private final float width;
        private final float height;
        private final Paint paint;

        FilledScreen(float width, float height, Paint paint) {
            this.width = width;
            this.height = height;
            this.paint = paint;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRect(0, 0, width, height, paint);
        }
    }

    public void onDestroy() {
        bitmap = null;
        backgroundBitmap = null;
        canvas = null;
        if (historyItems != null) {
            historyItems.clear();
            historyItems = null;
        }
    }

    private interface HistoryItem {
        void draw(Canvas canvas);
    }

    public interface DrawingCanvasCallback {
        void drawingAdded();

        void drawingCleared();

        void setRotation(int rotation);

        void reserveBitmapMemory(int width, int height);
    }

}
