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
package com.waz.zclient.pages.main.connectivity;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.zclient.R;

public class ConnectivityIndicatorView extends FrameLayout {

    private View contentView;
    private float removedPosition;
    private float collapsedPosition;

    private final int animationDuration;
    private final int messageShowDuration;
    private OnExpandListener onExpandListener;

    public ConnectivityIndicatorView(Context context) {
        this(context, null);
    }

    public ConnectivityIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ConnectivityIndicatorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        animationDuration = getContext().getResources().getInteger(R.integer.network_indicator__animation_duration);
        messageShowDuration = getContext().getResources().getInteger(R.integer.network_indicator__show_expanded__duration);

        init();
    }

    private void init() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        contentView = inflater.inflate(R.layout.connectivity_indicator, this, true);
        removedPosition = -getContext().getResources().getDimension(R.dimen.network_indicator__expanded_height);
        collapsedPosition = -getContext().getResources().getDimension(R.dimen.network_indicator__collapse_position);

        contentView.setY(removedPosition);
    }

    public void show() {
        contentView.animate().y(0)
                   .setDuration(animationDuration)
                   .setStartDelay(0)
                   .withStartAction(new Runnable() {
                       @Override
                       public void run() {
                           onExpandListener.onExpandBegin(animationDuration);
                       }
                   })
                   .withEndAction(new Runnable() {
                       @Override
                       public void run() {
                           collapse(messageShowDuration);
                       }
                   })
                   .start();
    }

    public void hide() {
        contentView.animate()
                   .y(removedPosition)
                   .setStartDelay(0)
                   .withStartAction(new Runnable() {
                       @Override
                       public void run() {
                           onExpandListener.onHideBegin(animationDuration);
                       }
                   })
                   .setDuration(animationDuration)
                   .start();
    }

    private void collapse(long startDelay) {
        contentView.animate()
                   .y(collapsedPosition)
                   .setStartDelay(startDelay)
                   .withStartAction(new Runnable() {
                       @Override
                       public void run() {
                           onExpandListener.onCollapseBegin(animationDuration);
                       }
                   })
                   .setDuration(animationDuration)
                   .start();
    }

    public void setOnExpandListener(OnExpandListener onExpandListener) {
        this.onExpandListener = onExpandListener;
    }

    public interface OnExpandListener {
        void onExpandBegin(long animationDuration);
        void onCollapseBegin(long animationDuration);
        void onHideBegin(long animationDuration);
    }
}
