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
package com.waz.zclient.pages.main.participants;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.waz.api.CommonConnections;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.MessagesList;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.api.Verification;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.stores.connect.ConnectStoreObserver;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.ui.text.AccentColorEditText;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.ui.views.e2ee.ShieldView;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;

public class ParticipantHeaderFragment extends BaseFragment<ParticipantHeaderFragment.Container> implements KeyboardVisibilityObserver,
                                                                                                            View.OnClickListener,
                                                                                                            ParticipantsStoreObserver,
                                                                                                            ConversationScreenControllerObserver,
                                                                                                            ConnectStoreObserver,
                                                                                                            AccentColorObserver {
    public static final String TAG = ParticipantHeaderFragment.class.getName();
    private static final String ARG_USER_REQUESTER = "ARG_USER_REQUESTER";


    private View headerTopBorder;
    private TextView subHeader;
    private TextView closeButton;
    private AccentColorEditText headerEditText;
    private TextView headerReadOnlyTextView;
    private LinearLayout textViewAddressBookNameContainer;
    private View bottomBorder;
    private TextView penIcon;
    private TextView textViewAddressBookName;
    // Helps center title in 1:1
    private ShieldView shieldView;
    private IConnectStore.UserRequester userRequester;

    private Handler penIconHandler;
    private Runnable penIconRunnable = new Runnable() {
        @Override
        public void run() {
            if (penIcon == null) {
                return;
            }
            showPenIcon(false);
        }
    };
    private boolean isGroupConversation;

    public static ParticipantHeaderFragment newInstance(IConnectStore.UserRequester userRequester) {
        ParticipantHeaderFragment participantHeaderFragment = new ParticipantHeaderFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER_REQUESTER, userRequester);
        participantHeaderFragment.setArguments(args);
        return participantHeaderFragment;
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            Animation doNothingAnim = new AlphaAnimation(1, 1);
            doNothingAnim.setDuration(ViewUtils.getNextAnimationDuration(parent));
            return doNothingAnim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        userRequester = (IConnectStore.UserRequester) args.getSerializable(ARG_USER_REQUESTER);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_participants_header, container, false);

        headerTopBorder = ViewUtils.getView(rootView, R.id.v_participants__header__top_border);
        subHeader = ViewUtils.getView(rootView, R.id.ttv__participants__sub_header);
        textViewAddressBookNameContainer = ViewUtils.getView(rootView, R.id.ll__single_participants__real_name__container);
        textViewAddressBookNameContainer.setVisibility(View.GONE);
        textViewAddressBookName = ViewUtils.getView(rootView, R.id.ttv__address_book_name);
        closeButton = ViewUtils.getView(rootView, R.id.gtv__participants__close);
        headerReadOnlyTextView = ViewUtils.getView(rootView, R.id.ttv__participants__header);
        headerEditText = ViewUtils.getView(rootView, R.id.taet__participants__header__editable);
        bottomBorder = ViewUtils.getView(rootView, R.id.v_participants__header__bottom_border);
        shieldView = ViewUtils.getView(rootView, R.id.sv__otr__verified_shield);
        shieldView.setVisibility(View.INVISIBLE);
        penIcon = ViewUtils.getView(rootView, R.id.gtv__participants_header__pen_icon);
        showPenIcon(false);

        closeButton.setAlpha(0);
        closeButton.setOnClickListener(this);

        headerEditText.setOnTouchListener(headerOnTouchListener);
        headerEditText.setOnEditorActionListener(editorActionListener);

        // Hide bottom border initially
        bottomBorder.setVisibility(View.GONE);

        // Toggle color background
        rootView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getContainer().onClickedEmptyBackground();
            }
        });

        penIconHandler = new Handler();

        if (LayoutSpec.isTablet(getActivity())) {
            headerTopBorder.setVisibility(View.GONE);
        }

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();
        if (userRequester == IConnectStore.UserRequester.POPOVER) {
            getStoreFactory().getConnectStore().addConnectRequestObserver(this);
            final User user = getStoreFactory().getSingleParticipantStore().getUser();
            getStoreFactory().getConnectStore().loadUser(user.getId(), userRequester);
        } else {
            getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        }
        getControllerFactory().getConversationScreenController().addConversationControllerObservers(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);

        if (getControllerFactory().getConversationScreenController().isShowingParticipant()) {
            showCancelButton(true, false);
        }

        if (!getControllerFactory().getThemeController().isDarkTheme()) {
            headerEditText.setAccentColor(getControllerFactory().getAccentColorController().getColor());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        headerEditText.clearFocus();
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
    }

    @Override
    public void onPause() {
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        KeyboardUtils.hideKeyboard(getActivity());
        super.onPause();
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getConnectStore().removeConnectRequestObserver(this);
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        headerTopBorder = null;
        subHeader = null;
        textViewAddressBookNameContainer = null;
        closeButton = null;
        headerReadOnlyTextView = null;
        headerEditText = null;
        bottomBorder = null;
        penIconHandler.removeCallbacks(null);
        penIconHandler = null;
        penIcon = null;
        shieldView = null;
        super.onDestroyView();
    }


    @Override
    public void onClick(View v) {
        if (userRequester == IConnectStore.UserRequester.POPOVER) {
            getContainer().dismissDialog();
        } else {
            getControllerFactory().getConversationScreenController().hideParticipants(true, false);
        }
    }

    @Override
    public void conversationUpdated(IConversation conversation) {
        if (conversation == null) {
            return;
        }
        isGroupConversation = conversation.getType() == IConversation.Type.GROUP;
        if (isGroupConversation) {
            subHeader.setAllCaps(false);
            textViewAddressBookNameContainer.setVisibility(View.GONE);
            headerReadOnlyTextView.setText(conversation.getName());
            if (conversation.isMemberOfConversation()) {
                headerEditText.setText(conversation.getName());
                headerEditText.setVisibility(View.VISIBLE);
            } else {
                headerEditText.setVisibility(View.GONE);
            }

            shieldView.setVisibility(View.GONE);
            penIcon.setVisibility(View.VISIBLE);
            headerReadOnlyTextView.setGravity(Gravity.LEFT);
            ViewUtils.setMarginLeft(headerReadOnlyTextView,
                                    getResources().getDimensionPixelSize(R.dimen.participants__header__input_field__layout_margin));
            subHeader.setGravity(Gravity.LEFT);
            ViewUtils.setMarginRight(subHeader,
                                     getResources().getDimensionPixelSize(R.dimen.wire__padding__big));
            ViewUtils.setMarginLeft(subHeader,
                                    getResources().getDimensionPixelSize(R.dimen.wire__padding__big));

        } else {
            subHeader.setTextColor(getControllerFactory().getAccentColorController().getColor());
            subHeader.setAllCaps(true);
            // In 1:1 conversation, show full user name as conversation name in participant view
            headerEditText.setVisibility(View.GONE);
            penIcon.setVisibility(View.GONE);
            headerReadOnlyTextView.setGravity(Gravity.CENTER);
            ViewUtils.setMarginLeft(headerReadOnlyTextView, 0);
            subHeader.setGravity(Gravity.CENTER);
            ViewUtils.setMarginRight(subHeader, 0);
            ViewUtils.setMarginLeft(subHeader, 0);
            shieldView.setVisibility(conversation.getOtherParticipant().getVerified() == Verification.VERIFIED ?
                                     View.VISIBLE : View.INVISIBLE);
        }
    }

    @Override
    public void participantsUpdated(UsersList participants) {
        subHeader.setText(getResources().getQuantityString(R.plurals.participants__sub_header_xx_people,
                                                           participants.size(), participants.size()));
        subHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                              getResources().getDimension(R.dimen.wire__text_size__small));
        subHeader.setOnClickListener(null);

        // Hide header bottom border
        bottomBorder.setVisibility(View.GONE);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getContainer() != null) {
                    final int height = getView().getMeasuredHeight();
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            }
        });
    }

    @Override
    public void otherUserUpdated(final User otherUser) {
        setParticipant(otherUser);
    }

    public void showCancelButton(boolean show, boolean animate) {
        if (closeButton == null || headerTopBorder == null) {
            return;
        }

        if (show) {
            if (!animate) {
                closeButton.setAlpha(1);
            } else {
                headerTopBorder
                    .animate()
                    .alpha(0)
                    .setDuration(getResources().getInteger(R.integer.open_close_button__alpha__animation_duration))
                    .setStartDelay(getResources().getInteger(R.integer.open_close_button__alpha__delay))
                    .start();
                ObjectAnimator animator = ObjectAnimator.ofFloat(closeButton, View.ALPHA, 1);
                animator.setDuration(getResources().getInteger(R.integer.open_close_button__alpha__animation_duration))
                        .setStartDelay(getResources().getInteger(R.integer.open_close_button__alpha__delay));
                animator.start();
            }
        } else {
            if (!animate) {
                closeButton.setAlpha(0);
            } else {
                ObjectAnimator animator = ObjectAnimator.ofFloat(closeButton, View.ALPHA, 0);
                animator.setDuration(getResources().getInteger(R.integer.close_close_button__alpha__animation_duration))
                        .setStartDelay(getResources().getInteger(R.integer.close_close_button__alpha__delay));
                animator.start();

                headerTopBorder
                    .animate()
                    .alpha(1)
                    .setDuration(getResources().getInteger(R.integer.close_close_button__alpha__animation_duration))
                    .setStartDelay(getResources().getInteger(R.integer.close_close_button__alpha__delay))
                    .start();
            }
        }
    }

    private View.OnTouchListener headerOnTouchListener = new View.OnTouchListener() {
        private boolean downAction = false;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && downAction) {
                if (MathUtils.floatEqual(headerEditText.getAlpha(), 0f)) {
                    // only if not already visible and network is available
                    getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                        @Override
                        public void execute(NetworkMode networkMode) {
                            showEditHeader(true);
                        }

                        @Override
                        public void onNoNetwork() {
                            showOfflineRenameError();
                        }
                    });

                }
                downAction = false;
            } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downAction = true;
            }

            // consume touch event if there is no network.
            return !getStoreFactory().getNetworkStore().hasInternetConnection();

        }
    };

    private void showOfflineRenameError() {
        ViewUtils.showAlertDialog(getActivity(),
                                  R.string.alert_dialog__no_network__header,
                                  R.string.rename_conversation__no_network__message,
                                  R.string.alert_dialog__confirmation,
                                  null, true);
    }

    private TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {

        @Override
        public boolean onEditorAction(final TextView textView, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                if (LayoutSpec.isPhone(getActivity())) {
                    renameConversation();
                }

                closeHeaderEditing();

                return true;

            }
            return false;
        }

        private void closeHeaderEditing() {
            headerEditText.clearFocus();
            final int height = getView().getMeasuredHeight();
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            });
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(headerEditText.getWindowToken(), 0);
        }

    };

    private void resetName() {
        headerReadOnlyTextView.setText(getStoreFactory().getConversationStore().getCurrentConversation().getName());
    }

    private void renameConversation() {
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
                                                                      @Override
                                                                      public void execute(NetworkMode networkMode) {
                                                                          updateConversationName();
                                                                      }

                                                                      @Override
                                                                      public void onNoNetwork() {
                                                                          resetName();
                                                                          showOfflineRenameError();
                                                                      }
                                                                  }
                                                                       );
    }

    private void updateConversationName() {
        if (headerEditText == null) {
            return;
        }

        String text = headerEditText.getText().toString();
        if (text.equals(getStoreFactory().getConversationStore().getCurrentConversation().getName())) {
            return;
        }
        if (!TextUtils.isEmpty(text.trim())) {
            getStoreFactory().getConversationStore().getCurrentConversation().setConversationName(
                text);
            headerReadOnlyTextView.setText(
                text);
            getControllerFactory().getTrackingController().updateSessionAggregates(
                RangedAttribute.CONVERSATION_RENAMES);
        }
    }

    private void showEditHeader(boolean edit) {
        getContainer().editingHeader(edit);
        if (edit) {
            headerEditText.setText(headerReadOnlyTextView.getText());
            headerEditText.setAlpha(1);
            headerEditText.requestFocus();
            getControllerFactory().getFocusController().setFocus(IFocusController.CONVERSATION_EDIT_NAME);
            headerEditText.setSelection(headerEditText.getText().length());
            KeyboardUtils.showKeyboard(getActivity());
            headerReadOnlyTextView.setAlpha(0);
            subHeader.setAlpha(0);
            closeButton.setAlpha(0);
            closeButton.setEnabled(false);
            showPenIcon(false);
        } else {
            headerEditText.setAlpha(0);
            headerEditText.requestLayout();
            headerReadOnlyTextView.setAlpha(1);
            headerEditText.clearFocus();
            subHeader.setAlpha(1);
            closeButton.setAlpha(1);
            closeButton.setEnabled(true);

            if (LayoutSpec.isTablet(getActivity())) {
                renameConversation();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Conversation Manager Notifications
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {
        showCancelButton(true, true);
        if (!isSingleConversation && isMemberOfConversation) {
            startPenIconTimeOut();
        } else {
            if (penIcon != null) {
                showPenIcon(false);
            }
        }
    }

    @Override
    public void onHideParticipants(boolean orButtonPressed,
                                   boolean hideByConversationChange,
                                   boolean backOrButtonPressed) {

        if (LayoutSpec.isTablet(getActivity()) &&
            !hideByConversationChange) {
            renameConversation();
        }

        showCancelButton(false, true);
    }

    @Override
    public void onShowEditConversationName(boolean edit) {
        showEditHeader(edit);
    }

    @Override
    public void setListOffset(int offset) {

    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {

    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {
        if (bottomBorder == null) {
            return;
        }
        if (verticalOffset > 0) {
            bottomBorder.setVisibility(View.VISIBLE);
        } else {
            bottomBorder.setVisibility(View.GONE);
        }
    }

    @Override
    public void onConversationLoaded() {

    }

    @Override
    public void onShowUser(User user) {

    }

    @Override
    public void onHideUser() {

    }

    @Override
    public void onShowCommonUser(User user) {

    }

    @Override
    public void onHideCommonUser() {

    }

    @Override
    public void onAddPeopleToConversation() {

    }

    @Override
    public void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester,
                                       IConversation conversation,
                                       View anchorView) {

    }

    @Override
    public void onShowOtrClient(OtrClient otrClient, User user) {

    }

    @Override
    public void onShowCurrentOtrClient() {

    }

    @Override
    public void onHideOtrClient() {

    }

    @Override
    public void onShowLikesList(Message message) {

    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        if (!keyboardIsVisible) {
            showEditHeader(false);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  BackgroundImageObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private void startPenIconTimeOut() {
        if (penIcon == null) {
            return;
        }
        showPenIcon(true);
        penIconHandler.removeCallbacks(penIconRunnable);
        penIconHandler.postDelayed(penIconRunnable,
                                   getResources().getInteger(R.integer.profile__pen_icon__hide_time_out));
    }

    private void showPenIcon(boolean show) {
        if (show) {
            penIcon.setVisibility(View.VISIBLE);
        } else {
            penIcon.setVisibility(View.GONE);
        }
    }

    public void onConnectUserUpdated(final User user, IConnectStore.UserRequester usertype) {
        if (usertype != userRequester) {
            return;
        }
        setParticipant(user);
    }

    private void setParticipant(final User user) {
        headerReadOnlyTextView.setText(user.getName());

        if (user.isContact()) {
            textViewAddressBookNameContainer.setVisibility(View.VISIBLE);
            textViewAddressBookName.setText(user.getFirstContact().getDisplayName());
        } else {
            textViewAddressBookNameContainer.setVisibility(View.GONE);
        }

        subHeader.setText(user.getEmail());
        subHeader.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                              getResources().getDimension(R.dimen.participants__subheader__font_size__one_to_one));
        if (!TextUtils.isEmpty(user.getEmail())) {
            subHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Uri uri = Uri.parse(String.format("mailto:%s", user.getEmail()));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
            });
        } else {
            subHeader.setOnClickListener(null);
        }
        headerEditText.setVisibility(View.GONE);

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                if (getView() != null && getControllerFactory().getConversationScreenController() != null) {
                    final int height = getView().getMeasuredHeight();
                    getControllerFactory().getConversationScreenController().setParticipantHeaderHeight(height);
                }
            }
        });
    }

    @Override
    public void onCommonConnectionsUpdated(CommonConnections commonConnections) {

    }

    @Override
    public void onInviteRequestSent(IConversation conversation) {

    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        if (isGroupConversation) {
            return;
        }
        subHeader.setTextColor(color);
        if (!getControllerFactory().getThemeController().isDarkTheme()) {
            headerEditText.setAccentColor(color);
        }
    }

    public interface Container {

        void editingHeader(boolean editing);

        void onClickedEmptyBackground();

        void dismissDialog();
    }
}
