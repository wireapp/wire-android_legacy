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
package com.waz.zclient.ui.optionsmenu;

import android.content.Context;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.IConversation;
import com.waz.zclient.common.views.UserDetailsView;
import com.waz.zclient.ui.R;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.waz.zclient.ui.theme.OptionsDarkTheme;
import com.waz.zclient.ui.theme.OptionsLightTheme;
import com.waz.zclient.ui.theme.OptionsTheme;
import com.waz.zclient.utils.ViewUtils;

import java.util.Collections;
import java.util.List;

public class OptionsMenu extends FrameLayout implements View.OnClickListener {

    private static final int MAX_ITEMS_PER_ROW = 3;
    private static final int TOTAL_ITEMS_FOUR = 4;
    private static final int TOTAL_ITEMS_FIVE = 5;

    /**
     * QuartOut out interpolator used in several locations.
     */
    private Quart.EaseOut quartOut;

    /**
     * Expo out interpolator used in several locations.
     */
    private Expo.EaseOut expoOut;

    /**
     * Expo in interpolator used in several locations.
     */
    private Expo.EaseIn expoIn;

    /**
     * The draggable menu.
     */
    private LinearLayout menuLayout;

    /**
     * The title of the settings box.
     */
    private TypefaceTextView titleTextView;

    private UserDetailsView userDetailsView;

    /**
     * The cancel button of the settings box.
     */
    private TypefaceTextView cancelView;

    /**
     * The overlay of the settingsbox.
     */
    private View backgroundView;

    /**
     * The state of the settings box.
     */
    private State state;

    /**
     * Observer of the settings box actions.
     */
    private Callback callback;


    public OptionsMenu(Context context) {
        this(context, null);
    }

