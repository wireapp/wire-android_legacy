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
package com.waz.zclient.pages.extendedcursor.voicefilter;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.waz.api.AudioOverview;
import com.waz.zclient.R;

public class WaveBinView extends View implements VoiceFilterController.PlaybackObserver {
    private static final int MAX_NUM_OF_LEVELS = 56;
    private long duration;
    private long currentHead;


    private Paint activePaint;
    private Paint inactivePaint;
    private float[] levels;
    private int binWidth;
    private int binSpaceWidth;

    public void setVoiceFilterController(VoiceFilterController voiceFilterController) {
        voiceFilterController.addPlaybackObserver(this);
    }

    public void setAccentColor(int accentColor) {
        activePaint.setColor(accentColor);
    }

    public WaveBinView(Context context) {
        this(context, null);
    }

    public WaveBinView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveBinView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(Color.BLUE);
        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(Color.WHITE);
        binWidth = getResources().getDimensionPixelSize(R.dimen.wave_graph_bin_width);
        binSpaceWidth = getResources().getDimensionPixelSize(R.dimen.wave_graph_bin_space_width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (levels == null) {
            return;
        }

        int size = levels.length;
        int totalBinWidth = size * binWidth + (size - 1) * binSpaceWidth;

        int height = canvas.getHeight();
        int width = canvas.getWidth();

        int currentX = (width - totalBinWidth) / 2;

        int breakPoint = (int) (MAX_NUM_OF_LEVELS * currentHead * 1.0f / duration);

        for (int i = 0; i < breakPoint; i++) {
            if (i > MAX_NUM_OF_LEVELS - 1) {
                return;
            }
            float lh = levels[i] * height;
            if (lh < binWidth) {
                lh = binWidth;
            }
            float top = (height - lh) / 2;

            canvas.drawRect(currentX, top, currentX + binWidth, top + lh, activePaint);
            currentX += binWidth + binSpaceWidth;
        }

        for (int i = breakPoint; i < MAX_NUM_OF_LEVELS; i++) {
            float lh = levels[i] * height;
            if (lh < binWidth) {
                lh = binWidth;
            }
            float top = (height - lh) / 2;


            canvas.drawRect(currentX, top, currentX + binWidth, top + lh, inactivePaint);
            currentX += binWidth + binSpaceWidth;
        }
    }

    @Override
    public void onPlaybackStopped(long seconds) {

    }

    @Override
    public void onPlaybackStarted(AudioOverview overview) {
        levels = overview.getLevels(MAX_NUM_OF_LEVELS);
    }

    @Override
    public void onPlaybackProceeded(long current, long total) {
        this.currentHead = current;
        this.duration = total;
        invalidate();
    }
}
