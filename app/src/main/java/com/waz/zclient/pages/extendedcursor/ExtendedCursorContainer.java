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
package com.waz.zclient.pages.extendedcursor;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import com.waz.api.EphemeralExpiration;
import com.waz.zclient.R;
import com.waz.zclient.controllers.globallayout.KeyboardHeightObserver;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.pages.extendedcursor.emoji.EmojiKeyboardLayout;
import com.waz.zclient.pages.extendedcursor.ephemeral.EphemeralLayout;
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout;
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterLayout;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;
import java.util.Set;

public class ExtendedCursorContainer extends FrameLayout implements KeyboardHeightObserver,
                                                                    KeyboardVisibilityObserver {
    private static final String PREF__NAME = "PREF__NAME";
    private static final String PREF__KEY__KEYBOARD_HEIGHT = "PREF__KEY__KEYBOARD_HEIGHT";
    private static final String PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE = "PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE";
    private Callback callback;

    public enum Type {
        NONE,
        VOICE_FILTER_RECORDING,
        IMAGES,
        EMOJIS,
        EPHEMERAL
    }

    private final SharedPreferences sharedPreferences;
    private int defaultExtendedContainerHeight;

    private Type type;
    private int accentColor;
    private boolean isExpanded;

    private VoiceFilterLayout voiceFilterLayout;
    private EmojiKeyboardLayout emojiKeyboardLayout;
    private EphemeralLayout ephemeralLayout;

    private int keyboardHeightLandscape;
    private int keyboardHeight;
    private CursorImagesLayout cursorImagesLayout;

    public ExtendedCursorContainer(Context context) {
        this(context, null);
    }

    public ExtendedCursorContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExtendedCursorContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        sharedPreferences = getContext().getSharedPreferences(PREF__NAME, Context.MODE_PRIVATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            accentColor = getResources().getColor(R.color.accent_blue);
        } else {
            accentColor = getResources().getColor(R.color.accent_blue, context.getTheme());
        }
        isExpanded = false;
        type = Type.NONE;
        initKeyboardHeight();
    }

    public void setKeyboardHeight(int height) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (height > keyboardHeightLandscape && height > defaultExtendedContainerHeight) {
                keyboardHeightLandscape = height;
                sharedPreferences.edit().putInt(PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE, height).apply();
            }
        } else {
            if (height > keyboardHeight && height > defaultExtendedContainerHeight) {
                keyboardHeight = height;
                sharedPreferences.edit().putInt(PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE, height).apply();
            }
        }

        updateHeight();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateHeight();
    }

    public void openVoiceFilter(VoiceFilterLayout.Callback callback) {
        openWithType(Type.VOICE_FILTER_RECORDING);
        voiceFilterLayout.setCallback(callback);
    }

    public void openCursorImages(CursorImagesLayout.Callback callback) {
        openWithType(Type.IMAGES);
        cursorImagesLayout.setCallback(callback);
    }

    public void openEmojis(List<String> recent,
                           Set<String> unsupported,
                           EmojiKeyboardLayout.Callback callback) {
        openWithType(Type.EMOJIS);
        emojiKeyboardLayout.setCallback(callback);
        emojiKeyboardLayout.setEmojis(recent, unsupported);
    }

    public void openEphemeral(EphemeralLayout.Callback callback, EphemeralExpiration expiration) {
        openWithType(Type.EPHEMERAL);
        ephemeralLayout.setSelectedExpiration(expiration);
        ephemeralLayout.setCallback(callback);
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
        switch (type) {
            case NONE:
                break;
            case VOICE_FILTER_RECORDING:
                voiceFilterLayout.setAccentColor(accentColor);
                break;
        }
    }

    @Override
    public void onKeyboardHeightChanged(int keyboardHeight) {
        setKeyboardHeight(keyboardHeight);
    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        if (keyboardIsVisible) {
            close(true);
        }
    }

    public void close(boolean immediate) {
        Type lastType = type;
        type = Type.NONE;
        if (!isExpanded) {
            return;
        }

        if (callback != null) {
            callback.onExtendedCursorClosed(lastType);
        }

        isExpanded = false;
        closeCursorImages();
        closeVoiceFilter();

        if (immediate) {
            setVisibility(View.GONE);
            removeAllViews();
            return;
        }

        setTranslationY(0);
        animate()
            .translationY(ViewUtils.toPx(getContext(), 160))
            .setDuration(150)
            .setInterpolator(new Expo.EaseOut())
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.GONE);
                    removeAllViews();
                }
            });
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public Type getType() {
        return type;
    }

    private void openWithType(Type type) {
        if (this.type == type) {
            return;
        }

        this.type = type;

        removeAllViews();

        switch (type) {
            case VOICE_FILTER_RECORDING:
                closeCursorImages();
                voiceFilterLayout = (VoiceFilterLayout) LayoutInflater.from(getContext()).inflate(R.layout.voice_filter_layout,
                                                                                                  this,
                                                                                                  false);
                voiceFilterLayout.setAccentColor(accentColor);
                addView(voiceFilterLayout);
                break;
            case IMAGES:
                closeVoiceFilter();
                cursorImagesLayout = (CursorImagesLayout) LayoutInflater.from(getContext()).inflate(R.layout.cursor_images_layout,
                                                                                                    this,
                                                                                                    false);
                addView(cursorImagesLayout);
                break;
            case EMOJIS:
                closeVoiceFilter();
                closeCursorImages();

                emojiKeyboardLayout = (EmojiKeyboardLayout) LayoutInflater.from(getContext()).inflate(R.layout.emoji_keyboard_layout,
                                                                                                     this,
                                                                                                     false);
                addView(emojiKeyboardLayout);
                break;
            case EPHEMERAL:
                closeVoiceFilter();
                closeCursorImages();

                ephemeralLayout = (EphemeralLayout) LayoutInflater.from(getContext()).inflate(R.layout.ephemeral_keyboard_layout, this, false);
                addView(ephemeralLayout);
                break;
        }

        if (KeyboardUtils.isKeyboardVisible(getContext())) {
            KeyboardUtils.closeKeyboardIfShown((Activity) getContext());
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateUp();
                }
            }, getResources().getInteger(R.integer.animation_delay_short));
        } else if (!isExpanded) {
            animateUp();
        }
    }

    private void closeVoiceFilter() {
        if (voiceFilterLayout != null) {
            voiceFilterLayout.onClose();
            voiceFilterLayout = null;
        }
    }

    private void closeCursorImages() {
        if (cursorImagesLayout != null) {
            cursorImagesLayout.onClose();
            cursorImagesLayout = null;
        }
    }

    private void animateUp() {
        setTranslationY(ViewUtils.toPx(getContext(), 160));
        animate()
            .translationY(0)
            .setDuration(150)
            .setInterpolator(new Expo.EaseOut())
            .withStartAction(new Runnable() {
                @Override
                public void run() {
                    setVisibility(View.VISIBLE);
                }
            })
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    isExpanded = true;
                }
            });
    }

    private void initKeyboardHeight() {
        defaultExtendedContainerHeight = getResources().getDimensionPixelSize(R.dimen.extend_container_height);

        if (sharedPreferences.contains(PREF__KEY__KEYBOARD_HEIGHT)) {
            keyboardHeight = sharedPreferences.getInt(PREF__KEY__KEYBOARD_HEIGHT,
                                                      getResources().getDimensionPixelSize(R.dimen.extend_container_height));
        } else {
            keyboardHeight = -1;
        }

        if (sharedPreferences.contains(PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE)) {
            keyboardHeightLandscape = sharedPreferences.getInt(PREF__KEY__KEYBOARD_HEIGHT_LANDSCAPE,
                                                               getResources().getDimensionPixelSize(R.dimen.extend_container_height));
        } else {
            keyboardHeightLandscape = -1;
        }
    }

    private void updateHeight() {
        int newHeight = defaultExtendedContainerHeight;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (keyboardHeightLandscape != -1) {
                newHeight = keyboardHeightLandscape;
            }
        } else {
            if (keyboardHeight != -1) {
                newHeight = keyboardHeight;
            }
        }

        getLayoutParams().height = newHeight;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onExtendedCursorClosed(ExtendedCursorContainer.Type lastType);
    }
}
