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
package com.waz.zclient.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.ViewUtils;

public class CheckBoxView extends LinearLayout {

    private GlyphTextView glyphTextViewIcon;
    private TextView labelTextView;

    public CheckBoxView(Context context) {
        this(context, null);
    }

    public CheckBoxView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CheckBoxView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.checkbox, this, true);

        glyphTextViewIcon = ViewUtils.getView(this, R.id.gtv__checkbox_icon);
        labelTextView = ViewUtils.getView(this, R.id.ttv__checkbox_label);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                setSelected(!isSelected());
            }
        });
    }

    public void setLabelText(String label) {
        labelTextView.setText(label);
    }

    public void setOptionsTheme(OptionsTheme optionsMenuTheme) {
        glyphTextViewIcon.setBackground(optionsMenuTheme.getCheckBoxBackgroundSelector());
        glyphTextViewIcon.setTextColor(optionsMenuTheme.getCheckboxTextColor());
        labelTextView.setTextColor(optionsMenuTheme.getTextColorPrimary());

        setSelected(false);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        if (selected) {
            glyphTextViewIcon.setText(R.string.glyph__check);
        } else {
            glyphTextViewIcon.setText("");
        }
    }
}
