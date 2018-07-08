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
package com.waz.zclient.pages.main.profile.views;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.support.annotation.LayoutRes;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.profile.validator.Validator;
import com.waz.zclient.ui.animation.HeightEvaluator;
import com.waz.zclient.ui.text.TypefaceEditText;
import com.waz.zclient.utils.ViewUtils;

public class GuidedEditText extends LinearLayout {

    TypefaceEditText editText;
    TextView guidanceText;
    View errorDot;
    private Validator validator;
    private int guidanceHeight;
    private boolean isMessageShown;
    private boolean isErrorDotShown;
    private boolean validateOnFocusChange = true;

    public GuidedEditText(Context context) {
        this(context, null);
    }

    public GuidedEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidedEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOrientation(VERTICAL);
    }

    public void setEditable(boolean editable) {
        editText.setClickable(editable);
        editText.setFocusable(editable);
        editText.setEnabled(editable);
    }

    public void setText(String text) {
        if (TextUtils.isEmpty(text)) {
            editText.setText("");
            return;
        }
        editText.setText(text);
        editText.setSelection(editText.getText().length());
    }

    public String getText() {
        return editText.getText().toString();
    }

    public void clearFocus() {
        editText.clearFocus();
    }

    public void showGuidance(boolean show) {
        showDot(show);
    }

    public void showMessage(boolean show) {
        // what I want already is
        if (isMessageShown == show) {
            return;
        }
        isMessageShown = show;
        int duration = getResources().getInteger(R.integer.profile__guidance__animation__duration);
        if (show) {
            guidanceText.setVisibility(VISIBLE);
            ValueAnimator.ofObject(new HeightEvaluator(guidanceText), 0, guidanceHeight).setDuration(duration).start();
        } else {
            guidanceText.setVisibility(GONE);
            ValueAnimator.ofObject(new HeightEvaluator(guidanceText), guidanceHeight, 0).setDuration(getResources().getInteger(R.integer.profile__guidance__animation__duration)).start();
        }
    }

    public void showDot(boolean show) {
        if (isErrorDotShown == show) {
            return;
        }
        isErrorDotShown = show;
        int duration = getResources().getInteger(R.integer.profile__guidance__animation__duration);
        if (show) {
            ObjectAnimator.ofFloat(errorDot, View.ALPHA, 0, 1).setDuration(duration).start();
        } else {
            ObjectAnimator.ofFloat(errorDot, View.ALPHA, 1, 0).setDuration(duration).start();
        }
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (guidanceText == null) {
            return;
        }
        guidanceText.measure(widthMeasureSpec, heightMeasureSpec);
        guidanceHeight = guidanceText.getMeasuredHeight();
    }

    /**
     * inflate in place
     */
    public void setResource(@LayoutRes int resourceId) {
        removeAllViews();

        View view = LayoutInflater.from(getContext()).inflate(resourceId, this, true);

        editText = ViewUtils.getView(view, R.id.tet__profile__guided);
        guidanceText = ViewUtils.getView(view, R.id.ttv__profile__guidance);
        errorDot = ViewUtils.getView(view, R.id.v__self_user__guided__dot);

        editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!validateOnFocusChange) {
                    return;
                }
                if (!hasFocus) {
                    if (!validator.validate(getText())) {
                        showGuidance(true);
                    }
                } else {
                    showMessage(false);
                }
            }
        });

        editText.addTextChangedListener(new OnTextChangedListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (validator.invalidate(s.toString())) {
                    showGuidance(false);
                }
            }
        });

    }

    public TypefaceEditText getEditText() {
        return editText;
    }

    public boolean onlyValidate() {
        return validator.validate(editText.getText().toString().trim());
    }

    public void invalidate() {
        showGuidance(false);
    }

    public void setAccentColor(int accentColor) {
        editText.setAccentColor(accentColor);
    }

    public void setSelection(int selection) {
        editText.setSelection(selection);
    }

}
