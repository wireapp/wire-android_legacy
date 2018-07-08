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
/*
 * This part of the Wire software uses source code from the Android Support Library.
 * (https://android.googlesource.com/platform/frameworks/support/)
 *
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.waz.zclient.pages.main.conversationpager;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;

/**
 * Lenny added:
 *
 * property sliding enabled flag
 * property dimming enabled flag
 *
 *
 * SlidingPaneLayout provides a horizontal, multi-pane layout for use at the top level
 * of a UI. A left (or first) pane is treated as a content list or browser, subordinate to a
 * primary detail view for displaying content.
 * <p/>
 * <p>Child views may overlap if their combined width exceeds the available width
 * in the SlidingPaneLayout. When this occurs the user may slide the topmost view out of the way
 * by dragging it, or by navigating in the direction of the overlapped view using a keyboard.
 * If the content of the dragged child view is itself horizontally scrollable, the user may
 * grab it by the very edge.</p>
 * <p/>
 * <p>Thanks to this sliding behavior, SlidingPaneLayout may be suitable for creating layouts
 * that can smoothly adapt across many different screen sizes, expanding out fully on larger
 * screens and collapsing on smaller screens.</p>
 * <p/>
 * <p>SlidingPaneLayout is distinct from a navigation drawer as described in the design
 * guide and should not be used in the same scenarios. SlidingPaneLayout should be thought
 * of only as a way to allow a two-pane layout normally used on larger screens to adapt to smaller
 * screens in a natural way. The interaction patterns expressed by SlidingPaneLayout imply
 * a physicality and direct information hierarchy between panes that does not necessarily exist
 * in a scenario where a navigation drawer should be used instead.</p>
 * <p/>
 * <p>Appropriate uses of SlidingPaneLayout include pairings of panes such as a contact list and
 * subordinate interactions with those contacts, or an email thread list with the content pane
 * displaying the contents of the selected thread. Inappropriate uses of SlidingPaneLayout include
 * switching between disparate functions of your app, such as jumping from a social stream view
 * to a view of your personal profile - cases such as this should use the navigation drawer
 * pattern instead. ({@link android.support.v4.widget.DrawerLayout DrawerLayout} implements this pattern.)</p>
 * <p/>
 * <p>Like {@link android.widget.LinearLayout LinearLayout}, SlidingPaneLayout supports
 * the use of the layout parameter <code>layout_weight</code> on child views to determine
 * how to divide leftover space after measurement is complete. It is only relevant for width.
 * When views do not overlap weight behaves as it does in a LinearLayout.</p>
 * <p/>
 * <p>When views do overlap, weight on a slideable pane indicates that the pane should be
 * sized to fill all available space in the closed state. Weight on a pane that becomes covered
 * indicates that the pane should be sized to fill all available space except a small minimum strip
 * that the user may use to grab the slideable view and pull it back over into a closed state.</p>
 */
@SuppressWarnings("all")
public class SlidingPaneLayout extends ViewGroup {
    private static final String TAG = "SlidingPaneLayout";

    /**
     * Default size of the overhang for a pane in the open state.
     * At least this much of a sliding pane will remain visible.
     * This indicates that there is more content available and provides
     * a "physical" edge to grab to pull it closed.
     */
    private static final int DEFAULT_OVERHANG_SIZE = 32; // dp;

    /**
     * If no fade color is given by default it will fade to 80% gray.
     */
    private static final int DEFAULT_FADE_COLOR = 0xcccccccc;

    /**
     * The fade color used for the sliding panel. 0 = no fading.
     */
    private int sliderFadeColor = DEFAULT_FADE_COLOR;

    /**
     * Minimum velocity that will be detected as a fling
     */
    private static final int MIN_FLING_VELOCITY = 400; // dips per second

    /**
     * The fade color used for the panel covered by the slider. 0 = no fading.
     */
    private int coveredFadeColor;

    /**
     * Drawable used to draw the shadow between panes by default.
     */
    private Drawable shadowDrawableLeft;

    /**
     * Drawable used to draw the shadow between panes to support RTL (right to left language).
     */
    private Drawable shadowDrawableRight;

    /**
     * The size of the overhang in pixels.
     * This is the minimum section of the sliding panel that will
     * be visible in the open state to allow for a closing drag.
     */
    private int overhangSize;

    /**
     * True if a panel can slide with the current measurements
     */
    private boolean canSlide;

    /**
     * The child view that can slide, if any.
     */
    private View slideableView;

    /**
     * How far the panel is offset from its closed position.
     * range [0, 1] where 0 = closed, 1 = open.
     */
    private float slideOffset;

