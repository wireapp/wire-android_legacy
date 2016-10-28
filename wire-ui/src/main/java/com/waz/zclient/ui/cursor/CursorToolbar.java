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
package com.waz.zclient.ui.cursor;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.waz.zclient.ui.R;
import com.waz.zclient.ui.utils.CursorUtils;
import com.waz.zclient.ui.views.CursorIconButton;

import java.util.List;

public class CursorToolbar extends LinearLayout {

    private Callback callback;

    private int buttonWidth;
    private View touchedButtonContainer;
    private GestureDetectorCompat detector;
    private List<CursorMenuItem> cursorItems;

    private CursorIconButton cursorIconButtonCamera;
    private CursorIconButton cursorIconButtonAudio;

    public void setAccentColor(int accentColor) {
        cursorIconButtonCamera.initTextColor(accentColor);
        cursorIconButtonAudio.initTextColor(accentColor);
    }

    public void showEphemeralMode(int color) {
        for (int i = 0; i < getChildCount(); i++) {
            FrameLayout containerView = (FrameLayout) getChildAt(i);
            CursorIconButton cursorIconButton = (CursorIconButton) containerView.getChildAt(0);
            if (cursorIconButton.getCursorMenuItem() != CursorMenuItem.MORE &&
                cursorIconButton.getCursorMenuItem() != CursorMenuItem.LESS) {
                cursorIconButton.showEphemeralMode(color);
            }
        }
    }

    public void hideEphemeraMode(int color) {
        for (int i = 0; i < getChildCount(); i++) {
            FrameLayout containerView = (FrameLayout) getChildAt(i);
            CursorIconButton cursorIconButton = (CursorIconButton) containerView.getChildAt(0);
            if (cursorIconButton.getCursorMenuItem() != CursorMenuItem.MORE &&
                cursorIconButton.getCursorMenuItem() != CursorMenuItem.LESS) {
                cursorIconButton.hideEphemeralMode(color);
            }
        }
    }


    private GestureDetector.OnGestureListener gestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (callback != null) {
                CursorMenuItem item = (CursorMenuItem) touchedButtonContainer.getTag();
                if (item != CursorMenuItem.AUDIO_MESSAGE &&
                    item != CursorMenuItem.DUMMY) {
                    callback.onShowTooltip(item, getResources().getString(item.resTooltip), touchedButtonContainer);
                }
                callback.onCursorButtonLongPressed(item);
            }

        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (callback != null) {
                CursorMenuItem item = (CursorMenuItem) touchedButtonContainer.getTag();
                callback.onCursorButtonClicked(item);

                switch (item) {
                    case CAMERA:
                        cursorIconButtonCamera.setSelected(true);
                        cursorIconButtonAudio.setSelected(false);
                        break;
                    case AUDIO_MESSAGE:
                        cursorIconButtonCamera.setSelected(false);
                        cursorIconButtonAudio.setSelected(true);
                        break;
                }
            }
            return true;
        }
    };

    public CursorToolbar(Context context) {
        this(context, null);
    }

    public CursorToolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        detector = new GestureDetectorCompat(getContext(), gestureListener);

        setOrientation(LinearLayout.HORIZONTAL);

        buttonWidth = getResources().getDimensionPixelSize(R.dimen.new_cursor_menu_button_width);
    }

    public void setCursorItems(List<CursorMenuItem> cursorItems) {
        this.cursorItems = cursorItems;
        createItems();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void createItems() {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        int diameter = getResources().getDimensionPixelSize(R.dimen.cursor__menu_button__diameter);
        int rightMargin;

        /*
            As long as 4 mainItems are shown in the toolbar, they are spread out equally in phone
         */
        rightMargin = CursorUtils.getMarginBetweenCursorButtons(getContext());

        CursorIconButton cursorIconButton;

        for (int i = 0; i < cursorItems.size(); i++) {
            final CursorMenuItem item = cursorItems.get(i);
            final FrameLayout buttonContainer = new FrameLayout(getContext());
            buttonContainer.setId(item.resId);

            cursorIconButton = (CursorIconButton) inflater.inflate(R.layout.cursor__item,
                                                                   this,
                                                                   false);
            cursorIconButton.setCursorMenuItem(item);
            cursorIconButton.setPressedBackgroundColor(ContextCompat.getColor(getContext(), R.color.light_graphite));

            switch (item) {
                case CAMERA:
                    cursorIconButtonCamera = cursorIconButton;
                    break;
                case AUDIO_MESSAGE:
                    cursorIconButtonAudio = cursorIconButton;
                    break;
            }

            if (item == CursorMenuItem.DUMMY) {
                cursorIconButton.initTextColor(ContextCompat.getColor(getContext(), R.color.transparent));
                cursorIconButton.setPressedBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
                cursorIconButton.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
            }

            buttonContainer.setTag(item);

            buttonContainer.setLongClickable(true);


            buttonContainer.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    CursorMenuItem item = (CursorMenuItem) view.getTag();
                    touchedButtonContainer = buttonContainer;
                    if (callback != null &&
                        item == CursorMenuItem.AUDIO_MESSAGE) {
                        callback.onMotionEvent(item, motionEvent);
                    }
                    detector.onTouchEvent(motionEvent);
                    return false;
                }
            });

            FrameLayout.LayoutParams paramsButton = new FrameLayout.LayoutParams(diameter, diameter);
            paramsButton.gravity = Gravity.CENTER;
            buttonContainer.addView(cursorIconButton, paramsButton);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonWidth,
                                                                             ViewGroup.LayoutParams.MATCH_PARENT);
            if (i < cursorItems.size() - 1) {
                params.rightMargin = rightMargin;
            }
            addView(buttonContainer, params);
        }
    }

    public void unselectItems() {
        cursorIconButtonCamera.setSelected(false);
        cursorIconButtonAudio.setSelected(false);
    }

    public interface Callback {

        void onCursorButtonClicked(CursorMenuItem cursorMenuItem);

        void onCursorButtonLongPressed(CursorMenuItem cursorMenuItem);

        void onMotionEvent(CursorMenuItem cursorMenuItem, MotionEvent motionEvent);

        void onShowTooltip(CursorMenuItem item, String message, View anchor);
    }
}
