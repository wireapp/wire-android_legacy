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
package com.waz.zclient.ui.cursor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.waz.api.EphemeralExpiration;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.ui.R;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.utils.CursorUtils;
import com.waz.zclient.ui.utils.TypefaceUtils;
import com.waz.zclient.ui.views.CursorIconButton;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.Arrays;
import java.util.List;

public class CursorLayout extends FrameLayout implements
                                              TextView.OnEditorActionListener,
                                              TextWatcher,
                                              CursorToolbar.Callback,
                                              EditMessageCursorToolbar.Callback {
    private static final long TOOLTIP_DURATION = 1500;

    private static List<CursorMenuItem> mainCursorItems = Arrays.asList(CursorMenuItem.CAMERA,
                                                                        CursorMenuItem.SKETCH,
                                                                        CursorMenuItem.GIF,
                                                                        CursorMenuItem.PING,
                                                                        CursorMenuItem.AUDIO_MESSAGE,
                                                                        CursorMenuItem.MORE);

    private static List<CursorMenuItem> secondaryCursorItems = Arrays.asList(CursorMenuItem.VIDEO_MESSAGE,
                                                                             CursorMenuItem.FILE,
                                                                             CursorMenuItem.LOCATION,
                                                                             CursorMenuItem.DUMMY,
                                                                             CursorMenuItem.DUMMY,
                                                                             CursorMenuItem.LESS);

    private View editMessageBackgroundView;
    private CursorToolbarFrame cursorToolbarFrame;
    private CursorEditText newCursorEditText;
    private CursorIconButton sendButton;
    private CursorIconButton ephemeralButton;
    private CursorToolbar mainToolbar;
    private CursorToolbar secondaryToolbar;
    private EditMessageCursorToolbar editMessageCursorToolbar;
    private View topBorder;
    private TextView tooltip;
    private TextView hintView;
    private View dividerView;
    private CursorIconButton emojiButton;

    private CursorCallback cursorCallback;
    private boolean sendButtonIsVisible;
    private boolean tooltipEnabled;
    private boolean isEditingMessage;
    private boolean ephemeralSelected;
    private int anchorPositionPx2;
    private int maxLines = 2;
    private boolean keyboardIsVisible;
    private int cursorHeight;
    private int cursorToolbarAnimationDuration;
    private ObjectAnimator showMainToolbarAnimator;
    private ObjectAnimator showSecondaryToolbarAnimator;
    private ObjectAnimator hideMainToolbarAnimator;
    private ObjectAnimator hideSecondaryToolbarAnimator;
    private ObjectAnimator showEditMessageToolbarAnimator;
    private ObjectAnimator hideEditMessageToolbarAnimator;
    private Message message;
    private int defaultEditTextColor;
    private int defaultDividerColor;
    private int defaultHintTextColor;
    private int ephemeralColor;
    private int accentColor;
    private IConversation conversation;

    private ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation conversation) {
            if (isEditingMessage) {
                dividerView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.separator_light));
            } else if (conversation.isEphemeral() || ephemeralSelected) {
                dividerView.setBackgroundColor(ephemeralColor);
                hintView.setText(R.string.cursor__ephemeral_message);
                hintView.setTextColor(ephemeralColor);
                ephemeralButton.setTextColor(ephemeralColor);
                updateEphemeralButtonBackground();
                sendButton.setSolidBackgroundColor(ephemeralColor);
                emojiButton.showEphemeralMode(ephemeralColor);
                mainToolbar.showEphemeralMode(ephemeralColor);
                secondaryToolbar.showEphemeralMode(ephemeralColor);
            } else {
                dividerView.setBackgroundColor(defaultDividerColor);
                hintView.setText(R.string.cursor__type_a_message);
                hintView.setTextColor(defaultHintTextColor);
                ephemeralButton.setTextColor(defaultEditTextColor);
                updateEphemeralButtonBackground();
                sendButton.setSolidBackgroundColor(accentColor);
                emojiButton.hideEphemeralMode(defaultEditTextColor);
                mainToolbar.hideEphemeraMode(defaultEditTextColor);
                secondaryToolbar.hideEphemeraMode(defaultEditTextColor);
            }

            if (!isEditingMessage &&
                conversation.getType() == IConversation.Type.ONE_TO_ONE &&
                StringUtils.isBlank(newCursorEditText.getText().toString()) &&
                !sendButtonIsVisible) {
                ephemeralButton.setVisibility(VISIBLE);
            } else {
                ephemeralButton.setVisibility(GONE);
            }
        }
    };

    public CursorLayout(Context context) {
        this(context, null);
    }

    public CursorLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        anchorPositionPx2 = getResources().getDimensionPixelSize(R.dimen.cursor_anchor2);
        ephemeralColor = ContextCompat.getColor(getContext(), R.color.ephemera);
    }

    /**
     * The width of the view is needed as it defines the third
     * anchor position.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (LayoutSpec.get(getContext()) == LayoutSpec.LAYOUT_PHONE ||
            LayoutSpec.get(getContext()) == LayoutSpec.LAYOUT_KINDLE) {
            int paddingEdge = getResources().getDimensionPixelSize(R.dimen.cursor_toolbar_padding_horizontal_edge);
            cursorToolbarFrame.setPadding(paddingEdge, 0, paddingEdge, 0);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);

        anchorPositionPx2 = CursorUtils.getCursorEditTextAnchorPosition(getContext(), width);
        if (ViewUtils.isInPortrait(getContext())) {
            int left = CursorUtils.getCursorMenuLeftMargin(getContext(), width);
            ((FrameLayout.LayoutParams) newCursorEditText.getLayoutParams()).leftMargin = anchorPositionPx2;
            ((FrameLayout.LayoutParams) hintView.getLayoutParams()).leftMargin = anchorPositionPx2;
            cursorToolbarFrame.setPadding(left, 0, left, 0);
        } else {
            int left = CursorUtils.getCursorMenuLeftMargin(getContext(), width);
            ((FrameLayout.LayoutParams) newCursorEditText.getLayoutParams()).leftMargin = anchorPositionPx2;
            ((FrameLayout.LayoutParams) hintView.getLayoutParams()).leftMargin = anchorPositionPx2;
            cursorToolbarFrame.setPadding(left, 0, left, 0);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        editMessageBackgroundView = ViewUtils.getView(this, R.id.fl__edit_message__background);
        cursorToolbarFrame = ViewUtils.getView(this, R.id.cal__cursor);
        newCursorEditText = ViewUtils.getView(this, R.id.cet__cursor);
        mainToolbar = ViewUtils.getView(this, R.id.c__cursor__main);
        secondaryToolbar = ViewUtils.getView(this, R.id.c__cursor__secondary);
        editMessageCursorToolbar = ViewUtils.getView(this, R.id.emct__edit_message__toolbar);
        topBorder = ViewUtils.getView(this, R.id.v__top_bar__cursor);
        tooltip = ViewUtils.getView(this, R.id.ctv__cursor);
        hintView = ViewUtils.getView(this, R.id.ttv__cursor_hint);
        defaultHintTextColor = hintView.getTextColors().getDefaultColor();
        dividerView = ViewUtils.getView(this, R.id.v__cursor__divider);
        FrameLayout emojiButtonContainer = ViewUtils.getView(this, R.id.fl__cursor__emoji_container);
        FrameLayout sendButtonContainer = ViewUtils.getView(this, R.id.fl__cursor__send_button_container);

        mainToolbar.setCursorItems(mainCursorItems);
        secondaryToolbar.setCursorItems(secondaryCursorItems);
        editMessageCursorToolbar.setVisibility(GONE);
        editMessageCursorToolbar.setCallback(this);

        cursorHeight = getResources().getDimensionPixelSize(R.dimen.new_cursor_height);
        secondaryToolbar.setTranslationY(2 * cursorHeight);
        cursorToolbarAnimationDuration = getResources().getInteger(R.integer.wire__animation__delay__regular);
        tooltip.setVisibility(View.GONE);
        connectEditText();
        editMessageBackgroundView.setVisibility(GONE);

        defaultEditTextColor = newCursorEditText.getCurrentTextColor();
        ColorDrawable dividerBg = (ColorDrawable) dividerView.getBackground();
        defaultDividerColor = dividerBg.getColor();

        // Emoji button
        LayoutInflater inflater = LayoutInflater.from(getContext());
        emojiButton = (CursorIconButton) inflater.inflate(R.layout.cursor__item, this, false);
        emojiButton.setCursorMenuItem(CursorMenuItem.EMOJI);
        emojiButton.setPressedBackgroundColor(ContextCompat.getColor(getContext(), R.color.light_graphite));
        int buttonWidth = getResources().getDimensionPixelSize(R.dimen.cursor__menu_button__diameter);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(buttonWidth,
                                                                       buttonWidth);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.setMarginEnd(getResources().getDimensionPixelSize(R.dimen.chathead__margin));
        emojiButtonContainer.addView(emojiButton, params);
        emojiButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cursorCallback == null) {
                    return;
                }
                mainToolbar.unselectItems();
                if (getContext().getString(R.string.glyph__emoji).equals(emojiButton.getText())) {
                    emojiButton.setText(R.string.glyph__keyboard);
                    cursorCallback.onEmojiButtonClicked(true);
                    resetEphemeralButton();
                } else {
                    emojiButton.setText(R.string.glyph__emoji);
                    cursorCallback.onEmojiButtonClicked(false);
                    resetEphemeralButton();
                }
            }
        });

        // Send button
        sendButton = (CursorIconButton) inflater.inflate(R.layout.cursor__item, this, false);
        sendButton.setText(R.string.glyph__send);
        sendButton.setTextColor(ContextCompat.getColor(getContext(), R.color.text__primary_dark));
        sendButtonContainer.addView(sendButton, new FrameLayout.LayoutParams(buttonWidth, buttonWidth));
        sendButton.setVisibility(View.INVISIBLE);

        // Ephemeral button
        ephemeralButton = (CursorIconButton) inflater.inflate(R.layout.cursor__item__ephemeral, this, false);
        ephemeralButton.setText(R.string.glyph__hourglass);
        ephemeralButton.setTextColor(defaultEditTextColor);
        ephemeralButton.setGravity(Gravity.CENTER);
        int ephemeralButtonWidth = getResources().getDimensionPixelSize(R.dimen.cursor__ephemeral_button__width);
        int ephemeralButtonHeight = getResources().getDimensionPixelSize(R.dimen.cursor__ephemeral_button__height);
        params = new FrameLayout.LayoutParams(ephemeralButtonWidth, ephemeralButtonHeight);
        params.gravity = Gravity.CENTER;
        sendButtonContainer.addView(ephemeralButton, params);
        ephemeralButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cursorCallback == null || conversation == null) {
                    return;
                }
                cursorCallback.onEphemeralButtonClicked(conversation.getEphemeralExpiration());
                ephemeralSelected = true;
                conversationModelObserver.updated(conversation);
            }
        });
        ephemeralButton.setVisibility(GONE);
    }

    public void setCursorCallback(CursorCallback cursorCallback) {
        this.cursorCallback = cursorCallback;
        mainToolbar.setCallback(this);
        secondaryToolbar.setCallback(this);
    }

    public void setAccentColor(int accentColor) {
        newCursorEditText.setAccentColor(accentColor);
        mainToolbar.setAccentColor(accentColor);
        this.accentColor = accentColor;
        if (conversation == null || !conversation.isEphemeral()) {
            sendButton.setSolidBackgroundColor(accentColor);
        }
        ephemeralColor = accentColor;
    }

    private void connectEditText() {
        newCursorEditText.addTextChangedListener(this);
        newCursorEditText.setOnEditorActionListener(this);

        newCursorEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus && cursorCallback != null) {
                    cursorCallback.onFocusChange(hasFocus);
                }
            }
        });
        newCursorEditText.setFocusableInTouchMode(true);
    }


    public void enableMessageWriting() {
        newCursorEditText.requestFocus();
    }

    // Edit Text

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    /**
     * Notifies container that edit text has changed. And if text is not empty it triggers
     * also the typing indicator.
     */
    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (isEditingMessage()) {
            if (message == null) {
                return;
            }
            boolean enableControls = !TextUtils.equals(newCursorEditText.getText(), message.getBody());
            hintView.setVisibility(View.GONE);
            editMessageCursorToolbar.enableEditControls(enableControls);
            return;
        }

        String text = charSequence.toString();
        if (cursorCallback != null) {
            cursorCallback.onEditTextHasChanged(newCursorEditText.getSelectionStart(), text);
        }

        if (TextUtils.isEmpty(charSequence.toString())) {
            hintView.setVisibility(View.VISIBLE);
        } else {
            hintView.setVisibility(View.GONE);
        }

        showTopbar(newCursorEditText.getLineCount() > maxLines);
    }

    @Override
    public void afterTextChanged(Editable editable) {
        // do nothing
    }

    /**
     * EditText callback when user sent text.
     */
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND ||
            (actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
             event != null &&
             event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
             event.getAction() == KeyEvent.ACTION_DOWN)) {

            if (isEditingMessage()) {
                onApproveEditMessage();
                return true;
            }

            String sendText = textView.getText().toString();
            if (TextUtils.isEmpty(sendText)) {
                return false;
            }
            if (cursorCallback != null) {
                cursorCallback.onMessageSubmitted(sendText);
            }
            return true;
        }
        return false;
    }

    public void showSendButtonAsEnterKey(boolean show) {
        if (show) {
            newCursorEditText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        } else {
            newCursorEditText.setImeOptions(EditorInfo.IME_ACTION_NONE);
        }
    }

    public void showSendButton(boolean show) {
        if (sendButtonIsVisible == show) {
            return;
        }
        sendButtonIsVisible = show;
        if (show) {
            if (isEditingMessage()) {
                return;
            }
            int duration = getResources().getInteger(R.integer.animation_duration_medium);
            ephemeralButton.setVisibility(GONE);
            sendButton.setVisibility(View.VISIBLE);
            sendButton.setAlpha(0);
            sendButton.animate()
                      .alpha(1f)
                      .setInterpolator(new Quart.EaseOut())
                      .setDuration(duration)
                      .setListener(new AnimatorListenerAdapter() {
                          @Override
                          public void onAnimationEnd(Animator animation) {
                              sendButton.setAlpha(1);
                          }
                      });

            sendButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (cursorCallback != null) {
                        String sendText = newCursorEditText.getText().toString();
                        if (TextUtils.isEmpty(sendText)) {
                            return;
                        }
                        cursorCallback.onMessageSubmitted(sendText);
                    }
                }
            });
        } else {
            if (conversation != null && conversation.getType() == IConversation.Type.ONE_TO_ONE) {
                ephemeralButton.setVisibility(VISIBLE);
            }
            sendButton.setVisibility(View.INVISIBLE);
            sendButton.setOnClickListener(null);
        }
    }

    public void setText(String text) {
        newCursorEditText.setText(text);
        newCursorEditText.setSelection(text.length());
    }

    public void appendText(String text) {
        newCursorEditText.append(text);
        newCursorEditText.setSelection(newCursorEditText.getText().length());
    }

    public void notifyKeyboardVisibilityChanged(boolean keyboardIsVisible, View currentFocus) {
        this.keyboardIsVisible = keyboardIsVisible;
        if (keyboardIsVisible) {
            cursorToolbarFrame.shrink();
            if (newCursorEditText.hasFocus() && cursorCallback != null) {
                cursorCallback.onCursorClicked();
            }
        } else {
            cursorToolbarFrame.expand();
        }
    }

    public boolean hasText() {
        return !TextUtils.isEmpty(newCursorEditText.getText().toString());
    }

    public String getText() {
        return newCursorEditText.getText().toString();
    }

    public int getSelection() {
        return newCursorEditText.getSelectionEnd();
    }

    public void setSelection(int selection) {
        this.newCursorEditText.setSelection(selection);
    }


    public void setConversation(IConversation conversation) {
        this.conversation = conversation;
        enableMessageWriting();
        resetMainAndSecondaryToolbars();
        closeEditMessage(false);
        conversationModelObserver.setAndUpdate(conversation);
    }

    @Override
    public void onCursorButtonClicked(CursorMenuItem item) {
        if (item == CursorMenuItem.DUMMY) {
            return;
        }
        if (cursorCallback != null) {
            cursorCallback.onCursorButtonClicked(item);
        }

        switch (item) {
            case MORE:
                showSecondaryCursorToolbar();
                break;
            case LESS:
                hideSecondaryCursorToolbar();
                break;
            case CAMERA:
            case AUDIO_MESSAGE:
                resetEmojiButton();
                resetEphemeralButton();
                break;
        }
    }

    @Override
    public void onCursorButtonLongPressed(CursorMenuItem cursorMenuItem) {
        if (cursorMenuItem == CursorMenuItem.DUMMY) {
            return;
        }
        if (cursorCallback != null) {
            cursorCallback.onCursorButtonLongPressed(cursorMenuItem);
        }
    }

    @Override
    public void onMotionEvent(CursorMenuItem cursorMenuItem, MotionEvent motionEvent) {
        if (cursorCallback != null) {
            cursorCallback.onMotionEventFromCursorButton(cursorMenuItem, motionEvent);
        }
    }

    @Override
    public void onShowTooltip(CursorMenuItem item, String message, View anchor) {
        if (tooltipEnabled) {
            return;
        }

        int w = (int) (anchor.getX() + anchor.getMeasuredWidth() / 2);
        int gravity;

        if (w < getMeasuredWidth() / 4) {
            gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

        } else if (w > 3 * getMeasuredWidth() / 4) {
            gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        } else {
            gravity = Gravity.CENTER;
        }

        ((FrameLayout.LayoutParams) tooltip.getLayoutParams()).gravity = gravity;

        tooltipEnabled = true;
        tooltip.setText(message);
        tooltip
            .animate()
            .alpha(1)
            .withStartAction(new Runnable() {
                @Override
                public void run() {
                    tooltip.setVisibility(View.VISIBLE);
                    tooltip.setAlpha(0);
                }
            });

        if (cursorCallback != null) {
            cursorCallback.onShowedActionHint(item);
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                dismissToolbar();
            }
        }, TOOLTIP_DURATION);
    }

    @Override
    public void onCloseEditMessage() {
        closeEditMessage(true);
    }

    @Override
    public void onResetEditMessage() {
        if (message == null) {
            return;
        }
        newCursorEditText.setText(message.getBody());
        newCursorEditText.setSelection(newCursorEditText.getText().length());
    }

    @Override
    public void onApproveEditMessage() {
        if (TextUtils.isEmpty(newCursorEditText.getText().toString().trim())) {
            message.recall();
            Toast.makeText(getContext(), R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show();
        } else {
            message.update(new MessageContent.Text(newCursorEditText.getText().toString()));
        }

        if (cursorCallback != null) {
            cursorCallback.onApprovedMessageEditing(message);
        }
        closeEditMessage(true);
    }

    public boolean isEditingMessage() {
        return isEditingMessage;
    }

    public void editMessage(Message message) {
        isEditingMessage = true;
        this.message = message;
        newCursorEditText.setText(message.getBody());
        newCursorEditText.setSelection(newCursorEditText.getText().length());

        if (sendButtonIsVisible) {
            showSendButton(false);
        }
        ephemeralButton.setVisibility(GONE);

        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white));
        ViewUtils.fadeInView(editMessageBackgroundView);
        newCursorEditText.setTextColor(ContextCompat.getColor(getContext(), R.color.text__primary_light));
        dividerView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.separator_light));

        showEditMessageToolbar();
    }

    public void closeEditMessage(boolean animated) {
        if (!isEditingMessage) {
            return;
        }
        message = null;
        newCursorEditText.setText("");
        hintView.setVisibility(VISIBLE);
        isEditingMessage = false;

        if (animated) {
            hideEditMessageToolbar();
            ViewUtils.fadeOutView(editMessageBackgroundView);
        } else {
            editMessageCursorToolbar.setVisibility(GONE);
            editMessageBackgroundView.setVisibility(GONE);
            resetMainAndSecondaryToolbars();
        }

        setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
        newCursorEditText.setTextColor(defaultEditTextColor);
        if (conversation != null && conversation.isEphemeral()) {
            dividerView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.ephemera));
        } else {
            dividerView.setBackgroundColor(defaultDividerColor);
        }

        if (cursorCallback != null) {
            cursorCallback.onClosedMessageEditing();
        }
    }

    private void dismissToolbar() {
        if (!tooltipEnabled) {
            return;
        }

        tooltipEnabled = false;
        tooltip
            .animate()
            .alpha(0)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    tooltip.setVisibility(View.GONE);
                }
            });
    }

    private void hideEditMessageToolbar() {
        if (showMainToolbarAnimator == null) {
            showMainToolbarAnimator = getShowToolbarAnimator(mainToolbar, -cursorHeight, 0);
        }

        if (hideEditMessageToolbarAnimator == null) {
            hideEditMessageToolbarAnimator = getHideToolbarAnimator(editMessageCursorToolbar, 0, 2 * cursorHeight);
        }

        mainToolbar.setVisibility(VISIBLE);
        showMainToolbarAnimator.start();
        hideEditMessageToolbarAnimator.start();
    }

    private void showEditMessageToolbar() {
        ObjectAnimator hideAnimator;
        if (mainToolbar.getVisibility() == VISIBLE) {
            if (hideMainToolbarAnimator == null) {
                hideMainToolbarAnimator = getHideToolbarAnimator(mainToolbar, 0, -cursorHeight);
            }
            hideAnimator = hideMainToolbarAnimator;
        } else {
            if (hideSecondaryToolbarAnimator == null) {
                hideSecondaryToolbarAnimator = getHideToolbarAnimator(secondaryToolbar, 0, 2 * cursorHeight);
            }
            hideAnimator = hideSecondaryToolbarAnimator;
        }

        if (showEditMessageToolbarAnimator == null) {
            showEditMessageToolbarAnimator = getShowToolbarAnimator(editMessageCursorToolbar, 2 * cursorHeight, 0);
        }
        editMessageCursorToolbar.setVisibility(VISIBLE);
        hideAnimator.start();
        showEditMessageToolbarAnimator.start();
    }

    private void showSecondaryCursorToolbar() {
        if (hideMainToolbarAnimator == null) {
            hideMainToolbarAnimator = getHideToolbarAnimator(mainToolbar, 0, -cursorHeight);
        }

        if (showSecondaryToolbarAnimator == null) {
            showSecondaryToolbarAnimator = getShowToolbarAnimator(secondaryToolbar, 2 * cursorHeight, 0);
        }

        secondaryToolbar.setVisibility(VISIBLE);
        hideMainToolbarAnimator.start();
        showSecondaryToolbarAnimator.start();
    }

    private void hideSecondaryCursorToolbar() {
        if (showMainToolbarAnimator == null) {
            showMainToolbarAnimator = getShowToolbarAnimator(mainToolbar, -cursorHeight, 0);
        }

        if (hideSecondaryToolbarAnimator == null) {
            hideSecondaryToolbarAnimator = getHideToolbarAnimator(secondaryToolbar, 0, 2 * cursorHeight);
        }

        mainToolbar.setVisibility(VISIBLE);
        showMainToolbarAnimator.start();
        hideSecondaryToolbarAnimator.start();
    }

    private ObjectAnimator getShowToolbarAnimator(View view, float fromValue, float toValue) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromValue, toValue);
        animator.setDuration(cursorToolbarAnimationDuration);
        animator.setStartDelay(getResources().getInteger(R.integer.animation_delay_short));
        animator.setInterpolator(new Expo.EaseOut());
        return animator;
    }

    private ObjectAnimator getHideToolbarAnimator(final View view, float fromValue, float toValue) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromValue, toValue);
        animator.setDuration(cursorToolbarAnimationDuration);
        animator.setInterpolator(new Expo.EaseIn());
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                if (view == null) {
                    return;
                }
                view.setVisibility(GONE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (view == null) {
                    return;
                }
                view.setVisibility(GONE);
            }
        });
        return animator;
    }

    public void showTopbar(boolean show) {
        if (show) {
            topBorder.setVisibility(View.VISIBLE);
        } else {
            topBorder.setVisibility(View.INVISIBLE);
        }
    }

    public void onExtendedCursorClosed() {
        mainToolbar.unselectItems();
        resetEmojiButton();
        resetEphemeralButton();
    }

    private void resetMainAndSecondaryToolbars() {
        mainToolbar.setTranslationY(0);
        mainToolbar.setVisibility(VISIBLE);
        secondaryToolbar.setTranslationY(2 * cursorHeight);
        secondaryToolbar.setVisibility(GONE);
    }

    private void resetEmojiButton() {
        emojiButton.setText(R.string.glyph__emoji);
    }

    private void resetEphemeralButton() {
        ephemeralSelected = false;
        if (conversation != null) {
            conversationModelObserver.updated(conversation);
        }
    }

    private void updateEphemeralButtonBackground() {
        if (conversation.isEphemeral()) {
            EphemeralExpiration expiration = conversation.getEphemeralExpiration();
            final String value;
            final int background;
            switch (expiration) {
                case ONE_DAY:
                    value = String.valueOf(expiration.milliseconds / 1000 / 60 / 60 / 24);
                    background = R.drawable.ephemeral_day;
                    break;
                case ONE_MINUTE:
                case FIVE_MINUTES:
                    value = String.valueOf(expiration.milliseconds / 1000 / 60);
                    background = R.drawable.ephemeral_min;
                    break;
                case FIVE_SECONDS:
                case FIFTEEN_SECONDS:
                case THIRTY_SECONDS:
                case NONE:
                default:
                    value = String.valueOf(expiration.milliseconds / 1000);
                    background = R.drawable.ephemeral_sec;
            }
            ephemeralButton.setBackgroundResource(background);
            ephemeralButton.setText(value);
            ephemeralButton.setTypeface(TypefaceUtils.getTypeface(getContext().getString(R.string.wire__typeface__regular)));
            ephemeralButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimensionPixelSize(R.dimen.wire__text_size__small));
        } else {
            ephemeralButton.setBackground(null);
            ephemeralButton.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName()));
            ephemeralButton.setText(R.string.glyph__hourglass);
            ephemeralButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, getContext().getResources().getDimensionPixelSize(R.dimen.wire__text_size__regular));
        }
    }
}
