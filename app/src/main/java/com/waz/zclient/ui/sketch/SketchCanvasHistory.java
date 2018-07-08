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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.LinkedList;
import java.util.List;

class SketchCanvasHistory {

    private final LinkedList<HistoryItem> historyItems; //NOPMD

    SketchCanvasHistory() {
        historyItems = new LinkedList<>();
    }

    List<HistoryItem> getHistoryItems() {
        return historyItems;
    }

    int size() {
        return historyItems.size();
    }

    void clear() {
        historyItems.clear();
    }

    void draw(Canvas canvas) {
        Text lastText = getLastText();
        for (HistoryItem item : historyItems) {
            if (!(item instanceof Text) || item == lastText) {
                item.draw(canvas);
            }
        }
    }

    HistoryItem undo() {
        return historyItems.removeLast();
    }

    Text getLastText() {
        Text lastText = null;
        for (int i = historyItems.size() - 1; i >= 0; i--) {
            if (historyItems.get(i) instanceof Text) {
                lastText = (Text) historyItems.get(i);
                break;
            }
        }
        return lastText;
    }

    void hideText() {
        historyItems.add(new HiddenText());
    }

    void showText() {
        Text lastText = getLastText();
        if (lastText != null && lastText instanceof HiddenText) {
            historyItems.remove(lastText);
        }
    }

    void addText(Bitmap textBitmap, float x, float y, String text, float scale, Paint paint) {
        Text newTextHistoryItem;
        if (textBitmap == null) {
            newTextHistoryItem = new ErasedText();
        } else {
            newTextHistoryItem = new Text(textBitmap, x, y, paint, text, scale);
        }
        historyItems.add(newTextHistoryItem);
    }

    void addEmoji(String emoji, float currentX, float currentY, Paint paint) {
        historyItems.add(new Emoji(emoji, currentX, currentY, paint));
    }

    void addFillScreen(float width, float height, Paint paint) {
        historyItems.add(new FilledScreen(width, height, paint));
    }

    void addStroke(Path path, Paint paint, RectF bounds) {
        historyItems.add(new Stroke(path, paint, bounds));
    }

    interface HistoryItem {
        void draw(Canvas canvas);
    }

    class Stroke implements HistoryItem {
        public final Path path;
        public final Paint paint;
        private RectF bounds;

        private Stroke(Path path, Paint paint, RectF bounds) {
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

    class Emoji implements HistoryItem {
        public final float x;
        public final float y;
        public final String emoji;
        public final Paint paint;

        private Emoji(String emoji, float currentX, float currentY, Paint paint) {
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

    class Text implements HistoryItem {
        public final float x;
        public final float y;
        public final Bitmap bitmap;
        public final Paint paint;
        public final String text;
        public final float scale;

        private Text(Bitmap bitmap, float currentX, float currentY, Paint paint, String text, float scale) {
            this.bitmap = bitmap;
            this.x = currentX;
            this.y = currentY;
            this.paint = paint;
            this.text = text;
            this.scale = scale;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(bitmap, x, y, paint);
        }

        public void recycle() {
            bitmap.recycle();
        }
    }

    class HiddenText extends Text {
        private HiddenText() {
            super(null, 0, 0, new Paint(), "", 1.0f);
        }
        @Override
        public void draw(Canvas canvas) {
        }
    }

    class ErasedText extends Text {
        ErasedText() {
            super(null, 0, 0, new Paint(), "", 1.0f);
        }
        @Override
        public void draw(Canvas canvas) {
        }
    }

    class FilledScreen implements HistoryItem {
        public final float width;
        public final float height;
        public final Paint paint;

        private FilledScreen(float width, float height, Paint paint) {
            this.width = width;
            this.height = height;
            this.paint = paint;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRect(0, 0, width, height, paint);
        }
    }
}
