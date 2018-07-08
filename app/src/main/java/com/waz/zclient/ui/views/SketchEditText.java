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
package com.waz.zclient.ui.views;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.EditText;

import com.waz.zclient.R;
import com.waz.zclient.ui.utils.TypefaceUtils;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class SketchEditText extends EditText {

    public Set<SketchEditTextListener> weakListenerSet;
    private String customHint;
    private int textFontId;
    private int hintFontId;
    private float regularTextSize;
    private float hintTextSize;
    private float sketchScale;
    private int regularHorizontalPadding;
    private int hintHorizontalPadding;
    private int regularVerticalPadding;
    private int hintVerticalPadding;
    private float mediumRegularTextSize;
    private float mediumHintTextSize;
    private float mediumPaddingSize;

    public SketchEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        weakListenerSet = Collections.newSetFromMap(
            new WeakHashMap<SketchEditTextListener, Boolean>());
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                updateField();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mediumRegularTextSize = getResources().getDimensionPixelSize(R.dimen.wire__text_size__regular);
        mediumHintTextSize = getResources().getDimensionPixelSize(R.dimen.wire__text_size__small);
        mediumPaddingSize = getResources().getDimensionPixelSize(R.dimen.wire__padding__regular);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        notifyListeners();
    }

    public void addListener(SketchEditTextListener listener) {
        weakListenerSet.add(listener);
    }

    public void removeListener(SketchEditTextListener listener) {
        weakListenerSet.remove(listener);
    }

    public void notifyListeners() {
        for (SketchEditTextListener listener : weakListenerSet) {
            listener.editTextChanged();
        }
    }

    private void updateField() {
        int textLength = getText().length();
        if (textLength > 0) {
            setHint("");
            setTypeface(TypefaceUtils.getTypeface(getContext().getString(textFontId)));
            setTextSize(TypedValue.COMPLEX_UNIT_PX, regularTextSize);
            setPadding(regularHorizontalPadding, regularVerticalPadding, regularHorizontalPadding, regularVerticalPadding);
        } else {
            setHint(customHint);
            setTypeface(TypefaceUtils.getTypeface(getContext().getString(hintFontId)));
            setTextSize(TypedValue.COMPLEX_UNIT_PX, hintTextSize);
            setPadding(hintHorizontalPadding, hintVerticalPadding, hintHorizontalPadding, hintVerticalPadding);
        }
    }

    public void setCustomHint(String customHint) {
        this.customHint = customHint;
        if (getText().length() == 0) {
            setHint(customHint);
        }
    }

    public void setTextFontId(int textFontId) {
        this.textFontId = textFontId;
        updateField();
    }

    public void setHintFontId(int hintFontId) {
        this.hintFontId = hintFontId;
        updateField();
    }

    public void setRegularTextSize(float textSize) {
        this.regularTextSize = textSize;
        updateField();
    }

    public float getHintTextSize() {
        return hintTextSize;
    }

    public void setHintTextSize(float hintSize) {
        this.hintTextSize = hintSize;
        updateField();
    }

    private void setRegularPaddingSize(int horizontal, int vertical) {
        this.regularVerticalPadding = vertical;
        this.regularHorizontalPadding = horizontal;
        updateField();
    }

    private void setHintPaddingSize(int horizontal, int vertical) {
        this.hintVerticalPadding = vertical;
        this.hintHorizontalPadding = horizontal;
        updateField();
    }

    public void setSketchScale(float scale) {
        sketchScale = scale;
        float newRegularSize = mediumRegularTextSize * scale;
        setRegularTextSize(newRegularSize);
        float newHintSize = mediumHintTextSize  * scale;
        setHintTextSize(newHintSize);
        int newPaddingSize = (int) (mediumPaddingSize  * scale);
        setRegularPaddingSize(newPaddingSize, newPaddingSize);
        float hintVerticalPadding = mediumPaddingSize + (mediumRegularTextSize - mediumHintTextSize) / 2;
        int newHintVerticalPadding = (int) (hintVerticalPadding  * scale);
        setHintPaddingSize(newPaddingSize, newHintVerticalPadding);
    }

    public float getSketchScale() {
        return sketchScale;
    }

    public interface SketchEditTextListener {
        void editTextChanged();
    }
}

