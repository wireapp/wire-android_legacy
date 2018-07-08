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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.CheckBoxView;

public class ConfirmationMenu extends LinearLayout {
    private static final int DEFAULT_COLOR = Color.BLUE;
    private String header;
    private String text;
    private String positiveButtonText;
    private String negativeButtonText;
    private boolean cancelVisible;
    private String checkboxLabelText;
    private boolean checkboxSelectedByDefault;
    private int headerIconRes;
    private int backgroundImage;

    private TextView headerTextView;
    private TextView contentTextView;
    private ZetaButton positiveButton;
    private GlyphTextView cancelButton;
    private ZetaButton negativeButton;
    private CheckBoxView checkBoxView;
    private ImageView headerIconView;
    private ImageView backgroundImageView;
    private View backgroundView;
    private View messageContainerView;
    private boolean confirmed;
    private boolean cancelled;
    private ConfirmationCallback callback;
    private OptionsTheme optionsTheme;

    private final OnClickListener onClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (callback == null) {
                return;
            }
            switch (v.getId()) {
                case R.id.positive:
                    confirmed = true;
                    boolean checkboxIsSelected = checkBoxView.getVisibility() == VISIBLE && checkBoxView.isSelected();
                    animateToShow(false);
                    callback.positiveButtonClicked(checkboxIsSelected);
                    break;
                case R.id.negative:
                    callback.negativeButtonClicked();
                    animateToShow(false);
                    break;
                case R.id.cancel:
                    cancelled = true;
                    callback.canceled();
                    animateToShow(false);
                default:
                    break;
            }
        }
    };

    public ConfirmationMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttributes(attrs);
        if (!isInEditMode()) {
            initViews();
        }
    }

    public ConfirmationMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttributes(attrs);
        if (!isInEditMode()) {
            initViews();
        }
    }

    private void initAttributes(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ConfirmationMenu);
        header = a.getString(R.styleable.ConfirmationMenu_header);
        text = a.getString(R.styleable.ConfirmationMenu_text);
        negativeButtonText = a.getString(R.styleable.ConfirmationMenu_negative);
        positiveButtonText = a.getString(R.styleable.ConfirmationMenu_positive);
        cancelVisible = a.getBoolean(R.styleable.ConfirmationMenu_cancelVisible, false);
        checkboxLabelText = a.getString(R.styleable.ConfirmationMenu_checkboxLabel);
        checkboxSelectedByDefault = a.getBoolean(R.styleable.ConfirmationMenu_checkboxIsSelected, false);
        headerIconRes = a.getResourceId(R.styleable.ConfirmationMenu_headerIcon, 0);
        backgroundImage = a.getResourceId(R.styleable.ConfirmationMenu_backgroundImage, 0);
        a.recycle();
    }

    public void setHeader(String text) {
        updateText(headerTextView, text);
    }

    public void setText(String text) {
        updateText(contentTextView, text);
    }

    public void setNegativeButton(String text) {
        updateText(negativeButton, text);
        if (negativeButton.getVisibility() == GONE) {
            ViewUtils.setMarginLeft(positiveButton, 0);
        }
    }

    public void setCancelVisible(boolean visible) {
        cancelVisible = visible;
        cancelButton.setVisibility(cancelVisible ? VISIBLE : GONE);
    }

    public void setPositiveButton(String text) {
        updateText(positiveButton, text);
    }

    public void setCheckBox(String label, boolean selectedByDefault) {
        checkboxLabelText = label;
        checkboxSelectedByDefault = selectedByDefault;
        if (TextUtils.isEmpty(checkboxLabelText)) {
            checkBoxView.setVisibility(GONE);
        } else {
            checkBoxView.setVisibility(VISIBLE);
            checkBoxView.setLabelText(checkboxLabelText);
            checkBoxView.setSelected(checkboxSelectedByDefault);
        }
    }

    private void updateText(TextView view, String text) {
        view.setText(text);
        if (!TextUtils.isEmpty(text)) {
            view.setVisibility(VISIBLE);
        } else {
            view.setVisibility(GONE);
        }
    }

    public void setButtonColor(int color) {
        positiveButton.setIsFilled(true);
        positiveButton.setAccentColor(color);

        negativeButton.setIsFilled(false);
        negativeButton.setAccentColor(color);
        if (optionsTheme != null && optionsTheme.getType() == OptionsTheme.Type.LIGHT) {
            negativeButton.setTextColor(color);
        }

        backgroundImageView.setColorFilter(ColorUtils.injectAlpha(0.1f, color));
    }

    public void useModalBackground(boolean show) {
        backgroundView.setVisibility(show ? VISIBLE : GONE);
    }

    public void setIcon(@DrawableRes int icon) {
        this.headerIconRes = icon;
        headerIconView.setVisibility(icon == 0 ? GONE : VISIBLE);
        headerIconView.setImageResource(icon);
    }

    public void setBackgroundImage(@DrawableRes int imageId) {
        this.backgroundImage = imageId;
        if (imageId != 0) {
            backgroundImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), imageId));
            backgroundImageView.setVisibility(VISIBLE);
        } else {
            backgroundImageView.setVisibility(GONE);
        }
    }

    public void animateToShow(boolean show) {
        if (show) {
            confirmed = false;
            cancelled = false;

            // Init views and post animations to get measured height of message container
            backgroundView.setAlpha(0);
            messageContainerView.setVisibility(INVISIBLE);
            setVisibility(VISIBLE);

            messageContainerView.post(new Runnable() {
                @Override
                public void run() {

                    ObjectAnimator showBackgroundAnimator = ObjectAnimator.ofFloat(backgroundView,
                                                                                   View.ALPHA,
                                                                                   0,
                                                                                   1);
                    showBackgroundAnimator.setInterpolator(new Quart.EaseOut());
                    showBackgroundAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_short));

                    ObjectAnimator showMessageAnimator = ObjectAnimator.ofFloat(messageContainerView,
                                                                                View.TRANSLATION_Y,
                                                                                messageContainerView.getMeasuredHeight(),
                                                                                0);
                    showMessageAnimator.setInterpolator(new Expo.EaseOut());
                    showMessageAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));
                    showMessageAnimator.setStartDelay(getResources().getInteger(R.integer.framework_animation__confirmation_menu__show_message_delay));
                    showMessageAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            messageContainerView.setVisibility(VISIBLE);
                        }
                    });

                    AnimatorSet showSet = new AnimatorSet();
                    showSet.playTogether(showBackgroundAnimator, showMessageAnimator);
                    showSet.setDuration(getResources().getInteger(R.integer.background_accent_color_transition_animation_duration));
                    showSet.start();
                }
            });
        } else {
            ObjectAnimator hideBackgroundAnimator = ObjectAnimator.ofFloat(backgroundView,
                                                                           View.ALPHA,
                                                                           1,
                                                                           0);
            hideBackgroundAnimator.setInterpolator(new Quart.EaseOut());
            hideBackgroundAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_short));
            hideBackgroundAnimator.setStartDelay(getResources().getInteger(R.integer.framework_animation__confirmation_menu__hide_background_delay));
            hideBackgroundAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(GONE);
                    boolean checkboxIsSelected = checkBoxView.getVisibility() == VISIBLE && checkBoxView.isSelected();
                    if (callback != null) {
                        callback.onHideAnimationEnd(confirmed, cancelled, checkboxIsSelected);
                    }
                }
            });

            ObjectAnimator hideMessageAnimator = ObjectAnimator.ofFloat(messageContainerView,
                                                                        View.TRANSLATION_Y,
                                                                        0,
                                                                        messageContainerView.getMeasuredHeight());
            hideMessageAnimator.setInterpolator(new Expo.EaseIn());
            hideMessageAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_medium));

            AnimatorSet hideSet = new AnimatorSet();
            hideSet.playTogether(hideMessageAnimator, hideBackgroundAnimator);
            hideSet.start();
        }
    }

    private void initViews() {
        LayoutInflater.from(getContext()).inflate(R.layout.confirmation_menu_light, this, true);

        backgroundView = ViewUtils.getView(this, R.id.fl__confirmation_dialog__background);
        messageContainerView = ViewUtils.getView(this, R.id.ll__confirmation_dialog__message_container);
        setVisibility(GONE);

        // header
        headerTextView = ViewUtils.getView(this, R.id.header);
        headerTextView.setText(header);
        if (header != null) {
            headerTextView.setVisibility(View.VISIBLE);
        }

        // text
        contentTextView = ViewUtils.getView(this, R.id.text);
        contentTextView.setText(text);
        if (text != null) {
            contentTextView.setVisibility(View.VISIBLE);
        }

        // Checkbox
        checkBoxView = ViewUtils.getView(this, R.id.ll_confirmation_menu__checkbox_container);
        if (TextUtils.isEmpty(checkboxLabelText)) {
            checkBoxView.setVisibility(GONE);
        } else {
            checkBoxView.setVisibility(VISIBLE);
            checkBoxView.setLabelText(checkboxLabelText);
            checkBoxView.setSelected(checkboxSelectedByDefault);
        }

        // buttons
        positiveButton = ViewUtils.getView(this, R.id.positive);
        positiveButton.setText(positiveButtonText);
        positiveButton.setOnClickListener(onClickListener);

        negativeButton = ViewUtils.getView(this, R.id.negative);
        negativeButton.setText(negativeButtonText);
        negativeButton.setOnClickListener(onClickListener);

        cancelButton = ViewUtils.getView(this, R.id.cancel);
        cancelButton.setVisibility(cancelVisible ? VISIBLE : GONE);
        cancelButton.setOnClickListener(onClickListener);

        headerIconView = ViewUtils.getView(this, R.id.icon);
        headerIconView.setVisibility(headerIconRes == 0 ? GONE : VISIBLE);
        headerIconView.setImageResource(headerIconRes);

        backgroundImageView = ViewUtils.getView(this, R.id.backgroundImage);
        setBackgroundImage(backgroundImage);

        // Consume all touch events
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        setButtonColor(DEFAULT_COLOR);
    }

    public void setWireTheme(OptionsTheme optionsTheme) {
        this.optionsTheme = optionsTheme;
        headerTextView.setTextColor(optionsTheme.getTextColorPrimary());
        contentTextView.setTextColor(optionsTheme.getTextColorPrimary());
        cancelButton.setTextColor(optionsTheme.getIconButtonTextColor());
        cancelButton.setBackground(optionsTheme.getIconButtonBackground());
        checkBoxView.setOptionsTheme(optionsTheme);
        if (optionsTheme.getType() == OptionsTheme.Type.DARK) {
            negativeButton.setTextColor(optionsTheme.getTextColorPrimary());
        }
        setBackground(optionsTheme.getOverlayColor());
    }

    public void setBackground(int color) {
        backgroundView.setBackgroundColor(color);
    }

    public void onRequestConfirmation(ConfirmationRequest confirmationRequest) {
        setWireTheme(confirmationRequest.optionsTheme);
        callback = confirmationRequest.callback;
        setHeader(confirmationRequest.header);
        setText(confirmationRequest.message);
        setPositiveButton(confirmationRequest.positiveButton);
        setNegativeButton(confirmationRequest.negativeButton);
        setCancelVisible(confirmationRequest.cancelVisible);
        setIcon(confirmationRequest.headerIconRes);
        setBackgroundImage(confirmationRequest.backgroundImage);
        setCheckBox(confirmationRequest.checkboxLabel, confirmationRequest.checkboxSelectedByDefault);
        animateToShow(true);
    }

    public void setCallback(ConfirmationCallback callback) {
        this.callback = callback;
    }

    public void resetFullScreenPadding() {
        int padding = getResources().getDimensionPixelSize(R.dimen.wire__padding__big);
        messageContainerView.setPadding(padding, padding, padding, padding);

    }
}