    /**
     * How far the non-sliding panel is parallaxed from its usual position when open.
     * range [0, 1]
     */
    private float parallaxOffset;

    /**
     * How far in pixels the slideable panel may move.
     */
    private int slideRange;

    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean isUnableToDrag;

    /**
     * Distance in pixels to parallax the fixed pane by when fully closed
     */
    private int parallaxBy;

    private float initialMotionX;
    private float initialMotionY;

    private ISlidingPaneController panelSlideController;

    private final ViewDragHelper dragHelper;

    /**
     * Stores whether or not the pane was open the last time it was slideable.
     * If open/close operations are invoked this state is modified. Used by
     * instance state save/restore.
     */
    private boolean preservedOpenState;
    private boolean firstLayout = true;

    private final Rect tmpRect = new Rect();

    private final List<DisableLayerRunnable> postedRunnables =
            new ArrayList<DisableLayerRunnable>();

    static final SlidingPanelLayoutImpl IMPL;

    static {
        IMPL = new SlidingPanelLayoutImplJBMR1();
    }

    private boolean isSlidingEnabled;
    private boolean isDimmingEnabled;
    private boolean closeOnClick;

    public void setIsSlidingEnabled(boolean isSlidingEnabled) {
        this.isSlidingEnabled = isSlidingEnabled;
    }

    public boolean isDimmingEnabled() {
        return isDimmingEnabled;
    }

    public void setDimmingEnabled(boolean isDimmingEnabled) {
        this.isDimmingEnabled = isDimmingEnabled;
    }

    public void setPreservedOpenState(boolean preservedOpenState) {
        this.preservedOpenState = preservedOpenState;
        requestLayout();
    }

    public SlidingPaneLayout(Context context) {
        this(context, null);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingPaneLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setWillNotDraw(false);

        ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);

