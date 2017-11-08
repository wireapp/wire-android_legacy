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
package com.waz.zclient.utils;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import java.lang.reflect.Field;

import static android.R.attr.width;

public class ViewUtils {

    private static final int DEFAULT_CHILD_ANIMATION_DURATION = 350;

    public static boolean isInLandscape(@NonNull Context context) {
        return isInLandscape(context.getResources().getConfiguration());
    }

    public static boolean isInLandscape(@NonNull Configuration configuration) {
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static boolean isInPortrait(@NonNull Context context) {
        return isInPortrait(context.getResources().getConfiguration());
    }

    public static boolean isInPortrait(@NonNull Configuration configuration) {
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    public static void lockCurrentOrientation(Activity activity, SquareOrientation squareOrientation) {
        activity.setRequestedOrientation(squareOrientation.activityOrientation);
    }

    public static void unlockOrientation(Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public static void lockScreenOrientation(int orientation, Activity activity) {
        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    public static void setSoftInputMode(Window window, int softInputMode, String sender) {
        window.setSoftInputMode(softInputMode);
    }

    public static void setBackground(View view, Drawable drawable) {
        view.setBackground(drawable);
    }

    public static void setBackground(Context context, View view, int resource) {
        view.setBackground(context.getResources().getDrawable(resource));
    }

    public static int getNavigationBarHeight(Context context) {
        Resources resources = context.getResources();
        String name = ViewUtils.isInPortrait(context) ? "navigation_bar_height" : "navigation_bar_height_landscape";
        int resourceId = resources.getIdentifier(name, "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public static Rect getOrientationDependentDisplayBounds(Context context) {
        return new Rect(
            0, 0,
            getOrientationDependentDisplayWidth(context),
            getOrientationDependentDisplayHeight(context)
        );
    }

    /**
     * @return everytime the amount of pixels of the (in portrait) horizontal axis of the phone
     */
    public static int getOrientationIndependentDisplayWidth(Context context) {
        int pixels;
        if (ViewUtils.isInPortrait(context)) {
            pixels = context.getResources().getDisplayMetrics().widthPixels;
        } else {
            pixels = context.getResources().getDisplayMetrics().heightPixels;
        }
        return pixels;
    }

    /**
     * @return the amount of pixels of the horizontal axis of the phone
     */
    public static int getOrientationDependentDisplayWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * @return everytime the amount of pixels of the (in portrait) width axis of the phone
     */
    public static int getOrientationIndependentDisplayHeight(Context context) {
        int pixels;
        if (ViewUtils.isInPortrait(context)) {
            pixels = context.getResources().getDisplayMetrics().heightPixels;
        } else {
            pixels = context.getResources().getDisplayMetrics().widthPixels;
        }
        return pixels;
    }

    /**
     * @return the amount of pixels of the vertical axis of the phone
     */
    public static int getOrientationDependentDisplayHeight(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static int getRealDisplayWidth(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        DisplayMetrics realMetrics = new DisplayMetrics();
        display.getRealMetrics(realMetrics);
        return realMetrics.widthPixels;
    }

    public static int toPx(Context context, int dp) {
        return toPx(context.getResources(), dp);
    }

    public static int toPx(Resources resources, int dp) {
        final float scale = resources.getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public static int toPx(Context context, double dp) {
        return toPx(context.getResources(), dp);
    }

    public static int toPx(Resources resources, double dp) {
        final float scale = resources.getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public static void setPaddingStart(View view, int startPadding) {
        view.setPaddingRelative(startPadding, view.getPaddingTop(), view.getPaddingEnd(), view.getPaddingBottom());
    }

    public static void setPaddingEnd(View view, int endPadding) {
        view.setPaddingRelative(view.getPaddingStart(), view.getPaddingTop(), endPadding, view.getPaddingBottom());
    }

    public static void setPaddingLeft(View view, int leftPadding) {
        view.setPadding(leftPadding, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
    }

    public static void setPaddingTop(View view, int topPadding) {
        view.setPadding(view.getPaddingLeft(), topPadding, view.getPaddingRight(), view.getPaddingBottom());
    }

    public static void setPaddingRight(View view, int rightPadding) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), rightPadding, view.getPaddingBottom());
    }

    public static void setPaddingBottom(View view, int bottomPadding) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottomPadding);
    }

    public static void setPaddingLeftRight(View view, int padding) {
        view.setPadding(padding, view.getPaddingTop(), padding, view.getPaddingBottom());
    }

    public static Point getLocationOnScreen(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Point(location[0], location[1]);
    }

    public static Drawable getRoundedRect(int cornerRadius, int backgroundColor) {
        RoundRectShape rect = new RoundRectShape(
            new float[] {cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius},
            null,
            null);

        ShapeDrawable background = new ShapeDrawable(rect);
        background.getPaint().setColor(backgroundColor);

        return background;
    }

    public static void fadeInView(@Nullable final View view) {
        fadeInView(view, 1, new ValueAnimator().getDuration(), 0);
    }

    public static void fadeInView(@Nullable final View view, long duration) {
        fadeInView(view, 1, duration, 0);
    }

    public static void fadeInView(@Nullable final View view, final float targetAlpha, final long duration, final long startDelay) {
        if (view == null) {
            return;
        }
        view.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .withStartAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(View.VISIBLE);
                }
            })
            .start();
    }

    public static void fadeOutView(@Nullable final View view) {
        fadeOutView(view, true);
    }

    public static void fadeOutView(@Nullable final View view, boolean setToGoneWithEndAction) {
        fadeOutView(view, new ValueAnimator().getDuration(), 0, setToGoneWithEndAction);
    }

    public static void fadeOutView(@Nullable final View view, final long duration) {
        fadeOutView(view, duration, 0);
    }

    public static void fadeOutView(@Nullable final View view, final long duration, final long startDelay) {
        fadeOutView(view, duration, startDelay, true);
    }

    public static void fadeOutView(@Nullable final View view, final long duration, final long startDelay, final boolean setToGoneWithEndAction) {
        if (view == null) {
            return;
        }
        if (view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE) {
            return;
        }

        view.animate()
            .alpha(0)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    view.setVisibility(setToGoneWithEndAction ? View.GONE : View.INVISIBLE);
                }
            })
            .start();
    }

    public static void setHeight(View v, int height) {
        ViewGroup.LayoutParams params = v.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        } else {
            params.height = height;
        }
        v.setLayoutParams(params);

    }

    public static void setWidth(View v, int width) {
        ViewGroup.LayoutParams params = v.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params.width = width;
        }
        v.setLayoutParams(params);
    }

    public static void setMarginTop(View v, int topMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin = topMargin;
        v.invalidate();
    }
    public static void setMarginBottom(View v, int bottomMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).bottomMargin = bottomMargin;
        v.invalidate();
    }

    public static void setMarginLeft(View v, int leftMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).leftMargin = leftMargin;
        v.invalidate();
    }

    public static void setMarginRight(View v, int rightMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).rightMargin = rightMargin;
        v.invalidate();
    }

    public static AlertDialog showAlertDialog(Context context,
                                              CharSequence title,
                                              CharSequence message,
                                              CharSequence button,
                                              DialogInterface.OnClickListener onClickListener,
                                              boolean cancelable) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setCancelable(cancelable)
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(button, onClickListener)
            .create();
        dialog.show();
        return dialog;
    }

    public static AlertDialog showAlertDialog(Context context,
                                              @StringRes int title,
                                              @StringRes int message,
                                              @StringRes int button,
                                              DialogInterface.OnClickListener onClickListener,
                                              boolean cancelable) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setCancelable(cancelable)
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(button, onClickListener)
            .create();
        dialog.show();
        return dialog;
    }

    public static AlertDialog showAlertDialog(Context context,
                                              CharSequence title,
                                              CharSequence message,
                                              CharSequence positiveButton,
                                              CharSequence negativeButton,
                                              DialogInterface.OnClickListener positiveAction,
                                              DialogInterface.OnClickListener negativeAction) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton, positiveAction)
            .setNegativeButton(negativeButton, negativeAction)
            .create();
        dialog.show();
        return dialog;
    }

    public static AlertDialog showAlertDialog(Context context,
                                              @StringRes int title,
                                              @StringRes int message,
                                              @StringRes int positiveButton,
                                              @StringRes int negativeButton,
                                              DialogInterface.OnClickListener positiveAction,
                                              DialogInterface.OnClickListener negativeAction) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton, positiveAction)
            .setNegativeButton(negativeButton, negativeAction)
            .create();
        dialog.show();
        return dialog;
    }

    public static AlertDialog showAlertDialog(Context context,
                                              View view,
                                              @StringRes int title,
                                              @StringRes int message,
                                              @StringRes int positiveButton,
                                              @StringRes int negativeButton,
                                              DialogInterface.OnClickListener positiveAction,
                                              DialogInterface.OnClickListener negativeAction) {
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setView(view)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButton, positiveAction)
            .setNegativeButton(negativeButton, negativeAction)
            .create();
        dialog.show();
        return dialog;
    }

    @SuppressLint("com.waz.ViewUtils")
    public static <T extends View> T getView(@NonNull View v, @IdRes int resId) {
        return (T) v.findViewById(resId);
    }

    @SuppressLint("com.waz.ViewUtils")
    public static <T extends View> T getView(@NonNull Dialog d, @IdRes int resId) {
        return (T) d.findViewById(resId);
    }


    @SuppressLint("com.waz.ViewUtils")
    public static <T extends View> T getView(@NonNull Activity activity, @IdRes int resId) {
        return  (T) activity.findViewById(resId);
    }

    public static int getAlphaValue(int opacity) {
        return (int) (255.00 * ((double) opacity / 100.00));
    }

    public static long getNextAnimationDuration(Fragment fragment) {
        try {
            // Attempt to get the resource ID of the next animation that
            // will be applied to the given fragment.
            Field nextAnimField = Fragment.class.getDeclaredField("mNextAnim");
            nextAnimField.setAccessible(true);
            int nextAnimResource = nextAnimField.getInt(fragment);
            Animation nextAnim = AnimationUtils.loadAnimation(fragment.getActivity(), nextAnimResource);
            // ...and if it can be loaded, return that animation's duration
            return (nextAnim == null) ? DEFAULT_CHILD_ANIMATION_DURATION : nextAnim.getDuration();
        } catch (NoSuchFieldException | IllegalAccessException | Resources.NotFoundException ex) {
            return DEFAULT_CHILD_ANIMATION_DURATION;
        }
    }


}
