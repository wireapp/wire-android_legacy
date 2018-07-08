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
package com.waz.zclient.ui.views.tab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;


public class TabIndicatorLayout extends LinearLayout implements ViewPager.OnPageChangeListener {

    private int selectedPosition;

    private LinearLayout textViewContainer;
    private int[] anchorPositions = null;
    private TabIndicatorView tabIndicatorView;
    private Callback callback;
    private ViewPager viewPager;
    private int anchorWidth;

    public TabIndicatorLayout(Context context) {
        this(context, null);
    }

    public TabIndicatorLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TabIndicatorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(LinearLayout.VERTICAL);

        selectedPosition = 0;

        textViewContainer = new LinearLayout(getContext());
        tabIndicatorView = new TabIndicatorView(context);

        addView(textViewContainer);

        LinearLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                            getResources().getDimensionPixelSize(R.dimen.sign_tab_indicator_marker__height));
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.wire__padding__small);
        addView(tabIndicatorView, params);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (isInEditMode()) {
            return;
        }
        super.onSizeChanged(w, h, oldw, oldh);


        int numAnchorPositions = anchorPositions.length;

        anchorWidth = 0;
        if (numAnchorPositions > 0) {
            anchorWidth = w / numAnchorPositions;
        }

        int ap = anchorWidth / 2;
        for (int i = 0; i < numAnchorPositions; i++) {
            anchorPositions[i] = ap;
            ap += anchorWidth;
        }

        tabIndicatorView.setPosition(anchorPositions[selectedPosition], false);
    }

    public void setLabels(int[] resIds) {
        List<String> labels = new ArrayList<>();

        for (int resId : resIds) {
            labels.add(getResources().getString(resId));
        }

        setLabels(labels);
    }

    public void setGlyphLabels(int[] resIds) {
        List<String> labels = new ArrayList<>();

        for (int resId : resIds) {
            labels.add(getResources().getString(resId));
        }

        setGlyphLabels(labels);
    }

    public void setLabelHeight(int height) {
        ViewGroup.LayoutParams params = textViewContainer.getLayoutParams();
        params.height = height;
        ViewUtils.setPaddingBottom(textViewContainer, getResources().getDimensionPixelSize(R.dimen.wire__divider__height));
        textViewContainer.setLayoutParams(params);
        textViewContainer.invalidate();
    }


    public void setLabels(List<String> lables) {
        anchorPositions = new int[lables.size()];

        textViewContainer.removeAllViews();

        for (int i = 0; i < lables.size(); i++) {
            String label = lables.get(i);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            TextView textView = (TextView) inflater.inflate(R.layout.tab_textview, textViewContainer, false);
            textViewContainer.addView(textView);

            textView.setText(label);
            textView.setId(i);
        }

        requestLayout();
    }

    public void setGlyphLabels(List<String> lables) {
        anchorPositions = new int[lables.size()];

        textViewContainer.removeAllViews();

        for (int i = 0; i < lables.size(); i++) {
            String label = lables.get(i);
            LayoutInflater inflater = LayoutInflater.from(getContext());
            GlyphTextView textView = (GlyphTextView) inflater.inflate(R.layout.tab_glyphtextview, textViewContainer, false);
            textViewContainer.addView(textView);

            textView.setText(label);
            textView.setId(i);
        }

        requestLayout();
    }


    public void setTextColor(ColorStateList textColor) {
        if (textViewContainer == null) {
            return;
        }
        for (int i = 0; i < textViewContainer.getChildCount(); i++) {
            View view = textViewContainer.getChildAt(i);
            if (view instanceof TextView) {
                ((TextView) view).setTextColor(textColor);
            }
        }
    }

    public void setPrimaryColor(int color) {
        tabIndicatorView.setColor(color);
    }

    public void setShowDivider(boolean show) {
        tabIndicatorView.setShowDivider(show);
    }

    public void setViewPager(ViewPager viewPager) {
        if (this.viewPager != null) {
            this.viewPager.removeOnPageChangeListener(this);
        }
        this.viewPager = viewPager;
        this.viewPager.addOnPageChangeListener(this);
        PagerAdapter adapter = this.viewPager.getAdapter();
        int count = adapter.getCount();

        List<String> labels = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            labels.add(adapter.getPageTitle(i).toString());
        }

        setLabels(labels);
        setSelected(viewPager.getCurrentItem());
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // anchorWidth * (position + 0.5f) points exactly to the middle of the anchor range
        // positionOffset * anchorWidth is the offset caused by the pager slide between the anchor positions
        tabIndicatorView.setPosition((int) (anchorWidth * (position + positionOffset + 0.5f)), false);
    }

    @Override
    public void onPageSelected(int position) {
        setSelected(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    public void setSelected(int pos) {
        selectedPosition = pos;
        tabIndicatorView.setPosition(anchorPositions[pos], true);

        for (int i = 0; i < textViewContainer.getChildCount(); i++) {
            textViewContainer.getChildAt(i).setSelected(i == selectedPosition);
        }
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int pos = (int) (anchorPositions.length * event.getX() / getMeasuredWidth());

            if (viewPager != null) {
                viewPager.setCurrentItem(pos);
            }

            if (callback != null) {
                callback.onItemSelected(pos);
            }

        }

        return true;
    }

    public interface Callback {
        void onItemSelected(int pos);
    }
}
