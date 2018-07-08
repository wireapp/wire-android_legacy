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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.widget.ViewAnimator;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;

public class VoiceFilterContent extends ViewAnimator {
    private VoiceFilterGridLayout voiceFilterGridLayout;
    private VoiceFilterRecordingLayout voiceFilterRecordingLayout;

    public VoiceFilterRecordingLayout getVoiceFilterRecordingLayout() {
        return voiceFilterRecordingLayout;
    }

    public VoiceFilterGridLayout getVoiceFilterGridLayout() {
        return voiceFilterGridLayout;
    }

    public VoiceFilterContent(Context context) {
        this(context, null);
    }

    public VoiceFilterContent(Context context, AttributeSet attrs) {
        super(context, attrs);
        addRecordingLayout();
        addGridView();
    }


    private void addRecordingLayout() {
        voiceFilterRecordingLayout = (VoiceFilterRecordingLayout) LayoutInflater.from(getContext()).inflate(R.layout.voice_filter_control_record,
                                                                                                            this,
                                                                                                            false);
        addView(voiceFilterRecordingLayout);
    }

    private void addGridView() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        voiceFilterGridLayout = (VoiceFilterGridLayout) inflater.inflate(R.layout.voice_filter_grid, this, false);
        addView(voiceFilterGridLayout);
    }

    @Override
    public void setInAnimation(Animation inAnimation) {
        inAnimation.setStartOffset(getResources().getInteger(R.integer.camera__control__ainmation__in_delay));
        inAnimation.setInterpolator(new Expo.EaseOut());
        inAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setInAnimation(inAnimation);
    }

    @Override
    public void setOutAnimation(Animation outAnimation) {
        outAnimation.setInterpolator(new Expo.EaseIn());
        outAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setOutAnimation(outAnimation);
    }

    public void setAccentColor(int accentColor) {
        voiceFilterRecordingLayout.setAccentColor(accentColor);
        voiceFilterGridLayout.setAccentColor(accentColor);
    }


}