    public OptionsMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OptionsMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attributeSet) {
        expoOut = new Expo.EaseOut();
        expoIn = new Expo.EaseIn();
        quartOut = new Quart.EaseOut();
        notifyOptionsMenuStateHasChanged(State.CLOSED);
        setVisibility(View.GONE);
    }

    /**
     * Open the settings box with an animation.
     */
    public void open() {
        if (state != State.CLOSED) {
            return;
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                animateOpen();
            }
        }, getResources().getInteger(R.integer.wire__animation__delay__very_short));

    }

    private void animateOpen() {
        setOnClickListener(this);
        setVisibility(View.VISIBLE);
        notifyOptionsMenuStateHasChanged(State.OPENING);

        //animate menu
        int menuHeight = menuLayout.getMeasuredHeight();
        menuLayout.setTranslationY(menuHeight);
        menuLayout.animate()
                  .withEndAction(new Runnable() {
                      @Override
                      public void run() {
                          onAnimationEnded();
                      }
                  })
                  .translationY(0)
                  .setStartDelay(getResources().getInteger(R.integer.wire__animation__delay__short))
                  .setDuration(getResources().getInteger(R.integer.wire__animation__duration__medium))
                  .setInterpolator(expoOut);


        // animate background
        backgroundView.setAlpha(0);
        backgroundView.animate()
                      .setStartDelay(getResources().getInteger(R.integer.wire__animation__delay__short))
                      .setDuration(getResources().getInteger(R.integer.wire__animation__duration__regular))
                      .setInterpolator(quartOut)
                      .alpha(1);
        // animate title
        titleTextView.setAlpha(0);
        titleTextView.animate()
                     .setStartDelay(getResources().getInteger(R.integer.wire__animation__delay__regular))
                     .setDuration(getResources().getInteger(R.integer.wire__animation__duration__medium))
                     .setInterpolator(quartOut)
                     .alpha(1);
        userDetailsView.setAlpha(0);
        userDetailsView.animate()
                     .setStartDelay(getResources().getInteger(R.integer.wire__animation__delay__regular))
                     .setDuration(getResources().getInteger(R.integer.wire__animation__duration__medium))
                     .setInterpolator(quartOut)
                     .alpha(1);
    }

    /**
     * Close the settings box with an animation.
     */
    public boolean close() {
        if (state != State.OPEN) {
            return false;
        }
        setOnClickListener(null);
        notifyOptionsMenuStateHasChanged(State.CLOSING);

        int duration = getResources().getInteger(R.integer.wire__animation__duration__regular);

        int menuHeight = menuLayout.getMeasuredHeight();
        menuLayout.animate()
                  .withEndAction(new Runnable() {
                      @Override
                      public void run() {
                          onAnimationEnded();
                      }
                  })
                  .translationY(menuHeight)
                  .setInterpolator(expoIn)
                  .setDuration(duration);

        // animate title
        titleTextView.animate()
                     .alpha(0)
                     .setInterpolator(quartOut)
                     .setDuration(duration);
        userDetailsView.animate()
                     .alpha(0)
                     .setInterpolator(quartOut)
                     .setDuration(duration);

        // animate background
        backgroundView.animate()
                      .alpha(0)
                      .setInterpolator(quartOut)
                      .setStartDelay(getResources().getInteger(R.integer.wire__animation__delay__long))
                      .setDuration(getResources().getInteger(R.integer.wire__animation__duration__medium));

        return true;
    }

    public void setBackgroundColor(int color) {
        backgroundView.setBackgroundColor(color);
    }

    public void setMenuItems(List<OptionsMenuItem> optionsMenuItems, @NonNull final OptionsTheme optionsTheme) {
        titleTextView.setTextColor(optionsTheme.getTextColorPrimary());
        backgroundView.setBackgroundColor(optionsTheme.getOverlayColor());

        //important that items are in order
        Collections.sort(optionsMenuItems);

        // add regular items
        menuLayout.removeAllViews();

        final int rows = (int) Math.ceil(optionsMenuItems.size() / 3f);
        int itemNumber = 0;

        for (int i = 0; i < rows; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            //int sideMargin = getResources().getDimensionPixelSize(R.dimen.options_menu_row_side_margin);
            int topMargin = getResources().getDimensionPixelSize(R.dimen.options_menu_row_top_margin);
            params.setMargins(0, topMargin, 0, 0);
            row.setLayoutParams(params);

            while (row.getChildCount() < MAX_ITEMS_PER_ROW) {
                if (itemNumber >= optionsMenuItems.size()) {
                    break;
                }
                //logic for keeping the first row at 2 items if there are 4/5 total
                if ((optionsMenuItems.size() == TOTAL_ITEMS_FIVE || optionsMenuItems.size() == TOTAL_ITEMS_FOUR) &&
                        i == 0 && row.getChildCount() == MAX_ITEMS_PER_ROW - 1) {
                    break;
                }

                final OptionsMenuItem item = optionsMenuItems.get(itemNumber);
                final View optionsMenuItemContainer = LayoutInflater.from(getContext()).inflate(R.layout.options_menu__item, this, false);
                final FrameLayout optionsMenuButton = ViewUtils.getView(optionsMenuItemContainer, R.id.fl_options_menu_button);

                final GlyphTextView optionsMenuItemGlyph = ViewUtils.getView(optionsMenuItemContainer, R.id.gtv__options_menu_button__glyph);
                optionsMenuItemGlyph.setText(item.resGlyphId);

                final TextView optionsMenuItemText = ViewUtils.getView(optionsMenuItemContainer, R.id.ttv__settings_box__item);
                optionsMenuItemText.setText(item.resTextId);
                optionsMenuItemText.setTextColor(optionsTheme.getTextColorPrimary());

                if (item.isToggled()) {
                    if (optionsTheme.getType() == OptionsTheme.Type.DARK) {
                        optionsMenuItemGlyph.setTextColor(new OptionsLightTheme(getContext()).getTextColorPrimarySelector());
                        optionsMenuButton.setBackground(getResources().getDrawable(R.drawable.selector__icon_button__background__dark_toggled));
                    } else {
                        optionsMenuItemGlyph.setTextColor(new OptionsDarkTheme(getContext()).getTextColorPrimarySelector());
                        optionsMenuButton.setBackground(getResources().getDrawable(R.drawable.selector__icon_button__background__light_toggled));
                    }
                } else {
                    if (optionsTheme.getType() == OptionsTheme.Type.DARK) {
                        optionsMenuButton.setBackground(getResources().getDrawable(R.drawable.selector__icon_button__background__dark));
                    } else {
                        optionsMenuButton.setBackground(getResources().getDrawable(R.drawable.selector__icon_button__background__light));
                    }
                    optionsMenuItemGlyph.setTextColor(optionsTheme.getTextColorPrimarySelector());
                }
                optionsMenuItemText.setTextColor(optionsTheme.getTextColorPrimarySelector());
                optionsMenuItemContainer.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        notifyOptionsMenuItemClicked(item);
                    }
                });

                optionsMenuItemContainer.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return notifyOptionsMenuItemLongClicked(item);
                    }
                });
                row.addView(optionsMenuItemContainer);
                itemNumber++;
            }
            menuLayout.addView(row);
        }

        //set cancel button
        cancelView.setText(getResources().getString(R.string.confirmation_menu__cancel));
        cancelView.setTextColor(optionsTheme.getTextColorPrimarySelector());

        cancelView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                close();
            }
        });
        menuLayout.addView(cancelView);
        notifyOptionsMenuStateHasChanged(State.CLOSED);
        setVisibility(View.INVISIBLE);
    }

    /**
     * Adds an observer to be notified.
     */
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    /**
     * Sets the context title.
     */
    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setConversationDetails(IConversation conversation) {
        if (conversation.getType() == IConversation.Type.GROUP ||
            conversation.getType() == IConversation.Type.UNKNOWN) {
            return;
        }
        userDetailsView.setUser(conversation.getOtherParticipant());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        LayoutInflater.from(getContext()).inflate(R.layout.options_menu, this);

        titleTextView = ViewUtils.getView(this, R.id.ttv__settings_box__title);
        userDetailsView = ViewUtils.getView(this, R.id.udv__settings_box__user_details);
        cancelView = ViewUtils.getView(this, R.id.ttv__settings_box__cancel_button);

        if (titleTextView == null) {
            throw new IllegalStateException("A typeface text view view needs to be provided in the xml layout.");
        }

        menuLayout = ViewUtils.getView(this, R.id.ll__settings_box__container);

        if (menuLayout == null) {
            throw new IllegalStateException("A typeface text view view needs to be provided in the xml layout.");
        }

        backgroundView = ViewUtils.getView(this, R.id.v__options_menu__overlay);
    }

    /**
     * Notifies observer that settings box item was clicked.
     */
    private void notifyOptionsMenuItemClicked(OptionsMenuItem optionsMenuItem) {
        if (callback != null) {
            callback.onOptionsMenuItemClicked(optionsMenuItem);
        }
    }

    /**
     * Notifies observer that settings box item was long clicked
     */
    private boolean notifyOptionsMenuItemLongClicked(OptionsMenuItem optionsMenuItem) {
        if (callback != null) {
            return callback.onOptionsMenuItemLongClicked(optionsMenuItem);
        }
        return false;
    }

    /**
     * Notifies observer that the state of the settings box has changed.
     *
     * @param state
     */
    private void notifyOptionsMenuStateHasChanged(State state) {
        if (callback != null) {
            callback.onOptionsMenuStateHasChanged(state);
        }
        this.state = state;
    }

    private void onAnimationEnded() {
        if (state == State.CLOSING) {
            setVisibility(View.GONE);
            notifyOptionsMenuStateHasChanged(State.CLOSED);
        } else if (state == State.OPENING) {
            notifyOptionsMenuStateHasChanged(State.OPEN);
        }
    }

    @Override
    public void onClick(View view) {
        close();
    }

    /**
     * Observer interface that needs to be implemented by parent layout or fragment.
     */
    public interface Callback {
        void onOptionsMenuStateHasChanged(State state);

        void onOptionsMenuItemClicked(OptionsMenuItem optionsMenuItem);

        boolean onOptionsMenuItemLongClicked(OptionsMenuItem optionsMenuItem);
    }

    public enum State {
        OPEN,
        OPENING,
        CLOSING,
        CLOSED
    }
}
