package com.wire.testinggallery.utils;

import android.content.Context;
import android.widget.Toast;

import static android.widget.Toast.LENGTH_LONG;

public class InfoDisplayManager {
    public static void showToast(Context context, String message) {
        Toast.makeText(context,
            message, LENGTH_LONG)
            .show();
    }
}
