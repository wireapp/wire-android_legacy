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
package com.waz.zclient.ui.sketch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.waz.zclient.R;

public class DrawingCanvasView extends View {

    private Bitmap bitmap = null;
    private Bitmap backgroundBitmap;
    private Canvas canvas;
    private Path path;
    private Paint bitmapPaint;
    private Paint drawingPaint;
    private Paint emojiPaint;
    private Paint whitePaint;
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
    private final int textPadding = getResources().getDimensionPixelSize(R.dimen.wire__padding__regular);
    private String emoji;
    private boolean drawEmoji;

    private final SketchCanvasHistory canvasHistory;

    public enum Mode {
        SKETCH,
        TEXT,
        EMOJI
    }
    private Mode currentMode;

    public DrawingCanvasView(Context context) {
        this(context, null);
    }

    public DrawingCanvasView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawingCanvasView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        canvasHistory = new SketchCanvasHistory();
        init();
    }

    private void init() {
        path = new Path();
        bitmapConfig = Bitmap.Config.ARGB_8888;
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
        currentMode = Mode.SKETCH;

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
            // Fallback to non-alpha canvas if in memory trouble
            if (bitmapConfig == Bitmap.Config.ARGB_8888) {
                bitmapConfig = Bitmap.Config.RGB_565;
                clearBitmapSpace(w, h);
                bitmap = Bitmap.createBitmap(w, h, bitmapConfig);
                canvas = new Canvas(bitmap);
            }
        }
        redraw();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap != null) {
            canvas.drawColor(Color.TRANSPARENT);
            canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
            if (drawEmoji) {
                canvas.drawText(emoji, currentX, currentY, emojiPaint);
            } else {
                canvas.drawPath(path, drawingPaint);
            }
        }
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        if (bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return;
        }
        backgroundBitmap = bitmap;
        if (backgroundBitmap.getWidth() > backgroundBitmap.getHeight()) {
            isBackgroundBitmapLandscape = true;
        }
        drawBackgroundBitmap();
    }

    public void reset() {
        paintedOn(false);
        canvasHistory.clear();
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        drawBackgroundBitmap();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMode == Mode.TEXT) {
            return scaleGestureDetector.onTouchEvent(event);
        }
        if (longPressGestureDetector.onTouchEvent(event) && backgroundBitmap == null) {
            invalidate();
            return true;
        }
        final int whiteColor = ContextCompat.getColor(getContext(), R.color.draw_white);
        if (backgroundBitmap == null &&
            canvasHistory.size() == 0 &&
            drawingPaint.getColor() == whiteColor) {
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

    private final ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleListener());

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        float scaleFactor = 1f;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            if (drawingCanvasCallback != null) {
                drawingCanvasCallback.onScaleStart();
            }
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (drawingCanvasCallback != null) {
                drawingCanvasCallback.onScaleEnd();
            }
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            // Don't let the object get too small or too large.
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
            if (drawingCanvasCallback != null) {
                drawingCanvasCallback.onScaleChanged(scaleFactor);
            }
            return true;
        }
    }

    private final GestureDetector longPressGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
        public void onLongPress(MotionEvent e) {
            if (backgroundBitmap != null || currentMode != Mode.SKETCH) {
                return;
            }
            drawingPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), drawingPaint);
            canvasHistory.addFillScreen(bitmap.getWidth(), bitmap.getHeight(), new Paint(drawingPaint));
            paintedOn(true);
            drawingPaint.setStyle(Paint.Style.STROKE);
            invalidate();
        }
    });

    private void touch_start(float x, float y) {
        if (currentMode == Mode.SKETCH) {
            path.reset();
            path.moveTo(x, y);
            currentX = x;
            currentY = y;
        } else if (currentMode == Mode.EMOJI) {
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
            canvasHistory.addEmoji(emoji, currentX, currentY, new Paint(emojiPaint));
            paintedOn(true);
        } else {
            path.lineTo(currentX, currentY);
            canvas.drawPath(path, drawingPaint);
            if (touchMoved) {
                touchMoved = false;
                RectF bounds = new RectF();
                path.computeBounds(bounds, true);
                canvasHistory.addStroke(new Path(path), new Paint(drawingPaint), bounds);
            }
            path.reset();
        }
    }

    public Rect getImageTrimValues() {
        int top = bitmap.getHeight();
        int left = bitmap.getWidth();
        int right = 0;
        int bottom = 0;

        boolean checkLeftRight = true;
        boolean checkTopBottom = true;

        int bitmapTop = 0;
        int bitmapBottom = 0;
        int bitmapLeft = 0;
        int bitmapRight = 0;
        if (includeBackgroundImage) {
            if (isBackgroundBitmapLandscape) {
                left = 0;
                right = bitmap.getWidth();
                checkLeftRight = false;
                float ratio = (float) bitmap.getWidth() / backgroundBitmap.getWidth();

                bitmapTop = (int) (bitmap.getHeight() / 2 - ratio * backgroundBitmap.getHeight() / 2);
                top = bitmapTop;
                bitmapBottom = (int) (bitmap.getHeight() / 2 + ratio * backgroundBitmap.getHeight() / 2);
                bottom = bitmapBottom;
            } else {
                top = 0;
                bottom = bitmap.getHeight();
                checkTopBottom = false;

                float ratio = (float) canvas.getHeight() / backgroundBitmap.getHeight();
                int imageWidth = (int) (backgroundBitmap.getWidth() * ratio);

                bitmapLeft = bitmap.getWidth() / 2 - imageWidth / 2;
                left = bitmapLeft;
                bitmapRight = bitmap.getWidth() / 2 + imageWidth / 2;
                right = bitmapRight;
            }
        }

        for (SketchCanvasHistory.HistoryItem historyItem: canvasHistory.getHistoryItems()) {
            if (historyItem instanceof SketchCanvasHistory.FilledScreen) {
                top = 0;
                bottom = bitmap.getHeight();
                left = 0;
                right = bitmap.getWidth();
                break;
            } else if (historyItem instanceof SketchCanvasHistory.Stroke) {
                RectF bounds = ((SketchCanvasHistory.Stroke) historyItem).getBounds();
                if (checkTopBottom) {
                    top = Math.min(top, (int) bounds.top);
                    bottom = Math.max(bottom, (int) bounds.bottom);
                }
                if (checkLeftRight) {
                    left = Math.min(left, (int) bounds.left);
                    right = Math.max(right, (int) bounds.right);
                }
            } else if (historyItem instanceof SketchCanvasHistory.Emoji) {
                SketchCanvasHistory.Emoji emoji = (SketchCanvasHistory.Emoji) historyItem;
                if (checkTopBottom) {
                    top = Math.min(top, (int) (emoji.y - emoji.paint.getTextSize()));
                    bottom = Math.max(bottom, (int) (emoji.y));
                }
                if (checkLeftRight) {
                    left = Math.min(left, (int) emoji.x);
                    right = Math.max(right, (int) (emoji.x + emoji.paint.getTextSize()));
                }
            } else if (historyItem instanceof SketchCanvasHistory.Text) {
                SketchCanvasHistory.Text text = (SketchCanvasHistory.Text) historyItem;
                if (checkTopBottom) {
                    top = Math.min(top, (int) (text.y));
                    bottom = Math.max(bottom, (int) (text.y + (text.paint.getTextSize() + 2 * textPadding) * text.scale));
                }
                if (checkLeftRight) {
                    left = Math.min(left, (int) text.x);
                    right = Math.max(right, (int) (text.x + (text.paint.measureText(text.text) + 2 * textPadding) * text.scale));
                }
            }
        }
        int topTrimBuffer = trimBuffer;
        int bottomTrimBuffer = trimBuffer;
        int leftTrimBuffer = trimBuffer;
        int rightTrimBuffer = trimBuffer;
        if (includeBackgroundImage) {
            if (left >= bitmapLeft) {
                leftTrimBuffer = 0;
            }
            if (right <= bitmapRight) {
                rightTrimBuffer = 0;
            }
            if (top >= bitmapTop) {
                topTrimBuffer = 0;
            }
            if (bottom <= bitmapBottom) {
                bottomTrimBuffer = 0;
            }
        }
        return new Rect(Math.max(0, left - leftTrimBuffer),
                        Math.max(0, top - topTrimBuffer),
                        Math.min(bitmap.getWidth(), right + rightTrimBuffer),
                        Math.min(bitmap.getHeight(), bottom + bottomTrimBuffer));
    }

    public void setDrawingColor(int color) {
        drawingPaint.setColor(color);
        emojiPaint.setColor(color);
    }

    public void setStrokeSize(int strokeSize) {
        drawingPaint.setStrokeWidth(strokeSize);
    }

    public void setEmoji(String emoji, float size) {
        currentMode = Mode.EMOJI;
        this.emoji = emoji;
        emojiPaint.setTextSize(size);
    }

    public void setCurrentMode(Mode mode) {
        currentMode = mode;
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public boolean undo() {
        if (canvasHistory.size() == 0) {
            return false;
        }
        if (canvasHistory.size() == 1) {
            paintedOn(false);
        }
        SketchCanvasHistory.HistoryItem last = canvasHistory.undo();
        if (last instanceof SketchCanvasHistory.Text) {
            SketchCanvasHistory.Text newLastText = canvasHistory.getLastText();
            if (newLastText != null && newLastText.text != null) {
                drawingCanvasCallback.onTextChanged(newLastText.text, (int) newLastText.x, (int) newLastText.y, newLastText.scale);
            } else {
                drawingCanvasCallback.onTextRemoved();
            }
        }
        redraw();
        return true;
    }

    public void drawTextBitmap(Bitmap textBitmap, float x, float y, String text, float scale) {
        canvasHistory.addText(textBitmap, x, y, text, scale, bitmapPaint);
        redraw();
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

        RectF src;
        RectF dest;
        int horizontalMargin;
        int imageHeight;
        int imageWidth;

        if (isBackgroundBitmapLandscape) {
            horizontalMargin = 0;
            imageWidth = canvas.getWidth();
            imageHeight = canvas.getHeight();
            src = new RectF(0, 0, backgroundBitmap.getWidth(), backgroundBitmap.getHeight());
            dest = new RectF(0, 0, imageWidth, imageHeight);
        } else {
            float ratio = (float) canvas.getHeight() / backgroundBitmap.getHeight();
            imageWidth = (int) (backgroundBitmap.getWidth() * ratio);
            imageHeight = canvas.getHeight();
            horizontalMargin = (canvas.getWidth() / 2) - (imageWidth / 2);
            src = new RectF(0, 0, backgroundBitmap.getWidth() - 1, backgroundBitmap.getHeight() - 1);
            dest = new RectF(0, 0, imageWidth, imageHeight);
        }

        Matrix matrix = new Matrix();
        matrix.setRectToRect(src, dest, Matrix.ScaleToFit.CENTER);

        matrix.postTranslate(horizontalMargin, 0);

        canvas.drawBitmap(backgroundBitmap, matrix, null);
    }

    public void removeBackgroundBitmap() {
        includeBackgroundImage = false;
        redraw();
    }

    public boolean isEmpty() {
        return canvasHistory.size() == 0;
    }

    public void clearBitmapSpace(int width, int height) {
        bitmap = null;
        canvas = null;
        if (drawingCanvasCallback != null) {
            drawingCanvasCallback.reserveBitmapMemory(width, height);
        }
    }

    public void hideText() {
        canvasHistory.hideText();
        redraw();
    }

    public void showText() {
        canvasHistory.showText();
        redraw();
    }

    private void redraw() {
        canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), whitePaint);
        paintedOn(canvasHistory.size() > 0);
        if (includeBackgroundImage) {
            drawBackgroundBitmap();
        }
        drawHistory();
        invalidate();
    }

    private void drawHistory() {
        canvasHistory.draw(canvas);
    }

    public void onDestroy() {
        bitmap = null;
        backgroundBitmap = null;
        canvas = null;
        if (canvasHistory != null) {
            canvasHistory.clear();
        }
    }
}
