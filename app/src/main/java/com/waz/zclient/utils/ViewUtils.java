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
package com.waz.zclient.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

public class ViewUtils {

    public static boolean isInPortrait(@NonNull Context context) { // still used in wire-ui
        return isInPortrait(context.getResources().getConfiguration());
    }

    private static boolean isInPortrait(@NonNull Configuration configuration) {
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
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

    /**
     * @return everytime the amount of pixels of the (in portrait) horizontal axis of the phone
     */
    public static int getOrientationIndependentDisplayWidth(Context context) { // still used in wire-ui
        int pixels;
        if (isInPortrait(context)) {
            pixels = context.getResources().getDisplayMetrics().widthPixels;
        } else {
            pixels = context.getResources().getDisplayMetrics().heightPixels;
        }
        return pixels;
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

    public static void setPaddingBottom(View view, int bottomPadding) {
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottomPadding);
    }

    public static Point getLocationOnScreen(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Point(location[0], location[1]);
    }

    public static void setMarginTop(View v, int topMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin = topMargin;
        v.invalidate();
    }

    public static void setMarginLeft(View v, int leftMargin) {
        ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).leftMargin = leftMargin;
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

    public static <T extends View> T getContentView(@NonNull Window window) {
        return getView(window.getDecorView(), android.R.id.content);
    }

    @SuppressLint("com.waz.ViewUtils")
    public static <T extends View> T getView(@NonNull Dialog d, @IdRes int resId) {
        return (T) d.findViewById(resId);
    }


    @SuppressLint("com.waz.ViewUtils")
    public static <T extends View> T getView(@NonNull Activity activity, @IdRes int resId) {
        return  (T) activity.findViewById(resId);
    }

}