        dragHelper = ViewDragHelper.create(this, 0.5f, new DragHelperCallback());
        dragHelper.setMinVelocity(MIN_FLING_VELOCITY * context.getResources().getDisplayMetrics().density);
    }

    public void setSideBarWidth(Configuration newConfig) {
        overhangSize = ViewUtils.toPx(getContext(), newConfig.screenWidthDp) - getContext().getResources().getDimensionPixelSize(R.dimen.framework__sidebar_width);
        requestLayout();
    }

    /**
     * Set a distance to parallax the lower pane by when the upper pane is in its
     * fully closed state. The lower pane will scroll between this position and
     * its fully open state.
     *
     * @param parallaxBy Distance to parallax by in pixels
     */
    public void setParallaxDistance(int parallaxBy) {
        this.parallaxBy = parallaxBy;
        requestLayout();
    }

    /**
     * @return The distance the lower pane will parallax by when the upper pane is fully closed.
     * @see #setParallaxDistance(int)
     */
    public int getParallaxDistance() {
        return parallaxBy;
    }

    /**
     * Set the color used to fade the sliding pane out when it is slid most of the way offscreen.
     *
     * @param color An ARGB-packed color value
     */
    public void setSliderFadeColor(int color) {
        sliderFadeColor = color;
    }

    /**
     * @return The ARGB-packed color value used to fade the sliding pane
     */
    public int getSliderFadeColor() {
        return sliderFadeColor;
    }

    /**
     * Set the color used to fade the pane covered by the sliding pane out when the pane
     * will become fully covered in the closed state.
     *
     * @param color An ARGB-packed color value
     */
    public void setCoveredFadeColor(int color) {
        coveredFadeColor = color;
    }

    /**
     * @return The ARGB-packed color value used to fade the fixed pane
     */
    public int getCoveredFadeColor() {
        return coveredFadeColor;
    }

    public void setPanelSlideController(ISlidingPaneController listener) {
        panelSlideController = listener;
    }

    void dispatchOnPanelSlide(View panel) {
        if (panelSlideController != null) {
            panelSlideController.onPanelSlide(panel, slideOffset);
        }
    }

    void dispatchOnPanelOpened(View panel) {
        if (panelSlideController != null) {
            panelSlideController.onPanelOpened(panel);
        }
    }

    void dispatchOnPanelClosed(View panel) {
        if (panelSlideController != null) {
            panelSlideController.onPanelClosed(panel);
        }
    }

    void updateObscuredViewsVisibility(View panel) {
        final boolean isLayoutRtl = isLayoutRtlSupport();
        final int startBound = isLayoutRtl ? (getWidth() - getPaddingRight()) :
                getPaddingLeft();
        final int endBound = isLayoutRtl ? getPaddingLeft() :
                (getWidth() - getPaddingRight());
        final int topBound = getPaddingTop();
        final int bottomBound = getHeight() - getPaddingBottom();
        final int left;
        final int right;
        final int top;
        final int bottom;
        if (panel != null && viewIsOpaque(panel)) {
            left = panel.getLeft();
            right = panel.getRight();
            top = panel.getTop();
            bottom = panel.getBottom();
        } else {
            left = right = top = bottom = 0;
        }

        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);

            if (child == panel) {
                // There are still more children above the panel but they won't be affected.
                break;
            }

            final int clampedChildLeft = Math.max((isLayoutRtl ? endBound :
                    startBound), child.getLeft());
            final int clampedChildTop = Math.max(topBound, child.getTop());
            final int clampedChildRight = Math.min((isLayoutRtl ? startBound :
                    endBound), child.getRight());
            final int clampedChildBottom = Math.min(bottomBound, child.getBottom());
            final int vis;
            if (clampedChildLeft >= left && clampedChildTop >= top &&
                    clampedChildRight <= right && clampedChildBottom <= bottom) {
                vis = INVISIBLE;
            } else {
                vis = VISIBLE;
            }
            child.setVisibility(vis);
        }
    }

    void setAllChildrenVisible() {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
    }

    private static boolean viewIsOpaque(View v) {
        if (ViewCompat.isOpaque(v)) {
            return true;
        }

        // View#isOpaque didn't take all valid opaque scrollbar modes into account
        // before API 18 (JB-MR2). On newer devices rely solely on isOpaque above and return false
        // here. On older devices, check the view's background drawable directly as a fallback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return false;
        }

        final Drawable bg = v.getBackground();
        if (bg != null) {
            return bg.getOpacity() == PixelFormat.OPAQUE;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        firstLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        firstLayout = true;

        for (int i = 0, count = postedRunnables.size(); i < count; i++) {
            final DisableLayerRunnable dlr = postedRunnables.get(i);
            dlr.run();
        }
        postedRunnables.clear();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode != MeasureSpec.EXACTLY) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Consume all of the space if specified
                // or pick a magic number from thin air otherwise.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (widthMode == MeasureSpec.AT_MOST) {
                    widthMode = MeasureSpec.EXACTLY;
                } else if (widthMode == MeasureSpec.UNSPECIFIED) {
                    widthMode = MeasureSpec.EXACTLY;
                    widthSize = 300;
                }
            } else {
                throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
            }
        } else if (heightMode == MeasureSpec.UNSPECIFIED) {
            if (isInEditMode()) {
                // Don't crash the layout editor. Pick a magic number from thin air instead.
                // TODO Better communication with tools of this bogus state.
                // It will crash on a real device.
                if (heightMode == MeasureSpec.UNSPECIFIED) {
                    heightMode = MeasureSpec.AT_MOST;
                    heightSize = 300;
                }
            } else {
                throw new IllegalStateException("Height must not be UNSPECIFIED");
            }
        }

        int layoutHeight = 0;
        int maxLayoutHeight = -1;
        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                layoutHeight = maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
            case MeasureSpec.AT_MOST:
                maxLayoutHeight = heightSize - getPaddingTop() - getPaddingBottom();
                break;
        }

        float weightSum = 0;
        boolean canSlide = false;
        final int widthAvailable = widthSize - getPaddingLeft() - getPaddingRight();
        int widthRemaining = widthAvailable;
        final int childCount = getChildCount();

        if (childCount > 2) {
            Timber.e("onMeasure: More than two child views are not supported.");
        }

        // We'll find the current one below.
        slideableView = null;

        // First pass. Measure based on child LayoutParams width/height.
        // Weight will incur a second pass.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (child.getVisibility() == GONE) {
                lp.dimWhenOffset = false;
                continue;
            }

            if (lp.weight > 0) {
                weightSum += lp.weight;

                // If we have no width, weight is the only contributor to the final size.
                // Measure this view on the weight pass only.
                if (lp.width == 0) {
                    continue;
                }
            }

            int childWidthSpec;
            final int horizontalMargin = lp.leftMargin + lp.rightMargin;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthAvailable - horizontalMargin,
                        MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.FILL_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(widthAvailable - horizontalMargin,
                        MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            int childHeightSpec;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.AT_MOST);
            } else if (lp.height == LayoutParams.FILL_PARENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight, MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            }

            child.measure(childWidthSpec, childHeightSpec);
            final int childWidth = child.getMeasuredWidth();
            final int childHeight = child.getMeasuredHeight();

            if (heightMode == MeasureSpec.AT_MOST && childHeight > layoutHeight) {
                layoutHeight = Math.min(childHeight, maxLayoutHeight);
            }

            widthRemaining -= childWidth;
            lp.slideable = widthRemaining < 0;
            canSlide = canSlide | lp.slideable;
            if (lp.slideable) {
                slideableView = child;
            }
        }

        // Resolve weight and make sure non-sliding panels are smaller than the full screen.
        if (canSlide || weightSum > 0) {
            final int fixedPanelWidthLimit = widthAvailable - overhangSize;

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);

                if (child.getVisibility() == GONE) {
                    continue;
                }

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (child.getVisibility() == GONE) {
                    continue;
                }

                final boolean skippedFirstPass = lp.width == 0 && lp.weight > 0;
                final int measuredWidth = skippedFirstPass ? 0 : child.getMeasuredWidth();
                if (canSlide && child != slideableView) {
                    if (lp.width < 0 && (measuredWidth > fixedPanelWidthLimit || lp.weight > 0)) {
                        // Fixed panels in a sliding configuration should
                        // be clamped to the fixed panel limit.
                        final int childHeightSpec;
                        if (skippedFirstPass) {
                            // Do initial height measurement if we skipped measuring this view
                            // the first time around.
                            if (lp.height == LayoutParams.WRAP_CONTENT) {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                        MeasureSpec.AT_MOST);
                            } else if (lp.height == LayoutParams.FILL_PARENT) {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                        MeasureSpec.EXACTLY);
                            } else {
                                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height,
                                        MeasureSpec.EXACTLY);
                            }
                        } else {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(
                                    child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                        }
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                fixedPanelWidthLimit, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                    }
                } else if (lp.weight > 0) {
                    int childHeightSpec;
                    if (lp.width == 0) {
                        // This was skipped the first time; figure out a real height spec.
                        if (lp.height == LayoutParams.WRAP_CONTENT) {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                    MeasureSpec.AT_MOST);
                        } else if (lp.height == LayoutParams.FILL_PARENT) {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(maxLayoutHeight,
                                    MeasureSpec.EXACTLY);
                        } else {
                            childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height,
                                    MeasureSpec.EXACTLY);
                        }
                    } else {
                        childHeightSpec = MeasureSpec.makeMeasureSpec(
                                child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                    }

                    if (canSlide) {
                        // Consume available space
                        final int horizontalMargin = lp.leftMargin + lp.rightMargin;
                        final int newWidth = widthAvailable - horizontalMargin;
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                newWidth, MeasureSpec.EXACTLY);
                        if (measuredWidth != newWidth) {
                            child.measure(childWidthSpec, childHeightSpec);
                        }
                    } else {
                        // Distribute the extra width proportionally similar to LinearLayout
                        final int widthToDistribute = Math.max(0, widthRemaining);
                        final int addedWidth = (int) (lp.weight * widthToDistribute / weightSum);
                        final int childWidthSpec = MeasureSpec.makeMeasureSpec(
                                measuredWidth + addedWidth, MeasureSpec.EXACTLY);
                        child.measure(childWidthSpec, childHeightSpec);
                    }
                }
            }
        }

        final int measuredWidth = widthSize;
        final int measuredHeight = layoutHeight + getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(measuredWidth, measuredHeight);
        this.canSlide = canSlide;

        if (dragHelper.getViewDragState() != ViewDragHelper.STATE_IDLE && !canSlide) {
            // Cancel scrolling in progress, it's no longer relevant.
            dragHelper.abort();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final boolean isLayoutRtl = isLayoutRtlSupport();
        if (isLayoutRtl) {
            dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_RIGHT);
        } else {
            dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT);
        }
        final int width = r - l;
        final int paddingStart = isLayoutRtl ? getPaddingRight() : getPaddingLeft();
        final int paddingEnd = isLayoutRtl ? getPaddingLeft() : getPaddingRight();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();
        int xStart = paddingStart;
        int nextXStart = xStart;

        if (firstLayout) {
            slideOffset = canSlide && preservedOpenState ? 1.f : 0.f;
        }


        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int childWidth = child.getMeasuredWidth();
            int offset = 0;

            if (lp.slideable) {
                final int margin = lp.leftMargin + lp.rightMargin;
                final int range = Math.min(nextXStart,
                        width - paddingEnd - overhangSize) - xStart - margin;
                slideRange = range;
                final int lpMargin = isLayoutRtl ? lp.rightMargin : lp.leftMargin;
                lp.dimWhenOffset = xStart + lpMargin + range + childWidth / 2 >
                        width - paddingEnd;
                final int pos = (int) (range * slideOffset);
                xStart += pos + lpMargin;
                slideOffset = (float) pos / slideRange;
            } else if (canSlide && parallaxBy != 0) {
                offset = (int) ((1 - slideOffset) * parallaxBy);
                xStart = nextXStart;
            } else {
                xStart = nextXStart;
            }

            final int childRight;
            final int childLeft;
            if (isLayoutRtl) {
                childRight = width - xStart + offset;
                childLeft = childRight - childWidth;
            } else {
                childLeft = xStart - offset;
                childRight = childLeft + childWidth;
            }

            final int childTop = paddingTop;
            final int childBottom = childTop + child.getMeasuredHeight();
            child.layout(childLeft, paddingTop, childRight, childBottom);

            nextXStart += child.getWidth();
        }

        if (firstLayout) {
            if (canSlide) {
                if (parallaxBy != 0) {
                    parallaxOtherViews(slideOffset);
                }
                if (((LayoutParams) slideableView.getLayoutParams()).dimWhenOffset) {
                    dimChildView(slideableView, slideOffset, sliderFadeColor);
                }
            } else {
                // Reset the dim level of all children; it's irrelevant when nothing moves.
                for (int i = 0; i < childCount; i++) {
                    dimChildView(getChildAt(i), 0, sliderFadeColor);
                }
            }
            updateObscuredViewsVisibility(slideableView);
        }

        firstLayout = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Recalculate sliding panes and their details
        if (w != oldw) {
            firstLayout = true;
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (!isInTouchMode() && !canSlide) {
            preservedOpenState = child == slideableView;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isSlidingEnabled) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(ev);

        // Preserve the open state based on the last view that was touched.
        if (!canSlide && action == MotionEvent.ACTION_DOWN && getChildCount() > 1) {
            // After the first things will be slideable.
            final View secondChild = getChildAt(1);
            if (secondChild != null) {
                preservedOpenState = !dragHelper.isViewUnder(secondChild,
                        (int) ev.getX(), (int) ev.getY());
            }
        }

        if (!canSlide || (isUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            dragHelper.cancel();
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper.cancel();
            return false;
        }

        boolean interceptTap = false;

        final float x;
        final float y;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isUnableToDrag = false;
                x = ev.getX();
                y = ev.getY();
                initialMotionX = x;
                initialMotionY = y;

                if (dragHelper.isViewUnder(slideableView, (int) x, (int) y) &&
                    ((closeOnClick && isOpen()) || isDimmed(slideableView))) {
                    interceptTap = true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = ev.getX();
                y = ev.getY();
                final float adx = Math.abs(x - initialMotionX);
                final float ady = Math.abs(y - initialMotionY);
                final int slop = dragHelper.getTouchSlop();
                if (adx > slop && ady > adx) {
                    dragHelper.cancel();
                    isUnableToDrag = true;
                    return false;
                }
        }

        final boolean interceptForDrag = dragHelper.shouldInterceptTouchEvent(ev);

        return interceptForDrag || interceptTap;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!canSlide || !isSlidingEnabled) {
            return super.onTouchEvent(ev);
        }

        dragHelper.processTouchEvent(ev);

        final int action = ev.getAction();

        final float x;
        final float y;
        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                x = ev.getX();
                y = ev.getY();
                initialMotionX = x;
                initialMotionY = y;
                break;
            case MotionEvent.ACTION_UP:
                if ((closeOnClick && isOpen()) ||
                    isDimmed(slideableView)) {
                    x = ev.getX();
                    y = ev.getY();
                    final float dx = x - initialMotionX;
                    final float dy = y - initialMotionY;
                    final int slop = dragHelper.getTouchSlop();
                    if (dx * dx + dy * dy < slop * slop &&
                            dragHelper.isViewUnder(slideableView, (int) x, (int) y)) {
                        // Taps close a dimmed open pane.
                        closePane(slideableView, 0);
                        break;
                    }
                }
                break;
        }

        return true;
    }

    /**
     * Close the sliding pane if it is currently slideable. If first layout
     * has already completed this will animate.
     *
     * @return true if the pane was slideable and is now closed/in the process of closing
     */
    public boolean closePane() {
        return closePane(slideableView, 0);
    }

    private boolean closePane(View pane, int initialVelocity) {
        if (firstLayout || smoothSlideTo(0.f, initialVelocity)) {
            preservedOpenState = false;
            return true;
        }
        return false;
    }

    /**
     * Open the sliding pane if it is currently slideable. If first layout
     * has already completed this will animate.
     *
     * @return true if the pane was slideable and is now open/in the process of opening
     */
    public boolean openPane() {
        return openPane(slideableView, 0);
    }

    private boolean openPane(View pane, int initialVelocity) {
        if (firstLayout || smoothSlideTo(1.f, initialVelocity)) {
            preservedOpenState = true;
            return true;
        }
        return false;
    }

    /**
     * @deprecated Renamed to {@link #openPane()} - this method is going away soon!
     */
    @Deprecated
    public void smoothSlideOpen() {
        openPane();
    }

        /**
     * @deprecated Renamed to {@link #closePane()} - this method is going away soon!
     */
    @Deprecated
    public void smoothSlideClosed() {
        closePane();
    }

    /**
     * Check if the layout is completely open. It can be open either because the slider
     * itself is open revealing the left pane, or if all content fits without sliding.
     *
     * @return true if sliding panels are completely open
     */
    public boolean isOpen() {
        return !canSlide || MathUtils.floatEqual(slideOffset, 1f);
    }

    /**
     * @return true if content in this layout can be slid open and closed
     * @deprecated Renamed to {@link #isSlideable()} - this method is going away soon!
     */
    @Deprecated
    public boolean canSlide() {
        return canSlide;
    }

    /**
     * Check if the content in this layout cannot fully fit side by side and therefore
     * the content pane can be slid back and forth.
     *
     * @return true if content in this layout can be slid open and closed
     */
    public boolean isSlideable() {
        return canSlide;
    }

    private void onPanelDragged(int newLeft) {
        if (slideableView == null) {
            // This can happen if we're aborting motion during layout because everything now fits.
            slideOffset = 0;
            return;
        }
        final boolean isLayoutRtl = isLayoutRtlSupport();
        final LayoutParams lp = (LayoutParams) slideableView.getLayoutParams();

        int childWidth = slideableView.getWidth();
        final int newStart = isLayoutRtl ? getWidth() - newLeft - childWidth : newLeft;

        final int paddingStart = isLayoutRtl ? getPaddingRight() : getPaddingLeft();
        final int lpMargin = isLayoutRtl ? lp.rightMargin : lp.leftMargin;
        final int startBound = paddingStart + lpMargin;

        slideOffset = (float) (newStart - startBound) / slideRange;

        if (parallaxBy != 0) {
            parallaxOtherViews(slideOffset);
        }

        if (lp.dimWhenOffset) {
            dimChildView(slideableView, slideOffset, sliderFadeColor);
        }
        dispatchOnPanelSlide(slideableView);
    }

    private void dimChildView(View v, float mag, int fadeColor) {
        if (!isDimmingEnabled) {
            return;
        }
        final LayoutParams lp = (LayoutParams) v.getLayoutParams();

        if (mag > 0 && fadeColor != 0) {
            final int baseAlpha = (fadeColor & 0xff000000) >>> 24;
            int imag = (int) (baseAlpha * mag);
            int color = imag << 24 | (fadeColor & 0xffffff);
            if (lp.dimPaint == null) {
                lp.dimPaint = new Paint();
            }
            lp.dimPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_OVER));
            if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_HARDWARE) {
                ViewCompat.setLayerType(v, ViewCompat.LAYER_TYPE_HARDWARE, lp.dimPaint);
            }
            invalidateChildRegion(v);
        } else if (ViewCompat.getLayerType(v) != ViewCompat.LAYER_TYPE_NONE) {
            if (lp.dimPaint != null) {
                lp.dimPaint.setColorFilter(null);
            }
            final DisableLayerRunnable dlr = new DisableLayerRunnable(v);
            postedRunnables.add(dlr);
            ViewCompat.postOnAnimation(this, dlr);
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        boolean result;
        final int save = canvas.save(Canvas.CLIP_SAVE_FLAG);

        if (canSlide && !lp.slideable && slideableView != null) {
            // Clip against the slider; no sense drawing what will immediately be covered.
            canvas.getClipBounds(tmpRect);
            if (isLayoutRtlSupport()) {
                tmpRect.left = Math.max(tmpRect.left, slideableView.getRight());
            } else {
                tmpRect.right = Math.min(tmpRect.right, slideableView.getLeft());
            }
            canvas.clipRect(tmpRect);
        }
        result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);

        return result;
    }

    private void invalidateChildRegion(View v) {
        IMPL.invalidateChildRegion(this, v);
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity    initial velocity in case of fling, or 0.
     */
    boolean smoothSlideTo(float slideOffset, int velocity) {
        if (!canSlide) {
            // Nothing to do.
            return false;
        }

        final boolean isLayoutRtl = isLayoutRtlSupport();
        final LayoutParams lp = (LayoutParams) slideableView.getLayoutParams();

        int x;
        if (isLayoutRtl) {
            int startBound = getPaddingRight() + lp.rightMargin;
            int childWidth = slideableView.getWidth();
            x = (int) (getWidth() - (startBound + slideOffset * slideRange + childWidth));
        } else {
            int startBound = getPaddingLeft() + lp.leftMargin;
            x = (int) (startBound + slideOffset * slideRange);
        }

        if (dragHelper.smoothSlideViewTo(slideableView, x, slideableView.getTop())) {
            setAllChildrenVisible();
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            if (!canSlide) {
                dragHelper.abort();
                return;
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * @param d drawable to use as a shadow
     * @deprecated Renamed to {@link #setShadowDrawableLeft(Drawable d)} to support LTR (left to
     * right language) and {@link #setShadowDrawableRight(Drawable d)} to support RTL (right to left
     * language) during opening/closing.
     */
    @Deprecated
    public void setShadowDrawable(Drawable d) {
        setShadowDrawableLeft(d);
    }

    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param d drawable to use as a shadow
     */
    public void setShadowDrawableLeft(Drawable d) {
        shadowDrawableLeft = d;
    }

    /**
     * Set a drawable to use as a shadow cast by the left pane onto the right pane
     * during opening/closing to support right to left language.
     *
     * @param d drawable to use as a shadow
     */
    public void setShadowDrawableRight(Drawable d) {
        shadowDrawableRight = d;
    }

    /**
     * @deprecated
     *
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param resId Resource ID of a drawable to use
     */
    @Deprecated
    public void setShadowResource(@DrawableRes int resId) {
        setShadowDrawable(getResources().getDrawable(resId));
    }

    /**
     * Set a drawable to use as a shadow cast by the right pane onto the left pane
     * during opening/closing.
     *
     * @param resId Resource ID of a drawable to use
     */
    public void setShadowResourceLeft(int resId) {
        setShadowDrawableLeft(getResources().getDrawable(resId));
    }

    /**
     * Set a drawable to use as a shadow cast by the left pane onto the right pane
     * during opening/closing to support right to left language.
     *
     * @param resId Resource ID of a drawable to use
     */
    public void setShadowResourceRight(int resId) {
        setShadowDrawableRight(getResources().getDrawable(resId));
    }


    @Override
    public void draw(Canvas c) {
        super.draw(c);
        final boolean isLayoutRtl = isLayoutRtlSupport();
        Drawable shadowDrawable;
        if (isLayoutRtl) {
            shadowDrawable = shadowDrawableRight;
        } else {
            shadowDrawable = shadowDrawableLeft;
        }

        final View shadowView = getChildCount() > 1 ? getChildAt(1) : null;
        if (shadowView == null || shadowDrawable == null) {
            // No need to draw a shadow if we don't have one.
            return;
        }

        final int top = shadowView.getTop();
        final int bottom = shadowView.getBottom();

        final int shadowWidth = shadowDrawable.getIntrinsicWidth();
        final int left;
        final int right;
        if (isLayoutRtlSupport()) {
            left = shadowView.getRight();
            right = left + shadowWidth;
        } else {
            right = shadowView.getLeft();
            left = right - shadowWidth;
        }

        shadowDrawable.setBounds(left, top, right, bottom);
        shadowDrawable.draw(c);
    }

    private void parallaxOtherViews(float slideOffset) {
        final boolean isLayoutRtl = isLayoutRtlSupport();
        final LayoutParams slideLp = (LayoutParams) slideableView.getLayoutParams();
        final boolean dimViews = slideLp.dimWhenOffset &&
                (isLayoutRtl ? slideLp.rightMargin : slideLp.leftMargin) <= 0;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View v = getChildAt(i);
            if (v == slideableView) {
                continue;
            }

            final int oldOffset = (int) ((1 - parallaxOffset) * parallaxBy);
            parallaxOffset = slideOffset;
            final int newOffset = (int) ((1 - slideOffset) * parallaxBy);
            final int dx = oldOffset - newOffset;

            v.offsetLeftAndRight(isLayoutRtl ? -dx : dx);

            if (dimViews) {
                dimChildView(v, isLayoutRtl ? parallaxOffset - 1 :
                        1 - parallaxOffset, coveredFadeColor);
            }
        }
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v      View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx     Delta scrolled in pixels
     * @param x      X coordinate of the active touch point
     * @param y      Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();
            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);
                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, (isLayoutRtlSupport() ? dx : -dx));
    }

    boolean isDimmed(View child) {
        if (child == null) {
            return false;
        }
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        return canSlide && lp.dimWhenOffset && slideOffset > 0;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.isOpen = isSlideable() ? isOpen() : preservedOpenState;

        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (ss.isOpen) {
            openPane();
        } else {
            closePane();
        }
        preservedOpenState = ss.isOpen;
    }

    public void setCloseOnClick(boolean closeOnClick) {
        this.closeOnClick = closeOnClick;
    }

    public boolean isCloseOnClick() {
        return closeOnClick;
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (isUnableToDrag) {
                return false;
            }

            return ((LayoutParams) child.getLayoutParams()).slideable;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (dragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                if (MathUtils.floatEqual(slideOffset, 0f)) {
                    updateObscuredViewsVisibility(slideableView);
                    dispatchOnPanelClosed(slideableView);
                    preservedOpenState = false;
                } else {
                    dispatchOnPanelOpened(slideableView);
                    preservedOpenState = true;
                }
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            // Make all child views visible in preparation for sliding things around
            setAllChildrenVisible();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            onPanelDragged(left);
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final LayoutParams lp = (LayoutParams) releasedChild.getLayoutParams();

            int left;
            if (isLayoutRtlSupport()) {
                int startToRight = getPaddingRight() + lp.rightMargin;
                if (xvel < 0 || (MathUtils.floatEqual(xvel, 0f) && slideOffset > 0.5f)) {
                    startToRight += slideRange;
                }
                int childWidth = slideableView.getWidth();
                left = getWidth() - startToRight - childWidth;
            } else {
                left = getPaddingLeft() + lp.leftMargin;
                if (xvel > 0 || (MathUtils.floatEqual(xvel, 0f) && slideOffset > 0.5f)) {
                    left += slideRange;
                }
            }
            dragHelper.settleCapturedViewAt(left, releasedChild.getTop());
            invalidate();
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return slideRange;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            final LayoutParams lp = (LayoutParams) slideableView.getLayoutParams();

            final int newLeft;
            if (isLayoutRtlSupport()) {
                int startBound = getWidth() -
                        (getPaddingRight() + lp.rightMargin + slideableView.getWidth());
                int endBound = startBound - slideRange;
                newLeft = Math.max(Math.min(left, startBound), endBound);
            } else {
                int startBound = getPaddingLeft() + lp.leftMargin;
                int endBound = startBound + slideRange;
                newLeft = Math.min(Math.max(left, startBound), endBound);
            }
            return newLeft;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            // Make sure we never move views vertically.
            // This could happen if the child has less height than its parent.
            return child.getTop();
        }

        @Override
        public void onEdgeDragStarted(int edgeFlags, int pointerId) {
            dragHelper.captureChildView(slideableView, pointerId);
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private static final int[] ATTRS = new int[]{
                android.R.attr.layout_weight
        };

        /**
         * The weighted proportion of how much of the leftover space
         * this child should consume after measurement.
         */
        public float weight = 0;

        /**
         * True if this pane is the slideable pane in the layout.
         */
        boolean slideable;

        /**
         * True if this view should be drawn dimmed
         * when it's been offset from its default position.
         */
        boolean dimWhenOffset;

        Paint dimPaint;

        public LayoutParams() {
            super(FILL_PARENT, FILL_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.weight = source.weight;
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            this.weight = a.getFloat(0, 0);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        boolean isOpen;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            isOpen = in.readInt() != 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(isOpen ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    interface SlidingPanelLayoutImpl {
        void invalidateChildRegion(SlidingPaneLayout parent, View child);
    }

    static class SlidingPanelLayoutImplJBMR1 implements SlidingPanelLayoutImpl {
        @Override
        public void invalidateChildRegion(SlidingPaneLayout parent, View child) {
            ViewCompat.setLayerPaint(child, ((LayoutParams) child.getLayoutParams()).dimPaint);
        }
    }

    private class DisableLayerRunnable implements Runnable {
        final View childView;

        DisableLayerRunnable(View childView) {
            this.childView = childView;
        }

        @Override
        public void run() {
            if (childView.getParent() == SlidingPaneLayout.this) {
                ViewCompat.setLayerType(childView, ViewCompat.LAYER_TYPE_NONE, null);
                invalidateChildRegion(childView);
            }
            postedRunnables.remove(this);
        }
    }

    private boolean isLayoutRtlSupport() {
        return ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_RTL;
    }
}
