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
package com.waz.zclient.pages.main.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.ActionMenuView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.waz.api.AssetFactory;
import com.waz.api.AssetForUpload;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.ConversationsList;
import com.waz.api.EphemeralExpiration;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.api.InputStateIndicator;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.Self;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.api.Verification;
import com.waz.model.ConvId;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.WireContext;
import com.waz.zclient.camera.controllers.GlobalCameraController;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.controllers.ThemeController;
import com.waz.zclient.controllers.UserAccountsController;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.calling.CallingObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.giphy.GiphyObserver;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.controllers.orientation.OrientationControllerObserver;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.controllers.singleimage.SingleImageObserver;
import com.waz.zclient.controllers.tracking.events.conversation.EditedMessageEvent;
import com.waz.zclient.controllers.tracking.events.navigation.OpenedMoreActionsEvent;
import com.waz.zclient.conversation.CollectionController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.attributes.OpenedMediaAction;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SelectedTooLargeFileEvent;
import com.waz.zclient.core.controllers.tracking.events.media.CancelledRecordingAudioMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedActionHintEvent;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedEmojiKeyboardEvent;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedMediaActionEvent;
import com.waz.zclient.core.controllers.tracking.events.media.PreviewedAudioMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.SentPictureEvent;
import com.waz.zclient.core.controllers.tracking.events.media.SentVideoMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.StartedRecordingAudioMessageEvent;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.SyncErrorObserver;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.media.SoundController;
import com.waz.zclient.messages.MessagesListView;
import com.waz.zclient.messages.controllers.EditActionSupport;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer;
import com.waz.zclient.pages.extendedcursor.emoji.EmojiKeyboardLayout;
import com.waz.zclient.pages.extendedcursor.ephemeral.EphemeralLayout;
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout;
import com.waz.zclient.pages.extendedcursor.image.ImagePreviewLayout;
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterLayout;
import com.waz.zclient.pages.main.conversation.views.TypingIndicatorView;
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation;
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneObserver;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.preferences.PreferencesActivity;
import com.waz.zclient.preferences.PreferencesController;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.audiomessage.AudioMessageRecordingView;
import com.waz.zclient.ui.cursor.CursorCallback;
import com.waz.zclient.ui.cursor.CursorLayout;
import com.waz.zclient.ui.cursor.CursorMenuItem;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.views.e2ee.ShieldView;
import com.waz.zclient.utils.AssetUtils;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.SquareOrientation;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationFragment extends BaseFragment<ConversationFragment.Container> implements ConversationStoreObserver,
                                                                                                  CallingObserver,
                                                                                                  KeyboardVisibilityObserver,
                                                                                                  AccentColorObserver,
                                                                                                  ParticipantsStoreObserver,
                                                                                                  SyncErrorObserver,
                                                                                                  NavigationControllerObserver,
                                                                                                  SlidingPaneObserver,
                                                                                                  SingleImageObserver,
                                                                                                  GiphyObserver,
                                                                                                  OnBackPressedListener,
                                                                                                  CursorCallback,
                                                                                                  AudioMessageRecordingView.Callback,
                                                                                                  RequestPermissionsObserver,
                                                                                                  ImagePreviewLayout.Callback,
                                                                                                  AssetIntentsManager.Callback,
                                                                                                  PagerControllerObserver,
                                                                                                  CursorImagesLayout.Callback,
                                                                                                  VoiceFilterLayout.Callback,
                                                                                                  EmojiKeyboardLayout.Callback,
                                                                                                  ExtendedCursorContainer.Callback,
                                                                                                  EphemeralLayout.Callback,
                                                                                                  TypingIndicatorView.Callback,
                                                                                                  OrientationControllerObserver {
    public static final String TAG = ConversationFragment.class.getName();
    private static final String SAVED_STATE_PREVIEW = "SAVED_STATE_PREVIEW";
    private static final int REQUEST_VIDEO_CAPTURE = 911;
    private static final int CAMERA_PERMISSION_REQUEST_ID = 21;

    private static final String[] EXTENDED_CURSOR_PERMISSIONS = new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int OPEN_EXTENDED_CURSOR_IMAGES = 1254;

    private static final String[] FILE_SHARING_PERMISSION = new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int FILE_SHARING_PERMISSION_REQUEST_ID = 179;

    private static final String[] AUDIO_PERMISSION = new String[] {android.Manifest.permission.RECORD_AUDIO};
    private static final int AUDIO_PERMISSION_REQUEST_ID = 864;
    private static final int AUDIO_FILTER_PERMISSION_REQUEST_ID = 865;

    private InputStateIndicator inputStateIndicator;
    private UpdateListener typingListener;

    private TypingIndicatorView typingIndicatorView;
    private LoadingIndicatorView conversationLoadingIndicatorViewView;

    private FrameLayout invisibleFooter;

    private IConversation.Type toConversationType;
    private Toolbar toolbar;
    private ActionMenuView leftMenu;
    private TextView toolbarTitle;
    private ShieldView shieldView;
    private CursorLayout cursorLayout;
    private AudioMessageRecordingView audioMessageRecordingView;
    private ExtendedCursorContainer extendedCursorContainer;
    private List<URI> sharingUris = new ArrayList<>();
    private AssetIntentsManager assetIntentsManager;
    private ViewGroup containerPreview;
    private boolean isPreviewShown;
    private boolean isVideoMessageButtonClicked;
    private MessagesListView listView;
    private Self self = null;
    private Boolean isInLandscape = null;

    public static ConversationFragment newInstance() {
        return new ConversationFragment();
    }

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            if (toolbar == null ||
                toolbarTitle == null ||
                shieldView == null) {
                return;
            }

            shieldView.setVisibility(model.getVerified() == Verification.VERIFIED ? View.VISIBLE : View.GONE);

            toolbarTitle.setText(model.getName());

            if (!model.isMemberOfConversation()) {
                return;
            }

            inflateCollectionIcon();

            inject(UserAccountsController.class).setIsGroupListener(new ConvId(model.getId()), new Callback<Boolean>() {
                @Override
                public void callback(Boolean isGroup) {
                    if (toolbar == null) {
                        return;
                    }
                    toolbar.getMenu().clear();
                    if (!isGroup) {
                        toolbar.inflateMenu(R.menu.conversation_header_menu_video);
                    } else {
                        toolbar.inflateMenu(R.menu.conversation_header_menu_audio);
                    }
                }
            });
        }
    };

    @Override
    public void onOrientationHasChanged(SquareOrientation squareOrientation) {
        boolean isInLandscape = ViewUtils.isInLandscape(getContext());

        if (this.isInLandscape == null) {
            this.isInLandscape = isInLandscape; // initial call, just set the flag
            return;
        }

        if (isInLandscape != this.isInLandscape) {
            this.isInLandscape = isInLandscape;
            boolean conversationListVisible = (getControllerFactory().getNavigationController().getCurrentPage() == Page.CONVERSATION_LIST);

            if (isInLandscape && !conversationListVisible) {
                int duration = getResources().getInteger(R.integer.framework_animation_duration_short);
                // post to give the RootFragment the chance to drive its animations first
                new Handler().postDelayed(new Runnable() {
                    @Override public void run() {
                        FragmentActivity activity = getActivity();
                        if (activity != null) {
                            activity.onBackPressed();
                        }
                    }
                }, duration);
            }
        }
    }

    private final MessageContent.Asset.ErrorHandler assetErrorHandler = new MessageContent.Asset.ErrorHandler() {
        @Override
        public void noWifiAndFileIsLarge(long sizeInBytes, NetworkMode net, final MessageContent.Asset.Answer answer) {
            if (getActivity() == null) {
                answer.ok();
                return;
            }
            AlertDialog dialog = ViewUtils.showAlertDialog(getActivity(),
                                                           R.string.asset_upload_warning__large_file__title,
                                                           R.string.asset_upload_warning__large_file__message_default,
                                                           R.string.asset_upload_warning__large_file__button_accept,
                                                           R.string.asset_upload_warning__large_file__button_cancel,
                                                           new DialogInterface.OnClickListener() {
                                                               @Override
                                                               public void onClick(DialogInterface dialog, int which) {
                                                                   answer.ok();
                                                               }
                                                           },
                                                           new DialogInterface.OnClickListener() {
                                                               @Override
                                                               public void onClick(DialogInterface dialog, int which) {
                                                                   answer.cancel();
                                                               }
                                                           }
                                                          );
            dialog.setCancelable(false);
            if (sizeInBytes > 0) {
                String fileSize = Formatter.formatFileSize(getContext(), sizeInBytes);
                dialog.setMessage(getString(R.string.asset_upload_warning__large_file__message, fileSize));
            }
        }
    };


    private final MessageContent.Asset.ErrorHandler assetErrorHandlerVideo = new MessageContent.Asset.ErrorHandler() {
        @Override
        public void noWifiAndFileIsLarge(long sizeInBytes, NetworkMode net, final MessageContent.Asset.Answer answer) {
            if (getActivity() == null) {
                answer.ok();
                return;
            }
            AlertDialog dialog = ViewUtils.showAlertDialog(getActivity(),
                                                           R.string.asset_upload_warning__large_file__title,
                                                           R.string.asset_upload_warning__large_file__message_default,
                                                           R.string.asset_upload_warning__large_file__button_accept,
                                                           R.string.asset_upload_warning__large_file__button_cancel,
                                                           new DialogInterface.OnClickListener() {
                                                               @Override
                                                               public void onClick(DialogInterface dialog, int which) {
                                                                   answer.ok();
                                                               }
                                                           },
                                                           new DialogInterface.OnClickListener() {
                                                               @Override
                                                               public void onClick(DialogInterface dialog, int which) {
                                                                   answer.cancel();
                                                               }
                                                           }
                                                          );
            dialog.setCancelable(false);
            if (sizeInBytes > 0) {
                dialog.setMessage(getString(R.string.asset_upload_warning__large_file__message__video));
            }
        }
    };

    private final MessageContent.Asset.ErrorHandler assetErrorHandlerAudio = new MessageContent.Asset.ErrorHandler() {
        @Override
        public void noWifiAndFileIsLarge(long sizeInBytes, NetworkMode net, MessageContent.Asset.Answer answer) {
            answer.ok();
        }
    };

    private final ModelObserver<Self> selfModelObserver = new ModelObserver<Self>() {
        @Override
        public void updated(Self model) {
            self = model;
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (nextAnim == 0 ||
            getContainer() == null ||
            getControllerFactory().isTornDown()) {
            return super.onCreateAnimation(transit, enter, nextAnim);
        } else if (nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_in || nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_out) {
            int width = ViewUtils.getOrientationDependentDisplayWidth(getActivity()) - getResources().getDimensionPixelSize(
                R.dimen.framework__sidebar_width);
            return new MessageStreamAnimation(enter,
                                              getResources().getInteger(R.integer.wire__animation__duration__medium),
                                              0,
                                              width);
        } else if (getControllerFactory().getPickUserController().isHideWithoutAnimations()) {
            return new ConversationListAnimation(0,
                                                 getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                 enter,
                                                 0,
                                                 0,
                                                 false,
                                                 1f);
        } else if (enter) {
            return new ConversationListAnimation(0,
                                                 getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                                 enter,
                                                 getResources().getInteger(R.integer.framework_animation_duration_long),
                                                 getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                 false,
                                                 1f);
        }
        return new ConversationListAnimation(0,
                                             getResources().getDimensionPixelSize(R.dimen.open_new_conversation__thread_list__max_top_distance),
                                             enter,
                                             getResources().getInteger(R.integer.framework_animation_duration_medium),
                                             0,
                                             false,
                                             1f);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assetIntentsManager = new AssetIntentsManager(getActivity(), this, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, viewGroup, false);

        extendedCursorContainer = ViewUtils.getView(view, R.id.ecc__conversation);
        containerPreview = ViewUtils.getView(view, R.id.fl__conversation_overlay);
        cursorLayout = ViewUtils.getView(view, R.id.cl__cursor);
        new EditActionSupport((WireContext) getActivity(), cursorLayout); // TODO: remove that once cursorLayout is reimplemented in scala
        audioMessageRecordingView = ViewUtils.getView(view, R.id.amrv_audio_message_recording);
        toolbar = ViewUtils.getView(view, R.id.t_conversation_toolbar);
        leftMenu = ViewUtils.getView(view, R.id.conversation_left_menu);
        toolbarTitle = ViewUtils.getView(toolbar, R.id.tv__conversation_toolbar__title);
        shieldView = ViewUtils.getView(view, R.id.sv__conversation_toolbar__verified_shield);
        shieldView.setVisibility(View.GONE);
        typingIndicatorView = ViewUtils.getView(view, R.id.tiv_typing_indicator_view);
        typingIndicatorView.setCallback(this);
        listView = ViewUtils.getView(view, R.id.messages_list_view);
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getControllerFactory().getConversationScreenController().showParticipants(toolbar, false);
            }
        });
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_audio_call:
                    getControllerFactory().getCallingController().startCall(false);
                    cursorLayout.closeEditMessage(false);
                    return true;
                case R.id.action_video_call:
                    getControllerFactory().getCallingController().startCall(true);
                    cursorLayout.closeEditMessage(false);
                    return true;
            }
            return false;
            }
        });
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            if (cursorLayout == null) {
                return;
            }

            cursorLayout.closeEditMessage(false);
            getActivity().onBackPressed();
            KeyboardUtils.closeKeyboardIfShown(getActivity());
            }
        });

        leftMenu.setOnMenuItemClickListener(new ActionMenuView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_collection:
                    getCollectionController().openCollection();
                    return true;
            }
            return false;
            }
        });

        if (LayoutSpec.isTablet(getContext()) && ViewUtils.isInLandscape(getContext())) {
            toolbar.setNavigationIcon(null);
        }

        conversationLoadingIndicatorViewView = ViewUtils.getView(view, R.id.lbv__conversation__loading_indicator);

        // invisible footer to scroll over inputfield
        invisibleFooter = new FrameLayout(getActivity());
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                       getResources().getDimensionPixelSize(R.dimen.cursor__list_view_footer__height));
        invisibleFooter.setLayoutParams(params);

        cursorLayout.showSendButton(false);

        // Recording audio messages
        audioMessageRecordingView.setCallback(this);

        if (LayoutSpec.isTablet(getActivity())) {
            view.setBackgroundColor(Color.WHITE);
        }

        if (savedInstanceState != null) {
            isPreviewShown = savedInstanceState.getBoolean(SAVED_STATE_PREVIEW, false);
        }

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        audioMessageRecordingView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getGlobalLayoutController().addKeyboardHeightObserver(extendedCursorContainer);
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(extendedCursorContainer);
        getControllerFactory().getRequestPermissionsController().addObserver(this);
        getControllerFactory().getOrientationController().addOrientationControllerObserver(this);
        cursorLayout.setCursorCallback(this);
        hideSendButtonIfNeeded();
        final String draftText = getStoreFactory().draftStore().getDraft(getStoreFactory().conversationStore().getCurrentConversation());
        if (!TextUtils.isEmpty(draftText)) {
            cursorLayout.setText(draftText);
        }

        audioMessageRecordingView.setDarkTheme(((BaseActivity) getActivity()).injectJava(ThemeController.class).isDarkTheme());

        if (!getControllerFactory().getConversationScreenController().isConversationStreamUiInitialized()) {
            getStoreFactory().conversationStore().addConversationStoreObserverAndUpdate(this);
        } else {
            getStoreFactory().conversationStore().addConversationStoreObserver(this);
        }
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
        getControllerFactory().getNavigationController().addPagerControllerObserver(this);

        getControllerFactory().getGiphyController().addObserver(this);
        getControllerFactory().getSingleImageController().addSingleImageObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().participantsStore().addParticipantsStoreObserver(this);
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
        getStoreFactory().inAppNotificationStore().addInAppNotificationObserver(this);
        getControllerFactory().getSlidingPaneController().addObserver(this);

        extendedCursorContainer.setCallback(this);
        selfModelObserver.setAndUpdate(getStoreFactory().zMessagingApiStore().getApi().getSelf());
    }

    @Override
    public void onResume() {
        super.onResume();
        cursorLayout.showSendButtonAsEnterKey(!getPreferencesController().isSendButtonEnabled());
        if (LayoutSpec.isTablet(getContext())) {
            conversationModelObserver.setAndUpdate(getStoreFactory().conversationStore().getCurrentConversation());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        assetIntentsManager.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_STATE_PREVIEW, isPreviewShown);
    }

    @Override
    public void onPause() {
        super.onPause();
        KeyboardUtils.hideKeyboard(getActivity());
        hideAudioMessageRecording();
    }

    @Override
    public void onStop() {
        extendedCursorContainer.close(true);
        extendedCursorContainer.setCallback(null);
        cursorLayout.setCursorCallback(null);
        getControllerFactory().getGlobalLayoutController().removeKeyboardHeightObserver(extendedCursorContainer);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(extendedCursorContainer);
        getControllerFactory().getOrientationController().removeOrientationControllerObserver(this);
        getControllerFactory().getGiphyController().removeObserver(this);
        getControllerFactory().getSingleImageController().removeSingleImageObserver(this);

        if (!cursorLayout.isEditingMessage()) {
            getStoreFactory().draftStore().setDraft(getStoreFactory().conversationStore().getCurrentConversation(),
                                                       cursorLayout.getText().trim());
        }
        getStoreFactory().inAppNotificationStore().removeInAppNotificationObserver(this);
        getStoreFactory().participantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);

        getStoreFactory().conversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        getControllerFactory().getSlidingPaneController().removeObserver(this);
        getControllerFactory().getConversationScreenController().setConversationStreamUiReady(false);
        getControllerFactory().getRequestPermissionsController().removeObserver(this);

        selfModelObserver.pauseListening();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        containerPreview = null;
        cursorLayout = null;
        conversationLoadingIndicatorViewView = null;
        if (inputStateIndicator != null) {
            inputStateIndicator.removeUpdateListener(typingListener);
            inputStateIndicator = null;
        }
        typingIndicatorView.clear();
        typingIndicatorView = null;
        typingListener = null;
        conversationModelObserver.clear();
        toolbarTitle = null;
        toolbar = null;
        selfModelObserver.clear();
        super.onDestroyView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Container implementations
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowSingleImage(Message message) {
    }

    @Override
    public void onShowUserImage(User user) {

    }

    @Override
    public void onHideSingleImage() {
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Notifications
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(final IConversation fromConversation,
                                                final IConversation toConversation,
                                                final ConversationChangeRequester conversationChangeRequester) {

        if (toConversation == null) {
            return;
        }

        if (LayoutSpec.isPhone(getContext())) {
            conversationModelObserver.setAndUpdate(toConversation);
        }

        if (isPreviewShown && fromConversation != null && !toConversation.getId().equals(fromConversation.getId())) {
            onCancelPreview();
        }

        extendedCursorContainer.close(true);

        getControllerFactory().getConversationScreenController().setSingleConversation(toConversation.getType() == IConversation.Type.ONE_TO_ONE);

        int duration = getResources().getInteger(R.integer.framework_animation_duration_short);
        // post to give the RootFragment the chance to drive its animations first
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (cursorLayout == null) {
                    return;
                }

                final boolean changeToDifferentConversation = fromConversation == null ||
                                                              !fromConversation.getId().equals(toConversation.getId());


                // handle draft
                if (fromConversation != null && changeToDifferentConversation &&
                    !cursorLayout.isEditingMessage()) {
                    getStoreFactory().draftStore().setDraft(fromConversation, cursorLayout.getText().trim());
                }

                if (toConversation.getType() == IConversation.Type.WAIT_FOR_CONNECTION) {
                    return;
                }

                KeyboardUtils.hideKeyboard(getActivity());
                conversationLoadingIndicatorViewView.hide();
                cursorLayout.enableMessageWriting();

                if (changeToDifferentConversation) {
                    getControllerFactory().getConversationScreenController().setConversationStreamUiReady(false);
                    toConversationType = toConversation.getType();
                    getControllerFactory().getSharingController().maybeResetSharedText(fromConversation);
                    getControllerFactory().getSharingController().maybeResetSharedUris(fromConversation);


                    cursorLayout.setVisibility(toConversation.isActive() ? View.VISIBLE : View.GONE);
                    if (!inSplitPortraitMode()) {
                        resetCursor();
                    }

                    final String draftText = getStoreFactory().draftStore().getDraft(toConversation);
                    if (TextUtils.isEmpty(draftText)) {
                        resetCursor();
                    } else {
                        cursorLayout.setText(draftText);
                    }
                    cursorLayout.setConversation(toConversation);

                    hideAudioMessageRecording();
                }

                final boolean isSharing = getControllerFactory().getSharingController().isSharedConversation(
                    toConversation);
                final boolean isSharingText = !TextUtils.isEmpty(getControllerFactory().getSharingController().getSharedText()) && isSharing;
                final List<URI> sharedFileUris = getControllerFactory().getSharingController().getSharedFileUris();
                final boolean isSharingFiles = !(sharedFileUris == null || sharedFileUris.isEmpty()) && isSharing;
                if (isSharing) {
                    if (isSharingText) {
                        final String draftText = getControllerFactory().getSharingController().getSharedText();
                        if (TextUtils.isEmpty(draftText)) {
                            resetCursor();
                        } else {
                            cursorLayout.setText(draftText);
                        }
                        cursorLayout.enableMessageWriting();
                        KeyboardUtils.showKeyboard(getActivity());
                        getControllerFactory().getSharingController().maybeResetSharedText(toConversation);
                    } else if (isSharingFiles) {
                        if (PermissionUtils.hasSelfPermissions(getActivity(), FILE_SHARING_PERMISSION)) {
                            for (URI uri : sharedFileUris) {
                                getStoreFactory().conversationStore().sendMessage(AssetFactory.fromContentUri(uri),
                                                                                     assetErrorHandler);
                            }
                        } else {
                            sharingUris.addAll(sharedFileUris);
                            ActivityCompat.requestPermissions(getActivity(),
                                                              FILE_SHARING_PERMISSION,
                                                              FILE_SHARING_PERMISSION_REQUEST_ID);
                        }
                        getControllerFactory().getSharingController().maybeResetSharedUris(toConversation);
                    }
                }


                if (inputStateIndicator != null) {
                    inputStateIndicator.getTypingUsers().removeUpdateListener(typingListener);
                }

                inputStateIndicator = toConversation.getInputStateIndicator();
                typingIndicatorView.setInputStateIndicator(inputStateIndicator);

                if (inputStateIndicator != null) {
                    inputStateIndicator.getTypingUsers().addUpdateListener(typingListener);
                }
            }
        }, duration);

        // Saving factories since this fragment may be re-created before the runnable is done,
        // but we still want runnable to work.
        final IStoreFactory storeFactory = getStoreFactory();
        final IControllerFactory controllerFactory = getControllerFactory();
        // TODO: Remove when call issue is resolved with https://wearezeta.atlassian.net/browse/CM-645
        // And also why do we use the ConversationFragment to start a call from somewhere else....
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (storeFactory == null || storeFactory.isTornDown() ||
                    controllerFactory == null || controllerFactory.isTornDown()) {
                    return;
                }

                switch (conversationChangeRequester) {
                    case START_CONVERSATION_FOR_VIDEO_CALL:
                        controllerFactory.getCallingController().startCall(true);
                        break;
                    case START_CONVERSATION_FOR_CALL:
                        controllerFactory.getCallingController().startCall(false);
                        break;
                    case START_CONVERSATION_FOR_CAMERA:
                        controllerFactory.getCameraController().openCamera(CameraContext.MESSAGE);
                        break;
                }
            }
        }, 1000);
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        cursorLayout.notifyKeyboardVisibilityChanged(keyboardIsVisible, currentFocus);
    }

    private void inflateCollectionIcon() {
        MenuInflater inflater = getActivity().getMenuInflater();
        leftMenu.getMenu().clear();

        boolean searchInProgress = !getCollectionController().contentSearchQuery().currentValue("").get().originalString().isEmpty();
        if (searchInProgress) {
            inflater.inflate(R.menu.conversation_header_menu_collection_searching, leftMenu.getMenu());
        } else {
            inflater.inflate(R.menu.conversation_header_menu_collection, leftMenu.getMenu());
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Cursor callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onEditTextHasChanged(int cursorPosition, String text) {
        if (inputStateIndicator != null) {
            if (text.isEmpty()) {
                inputStateIndicator.textCleared();
            } else {
                inputStateIndicator.textChanged();
            }
        }

        if (getPreferencesController().isSendButtonEnabled()) {
            cursorLayout.showSendButton(!TextUtils.isEmpty(text));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        assetIntentsManager.onActivityResult(requestCode, resultCode, data);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  AccentColorObserver
    //
    //////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        cursorLayout.setAccentColor(color);
        conversationLoadingIndicatorViewView.setColor(color);
        audioMessageRecordingView.setAccentColor(color);
        extendedCursorContainer.setAccentColor(color);
    }

    @Override
    public void conversationUpdated(IConversation conversation) {
        if (conversation == null || getStoreFactory() == null || getStoreFactory().isTornDown()) {
            return;
        }
        if (!LayoutSpec.isTablet(getActivity())) {
            toolbarTitle.setText(conversation.getName());
        }
        if (cursorLayout == null) {
            return;
        }

        final IConversation currentConversation = getStoreFactory().conversationStore().getCurrentConversation();

        if (currentConversation == null || !currentConversation.isMemberOfConversation()) {
            cursorLayout.setVisibility(View.GONE);
        } else {
            cursorLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void participantsUpdated(UsersList participants) {

    }

    @Override
    public void otherUserUpdated(User otherUser) {

    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  NavigationController Callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPageVisible(Page page) {
        if (page == Page.MESSAGE_STREAM) {
            inflateCollectionIcon();
            cursorLayout.enableMessageWriting();
        }
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //  GroupCallingStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStartCall(boolean withVideo) {

    }

    public IConversation.Type getConversationType() {
        return toConversationType;
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //  CurrentFocusObserver
    //
    //////////////////////////////////////////////////////////////////////////////


    private boolean inSplitPortraitMode() {
        return LayoutSpec.isTablet(getActivity()) && ViewUtils.isInPortrait(getActivity()) && getControllerFactory().getNavigationController().getPagerPosition() == 0;
    }

    @Override
    public void onSearch(String keyword) {

    }

    @Override
    public void onRandomSearch() {

    }

    @Override
    public void onTrendingSearch() {

    }

    @Override
    public void onCloseGiphy() {
        resetCursor();
    }

    @Override
    public void onCancelGiphy() {

    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //  SlidingPaneObserver
    //
    //////////////////////////////////////////////////////////////////////////////


    @Override
    public void onPanelSlide(View panel, float slideOffset) {

    }

    @Override
    public void onPanelOpened(View panel) {
        KeyboardUtils.closeKeyboardIfShown(getActivity());
    }

    @Override
    public void onPanelClosed(View panel) {

    }

    private void resetCursor() {
        cursorLayout.setText("");
    }

    @Override
    public void onSyncError(final ErrorsList.ErrorDescription errorDescription) {
        switch (errorDescription.getType()) {
            case CANNOT_SEND_ASSET_FILE_NOT_FOUND:
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.asset_upload_error__not_found__title,
                                          R.string.asset_upload_error__not_found__message,
                                          R.string.asset_upload_error__not_found__button,
                                          null,
                                          true);
                errorDescription.dismiss();
                break;
            case CANNOT_SEND_ASSET_TOO_LARGE:
                AlertDialog dialog = ViewUtils.showAlertDialog(getActivity(),
                                                               R.string.asset_upload_error__file_too_large__title,
                                                               R.string.asset_upload_error__file_too_large__message_default,
                                                               R.string.asset_upload_error__file_too_large__button,
                                                               null,
                                                               true);
                long maxAllowedSizeInBytes = AssetFactory.getMaxAllowedAssetSizeInBytes();
                if (maxAllowedSizeInBytes > 0) {
                    String maxFileSize = Formatter.formatShortFileSize(getContext(), maxAllowedSizeInBytes);
                    dialog.setMessage(getString(R.string.asset_upload_error__file_too_large__message, maxFileSize));
                }

                errorDescription.dismiss();
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SelectedTooLargeFileEvent());
                break;
            case RECORDING_FAILURE:
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.audio_message__recording__failure__title,
                                          R.string.audio_message__recording__failure__message,
                                          R.string.alert_dialog__confirmation,
                                          null,
                                          true);
                errorDescription.dismiss();

                break;
            case CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION:
                onErrorCanNotSentMessageToUnverifiedConversation(errorDescription);
                break;
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onCursorButtonClicked(CursorMenuItem cursorMenuItem) {
        final IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
        switch (cursorMenuItem) {
            case AUDIO_MESSAGE:
                if (PermissionUtils.hasSelfPermissions(getActivity(), AUDIO_PERMISSION)) {
                    openExtendedCursor(ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING);
                } else {
                    ActivityCompat.requestPermissions(getActivity(),
                                                      AUDIO_PERMISSION,
                                                      AUDIO_FILTER_PERMISSION_REQUEST_ID);
                }
                break;
            case CAMERA:
                if (LayoutSpec.isTablet(getContext())) {
                    KeyboardUtils.closeKeyboardIfShown(getActivity());
                    getControllerFactory().getCameraController().openCamera(CameraContext.MESSAGE);
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.PHOTO, conversation));
                } else {

                    if (PermissionUtils.hasSelfPermissions(getContext(), EXTENDED_CURSOR_PERMISSIONS)) {
                        openExtendedCursor(ExtendedCursorContainer.Type.IMAGES);
                    } else {
                        ActivityCompat.requestPermissions(getActivity(),
                                                          EXTENDED_CURSOR_PERMISSIONS,
                                                          OPEN_EXTENDED_CURSOR_IMAGES);
                    }
                }
                break;
            case PING:
                getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
                    @Override
                    public void execute(NetworkMode networkMode) {
                        getStoreFactory().conversationStore().knockCurrentConversation();
                        SoundController ctrl = inject(SoundController.class);
                        if (ctrl != null) {
                            ctrl.playPingFromMe();
                        }
                    }
                });
                TrackingUtils.onSentPingMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                getStoreFactory().conversationStore().getCurrentConversation());
                break;
            case SKETCH:
                getControllerFactory().getDrawingController().showDrawing(null,
                                                                          IDrawingController.DrawingDestination.SKETCH_BUTTON);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.SKETCH, conversation));
                break;
            case FILE:
                assetIntentsManager.openFileSharing();
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.FILE, conversation));
                break;
            case VIDEO_MESSAGE:
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.VIDEO_MESSAGE, conversation));
                isVideoMessageButtonClicked = true;
                getCameraController().releaseCamera(new Callback<Void>() {
                    @Override
                    public void callback(Void v) {
                        if (!isVideoMessageButtonClicked || assetIntentsManager == null) {
                            return;
                        }
                        isVideoMessageButtonClicked = false;
                        assetIntentsManager.maybeCaptureVideo(getActivity(), AssetIntentsManager.IntentType.VIDEO_CURSOR_BUTTON);
                    }
                });
                break;
            case LOCATION:
                GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
                if (ConnectionResult.SUCCESS == googleAPI.isGooglePlayServicesAvailable(getContext())) {
                    KeyboardUtils.hideKeyboard(getActivity());
                    getControllerFactory().getLocationController().showShareLocation();
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.LOCATION, conversation));
                } else {
                    Toast.makeText(getContext(), R.string.location_sharing__missing_play_services, Toast.LENGTH_LONG).show();
                }
                break;
            case MORE:
            case LESS:
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedMoreActionsEvent(
                    getConversationTypeString()));
                break;
            case GIF:
                getControllerFactory().getGiphyController().handleInput(cursorLayout.getText());
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.GIPHY, conversation));
                break;
        }
    }

    private CollectionController getCollectionController() {
        return ((BaseActivity) getActivity()).injectJava(CollectionController.class);
    }

    private GlobalCameraController getCameraController() {
        return ((BaseActivity) getActivity()).injectJava(GlobalCameraController.class);
    }

    private PreferencesController getPreferencesController() {
        return ((BaseActivity) getActivity()).injectJava(PreferencesController.class);
    }

    private void openExtendedCursor(ExtendedCursorContainer.Type type) {
        final IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
        switch (type) {
            case NONE:
                break;
            case VOICE_FILTER_RECORDING:
                extendedCursorContainer.openVoiceFilter(this);
                hideSendButtonIfNeeded();
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.AUDIO_MESSAGE,
                                                                                                            conversation));
                break;
            case IMAGES:
                extendedCursorContainer.openCursorImages(this);
                hideSendButtonIfNeeded();
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.PHOTO,
                                                                                                            conversation));
                break;
        }
    }

    @Override
    public void onCursorButtonLongPressed(CursorMenuItem cursorMenuItem) {
        switch (cursorMenuItem) {
            case AUDIO_MESSAGE:
                if (PermissionUtils.hasSelfPermissions(getActivity(), AUDIO_PERMISSION)) {
                    extendedCursorContainer.close(true);
                    if (audioMessageRecordingView.getVisibility() == View.VISIBLE) {
                        break;
                    }
                    SoundController ctrl = inject(SoundController.class);
                    if (ctrl != null) {
                        ctrl.shortVibrate();
                    }
                    audioMessageRecordingView.prepareForRecording();
                    audioMessageRecordingView.setVisibility(View.VISIBLE);
                    final IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.cursorAction(OpenedMediaAction.AUDIO_MESSAGE,
                                                                                                                conversation));
                    ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new StartedRecordingAudioMessageEvent(
                        getConversationTypeString(),
                        true));
                } else {
                    ActivityCompat.requestPermissions(getActivity(), AUDIO_PERMISSION, AUDIO_PERMISSION_REQUEST_ID);
                }
                break;
        }
    }

    @Override
    public void onMotionEventFromCursorButton(CursorMenuItem cursorMenuItem, MotionEvent motionEvent) {
        if (cursorMenuItem != CursorMenuItem.AUDIO_MESSAGE ||
            audioMessageRecordingView == null ||
            audioMessageRecordingView.getVisibility() == View.INVISIBLE) {
            return;
        }

        audioMessageRecordingView.onMotionEventFromAudioMessageButton(motionEvent);
    }


    @Override
    public void onMessageSubmitted(String message) {
        if (TextUtils.isEmpty(message.trim())) {
            return;
        }
        resetCursor();
        getStoreFactory().conversationStore().sendMessage(message);
        TrackingUtils.onSentTextMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                        getStoreFactory().conversationStore().getCurrentConversation());

        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(null);
        getControllerFactory().getSharingController().maybeResetSharedText(getStoreFactory().conversationStore().getCurrentConversation());
    }

    public void onFocusChange(boolean hasFocus) {
        if (cursorLayout == null) {
            return;
        }

        if (hasFocus) {
            getControllerFactory().getFocusController().setFocus(IFocusController.CONVERSATION_CURSOR);
        }

        if (LayoutSpec.isPhone(getActivity()) || !getControllerFactory().getPickUserController().isShowingPickUser(
            IPickUserController.Destination.CONVERSATION_LIST)) {
            return;
        }

        // On tablet, apply Page.MESSAGE_STREAM soft input mode when conversation cursor has focus (soft input mode of page gets changed when left startui is open)
        int softInputMode = hasFocus ?
                            getControllerFactory().getGlobalLayoutController().getSoftInputModeForPage(Page.MESSAGE_STREAM)
                                     :
                            getControllerFactory().getGlobalLayoutController().getSoftInputModeForPage(Page.PICK_USER);
        ViewUtils.setSoftInputMode(getActivity().getWindow(), softInputMode, TAG);
    }

    @Override
    public void onCursorClicked() {
        if (!cursorLayout.isEditingMessage()) {
            listView.scrollToBottom();
        }
    }

    @Override
    public void onShowedActionHint(CursorMenuItem item) {
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedActionHintEvent(item.name(),
                                                                                          getConversationTypeString()));
    }

    @Override
    public void onApprovedMessageEditing(Message message) {
        KeyboardUtils.hideKeyboard(getActivity());
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new EditedMessageEvent(message));
    }

    @Override
    public void onEmojiButtonClicked(boolean showEmojiKeyboard) {
        if (showEmojiKeyboard) {
            KeyboardUtils.hideKeyboard(getActivity());
            extendedCursorContainer.openEmojis(getControllerFactory().getUserPreferencesController().getRecentEmojis(),
                                               getControllerFactory().getUserPreferencesController().getUnsupportedEmojis(),
                                               this);
            boolean withBot = getStoreFactory().conversationStore().getCurrentConversation().isOtto();
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new OpenedEmojiKeyboardEvent(withBot));
            cursorLayout.showSendButton(true);
        } else {
            extendedCursorContainer.close(false);
            KeyboardUtils.showKeyboard(getActivity());
        }
    }

    @Override
    public void onEphemeralButtonClicked(EphemeralExpiration currentEphemeralExpiration) {
        extendedCursorContainer.openEphemeral(this, currentEphemeralExpiration);
        if (currentEphemeralExpiration == EphemeralExpiration.NONE) {
            IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.ephemeral(conversation,
                                                                                                     false));
        }
    }

    @Override
    public void onEphemeralButtonDoubleClicked(EphemeralExpiration currentEphemeralExpiration) {
        EphemeralExpiration lastExpiraton = EphemeralExpiration.getForMillis(getControllerFactory().getUserPreferencesController().getLastEphemeralValue());
        if (lastExpiraton.equals(EphemeralExpiration.NONE)) {
            return;
        }
        if (currentEphemeralExpiration.equals(EphemeralExpiration.NONE)) {
            onEphemeralExpirationSelected(lastExpiraton, true);
            IConversation conversation = getStoreFactory().conversationStore().getCurrentConversation();
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(OpenedMediaActionEvent.ephemeral(conversation, true));
        } else {
            onEphemeralExpirationSelected(EphemeralExpiration.NONE, true);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (isPreviewShown) {
            onCancelPreview();
            return true;
        }

        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            getChildFragmentManager().popBackStack();
            return true;
        }

        if (extendedCursorContainer.isExpanded()) {
            extendedCursorContainer.close(false);
            return true;
        }

        if (cursorLayout.isEditingMessage()) {
            cursorLayout.closeEditMessage(true);
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (assetIntentsManager.onRequestPermissionsResult(requestCode, grantResults)) {
            return;
        }

        switch (requestCode) {
            case OPEN_EXTENDED_CURSOR_IMAGES:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    openExtendedCursor(ExtendedCursorContainer.Type.IMAGES);
                }
                break;
            case CAMERA_PERMISSION_REQUEST_ID:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
                    }
                    startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
                } else {
                    onCameraPermissionsFailed();
                }
                break;
            case FILE_SHARING_PERMISSION_REQUEST_ID:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    for (URI uri : sharingUris) {
                        getStoreFactory().conversationStore().sendMessage(AssetFactory.fromContentUri(uri),
                                                                             assetErrorHandler);
                    }
                    sharingUris.clear();
                } else {
                    ViewUtils.showAlertDialog(getActivity(),
                                              R.string.asset_upload_error__not_found__title,
                                              R.string.asset_upload_error__not_found__message,
                                              R.string.asset_upload_error__not_found__button,
                                              null,
                                              true);
                }
                break;
            case AUDIO_PERMISSION_REQUEST_ID:
                // No actions required if permission is granted
                // TODO: https://wearezeta.atlassian.net/browse/AN-4027 Show information dialog if permission is not granted
                break;
            case AUDIO_FILTER_PERMISSION_REQUEST_ID:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    openExtendedCursor(ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING);
                } else {
                    Toast.makeText(getActivity(),
                                   R.string.audio_message_error__missing_audio_permissions,
                                   Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }

    }

    private void onCameraPermissionsFailed() {
        Toast.makeText(getActivity(),
                       R.string.video_message_error__missing_camera_permissions,
                       Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSendAudioMessage(AudioAssetForUpload audioAssetForUpload,
                                   AudioEffect appliedAudioEffect,
                                   boolean sentWithQuickAction) {
        getStoreFactory().conversationStore().sendMessage(audioAssetForUpload, assetErrorHandlerAudio);
        hideAudioMessageRecording();
        TrackingUtils.tagSentAudioMessageEvent(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                               audioAssetForUpload,
                                               appliedAudioEffect,
                                               true,
                                               sentWithQuickAction,
                                               getStoreFactory().conversationStore().getCurrentConversation());
    }

    @Override
    public void onCancel() {
        extendedCursorContainer.close(false);
    }

    @Override
    public void onAudioMessageRecordingStarted() {
        getControllerFactory().getGlobalLayoutController().keepScreenAwake();
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new StartedRecordingAudioMessageEvent(
            getConversationTypeString(),
            false));
    }

    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {
        getStoreFactory().conversationStore().sendMessage(audioAssetForUpload, assetErrorHandlerAudio);
        hideAudioMessageRecording();
        TrackingUtils.tagSentAudioMessageEvent(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                               audioAssetForUpload,
                                               appliedAudioEffect,
                                               false,
                                               false,
                                               getStoreFactory().conversationStore().getCurrentConversation());
        extendedCursorContainer.close(true);

    }

    @Override
    public void onCancelledAudioMessageRecording() {
        hideAudioMessageRecording();
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new CancelledRecordingAudioMessageEvent(
            getConversationTypeString()));
    }

    @Override
    public void onPreviewedAudioMessage() {
        ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new PreviewedAudioMessageEvent(getConversationTypeString()));
    }

    @Override
    public void onStartedRecordingAudioMessage() {
        getControllerFactory().getGlobalLayoutController().keepScreenAwake();
    }

    private void hideAudioMessageRecording() {
        if (audioMessageRecordingView.getVisibility() == View.INVISIBLE) {
            return;
        }
        audioMessageRecordingView.reset();
        audioMessageRecordingView.setVisibility(View.INVISIBLE);
        getControllerFactory().getGlobalLayoutController().resetScreenAwakeState();
    }

    private void onErrorCanNotSentMessageToUnverifiedConversation(final ErrorsList.ErrorDescription errorDescription) {
        if (getControllerFactory().getNavigationController().getCurrentPage() != Page.MESSAGE_STREAM) {
            return;
        }

        KeyboardUtils.hideKeyboard(getActivity());

        final IConversation currentConversation = errorDescription.getConversation();
        final Iterable<? extends User> users = currentConversation.getUsers();
        final Map<User, String> userNameMap = new HashMap<>();
        int tmpUnverifiedDevices = 0;
        int userCount = 0;
        final boolean onlySelfChanged;
        for (User user : users) {
            if (user.getVerified() == Verification.VERIFIED) {
                continue;
            }
            userCount++;
            userNameMap.put(user, user.getDisplayName());
            for (OtrClient client : user.getOtrClients()) {
                if (client.getVerified() == Verification.VERIFIED) {
                    continue;
                }
                tmpUnverifiedDevices++;
            }
        }

        if (self != null && self.getUser().getVerified() != Verification.VERIFIED) {
            onlySelfChanged = userCount == 0;
            userCount++;
            userNameMap.put(self.getUser(), getString(R.string.conversation_degraded_confirmation__header__you));
            for (OtrClient client : self.getUser().getOtrClients()) {
                if (client.getVerified() == Verification.VERIFIED) {
                    continue;
                }
                tmpUnverifiedDevices++;
            }
        } else {
            onlySelfChanged = false;
        }

        final List<String> userNameList = new ArrayList<>(userNameMap.values());
        final int userNameCount = userNameList.size();

        final String header;
        if (userNameCount == 0) {
            header = getResources().getString(R.string.conversation__degraded_confirmation__header__someone);
        } else if (userNameCount == 1) {
            final int unverifiedDevices = Math.max(1, tmpUnverifiedDevices);
            header = getResources().getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user,
                                                      unverifiedDevices,
                                                      userNameList.get(0));
        } else {
            header = getString(R.string.conversation__degraded_confirmation__header__multiple_user,
                               TextUtils.join(", ", userNameList.subList(0, userNameCount - 1)),
                               userNameList.get(userNameCount - 1));
        }
        int tmpMessageCount = 0;
        for (Message m : errorDescription.getMessages()) {
            tmpMessageCount++;
        }
        final int messageCount = Math.max(1, tmpMessageCount);
        final String message = getResources().getQuantityString(R.plurals.conversation__degraded_confirmation__message,
                                                                messageCount);


        final ConfirmationCallback callback = new ConfirmationCallback() {
            @Override
            public void positiveButtonClicked(boolean checkboxIsSelected) {
                final Iterable<? extends Message> messages = errorDescription.getMessages();
                for (Message message : messages) {
                    message.retry();
                }
                errorDescription.dismiss();
            }

            @Override
            public void negativeButtonClicked() {
            }

            @Override
            public void canceled() {
            }

            @Override
            public void onHideAnimationEnd(boolean confirmed, boolean canceled, boolean checkboxIsSelected) {
                if (confirmed || canceled) {
                    return;
                }
                if (onlySelfChanged) {
                    getContext().startActivity(PreferencesActivity.getOtrDevicesPreferencesIntent(getContext()));
                } else {
                    final View anchorView = ViewUtils.getView(getActivity(), R.id.cursor_menu_item_participant);
                    getControllerFactory().getConversationScreenController().showParticipants(anchorView, true);
                }

            }
        };
        final String positiveButton = getString(R.string.conversation__degraded_confirmation__positive_action);
        final String negativeButton = onlySelfChanged ?
            getString(R.string.conversation__degraded_confirmation__negative_action_self) :
            getResources().getQuantityString(R.plurals.conversation__degraded_confirmation__negative_action, userCount);
        final ConfirmationRequest request = new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(message)
            .withPositiveButton(positiveButton)
            .withNegativeButton(negativeButton)
            .withConfirmationCallback(callback)
            .withCancelButton()
            .withBackgroundImage(R.drawable.degradation_overlay)
            .withWireTheme(((BaseActivity) getActivity()).injectJava(ThemeController.class).getThemeDependentOptionsTheme())
            .build();

        getControllerFactory().getConfirmationController().requestConfirmation(request,
                                                                               IConfirmationController.CONVERSATION);
    }

    private String getConversationTypeString() {
        return getConversationType() != null ? getConversationType().name() : "";
    }

    @Override
    public void openCamera() {
        getControllerFactory().getCameraController().openCamera(CameraContext.MESSAGE);
    }

    @Override
    public void openVideo() {
        assetIntentsManager.maybeCaptureVideo(getActivity(), AssetIntentsManager.IntentType.VIDEO);
    }

    @Override
    public void openGallery() {
        assetIntentsManager.openGallery();
    }

    @Override
    public void onPictureTaken(ImageAsset imageAsset) {
        showImagePreview(imageAsset, ImagePreviewLayout.Source.CAMERA);
    }

    @Override
    public void onGalleryPictureSelected(ImageAsset asset) {
        isPreviewShown = true;
        showImagePreview(asset, ImagePreviewLayout.Source.IN_APP_GALLERY);
    }

    private void showImagePreview(ImageAsset asset, ImagePreviewLayout.Source source) {
        ImagePreviewLayout imagePreviewLayout = createPreviewLayout();
        imagePreviewLayout.setImageAsset(asset,
                                         source,
                                         this);
        imagePreviewLayout.setAccentColor(getControllerFactory().getAccentColorController().getAccentColor().getColor());
        imagePreviewLayout.setTitle(getStoreFactory().conversationStore().getCurrentConversation().getName());

        containerPreview.addView(imagePreviewLayout);
        openPreview(containerPreview);
    }

    private ImagePreviewLayout createPreviewLayout() {
        return (ImagePreviewLayout) LayoutInflater.from(getContext()).inflate(
            R.layout.fragment_cursor_images_preview,
            containerPreview,
            false);
    }

    private void openPreview(View containerPreview) {
        isPreviewShown = true;
        getControllerFactory().getNavigationController().setPagerEnabled(false);
        containerPreview.setTranslationY(getView().getMeasuredHeight());
        containerPreview
            .animate()
            .translationY(0)
            .setDuration(getResources().getInteger(R.integer.animation_duration_medium))
            .setInterpolator(new Expo.EaseOut());
    }

    @Override
    public void onCancelPreview() {
        isPreviewShown = false;
        getControllerFactory().getNavigationController().setPagerEnabled(true);
        containerPreview
            .animate()
            .translationY(getView().getMeasuredHeight())
            .setDuration(getResources().getInteger(R.integer.animation_duration_medium))
            .setInterpolator(new Expo.EaseIn())
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (containerPreview != null) {
                        containerPreview.removeAllViews();
                    }
                }
            });
    }

    @Override
    public void onSketchOnPreviewPicture(ImageAsset imageAsset,
                                         ImagePreviewLayout.Source source,
                                         DrawingController.DrawingMethod method) {
        getControllerFactory().getDrawingController().showDrawing(imageAsset,
                                                                  IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW,
                                                                  method);
        extendedCursorContainer.close(true);
    }

    @Override
    public void onSendPictureFromPreview(ImageAsset imageAsset, ImagePreviewLayout.Source source) {
        getStoreFactory().conversationStore().sendMessage(imageAsset);
        TrackingUtils.onSentPhotoMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                         getStoreFactory().conversationStore().getCurrentConversation(),
                                         source);
        extendedCursorContainer.close(true);

        onCancelPreview();
    }

    @Override
    public void onDataReceived(AssetIntentsManager.IntentType type, URI uri) {
        switch (type) {
            case FILE_SHARING:
                sharingUris.clear();
                if (PermissionUtils.hasSelfPermissions(getActivity(), FILE_SHARING_PERMISSION)) {
                    getStoreFactory().conversationStore().sendMessage(AssetFactory.fromContentUri(uri),
                                                                         assetErrorHandler);
                } else {
                    sharingUris.add(uri);
                    ActivityCompat.requestPermissions(getActivity(),
                                                      FILE_SHARING_PERMISSION,
                                                      FILE_SHARING_PERMISSION_REQUEST_ID);
                }
                break;
            case GALLERY:
                showImagePreview(ImageAssetFactory.getImageAsset(uri),
                                 ImagePreviewLayout.Source.DEVICE_GALLERY);
                break;
            case VIDEO_CURSOR_BUTTON:
                sendVideo(uri);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SentVideoMessageEvent((int) (AssetUtils.getVideoAssetDurationMilliSec(
                    getContext(),
                    uri) / 1000),
                                                                                                  getStoreFactory().conversationStore().getCurrentConversation(),
                                                                                                  SentVideoMessageEvent.Source.CURSOR_BUTTON));
                break;
            case VIDEO:
                sendVideo(uri);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new SentVideoMessageEvent((int) (AssetUtils.getVideoAssetDurationMilliSec(
                    getContext(),
                    uri) / 1000),
                                                                                                  getStoreFactory().conversationStore().getCurrentConversation(),
                                                                                                  SentVideoMessageEvent.Source.KEYBOARD));
                break;
            case CAMERA:
                sendImage(uri);
                TrackingUtils.onSentPhotoMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                 getStoreFactory().conversationStore().getCurrentConversation(),
                                                 SentPictureEvent.Source.CAMERA,
                                                 SentPictureEvent.Method.FULL_SCREEN);
                extendedCursorContainer.close(true);
                break;
        }
    }

    private void sendVideo(URI uri) {
        AssetForUpload assetForUpload = AssetFactory.fromContentUri(uri);
        getStoreFactory().conversationStore().sendMessage(assetForUpload, assetErrorHandlerVideo);

        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        extendedCursorContainer.close(true);
    }

    private void sendImage(URI uri) {
        ImageAsset imageAsset = ImageAssetFactory.getImageAsset(uri);

        getStoreFactory().conversationStore().sendMessage(imageAsset);
    }

    @Override
    public void onCanceled(AssetIntentsManager.IntentType type) {
    }

    @Override
    public void onFailed(AssetIntentsManager.IntentType type) {
    }

    @Override
    public void openIntent(Intent intent, AssetIntentsManager.IntentType intentType) {
        if (MediaStore.ACTION_VIDEO_CAPTURE.equals(intent.getAction()) &&
            extendedCursorContainer.getType() == ExtendedCursorContainer.Type.IMAGES &&
            extendedCursorContainer.isExpanded()) {
            // Close keyboard camera before requesting external camera for recording video
            extendedCursorContainer.close(true);
        }
        startActivityForResult(intent, intentType.requestCode);
        getActivity().overridePendingTransition(R.anim.camera_in, R.anim.camera_out);
    }

    @Override
    public void onPermissionFailed(AssetIntentsManager.IntentType type) {

    }

    @Override
    public void onPagerEnabledStateHasChanged(boolean enabled) {

    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (positionOffset > 0) {
            extendedCursorContainer.close(true);
        }
    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @Override
    public void onExtendedCursorClosed(ExtendedCursorContainer.Type lastType) {
        cursorLayout.onExtendedCursorClosed();
        hideSendButtonIfNeeded();
        if (lastType == ExtendedCursorContainer.Type.EPHEMERAL) {
            EphemeralExpiration expiration = getStoreFactory().conversationStore().getCurrentConversation().getEphemeralExpiration();
            if (!expiration.equals(EphemeralExpiration.NONE)) {
                getControllerFactory().getUserPreferencesController().setLastEphemeralValue(expiration.milliseconds);
            }
        }
        getControllerFactory().getGlobalLayoutController().resetScreenAwakeState();
    }

    private void hideSendButtonIfNeeded() {
        if (!getPreferencesController().isSendButtonEnabled() || TextUtils.isEmpty(cursorLayout.getText())) {
            cursorLayout.showSendButton(false);
        }
    }

    @Override
    public void onEmojiSelected(String emoji) {
        cursorLayout.insertText(emoji);
        getControllerFactory().getUserPreferencesController().addRecentEmoji(emoji);
    }

    @Override
    public void onTypingIndicatorVisibilityChanged(boolean visible) {
        if (visible) {
            cursorLayout.showTopbar(false);
        } else {
            cursorLayout.showTopbar(!listView.scrollController().shouldScrollToBottom());
        }
    }

    @Override
    public void onEphemeralExpirationSelected(EphemeralExpiration expiration, boolean close) {
        if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
            return;
        }
        if (close) {
            extendedCursorContainer.close(false);
        }
        getStoreFactory().conversationStore().getCurrentConversation().setEphemeralExpiration(expiration);
    }

    public interface Container {
    }

}
