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
package com.waz.zclient.pages.main.participants.dialog;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import com.waz.api.IConversation;
import com.waz.api.Message;
import com.waz.api.OtrClient;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.globallayout.KeyboardHeightObserver;
import com.waz.zclient.controllers.tracking.events.group.AddedMemberToGroupEvent;
import com.waz.zclient.controllers.tracking.events.group.CreatedGroupConversationEvent;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment;
import com.waz.zclient.pages.main.connect.ConnectRequestLoadMode;
import com.waz.zclient.pages.main.connect.PendingConnectRequestManagerFragment;
import com.waz.zclient.pages.main.connect.SendConnectRequestFragment;
import com.waz.zclient.pages.main.conversation.controller.ConversationScreenControllerObserver;
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController;
import com.waz.zclient.pages.main.participants.ParticipantFragment;
import com.waz.zclient.pages.main.participants.SingleParticipantFragment;
import com.waz.zclient.pages.main.participants.TabbedParticipantBodyFragment;
import com.waz.zclient.pages.main.pickuser.PickUserFragment;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.pickuser.controller.PickUserControllerScreenObserver;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.animation.HeightEvaluator;
import com.waz.zclient.ui.animation.interpolators.penner.Quart;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import com.waz.zclient.views.menus.ConfirmationMenu;

import java.util.List;

