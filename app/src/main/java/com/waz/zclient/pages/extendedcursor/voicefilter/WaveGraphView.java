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
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import com.waz.zclient.R;

public class WaveGraphView extends View {
    private final Path path;
    private int waveColor;
    private final float frequency;
    private float amplitude;
    private final float idleAmplitude;
    private final float phaseShift;
    private final double density;
    private float[] levels;

    private static float kDefaultFrequency = 1.5f;
    private static float kDefaultAmplitude = 1.0f;
    private static float kDefaultIdleAmplitude = 0.01f;
    private static int kDefaultNumberOfWaves = 5;
    private static float kDefaultPhaseShift = -0.25f;
    private static float kDefaultDensity = 5.0f;

    private Paint paint;
    private int numberOfWaves;
    private float phase;


    public void setAccentColor(int accentColor) {
        waveColor = accentColor;
    }

    public WaveGraphView(Context context) {
        this(context, null);
    }

    public WaveGraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.waveColor = Color.WHITE;

        this.frequency = kDefaultFrequency;

        this.amplitude = kDefaultAmplitude;
        this.idleAmplitude = kDefaultIdleAmplitude;

        this.numberOfWaves = kDefaultNumberOfWaves;
        this.phaseShift = -kDefaultPhaseShift;
        this.density = kDefaultDensity;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.wire__divider__height));
        paint.setStyle(Paint.Style.STROKE);
        path = new Path();
    }

    public void setLevels(float[] levels) {
        this.levels = levels;
        invalidate();

        phase += phaseShift;
        float newAmplitude = Math.max(levels[0], idleAmplitude);
        amplitude = (amplitude * 2 + newAmplitude) / 3;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (levels == null) {
            return;
        }

        // We draw multiple sinus waves, with equal phases but altered amplitudes, multiplied by a parable function.
        for (int i = 0; i < numberOfWaves; i++) {
            path.reset();

            float halfHeight = canvas.getHeight() / 2.0f;
            int width = canvas.getWidth();
            float mid = width / 2.0f;

            float maxAmplitude = halfHeight - 4.0f; // 4 corresponds to twice the stroke width

            // Progress is a value between 1.0 and -0.5, determined by the current wave idx, which is used to alter the wave's amplitude.
            float progress = 1.0f - (float) i / numberOfWaves;
            float normedAmplitude = (1.5f * progress - 0.5f) * amplitude;

            path.moveTo(0, halfHeight);
            for (double x = density; x < width + this.density; x += this.density) {
                // We use a parable to scale the sinus wave, that has its peak in the middle of the view.
                float scaling = (float) (-Math.pow(1.0f / mid * (x - mid), 2f) + 1);
                float y = (float) (scaling * maxAmplitude * normedAmplitude * Math.sin(2f * Math.PI * (x / width) * this.frequency + this.phase) + halfHeight);
                path.lineTo((float) x, y);
            }

            float multiplier = Math.min(1.0f, (progress / 3.0f * 2.0f) + (1.0f / 3.0f));
            int alpha = Color.alpha(waveColor);
            int newAlpha = (int) (multiplier * alpha);
            int color = Color.argb(newAlpha, Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor));
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }
}
