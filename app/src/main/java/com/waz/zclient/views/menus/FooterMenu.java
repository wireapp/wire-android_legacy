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
package com.waz.zclient.views.menus;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.utils.ViewUtils;

public class FooterMenu extends FrameLayout {
    private View leftActionContainerView;
    private TextView leftActionTextView;
    private TextView leftLabelTextView;

    private View rightActionContainerView;
    private TextView rightActionTextView;
    private TextView rightLabelTextView;

    public FooterMenu(Context context) {
        super(context);
        init();
    }

    public FooterMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        setCustomAttributes(context, attrs);
    }

    public FooterMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();

        setCustomAttributes(context, attrs);
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.ll__user_profile__footer, this, true);

        leftActionContainerView = ViewUtils.getView(this, R.id.ll__participants__left__action);
        leftActionTextView = ViewUtils.getView(this, R.id.gtv__participants__left__action);
        leftLabelTextView = ViewUtils.getView(this, R.id.ttv__participants__left_label);

        rightActionContainerView = ViewUtils.getView(this, R.id.ll__participants__right__action);
        rightActionTextView = ViewUtils.getView(this, R.id.gtv__participants__right__action);
        rightLabelTextView = ViewUtils.getView(this, R.id.ttv__participants__right_label);
    }

    private void setCustomAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.FooterMenu);

        String leftActionText = a.getString(R.styleable.FooterMenu_leftActionText);
        if (leftActionText != null) {
            setLeftActionText(leftActionText);
        }

        String rightActionText = a.getString(R.styleable.FooterMenu_rightActionText);
        if (rightActionText != null) {
            setRightActionText(rightActionText);
        }

        String leftActionLabelText = a.getString(R.styleable.FooterMenu_leftActionLabelText);
        if (leftActionLabelText != null) {
            setLeftActionLabelText(leftActionLabelText);
        }

        String rightActionLabelText = a.getString(R.styleable.FooterMenu_rightActionLabelText);
        if (rightActionLabelText != null) {
            setRightActionLabelText(rightActionLabelText);
        }
        a.recycle();
    }

    public void setLeftActionText(String text) {
        if (text == null || text.isEmpty()) {
            leftActionTextView.setText("");
            leftActionTextView.setVisibility(View.GONE);
        } else {
            leftActionTextView.setText(text);
            leftActionTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setLeftActionLabelText(String text) {
        if (text == null || text.isEmpty()) {
            leftLabelTextView.setText("");
            leftLabelTextView.setVisibility(View.GONE);
        } else {
            leftLabelTextView.setText(text);
            leftLabelTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setRightActionLabelText(String text) {
        if (text == null || text.isEmpty()) {
            rightLabelTextView.setText("");
            rightLabelTextView.setVisibility(View.GONE);
        } else {
            rightLabelTextView.setText(text);
            rightLabelTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setRightActionText(String text) {
        if (text == null || text.isEmpty()) {
            rightActionTextView.setText("");
            rightActionTextView.setVisibility(View.GONE);
        } else {
            rightActionTextView.setText(text);
            rightActionTextView.setVisibility(View.VISIBLE);
        }
    }

    public void setCallback(final FooterMenuCallback callback) {
        if (callback == null) {
            leftActionContainerView.setOnClickListener(null);
            rightActionContainerView.setOnClickListener(null);
        } else {
            leftActionContainerView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onLeftActionClicked();
                }
            });
            rightActionContainerView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    callback.onRightActionClicked();
                }
            });
        }
    }
}