public class ParticipantsDialogFragment extends BaseFragment<ParticipantsDialogFragment.Container> implements
                                                                                                   ParticipantsStoreObserver,
                                                                                                   ParticipantFragment.Container,
                                                                                                   OnBackPressedListener,
                                                                                                   ConversationScreenControllerObserver,
                                                                                                   KeyboardHeightObserver,
                                                                                                   SingleParticipantFragment.Container,
                                                                                                   SendConnectRequestFragment.Container,
                                                                                                   PendingConnectRequestManagerFragment.Container,
                                                                                                   BlockedUserProfileFragment.Container,
                                                                                                   PickUserFragment.Container,
                                                                                                   PickUserControllerScreenObserver,
                                                                                                   ConfirmationObserver,
                                                                                                   AccentColorObserver {

    public static final String TAG = ParticipantsDialogFragment.class.getName();
    private static final String ARG_ANCHOR_RECT = "argRect";
    private static final String ARG_POS_X = "argPosX";
    private static final String ARG_POS_Y = "argPosY";
    private static final String ARG_USER_ID = "ARG_USER_ID";
    private static final String ARG__FIRST__PAGE = "ARG__FIRST__PAGE";
    private static final String ARG__ADD_TO_CONVERSATION = "ARG__ADD_TO_CONVERSATION";
    private static final String ARG__GROUP_CONVERSATION = "ARG__GROUP_CONVERSATION";

    private View mainParticipantsContainer;
    private View detailParticipantContainer;
    private FrameLayout dialogFrameLayout;
    private View marker;
    private ConfirmationMenu confirmationMenu;
    private int minParticipantsDialogHeight;
    private int regularParticipantsDialogHeight;
    private int selfGravity;

    private User user;
    private int participantDialogPadding;
    private int dialogTranslationX;
    private int dialogTranslationY;
    private int markerTranslationX;
    private int markerTranslationY;
    private boolean isInConfigurationChange;

    public static Fragment newParticipantButtonInstance(int x, int y, Rect rect, int firstPage) {
        ParticipantsDialogFragment participantsDialogFragment = new ParticipantsDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POS_X, x);
        args.putInt(ARG_POS_Y, y);
        args.putParcelable(ARG_ANCHOR_RECT, rect);
        args.putInt(ARG__FIRST__PAGE, firstPage);
        participantsDialogFragment.setArguments(args);
        return participantsDialogFragment;
    }

    public static Fragment newAvatarPopoverInstance(int x, int y, Rect rect, String userId) {
        ParticipantsDialogFragment participantsDialogFragment = new ParticipantsDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POS_X, x);
        args.putInt(ARG_POS_Y, y);
        args.putParcelable(ARG_ANCHOR_RECT, rect);
        args.putString(ARG_USER_ID, userId);
        args.putInt(ARG__FIRST__PAGE, TabbedParticipantBodyFragment.USER_PAGE);
        participantsDialogFragment.setArguments(args);
        return participantsDialogFragment;
    }

    public static Fragment newStartUiInstance(int x, int y, Rect rect, boolean groupConversation) {
        ParticipantsDialogFragment participantsDialogFragment = new ParticipantsDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POS_X, x);
        args.putInt(ARG_POS_Y, y);
        args.putParcelable(ARG_ANCHOR_RECT, rect);
        args.putBoolean(ARG__ADD_TO_CONVERSATION, true);
        args.putBoolean(ARG__GROUP_CONVERSATION, groupConversation);
        participantsDialogFragment.setArguments(args);
        return participantsDialogFragment;
    }

    private void adjustAccordingToAnchor() {
        Bundle bundle = getArguments();
        if (bundle == null || getView() == null) {
            return;
        }
        View view = getView();
        ViewGroup viewGroup = (ViewGroup) view.getParent();
        Rect rect = bundle.getParcelable(ARG_ANCHOR_RECT);
        int posX = bundle.getInt(ARG_POS_X);
        int posY = bundle.getInt(ARG_POS_Y);

        final int widthSpec = View.MeasureSpec.makeMeasureSpec(viewGroup.getMeasuredWidth()
                                                               - viewGroup.getPaddingLeft()
                                                               - viewGroup.getPaddingRight(),
                                                               View.MeasureSpec.AT_MOST);
        final int heightSpec = View.MeasureSpec.makeMeasureSpec(viewGroup.getMeasuredHeight()
                                                                - viewGroup.getPaddingTop()
                                                                - viewGroup.getPaddingBottom(),
                                                                View.MeasureSpec.AT_MOST);
        view.measure(widthSpec, heightSpec);

        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) dialogFrameLayout.getLayoutParams();
        int rootHeight = dialogFrameLayout.getMeasuredHeight();
        int rootWidth = dialogFrameLayout.getMeasuredWidth();

        int dialogHeight = rootHeight + layoutParams.bottomMargin + layoutParams.topMargin;
        int dialogWidth = rootWidth + layoutParams.leftMargin + layoutParams.rightMargin;

        setDialogPosition(rect, posX, posY, dialogWidth, dialogHeight);
    }

    private void setDialogPosition(Rect rect,
                                   int posX,
                                   int posY,
                                   int dialogWidth,
                                   int dialogHeight) {
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_TOOLBAR) {
            int screenWidth = ViewUtils.getRealDisplayWidth(getActivity());
            dialogTranslationX = screenWidth / 2 - dialogWidth / 2;
            marker.setVisibility(View.INVISIBLE);
        } else {
            dialogTranslationX = getResources().getDimensionPixelSize(R.dimen.framework__participants_dialog__pos_x);
            marker.setVisibility(View.VISIBLE);
        }

        int markerHeight = marker.getMeasuredHeight() / 2; // because we draw on our own
        int markerWidth = marker.getMeasuredWidth();

        markerTranslationX = posX + (rect.width() - markerWidth) / 2;

        int displayHeight;
        int displayWidth;

        boolean forceRight = getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.SEARCH ||
                             getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_MENU;

        if (ViewUtils.isInPortrait(getActivity())) {
            displayHeight = ViewUtils.getOrientationIndependentDisplayHeight(getActivity());
            displayWidth = ViewUtils.getOrientationIndependentDisplayWidth(getActivity());
        } else {
            displayHeight = ViewUtils.getOrientationIndependentDisplayWidth(getActivity());
            displayWidth = ViewUtils.getOrientationIndependentDisplayHeight(getActivity());
        }

        final int screenBottom = displayHeight - participantDialogPadding - ViewUtils.getStatusBarHeight(getActivity());
        final int screenRight = displayWidth - participantDialogPadding;
        final int screenLeft = participantDialogPadding;

        if ((posY - dialogHeight - markerHeight) >= participantDialogPadding && !forceRight) {
            // put above
            markerTranslationY = posY - markerHeight;
            dialogTranslationY = markerTranslationY - dialogHeight;
            marker.setRotation(0f);
            if (dialogTranslationX + dialogWidth > screenRight) {
                // too far right
                dialogTranslationX = screenRight - dialogWidth;
            } else if (dialogTranslationX < participantDialogPadding) {
                // too far left
                dialogTranslationX = participantDialogPadding;
            }
            selfGravity = Gravity.TOP;
        } else if (posY + rect.height() + dialogHeight + markerHeight < screenBottom && !forceRight) {
            // put below
            markerTranslationY = posY + rect.height() - markerHeight;
            dialogTranslationY = markerTranslationY + 2 * markerHeight; // 2 * because we draw on our own
            marker.setRotation(180f);
            if (dialogTranslationX + dialogWidth > screenRight) {
                // too far right
                dialogTranslationX = screenRight - dialogWidth;
            } else if (dialogTranslationX < participantDialogPadding) {
                // too far left
                dialogTranslationX = participantDialogPadding;
            }
            selfGravity = Gravity.BOTTOM;
        } else if (posX + rect.width() + markerWidth + dialogWidth <= displayWidth - participantDialogPadding || forceRight) {
            int tmp = markerHeight;
            //noinspection SuspiciousNameCombination
            markerHeight = markerWidth;
            markerWidth = tmp;

            // centered
            markerTranslationX = posX + rect.width() - markerWidth;
            dialogTranslationX = markerTranslationX + 2 * markerWidth;  // 2 * because we draw on our own

            markerTranslationY = (posY + rect.centerY()) - (markerHeight / 2);
            dialogTranslationY = (posY + rect.centerY()) - (dialogHeight / 2);
            marker.setRotation(90f);

            if (dialogTranslationY < participantDialogPadding) {
                // too high
                dialogTranslationY = participantDialogPadding;
            } else if (posY + dialogHeight > screenBottom) {
                // too low
                dialogTranslationY = displayHeight - participantDialogPadding - dialogHeight - ViewUtils.getStatusBarHeight(getActivity());
            }

            // too far right
            if (dialogTranslationX + dialogWidth > screenRight) {
                dialogTranslationX = screenRight - dialogWidth;
                markerTranslationX = dialogTranslationX - 2 * markerWidth;  // 2 * because we draw on our own
            }
            selfGravity = Gravity.RIGHT;
        } else {
            int tmp = markerHeight;
            //noinspection SuspiciousNameCombination
            markerHeight = markerWidth;
            markerWidth = tmp;

            // centered
            markerTranslationX = posX - markerWidth;
            dialogTranslationX = markerTranslationX - dialogWidth;

            markerTranslationY = (posY + rect.centerY()) - (markerHeight / 2);
            dialogTranslationY = (posY + rect.centerY()) - (dialogHeight / 2);
            marker.setRotation(270f);

            if (dialogTranslationY < participantDialogPadding) {
                // too high
                dialogTranslationY = participantDialogPadding;
            } else if (posY + dialogHeight > screenBottom) {
                // too low
                dialogTranslationY = displayHeight - participantDialogPadding - dialogHeight - ViewUtils.getStatusBarHeight(getActivity());
            }

            // too far left
            if (dialogTranslationX < screenLeft) {
                dialogTranslationX = screenLeft;
                markerTranslationX = dialogTranslationX + dialogWidth;
            }

            selfGravity = Gravity.LEFT;
        }

        dialogFrameLayout.setTranslationX(dialogTranslationX);
        dialogFrameLayout.setTranslationY(dialogTranslationY);

        marker.setTranslationX(markerTranslationX);
        marker.setTranslationY(markerTranslationY);
    }

    private void hide() {
        if (getControllerFactory() == null ||
            getControllerFactory().isTornDown()) {
            return;
        }
        DialogLaunchMode launchMode = getControllerFactory().getConversationScreenController().getPopoverLaunchMode();
        if (launchMode == null) {
            return;
        }
        switch (launchMode) {
            case PARTICIPANT_BUTTON:
            case CONVERSATION_TOOLBAR:
                getControllerFactory().getConversationScreenController().hideParticipants(true, false);
                if (getArguments().getBoolean(ARG__ADD_TO_CONVERSATION)) {
                    getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(IPickUserController.Destination.CURSOR);
                } else {
                    getControllerFactory().getPickUserController().hidePickUserWithoutAnimations(IPickUserController.Destination.PARTICIPANTS);
                }
                break;
            case AVATAR:
            case SEARCH:
                getControllerFactory().getPickUserController().hideUserProfile();
                break;
            case CONVERSATION_MENU:
                setVisible(false);
                break;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isInConfigurationChange = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_participants_dialog, viewGroup, false);

        dialogFrameLayout = ViewUtils.getView(view, R.id.fl__participant_dialog__root);
        marker = ViewUtils.getView(view, R.id.v__participant_dialog__marker);
        mainParticipantsContainer = ViewUtils.getView(view, R.id.fl__participant_dialog__main__container);
        detailParticipantContainer = ViewUtils.getView(view, R.id.fl__participant_dialog__detail__container);
        confirmationMenu = ViewUtils.getView(view, R.id.cm__participants_dialog__confirm_action);
        confirmationMenu.setVisibility(View.GONE);
        confirmationMenu.resetFullScreenPadding();
        int firstPage = getArguments().getInt(ARG__FIRST__PAGE);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            Bundle bundle = getArguments();
            user = null;
            if (bundle != null) {
                String userId = bundle.getString(ARG_USER_ID);
                if (userId != null) {
                    user = getStoreFactory().getPickUserStore().getUser(userId);
                }
            }
            if (getArguments().getBoolean(ARG__ADD_TO_CONVERSATION)) {
                transaction.replace(R.id.fl__participant_dialog__main__container,
                                    PickUserFragment.newInstance(true,
                                                                 getArguments().getBoolean(ARG__GROUP_CONVERSATION)),
                                    PickUserFragment.TAG);

            } else if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.PARTICIPANT_BUTTON ||
                getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_TOOLBAR) {
                transaction.add(R.id.fl__participant_dialog__main__container,
                                ParticipantFragment.newInstance(IConnectStore.UserRequester.PARTICIPANTS, firstPage),
                                ParticipantFragment.TAG);
            } else if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_MENU) { // NOPMD
                // do nothing, according to design the menu should open from bottom in conversation list
            } else {
                getControllerFactory().getDialogBackgroundImageController().setUser(user);
                transaction.add(R.id.fl__participant_dialog__detail__container,
                                SingleParticipantFragment.newInstance(true,
                                                                      IConnectStore.UserRequester.POPOVER),
                                SingleParticipantFragment.TAG);
                switch (user.getConnectionStatus()) {
                    case ACCEPTED:
                    case SELF:
                        getStoreFactory().getSingleParticipantStore().setUser(user);
                        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.COMMON_USER) {
                            transaction.add(R.id.fl__participant_dialog__main__container,
                                            SingleParticipantFragment.newInstance(true,
                                                                                  IConnectStore.UserRequester.POPOVER),
                                            SingleParticipantFragment.TAG);
                        } else {
                            transaction.add(R.id.fl__participant_dialog__main__container,
                                            ParticipantFragment.newInstance(IConnectStore.UserRequester.POPOVER, firstPage),
                                            ParticipantFragment.TAG);
                        }
                        break;
                    case CANCELLED:
                    case UNCONNECTED:
                        transaction.add(R.id.fl__participant_dialog__main__container,
                                        SendConnectRequestFragment.newInstance(user.getId(),
                                                                               IConnectStore.UserRequester.POPOVER),
                                        SendConnectRequestFragment.TAG);
                        break;
                    case PENDING_FROM_OTHER:
                    case PENDING_FROM_USER:
                    case IGNORED:
                        transaction.add(R.id.fl__participant_dialog__main__container,
                                        PendingConnectRequestManagerFragment.newInstance(user.getId(),
                                                                                  null,
                                                                                  ConnectRequestLoadMode.LOAD_BY_USER_ID,
                                                                                  IConnectStore.UserRequester.POPOVER),
                                        PendingConnectRequestManagerFragment.TAG);
                        break;
                    case BLOCKED:
                        transaction.add(R.id.fl__participant_dialog__main__container,
                                        BlockedUserProfileFragment.newInstance(user.getId(),
                                                                               IConnectStore.UserRequester.POPOVER),
                                        BlockedUserProfileFragment.TAG);
                        break;
                }
            }
            transaction.commit();
        }

        new Handler().post(new Runnable() {
            @Override
            public void run() {
                detailParticipantContainer.setTranslationX(dialogFrameLayout.getMeasuredWidth());
            }
        });

        participantDialogPadding = getResources().getDimensionPixelSize(R.dimen.framework__participants_dialog__display_padding);
        minParticipantsDialogHeight = getResources().getDimensionPixelSize(R.dimen.participant_dialog__min_height);
        regularParticipantsDialogHeight = getResources().getDimensionPixelSize(R.dimen.participant_dialog__regular_height);

        dialogFrameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                KeyboardUtils.hideKeyboard(getActivity());
                return true;
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adjustAccordingToAnchor();
        setVisible(true);
    }

    @SuppressLint("RtlHardcoded")
    private void setVisible(final boolean show) {
        if (getView() == null ||
            isInConfigurationChange) {
            removeFragment();
            return;
        }

        Animation animation;
        if (show) {
            switch (selfGravity) {
                case Gravity.TOP:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_show_gravity_top);
                    break;
                case Gravity.RIGHT:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_show_gravity_right);
                    break;
                case Gravity.LEFT:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_show_gravity_left);
                    break;
                case Gravity.BOTTOM:
                default:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_show_gravity_bottom);
            }
        } else {
            switch (selfGravity) {
                case Gravity.TOP:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_hide_gravity_top);
                    break;
                case Gravity.RIGHT:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_hide_gravity_right);
                    break;
                case Gravity.LEFT:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_hide_gravity_left);
                    break;
                case Gravity.BOTTOM:
                default:
                    animation = AnimationUtils.loadAnimation(getActivity(), R.anim.popover_hide_gravity_bottom);
            }
        }
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                if (show) {
                    return;
                }
                removeFragment();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        getView().startAnimation(animation);
    }

    private boolean removeFragment() {
        Fragment parentFragment = getParentFragment();
        if (parentFragment == null) {
            return true;
        }
        FragmentManager childFragmentManager = parentFragment.getChildFragmentManager();
        if (childFragmentManager == null) {
            return true;
        }
        FragmentTransaction transaction = childFragmentManager.beginTransaction();
        transaction.remove(this);
        transaction.commitAllowingStateLoss();
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        final IConversationScreenController conversationScreenController = getControllerFactory().getConversationScreenController();
        conversationScreenController.addConversationControllerObservers(this);
        getControllerFactory().getGlobalLayoutController().addKeyboardHeightObserver(this);
        getControllerFactory().getPickUserController().addPickUserScreenControllerObserver(this);
        getControllerFactory().getConfirmationController().addConfirmationObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        if (conversationScreenController.shouldShowDevicesTab()) {
            conversationScreenController.showUser(conversationScreenController.getRequestedDeviceTabUser());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) {
            getView().setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        hide();
                    }
                    return true;
                }
            });
        }
    }

    @Override
    public void onStop() {
        getControllerFactory().getConversationScreenController().removeConversationControllerObservers(this);
        getControllerFactory().getConversationScreenController().resetToMessageStream();
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getGlobalLayoutController().removeKeyboardHeightObserver(this);
        getControllerFactory().getPickUserController().removePickUserScreenControllerObserver(this);
        getControllerFactory().getConfirmationController().removeConfirmationObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mainParticipantsContainer = null;
        detailParticipantContainer = null;
        marker = null;
        dialogFrameLayout = null;
        confirmationMenu = null;
        super.onDestroyView();
    }

    @Override
    public void conversationUpdated(final IConversation conversation) {
        if (!(getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.PARTICIPANT_BUTTON ||
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_TOOLBAR)) {
            return;
        }
        updateGroupDialogBackground(conversation);
    }

    @Override
    public void participantsUpdated(UsersList participants) {

    }

    @Override
    public void otherUserUpdated(User otherUser) {

    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void dismissDialog() {
        hide();
    }

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof OnBackPressedListener && ((OnBackPressedListener) fragment).onBackPressed()) {
            return true;
        }

        if (animateBetweenMainAndDetail(true)) {
            return true;
        }

        hide();
        return true;
    }

    @Override
    public void onShowParticipants(View anchorView, boolean isSingleConversation, boolean isMemberOfConversation, boolean showDeviceTabIfSingle) {

    }

    @Override
    public void onHideParticipants(boolean backOrButtonPressed,
                                   boolean hideByConversationChange,
                                   boolean isSingleConversation) {
        setVisible(false);
    }

    @Override
    public void onShowEditConversationName(boolean show) {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onShowEditConversationName(show);
        }
    }

    @Override
    public void setListOffset(int offset) {

    }

    @Override
    public void onHeaderViewMeasured(int participantHeaderHeight) {

    }

    @Override
    public void onScrollParticipantsList(int verticalOffset, boolean scrolledToBottom) {

    }

    @Override
    public void onConversationLoaded() {

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //  ConversationActionObserver
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public void onShowOtrClient(OtrClient otrClient, User user) {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onShowOtrClient(otrClient, user);
        }
    }

    @Override
    public void onShowCurrentOtrClient() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onShowCurrentOtrClient();
        }
    }

    @Override
    public void onHideOtrClient() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onHideOtrClient();
        }
    }

    @Override
    public void onShowLikesList(Message message) {

    }

    @Override
    public void onShowUser(User user) {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onShowUser(user);
        }
    }

    @Override
    public void onHideUser() {
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.PARTICIPANT_BUTTON ||
            getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.CONVERSATION_TOOLBAR) {
            Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
            if (fragment instanceof ConversationScreenControllerObserver) {
                ((ConversationScreenControllerObserver) fragment).onHideUser();
            }
            updateGroupDialogBackground(getStoreFactory().getConversationStore().getCurrentConversation());
        } else {
            setVisible(false);
        }
    }

    private void updateGroupDialogBackground(IConversation conversation) {
        if (conversation == null) {
            getControllerFactory().getDialogBackgroundImageController().setImageAsset(null, false);
            return;
        }
        boolean blurred = conversation.getType() == IConversation.Type.GROUP &&
                          getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == DialogLaunchMode.PARTICIPANT_BUTTON;
        getControllerFactory().getDialogBackgroundImageController().setImageAsset(conversation.getBackground(), blurred);
    }

    @Override
    public void onAddPeopleToConversation() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onAddPeopleToConversation();
        }
    }

    @Override
    public void onShowConversationMenu(@IConversationScreenController.ConversationMenuRequester int requester, IConversation conversation, View anchorView) {
        if (requester != IConversationScreenController.USER_PROFILE_PARTICIPANTS &&
            requester != IConversationScreenController.CONVERSATION_DETAILS) {
            return;
        }
        Fragment fragment = getChildFragmentManager().findFragmentByTag(ParticipantFragment.TAG);
        if (fragment instanceof ConversationScreenControllerObserver) {
            ((ConversationScreenControllerObserver) fragment).onShowConversationMenu(requester,
                                                                                     conversation,
                                                                                     anchorView);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  KeyboardHeightObserver
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onKeyboardHeightChanged(int keyboardHeight) {
        if (getControllerFactory().getConversationScreenController().getPopoverLaunchMode() == null) {
            setVisible(false);
            getControllerFactory().getPickUserController().resetShowingPickUser(IPickUserController.Destination.CURSOR);
            getControllerFactory().getPickUserController().resetShowingPickUser(IPickUserController.Destination.PARTICIPANTS);
            return;
        }
        final boolean keyboardIsVisible = keyboardHeight > 0;
        switch (getControllerFactory().getConversationScreenController().getPopoverLaunchMode()) {
            case CONVERSATION_MENU:
            case PARTICIPANT_BUTTON:
                int minDialogHeight = ViewUtils.getOrientationDependentDisplayHeight(getActivity())
                                      - keyboardHeight
                                      - marker.getMeasuredHeight()
                                      - participantDialogPadding;

                if (minDialogHeight > minParticipantsDialogHeight) {
                    minDialogHeight = minParticipantsDialogHeight;
                }
                updateParticipantsDialogSizeAndPos(keyboardIsVisible, minDialogHeight, keyboardHeight);
                break;
        }
    }

    private void updateParticipantsDialogSizeAndPos(boolean keyboardIsVisible, int minParticipantsDialogHeight, int keyboardHeight) {
        // Expand / collapse when needed
        final boolean shouldExpand = !keyboardIsVisible &&
                                     isParticipantsDialogMinimized();
        final boolean shouldCollapse = keyboardIsVisible &&
                                       !isParticipantsDialogMinimized() &&
                                       ViewUtils.isInLandscape(getActivity());
        final ValueAnimator sizeAnimator;
        if (shouldExpand) {
            sizeAnimator = ValueAnimator.ofObject(new HeightEvaluator(dialogFrameLayout),
                                                  dialogFrameLayout.getMeasuredHeight(),
                                                  regularParticipantsDialogHeight);
        } else if (shouldCollapse) {
            sizeAnimator = ValueAnimator.ofObject(new HeightEvaluator(dialogFrameLayout),
                                                  dialogFrameLayout.getMeasuredHeight(),
                                                  minParticipantsDialogHeight);
        } else {
            sizeAnimator = null;
        }

        if (sizeAnimator != null) {
            sizeAnimator.setDuration(getResources().getInteger(R.integer.framework_animation_duration_short));
            sizeAnimator.start();
        }

        // Update vertical position
        if (keyboardIsVisible && !isParticipantsDialogShiftedUp()) {
            final int navigationBarHeight = ViewUtils.getNavigationBarHeight(getActivity());
            int dialogDY;
            int markerDY;
            if (selfGravity == Gravity.TOP) {
                dialogDY = navigationBarHeight - keyboardHeight + participantDialogPadding;
                markerDY = navigationBarHeight - keyboardHeight + participantDialogPadding;
            } else {
                dialogDY = -keyboardHeight;
                markerDY = -keyboardHeight;
            }
            if (shouldCollapse) {
                dialogDY += dialogFrameLayout.getMeasuredHeight() - minParticipantsDialogHeight;
            }
            dialogFrameLayout.animate()
                             .translationYBy(dialogDY)
                             .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                             .withEndAction(new Runnable() {
                                 @Override
                                 public void run() {
                                     if (getView() == null) {
                                         return;
                                     }
                                     // Invalidate to avoid view being incorrectly drawn multiple times due to animation
                                     getView().invalidate();
                                 }
                             })
                             .start();

            marker.animate()
                  .translationYBy(markerDY)
                  .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                  .start();
        } else if (!keyboardIsVisible && isParticipantsDialogShiftedUp()) {
            dialogFrameLayout.animate()
                             .translationY(dialogTranslationY)
                             .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                             .withEndAction(new Runnable() {
                                 @Override
                                 public void run() {
                                     if (getView() == null) {
                                         return;
                                     }
                                     // Invalidate to avoid view being incorrectly drawn multiple times due to animation
                                     getView().invalidate();
                                 }
                             })
                             .start();

            marker.animate()
                  .translationY(markerTranslationY)
                  .setDuration(getResources().getInteger(R.integer.framework_animation_duration_short))
                  .start();
        }
    }

    private boolean isParticipantsDialogMinimized() {
        return dialogFrameLayout.getMeasuredHeight() <= minParticipantsDialogHeight;
    }

    private boolean isParticipantsDialogShiftedUp() {
        return dialogFrameLayout.getTranslationY() < dialogTranslationY;
    }


    @Override
    public void dismissUserProfile() {
        setVisible(false);
    }

    @Override
    public void dismissSingleUserProfile() {
        getControllerFactory().getDialogBackgroundImageController().setUser(user);
        animateBetweenMainAndDetail(true);
    }

    @Override
    public void showRemoveConfirmation(User user) {

    }

    @Override
    public void onUnblockedUser(IConversation restoredConversationWithUser) {
        hide();
    }

    @Override
    public void onConnectRequestWasSentToUser() {
        getControllerFactory().getPickUserController().hideUserProfile();
    }

    /**
     * @return true if the animation was performed,
     *         false otherwise
     */
    private boolean animateBetweenMainAndDetail(boolean showGroup) {
        float startDetail;
        float endDetail;
        float startMain;
        float endMain;
        Interpolator interpolator;
        if (showGroup) {
            startDetail = 0;
            endDetail = dialogFrameLayout.getMeasuredWidth();
            startMain = -dialogFrameLayout.getMeasuredHeight();
            endMain = 0;
            interpolator = new Quart.EaseOut();
        } else {
            startDetail = dialogFrameLayout.getMeasuredWidth();
            endDetail = 0;
            startMain = 0;
            endMain = -dialogFrameLayout.getMeasuredHeight();
            interpolator = new Quart.EaseOut();
        }

        if (MathUtils.floatEqual(mainParticipantsContainer.getTranslationX(), endMain) &&
            MathUtils.floatEqual(detailParticipantContainer.getTranslationX(), endDetail)) {
            return false;
        }

        ObjectAnimator slideInDetailParticipantAnimation = ObjectAnimator.ofFloat(detailParticipantContainer,
                                                                                  View.TRANSLATION_X,
                                                                                  startDetail,
                                                                                  endDetail);
        slideInDetailParticipantAnimation.setDuration(getResources().getInteger(R.integer.framework_animation_duration_long));
        slideInDetailParticipantAnimation.setInterpolator(interpolator);

        ObjectAnimator slideOutMainAnimation = ObjectAnimator.ofFloat(mainParticipantsContainer,
                                                                      View.TRANSLATION_X,
                                                                      startMain,
                                                                      endMain);
        slideOutMainAnimation.setDuration(getResources().getInteger(R.integer.framework_animation_duration_long));
        slideOutMainAnimation.setInterpolator(interpolator);

        AnimatorSet participantTransition = new AnimatorSet();
        participantTransition.playTogether(slideOutMainAnimation,
                                           slideInDetailParticipantAnimation);
        participantTransition.start();
        return true;
    }

    @Override
    public void onShowPickUser(IPickUserController.Destination destination, View anchorView) {

    }

    @Override
    public void onHidePickUser(IPickUserController.Destination destination, boolean closeWithoutSelectingPeople) {
        dismissDialog();
    }

    @Override
    public void onShowUserProfile(User user, View anchorView) {

    }

    @Override
    public void onHideUserProfile() {
        setVisible(false);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  ConfirmationObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRequestConfirmation(ConfirmationRequest confirmationRequest, @IConfirmationController.ConfirmationMenuRequester int requester) {
        if (requester == IConfirmationController.CONVERSATION_LIST ||
                requester == IConfirmationController.CONVERSATION) {
            return;
        }
        confirmationMenu.onRequestConfirmation(confirmationRequest);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        if (getView() == null) {
            return;
        }
        confirmationMenu.setButtonColor(color);
    }

    @Override
    public void onAcceptedConnectRequest(IConversation conversation) {

    }

    @Override
    public void onAcceptedPendingOutgoingConnectRequest(IConversation conversation) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  PickUserFragment.Container
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void showIncomingPendingConnectRequest(IConversation conversation) {
        // noop
    }

    @Override
    public void onSelectedUsers(List<User> users, ConversationChangeRequester requester) {
        // TODO https://wearezeta.atlassian.net/browse/AN-3730
        getControllerFactory().getPickUserController().hidePickUser(getCurrentPickerDestination(), false);

        IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (currentConversation.getType() == IConversation.Type.ONE_TO_ONE) {
            getStoreFactory().getConversationStore().createGroupConversation(users, requester);
            if (!getStoreFactory().getNetworkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__create_group_conversation__no_network__title,
                                          R.string.conversation__create_group_conversation__no_network__message,
                                          R.string.conversation__create_group_conversation__no_network__button,
                                          null, true);
            }
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new CreatedGroupConversationEvent(true, (users.size() + 1)));
        } else if (currentConversation.getType() == IConversation.Type.GROUP) {
            currentConversation.addMembers(users);
            if (!getStoreFactory().getNetworkStore().hasInternetConnection()) {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.conversation__add_user__no_network__title,
                                          R.string.conversation__add_user__no_network__message,
                                          R.string.conversation__add_user__no_network__button,
                                          null, true);
            }
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new AddedMemberToGroupEvent(getParticipantsCount(), users.size()));
        }
        hide();
    }

    @Override
    public LoadingIndicatorView getLoadingViewIndicator() {
        return ViewUtils.getView(getView(), R.id.lbv__conversation__loading_indicator);
    }

    @Override
    public IPickUserController.Destination getCurrentPickerDestination() {
        return IPickUserController.Destination.CURSOR;
    }

    private int getParticipantsCount() {
        return getStoreFactory().getConversationStore().getCurrentConversation().getUsers().size();
    }

    public interface Container {
        void onOpenUrl(String url);
    }
}
