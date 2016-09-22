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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.waz.api.Asset;
import com.waz.api.AssetFactory;
import com.waz.api.AssetForUpload;
import com.waz.api.AudioAssetForUpload;
import com.waz.api.AudioEffect;
import com.waz.api.ConversationsList;
import com.waz.api.ErrorsList;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.api.InputStateIndicator;
import com.waz.api.Message;
import com.waz.api.MessageContent;
import com.waz.api.MessagesList;
import com.waz.api.NetworkMode;
import com.waz.api.OtrClient;
import com.waz.api.SyncIndicator;
import com.waz.api.SyncState;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.api.UsersList;
import com.waz.api.Verification;
import com.waz.zclient.BaseScalaActivity;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.camera.controllers.GlobalCameraController;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.calling.CallingObserver;
import com.waz.zclient.controllers.confirmation.ConfirmationCallback;
import com.waz.zclient.controllers.confirmation.ConfirmationRequest;
import com.waz.zclient.controllers.confirmation.IConfirmationController;
import com.waz.zclient.controllers.currentfocus.IFocusController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.giphy.GiphyObserver;
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver;
import com.waz.zclient.controllers.mentioning.MentioningObserver;
import com.waz.zclient.controllers.navigation.NavigationControllerObserver;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.controllers.navigation.PagerControllerObserver;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.controllers.singleimage.SingleImageObserver;
import com.waz.zclient.controllers.streammediaplayer.StreamMediaBarObserver;
import com.waz.zclient.controllers.tracking.events.conversation.CopiedMessageEvent;
import com.waz.zclient.controllers.tracking.events.conversation.DeletedMessageEvent;
import com.waz.zclient.controllers.tracking.events.conversation.EditedMessageEvent;
import com.waz.zclient.controllers.tracking.events.conversation.ForwardedMessageEvent;
import com.waz.zclient.controllers.tracking.events.conversation.OpenedMessageActionEvent;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.tracking.events.navigation.OpenedMoreActionsEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SavedFileEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SelectedTooLargeFileEvent;
import com.waz.zclient.core.controllers.tracking.events.media.CancelledRecordingAudioMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedActionHintEvent;
import com.waz.zclient.core.controllers.tracking.events.media.OpenedMediaActionEvent;
import com.waz.zclient.core.controllers.tracking.events.media.PreviewedAudioMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.SentPictureEvent;
import com.waz.zclient.core.controllers.tracking.events.media.SentVideoMessageEvent;
import com.waz.zclient.core.controllers.tracking.events.media.StartedRecordingAudioMessageEvent;
import com.waz.zclient.core.stores.IStoreFactory;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.InAppNotificationStoreObserver;
import com.waz.zclient.core.stores.inappnotification.KnockingEvent;
import com.waz.zclient.core.stores.network.DefaultNetworkAction;
import com.waz.zclient.core.stores.participants.ParticipantsStoreObserver;
import com.waz.zclient.notifications.controllers.ImageNotificationsController;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer;
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout;
import com.waz.zclient.pages.extendedcursor.image.ImagePreviewLayout;
import com.waz.zclient.pages.extendedcursor.voicefilter.VoiceFilterLayout;
import com.waz.zclient.pages.main.calling.enums.VoiceBarAppearance;
import com.waz.zclient.pages.main.conversation.views.ExpandableView;
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.TypingIndicatorView;
import com.waz.zclient.pages.main.conversation.views.header.StreamMediaPlayerBarFragment;
import com.waz.zclient.pages.main.conversation.views.listview.ConversationListView;
import com.waz.zclient.pages.main.conversation.views.listview.ConversationScrollListener;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.ImageMessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.MediaPlayerViewController;
import com.waz.zclient.pages.main.conversation.views.row.message.views.YouTubeMessageViewController;
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation;
import com.waz.zclient.pages.main.conversationpager.controller.SlidingPaneObserver;
import com.waz.zclient.pages.main.onboarding.OnBoardingHintFragment;
import com.waz.zclient.pages.main.onboarding.OnBoardingHintType;
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController;
import com.waz.zclient.pages.main.profile.ZetaPreferencesActivity;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.ui.audiomessage.AudioMessageRecordingView;
import com.waz.zclient.ui.cursor.CursorCallback;
import com.waz.zclient.ui.cursor.CursorLayout;
import com.waz.zclient.ui.cursor.CursorMenuItem;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.utils.AssetUtils;
import com.waz.zclient.utils.Callback;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.OtrDestination;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import com.waz.zclient.views.MentioningFragment;
import net.hockeyapp.android.ExceptionHandler;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversationFragment extends BaseFragment<ConversationFragment.Container> implements ConversationStoreObserver,
                                                                                                  CallingObserver,
                                                                                                  OnBoardingHintFragment.Container,
                                                                                                  ConversationScrollListener.ScrolledToBottomListener,
                                                                                                  ConversationScrollListener.VisibleMessagesChangesListener,
                                                                                                  KeyboardVisibilityObserver,
                                                                                                  AccentColorObserver,
                                                                                                  StreamMediaPlayerBarFragment.Container,
                                                                                                  StreamMediaBarObserver,
                                                                                                  ParticipantsStoreObserver,
                                                                                                  InAppNotificationStoreObserver,
                                                                                                  MessageViewsContainer,
                                                                                                  NavigationControllerObserver,
                                                                                                  SlidingPaneObserver,
                                                                                                  SingleImageObserver,
                                                                                                  MentioningObserver,
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
                                                                                                  ExtendedCursorContainer.Callback {
    public static final String TAG = ConversationFragment.class.getName();
    private static final String SAVED_STATE_PREVIEW = "SAVED_STATE_PREVIEW";
    private static final int REQUEST_VIDEO_CAPTURE = 911;
    private static final int CAMERA_PERMISSION_REQUEST_ID = 21;
    private static final int BOTTOM_MENU_DISPLAY_DELAY_MS = 200;

    private static final String[] EXTENDED_CURSOR_PERMISSIONS = new String[] {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int OPEN_EXTENDED_CURSOR_IMAGES = 1254;

    private static final String[] FILE_SHARING_PERMISSION = new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final int FILE_SHARING_PERMISSION_REQUEST_ID = 179;

    private static final String[] AUDIO_PERMISSION = new String[] {android.Manifest.permission.RECORD_AUDIO};
    private static final int AUDIO_PERMISSION_REQUEST_ID = 864;
    private static final int AUDIO_FILTER_PERMISSION_REQUEST_ID = 865;

    private static final String[] SAVE_IMAGE_PERMISSIONS = new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private static final int SAVE_IMAGE_PERMISSION_REQUEST_ID = 6;

    private ConversationListView listView;
    private MessageAdapter messageAdapter;
    private MessageStreamManager messageStreamManager;
    private InputStateIndicator inputStateIndicator;
    private UpdateListener typingListener;

    private TypingIndicatorView typingIndicatorView;
    private LoadingIndicatorView conversationLoadingIndicatorViewView;

    private StreamMediaPlayerBarFragment streamMediaBarFragment;
    private FrameLayout invisibleFooter;

    private IConversation.Type toConversationType;
    private String expandedMessageId;
    private ExpandableView currentExpandableView;
    private String lastPingMessageId;
    private String lastHotPingMessageId;
    private Toolbar toolbar;
    private TextView toolbarTitle;

    private CursorLayout cursorLayout;
    private AudioMessageRecordingView audioMessageRecordingView;
    private ExtendedCursorContainer extendedCursorContainer;
    private List<Uri> sharingUris = new ArrayList<>();
    private AssetIntentsManager assetIntentsManager;
    private ViewGroup containerPreview;
    private boolean isPreviewShown;
    private boolean isVideoMessageButtonClicked;
    private MessageBottomSheetDialog messageBottomSheetDialog;
    private ImageAsset imageAssetToSave;

    public static ConversationFragment newInstance() {
        return new ConversationFragment();
    }

    private final ModelObserver<IConversation> conversationModelObserver = new ModelObserver<IConversation>() {
        @Override
        public void updated(IConversation model) {
            if (toolbar == null || toolbarTitle == null) {
                return;
            }
            toolbarTitle.setText(model.getName());
            toolbar.getMenu().clear();
            if (!model.isMemberOfConversation()) {
                return;
            }
            if (model.getType() == IConversation.Type.ONE_TO_ONE) {
                toolbar.inflateMenu(R.menu.conversation_header_menu_video);
            } else {
                toolbar.inflateMenu(R.menu.conversation_header_menu_audio);
            }
        }
    };

    private final ModelObserver<MessagesList> messagesListModelObserver = new ModelObserver<MessagesList>() {
        @Override
        public void updated(MessagesList messagesList) {
            if (LayoutSpec.isPhone(getActivity()) &&
                getControllerFactory().getNavigationController().getCurrentPage() != Page.MESSAGE_STREAM) {
                return;
            }

            showLoadingIndicator(messagesList);
            syncIndicatorModelObserver.setAndUpdate(messagesList.getSyncIndicator());
        }
    };

    private final ModelObserver<SyncIndicator> syncIndicatorModelObserver = new ModelObserver<SyncIndicator>() {
        @Override
        public void updated(SyncIndicator syncIndicator) {
            switch (syncIndicator.getState()) {
                case SYNCING:
                case WAITING:
                    conversationLoadingIndicatorViewView.show();
                    getControllerFactory().getLoadTimeLoggerController().conversationContentSyncStart();
                    return;
                case COMPLETED:
                case FAILED:
                default:
                    conversationLoadingIndicatorViewView.hide();
                    getControllerFactory().getLoadTimeLoggerController().conversationContentSyncFinish();
            }
        }
    };

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

    private final MessageBottomSheetDialog.Callback messageBottomSheetDialogCallback = new MessageBottomSheetDialog.Callback() {

        @Override
        public void onAction(MessageBottomSheetDialog.MessageAction action, Message message) {
            switch (action) {
                case COPY:
                    copyMessage(message);
                    getControllerFactory().getTrackingController().tagEvent(OpenedMessageActionEvent.copy(message.getMessageType().name()));
                    break;

                case DELETE_GLOBAL:
                    deleteMessage(message, true);
                    getControllerFactory().getTrackingController().tagEvent(OpenedMessageActionEvent.deleteForEveryone(message.getMessageType().name()));
                    break;

                case DELETE_LOCAL:
                    deleteMessage(message, false);
                    getControllerFactory().getTrackingController().tagEvent(OpenedMessageActionEvent.deleteForMe(message.getMessageType().name()));
                    break;

                case EDIT:
                    editMessage(message);
                    getControllerFactory().getTrackingController().tagEvent(OpenedMessageActionEvent.edit(message.getMessageType().name()));
                    break;

                case FORWARD:
                    forwardMessage(message);
                    getControllerFactory().getTrackingController().tagEvent(OpenedMessageActionEvent.forward(message.getMessageType().name()));
                    break;
                case LIKE:
                case UNLIKE:
                    if (message.isLikedByThisUser()) {
                        message.unlike();
                        getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.unlike(message.getConversation(),
                                                                                                             message,
                                                                                                             ReactedToMessageEvent.Method.MENU));
                    } else {
                        message.like();
                        getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.LIKED_MESSAGE);
                        getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.like(message.getConversation(),
                                                                                                           message,
                                                                                                           ReactedToMessageEvent.Method.MENU));
                    }
                    break;
                case SAVE:
                    saveMessage(message);
                    break;
                default:
                    ExceptionHandler.saveException(new RuntimeException("Unhandled action"), null, null);
            }
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (messageStreamManager != null) {
            messageStreamManager.onConfigurationChanged(getContext(), messageAdapter);
        }

        if (BuildConfig.SHOW_MENTIONING) {
            getControllerFactory().getMentioningController().hide();
        }

        audioMessageRecordingView.requestLayout();
        audioMessageRecordingView.invalidate();

        if (LayoutSpec.isTablet(getContext()) && listView != null && messageAdapter != null) {
            // To clear ListView cache as we use different views for portrait and landscape
            listView.setAdapter(messageAdapter);
        }

        if (ViewUtils.isInLandscape(newConfig)) {
            toolbar.setNavigationIcon(null);
        } else {
            if (ThemeUtils.isDarkTheme(getContext())) {
                toolbar.setNavigationIcon(R.drawable.ic_action_menu_light);
            } else {
                toolbar.setNavigationIcon(R.drawable.ic_action_menu_dark);
            }
        }
        toolbar.setContentInsetsRelative(getResources().getDimensionPixelSize(R.dimen.content__padding_left),
                                         toolbar.getContentInsetEnd());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversation, viewGroup, false);

        extendedCursorContainer = ViewUtils.getView(view, R.id.ecc__conversation);
        containerPreview = ViewUtils.getView(view, R.id.fl__conversation_overlay);
        cursorLayout = ViewUtils.getView(view, R.id.cl__cursor);
        audioMessageRecordingView = ViewUtils.getView(view, R.id.amrv_audio_message_recording);
        toolbar = ViewUtils.getView(view, R.id.t_conversation_toolbar);
        toolbarTitle = ViewUtils.getView(toolbar, R.id.tv__conversation_toolbar__title);
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
                if (LayoutSpec.isTablet(getContext()) && ViewUtils.isInLandscape(getContext())) {
                    return;
                }
                cursorLayout.closeEditMessage(false);
                getActivity().onBackPressed();
                KeyboardUtils.closeKeyboardIfShown(getActivity());
            }
        });

        if (LayoutSpec.isTablet(getContext()) && ViewUtils.isInLandscape(getContext())) {
            toolbar.setNavigationIcon(null);
        }

        conversationLoadingIndicatorViewView = ViewUtils.getView(view, R.id.lbv__conversation__loading_indicator);

        if (BuildConfig.SHOW_MENTIONING) {
            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__conversation_overlay,
                                          MentioningFragment.getInstance(),
                                          MentioningFragment.TAG)
                                     .commit();
        }

        listView = ViewUtils.getView(view, R.id.clv__conversation_list_view);
        listView.setVerticalScrollBarEnabled(false);
        listView.setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                if (view == null || view.getTag() == null || !(view.getTag() instanceof MessageViewController)) {
                    return;
                }
                MessageViewController viewTag = (MessageViewController) view.getTag();
                // We want to ignore those because it looks weird if the images and so on fades in again
                if (viewTag instanceof ImageMessageViewController || viewTag instanceof YouTubeMessageViewController || viewTag instanceof MediaPlayerViewController) {
                    return;
                }
                viewTag.recycle();
            }
        });

        messageAdapter = new MessageAdapter(this);
        listView.setAdapter(messageAdapter);
        messageStreamManager = new MessageStreamManager(listView, messageAdapter);

        // invisible footer to scroll over inputfield
        invisibleFooter = new FrameLayout(getActivity());
        AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                                       getResources().getDimensionPixelSize(R.dimen.cursor__list_view_footer__height));
        invisibleFooter.setLayoutParams(params);

        listView.addFooterView(invisibleFooter, null, false);

        typingIndicatorView = new TypingIndicatorView(getActivity());
        FrameLayout.LayoutParams typingIndicatorLayoutParams = new FrameLayout.LayoutParams(getResources().getDimensionPixelSize(
            R.dimen.typing_indicator__chathead_size),
                                                                                            getResources().getDimensionPixelSize(
                                                                                                R.dimen.typing_indicator__chathead_size));
        typingIndicatorLayoutParams.gravity = Gravity.CENTER;
        typingIndicatorView.setLayoutParams(typingIndicatorLayoutParams);
        cursorLayout.getTypingIndicatorContainer().addTypingIndicatorView(typingIndicatorView);
        cursorLayout.showSendButton(false);

        typingListener = new UpdateListener() {
            @Override
            public void updated() {
                if (inputStateIndicator == null || typingIndicatorView == null || cursorLayout == null) {
                    return;
                }

                if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
                    return;
                }

                final IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
                if (currentConversation == null || currentConversation.getType() != IConversation.Type.ONE_TO_ONE) {
                    return;
                }

                UsersList usersList = inputStateIndicator.getTypingUsers();
                typingIndicatorView.usersUpdated(usersList, true);
                cursorLayout.getTypingIndicatorContainer().setOtherIsTyping(usersList.size() > 0);
            }
        };

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
        cursorLayout.setCursorCallback(this);
        cursorLayout.showSendButtonAsEnterKey(!getControllerFactory().getUserPreferencesController().isCursorSendButtonEnabled());
        final String draftText = getStoreFactory().getDraftStore().getDraft(getStoreFactory().getConversationStore().getCurrentConversation());
        if (!TextUtils.isEmpty(draftText)) {
            cursorLayout.setText(draftText);
        }

        if (BuildConfig.SHOW_MENTIONING) {
            getControllerFactory().getMentioningController().addObserver(this);
        }

        messagesListModelObserver.resumeListening();
        syncIndicatorModelObserver.resumeListening();
        audioMessageRecordingView.setDarkTheme(getControllerFactory().getThemeController().isDarkTheme());

        getControllerFactory().getConversationScreenController().setConversationStreamUiReady(messageStreamManager.getCount() > 0);
        if (!getControllerFactory().getConversationScreenController().isConversationStreamUiInitialized()) {
            getStoreFactory().getConversationStore().addConversationStoreObserverAndUpdate(this);
        } else {
            getStoreFactory().getConversationStore().addConversationStoreObserver(this);
        }
        getControllerFactory().getNavigationController().addNavigationControllerObserver(this);
        getControllerFactory().getNavigationController().addPagerControllerObserver(this);

        getControllerFactory().getGiphyController().addObserver(this);
        getControllerFactory().getSingleImageController().addSingleImageObserver(this);
        getControllerFactory().getStreamMediaPlayerController().addStreamMediaBarObserver(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().getParticipantsStore().addParticipantsStoreObserver(this);
        listView.registerScrolledToBottomListener(this);
        listView.registVisibleMessagesChangedListener(this);
        getControllerFactory().getGlobalLayoutController().addKeyboardVisibilityObserver(this);
        getStoreFactory().getInAppNotificationStore().addInAppNotificationObserver(this);

        getControllerFactory().getSlidingPaneController().addObserver(this);

        typingIndicatorView.setSelfUser(getStoreFactory().getProfileStore().getSelfUser());
        extendedCursorContainer.setCallback(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (LayoutSpec.isTablet(getContext())) {
            conversationModelObserver.setAndUpdate(getStoreFactory().getConversationStore().getCurrentConversation());
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
        if (messageBottomSheetDialog != null) {
            if (messageBottomSheetDialog.isShowing()) {
                messageBottomSheetDialog.dismiss();
            }
            messageBottomSheetDialog = null;
        }
    }

    @Override
    public void onStop() {
        extendedCursorContainer.close(true);
        extendedCursorContainer.setCallback(null);
        cursorLayout.setCursorCallback(null);
        getControllerFactory().getGlobalLayoutController().removeKeyboardHeightObserver(extendedCursorContainer);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(extendedCursorContainer);
        if (BuildConfig.SHOW_MENTIONING) {
            getControllerFactory().getMentioningController().removeObserver(this);
        }
        getControllerFactory().getGiphyController().removeObserver(this);
        getControllerFactory().getSingleImageController().removeSingleImageObserver(this);

        if (!cursorLayout.isEditingMessage()) {
            getStoreFactory().getDraftStore().setDraft(getStoreFactory().getConversationStore().getCurrentConversation(),
                                                       cursorLayout.getText().trim());
        }
        getStoreFactory().getInAppNotificationStore().removeInAppNotificationObserver(this);
        getStoreFactory().getParticipantsStore().removeParticipantsStoreObserver(this);
        getControllerFactory().getStreamMediaPlayerController().removeStreamMediaBarObserver(this);
        getControllerFactory().getGlobalLayoutController().removeKeyboardVisibilityObserver(this);
        getControllerFactory().getNavigationController().removePagerControllerObserver(this);

        messagesListModelObserver.pauseListening();
        syncIndicatorModelObserver.pauseListening();

        getStoreFactory().getConversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getNavigationController().removeNavigationControllerObserver(this);
        listView.unregistVisibleMessagesChangedListener(this);
        listView.unregisterScrolledToBottomListener(this);
        getControllerFactory().getSlidingPaneController().removeObserver(this);
        getControllerFactory().getConversationScreenController().setConversationStreamUiReady(false);
        getControllerFactory().getRequestPermissionsController().removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        containerPreview = null;
        listView = null;
        messageAdapter = null;
        cursorLayout.tearDown();
        cursorLayout = null;
        conversationLoadingIndicatorViewView = null;
        if (inputStateIndicator != null) {
            inputStateIndicator.removeUpdateListener(typingListener);
            inputStateIndicator = null;
        }
        typingIndicatorView = null;
        typingListener = null;
        conversationModelObserver.clear();
        toolbarTitle = null;
        toolbar = null;
        super.onDestroyView();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Container implementations
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowSingleImage(Message message) {
        listView.setEnabled(true);
    }

    @Override
    public void onShowUserImage(User user) {

    }

    @Override
    public void onHideSingleImage() {
        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        listView.setEnabled(true);
    }

    @Override
    public void updateSingleImageReferences() {
        listView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
                    return;
                }
                Message message = getControllerFactory().getSingleImageController().getMessage();
                if (message == null) {
                    getControllerFactory().getSingleImageController().setContainerOutOfScreen(true);
                    return;
                }
                // Single image is visible
                int messagePosition = messageStreamManager.getIndexOfMessage(message);
                View messageView = getViewByPosition(messagePosition, listView);
                if (messageView == null) {
                    getControllerFactory().getSingleImageController().setContainerOutOfScreen(true);
                    return;
                }
                final ImageView clickedImageView = ViewUtils.getView(messageView,
                                                                     R.id.iv__row_conversation__message_image);
                getControllerFactory().getSingleImageController().setViewReferences(clickedImageView);
            }
        }, getResources().getInteger(R.integer.framework_animation_duration_long));
    }

    private View getViewByPosition(int pos, ListView listView) {
        final int firstListItemPosition = listView.getFirstVisiblePosition();
        final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1;

        if (pos < firstListItemPosition || pos > lastListItemPosition) {
            return listView.getAdapter().getView(pos, null, listView);
        } else {
            final int childIndex = pos - firstListItemPosition;
            return listView.getChildAt(childIndex);
        }
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

        if (isPreviewShown) {
            onCancelPreview();
        }

        extendedCursorContainer.close(true);

        messageStreamManager.setConversation(toConversation,
                                             getControllerFactory().getNavigationController().getCurrentPage() != Page.MESSAGE_STREAM);

        getControllerFactory().getConversationScreenController().setSingleConversation(toConversation.getType() == IConversation.Type.ONE_TO_ONE);


        if (BuildConfig.SHOW_MENTIONING) {
            getControllerFactory().getMentioningController().setCurrentConversation(toConversation);
        }

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
                    getStoreFactory().getDraftStore().setDraft(fromConversation, cursorLayout.getText().trim());
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
                    messagesListModelObserver.setAndUpdate(toConversation.getMessages());
                    getControllerFactory().getSharingController().maybeResetSharedText(fromConversation);
                    getControllerFactory().getSharingController().maybeResetSharedUris(fromConversation);


                    cursorLayout.setVisibility(toConversation.isActive() ? View.VISIBLE : View.GONE);
                    if (!inSplitPortraitMode() && listView.computeIsScrolledToBottom()) {
                        resetCursor();
                    }
                    typingIndicatorView.reset();

                    final String draftText = getStoreFactory().getDraftStore().getDraft(toConversation);
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
                final List<Uri> sharedFileUris = getControllerFactory().getSharingController().getSharedFileUris();
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
                            for (Uri uri : sharedFileUris) {
                                getStoreFactory().getConversationStore().sendMessage(AssetFactory.fromContentUri(uri),
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


                if (!getControllerFactory().getStreamMediaPlayerController().isSelectedConversation(toConversation.getId())) {
                    onHideMediaBar();
                }
                if (inputStateIndicator != null) {
                    inputStateIndicator.getTypingUsers().removeUpdateListener(typingListener);
                }

                inputStateIndicator = toConversation.getInputStateIndicator();

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
    public void onVerificationStateChanged(String conversationId,
                                           Verification previousVerification,
                                           Verification currentVerification) {

    }

    @Override
    public void onCursorPositionChanged(float x, float y) {

    }

    @Override
    public void onQueryResultChanged(@NonNull List<User> usersList) {

    }

    @Override
    public void onMentionedUserSelected(@NonNull String query, @NonNull User user) {
        final int cursorPosition = cursorLayout.getSelection();
        final String text = cursorLayout.getText();
        if (cursorPosition == -1 || TextUtils.isEmpty(text)) {
            cursorLayout.setText(text);
            cursorLayout.setSelection(text.length());
            return;
        }
        final String[] words = text.split(" ");
        StringBuilder builder = new StringBuilder();
        int desiredCursorPosition = 0;
        boolean inserted = false;
        for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
            String word = words[i];
            if (!inserted && builder.length() + word.length() + 1 > cursorPosition) {
                final int diff = word.length() - 1 - query.length();
                String rest = word.substring(query.length() + 1);
                builder.append('@')
                       .append(user.getDisplayName())
                       .append(' ');
                if (!TextUtils.isEmpty(rest)) {
                    builder.append(rest);
                }
                desiredCursorPosition = builder.length() - diff;
                inserted = true;
            } else {
                builder.append(word);
            }
            if (i < wordsLength - 1) {
                builder.append(' ');
            }
        }
        cursorLayout.setText(builder.toString());
        cursorLayout.setSelection(desiredCursorPosition);
    }

    @Override
    public void onScrolledToBottom() {
        getStoreFactory().getInAppNotificationStore().onScrolledToBottom();
        messageStreamManager.onScrolledToBottom(true);
        cursorLayout.showTopbar(false);
    }

    @Override
    public void onScrolledAwayFromBottom() {
        getStoreFactory().getInAppNotificationStore().onScrolledAwayFromBottom();
        messageStreamManager.onScrolledToBottom(false);
        cursorLayout.showTopbar(true);
    }

    @Override
    public void onScrollOffsetFromFirstElement(int offset) {
    }

    @Override
    public void onKeyboardVisibilityChanged(boolean keyboardIsVisible, int keyboardHeight, View currentFocus) {
        cursorLayout.notifyKeyboardVisibilityChanged(keyboardIsVisible, currentFocus);

        if (keyboardIsVisible &&
            getControllerFactory().getFocusController().getCurrentFocus() == IFocusController.CONVERSATION_CURSOR &&
            !cursorLayout.isEditingMessage()) {
            messageStreamManager.onCursorStateEdit();
            getControllerFactory().getNavigationController().setMessageStreamState(VoiceBarAppearance.MINI);
        }

        if (!keyboardIsVisible) {
            getControllerFactory().getNavigationController().setMessageStreamState(VoiceBarAppearance.FULL);
        }
    }

    private void showLoadingIndicator(MessagesList messages) {
        if (messages != null && messages.size() > 0) {
            conversationLoadingIndicatorViewView.setType(LoadingIndicatorView.INFINITE_LOADING_BAR);
        } else {
            conversationLoadingIndicatorViewView.setType(LoadingIndicatorView.SPINNER);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Cursor callback
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onEditTextHasChanged(int cursorPosition, String text) {
        if (listView == null) {
            return;
        }
        // this is needed to make sure that text is scrolled to bottom - on some devices
        // the keyboard height changes while text is being entered
        if (!cursorLayout.isEditingMessage()) {
            messageStreamManager.onCursorStateEdit();
        }

        if (inputStateIndicator != null) {
            if (text.isEmpty()) {
                inputStateIndicator.textCleared();
            } else {
                inputStateIndicator.textChanged();
            }
        }

        if (getControllerFactory().getUserPreferencesController().isCursorSendButtonEnabled()) {
            cursorLayout.showSendButton(!TextUtils.isEmpty(text));
        }
    }

    public boolean isKeyboardUp() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return false;
        }
        return getControllerFactory().getGlobalLayoutController().isKeyboardVisible();
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

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  VisibleMessagesChangesListener
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onVisibleMessagesChanged(List<String> visibleMessageIds) {
        getControllerFactory().getStreamMediaPlayerController().informVisibleItems(visibleMessageIds);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  StreamMediaBar
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onShowMediaBar(String conversationId) {
        IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();
        if (currentConversation == null || !conversationId.equals(currentConversation.getId())) {
            return;
        }

        if (streamMediaBarFragment != null) {
            streamMediaBarFragment.show();
            return;
        }

        try {
            FragmentManager fragmentManager = getChildFragmentManager();
            streamMediaBarFragment = StreamMediaPlayerBarFragment.newInstance();
            fragmentManager
                .beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
                .add(R.id.fl__conversation__mediabar_container,
                     streamMediaBarFragment,
                     StreamMediaPlayerBarFragment.TAG)
                .commit();
        } catch (IllegalStateException e) {
            Timber.e(e, "onShowMediaBar failed");
        }
    }

    @Override
    public void onHideMediaBar() {
        if (streamMediaBarFragment == null) {
            return;
        }
        streamMediaBarFragment.hide();
    }

    @Override
    public void onScrollTo(Message message) {
        if (listView != null) {
            final int scrollPosition = messageStreamManager.getIndexOfMessage(message);
            Timber.i("onScrollTo. Smooth scroll list to position %s", scrollPosition);
            listView.smoothScrollToPosition(scrollPosition);
        }
    }

    @Override
    public void onIncomingMessage(Message message) {

    }

    @Override
    public void onIncomingKnock(KnockingEvent knock) {
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

        final IConversation currentConversation = getStoreFactory().getConversationStore().getCurrentConversation();

        if (currentConversation == null || !currentConversation.isMemberOfConversation()) {
            cursorLayout.setVisibility(View.GONE);
            ViewUtils.setMarginBottom(listView,
                                      getResources().getDimensionPixelSize(R.dimen.cursor__list_view_footer_no_cursor__height));
        } else {
            cursorLayout.setVisibility(View.VISIBLE);
            ViewUtils.setMarginBottom(listView, 0);
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
            messagesListModelObserver.forceUpdate();
            cursorLayout.enableMessageWriting();
        }
        if (LayoutSpec.isPhone(getContext())) {
            if (page == Page.MESSAGE_STREAM) {
                messageStreamManager.resume();
            } else {
                messageStreamManager.pause();
            }
        }
    }

    @Override
    public void onPageStateHasChanged(Page page) {

    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //  GroupCallingStoreObserver
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public void onStartCall(boolean withVideo) {

    }

    //////////////////////////////////////////////////////////////////////////////
    //
    //  MessageViewsContainer
    //
    //////////////////////////////////////////////////////////////////////////////

    @Override
    public int getUnreadMessageCount() {
        return messageStreamManager.getUnreadMessageCount();
    }

    @Override
    public IConversation.Type getConversationType() {
        return toConversationType;
    }

    @Override
    public void setExpandedMessageId(String messageId) {
        expandedMessageId = messageId;
    }

    @Override
    public String getExpandedMessageId() {
        return expandedMessageId;
    }

    @Override
    public void setExpandedView(ExpandableView expandedView) {
        currentExpandableView = expandedView;
    }

    @Override
    public ExpandableView getExpandedView() {
        return currentExpandableView;
    }

    @Override
    public boolean ping(boolean hotKnock, String id, String message, int color) {
        if (hotKnock) {
            if (lastHotPingMessageId != null && lastHotPingMessageId.equals(id)) {
                return false;
            }

            lastHotPingMessageId = id;
        } else {
            if (lastPingMessageId != null && lastPingMessageId.equals(id)) {
                return false;
            }

            lastPingMessageId = id;
        }

        return true;
    }

    @Override
    public boolean isPhone() {
        return LayoutSpec.isPhone(getActivity());
    }

    @Override
    public void openSpotifySettings() {
        startActivity(ZetaPreferencesActivity.getSpotifyLoginIntent(getActivity()));
    }

    @Override
    public void openDevicesPage(OtrDestination otrDestination, View anchorView) {
        if (isKeyboardUp()) {
            KeyboardUtils.hideKeyboard(getActivity());
        }
        switch (otrDestination) {
            case PREFERENCES:
                startActivity(ZetaPreferencesActivity.getOtrDevicesPreferencesIntent(getContext()));
                break;
            case PARTICIPANTS:
                getControllerFactory().getConversationScreenController().showParticipants(anchorView, true);
                break;
        }
    }

    @Override
    public boolean onItemLongClick(final Message message) {
        final boolean isMemberOfConversation = getStoreFactory().getConversationStore().getCurrentConversation().isMemberOfConversation();
        if (messageBottomSheetDialog != null) {
            if (messageBottomSheetDialog.isShowing()) {
                messageBottomSheetDialog.dismiss();
            }
            messageBottomSheetDialog = null;
        }
        if (KeyboardUtils.isKeyboardVisible(getContext())) {
            KeyboardUtils.hideKeyboard(getActivity());
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) {
                        return;
                    }
                    messageBottomSheetDialog = new MessageBottomSheetDialog(getContext(), message, isMemberOfConversation, messageBottomSheetDialogCallback);
                    messageBottomSheetDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            messageBottomSheetDialog = null;
                        }
                    });
                    messageBottomSheetDialog.show();
                }
            }, BOTTOM_MENU_DISPLAY_DELAY_MS);
        } else {
            messageBottomSheetDialog = new MessageBottomSheetDialog(getContext(), message, isMemberOfConversation, messageBottomSheetDialogCallback);
            messageBottomSheetDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    messageBottomSheetDialog = null;
                }
            });
            messageBottomSheetDialog.show();
        }
        return true;
    }

    @Override
    public void onOpenUrl(String url) {
        getContainer().onOpenUrl(url);
    }

    @Override
    public void openSettings() {
        startActivity(ZetaPreferencesActivity.getDefaultIntent(getActivity()));
    }

    @Override
    public boolean isTornDown() {
        return getContainer() == null || getControllerFactory().isTornDown() || getStoreFactory().isTornDown();
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
        if (ViewUtils.isInLandscape(getActivity())) {
            return;
        }
        KeyboardUtils.closeKeyboardIfShown(getActivity());
    }

    @Override
    public void onPanelClosed(View panel) {

    }

    private void resetCursor() {
        cursorLayout.setText("");
    }

    @Override
    public void dismissOnBoardingHint(OnBoardingHintType requestedType) {

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
                getControllerFactory().getTrackingController().tagEvent(new SelectedTooLargeFileEvent());
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

        final boolean isGroupConversation = getConversationType() == IConversation.Type.GROUP;
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
                    getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.photo(
                        isGroupConversation));
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
                getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
                    @Override
                    public void execute(NetworkMode networkMode) {
                        getStoreFactory().getConversationStore().knockCurrentConversation();
                        getStoreFactory().getMediaStore().playSound(R.raw.ping_from_me);
                        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.PINGS_SENT);
                    }
                });
                TrackingUtils.onSentPingMessage(getControllerFactory().getTrackingController(),
                                                getStoreFactory().getConversationStore().getCurrentConversation());
                break;
            case SKETCH:
                getControllerFactory().getDrawingController().showDrawing(null,
                                                                          IDrawingController.DrawingDestination.SKETCH_BUTTON);
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.sketch(
                    isGroupConversation));
                break;
            case FILE:
                assetIntentsManager.openFileSharing();
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.file(isGroupConversation));
                break;
            case VIDEO_MESSAGE:
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.videomessage(isGroupConversation));
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
                if (ConnectionResult.SUCCESS == GooglePlayServicesUtil.isGooglePlayServicesAvailable(getContext())) {
                    KeyboardUtils.hideKeyboard(getActivity());
                    getControllerFactory().getLocationController().showShareLocation();
                    getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.location(isGroupConversation));
                } else {
                    Toast.makeText(getContext(), R.string.location_sharing__missing_play_services, Toast.LENGTH_LONG).show();
                }
                break;
            case MORE:
            case LESS:
                getControllerFactory().getTrackingController().tagEvent(new OpenedMoreActionsEvent(
                    getConversationTypeString()));
                break;
            case GIF:
                getControllerFactory().getGiphyController().handleInput(cursorLayout.getText());
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.giphy(isGroupConversation));
                break;
        }
    }

    private GlobalCameraController getCameraController() {
        return ((BaseScalaActivity) getActivity()).injectJava(GlobalCameraController.class);
    }

    private void openExtendedCursor(ExtendedCursorContainer.Type type) {

        final boolean isGroupConversation = getConversationType() == IConversation.Type.GROUP;
        switch (type) {
            case NONE:
                break;
            case VOICE_FILTER_RECORDING:
                extendedCursorContainer.openVoiceFilter(this);
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.audiomessage(
                    isGroupConversation));
                break;
            case IMAGES:
                extendedCursorContainer.openCursorImages(this);
                getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.photo(
                    isGroupConversation));
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
                    getControllerFactory().getVibratorController().vibrate(R.array.alert);
                    audioMessageRecordingView.prepareForRecording();
                    audioMessageRecordingView.setVisibility(View.VISIBLE);
                    final IConversation conversation = getStoreFactory().getConversationStore().getCurrentConversation();
                    final boolean isGroupConversation = conversation.getType() == IConversation.Type.GROUP;
                    getControllerFactory().getTrackingController().tagEvent(OpenedMediaActionEvent.audiomessage(
                        isGroupConversation));
                    getControllerFactory().getTrackingController().tagEvent(new StartedRecordingAudioMessageEvent(
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
        getStoreFactory().getConversationStore().sendMessage(message);
        TrackingUtils.onSentTextMessage(getControllerFactory().getTrackingController(),
                                        getStoreFactory().getConversationStore().getCurrentConversation());

        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(null);
        getControllerFactory().getTrackingController().updateSessionAggregates(RangedAttribute.TEXT_MESSAGES_SENT,
                                                                               message);
        getControllerFactory().getSharingController().maybeResetSharedText(getStoreFactory().getConversationStore().getCurrentConversation());
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
        getControllerFactory().getTrackingController().tagEvent(new OpenedActionHintEvent(item.name(),
                                                                                          getConversationTypeString()));
    }

    @Override
    public void onApprovedMessageEditing(Message message) {
        KeyboardUtils.hideKeyboard(getActivity());
        getControllerFactory().getTrackingController().tagEvent(new EditedMessageEvent(message));
    }

    @Override
    public void onClosedMessageEditing() {
        getControllerFactory().getConversationScreenController().setMessageBeingEdited(null);
        messageAdapter.notifyDataSetChanged();
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
                    for (Uri uri : sharingUris) {
                        getStoreFactory().getConversationStore().sendMessage(AssetFactory.fromContentUri(uri),
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
            case SAVE_IMAGE_PERMISSION_REQUEST_ID:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    saveImageAssetToGallery();
                } else {
                    unableToSaveImageNoPermissions();
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
        getStoreFactory().getConversationStore().sendMessage(audioAssetForUpload, assetErrorHandler);
        hideAudioMessageRecording();
        TrackingUtils.tagSentAudioMessageEvent(getControllerFactory().getTrackingController(),
                                               audioAssetForUpload,
                                               appliedAudioEffect,
                                               true,
                                               sentWithQuickAction,
                                               getStoreFactory().getConversationStore().getCurrentConversation());
    }

    @Override
    public void onCancel() {
        extendedCursorContainer.close(false);
    }

    @Override
    public void onAudioMessageRecordingStarted() {
        getControllerFactory().getTrackingController().tagEvent(new StartedRecordingAudioMessageEvent(
            getConversationTypeString(),
            false));
    }

    @Override
    public void sendRecording(AudioAssetForUpload audioAssetForUpload, AudioEffect appliedAudioEffect) {
        getStoreFactory().getConversationStore().sendMessage(audioAssetForUpload, assetErrorHandler);
        hideAudioMessageRecording();
        TrackingUtils.tagSentAudioMessageEvent(getControllerFactory().getTrackingController(),
                                               audioAssetForUpload,
                                               appliedAudioEffect,
                                               false,
                                               false,
                                               getStoreFactory().getConversationStore().getCurrentConversation());
        extendedCursorContainer.close(true);

    }

    @Override
    public void onCancelledAudioMessageRecording() {
        hideAudioMessageRecording();
        getControllerFactory().getTrackingController().tagEvent(new CancelledRecordingAudioMessageEvent(
            getConversationTypeString()));
    }

    @Override
    public void onPreviewedAudioMessage() {
        getControllerFactory().getTrackingController().tagEvent(new PreviewedAudioMessageEvent(getConversationTypeString()));
    }

    private void hideAudioMessageRecording() {
        if (audioMessageRecordingView.getVisibility() == View.INVISIBLE) {
            return;
        }
        audioMessageRecordingView.reset();
        audioMessageRecordingView.setVisibility(View.INVISIBLE);
    }

    private void onErrorCanNotSentMessageToUnverifiedConversation(final ErrorsList.ErrorDescription errorDescription) {
        if (getControllerFactory().getNavigationController().getCurrentPage() != Page.MESSAGE_STREAM) {
            return;
        }

        errorDescription.dismiss();
        KeyboardUtils.hideKeyboard(getActivity());

        final IConversation currentConversation = errorDescription.getConversation();
        final Iterable<? extends User> users = currentConversation.getUsers();
        final Map<User, String> userNameMap = new HashMap<>();
        int tmpUnverifiedDevices = 0;
        int userCount = 0;
        for (User user : users) {
            userCount++;
            if (user.getVerified() == Verification.VERIFIED) {
                continue;
            }
            userNameMap.put(user, user.getDisplayName());
            for (OtrClient client : user.getOtrClients()) {
                if (client.getVerified() == Verification.VERIFIED) {
                    continue;
                }
                tmpUnverifiedDevices++;
            }
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
                final View anchorView = ViewUtils.getView(getActivity(), R.id.cursor_menu_item_participant);
                getControllerFactory().getConversationScreenController().showParticipants(anchorView, true);
            }
        };
        final String positiveButton = getString(R.string.conversation__degraded_confirmation__positive_action);
        final String negativeButton = getResources().getQuantityString(R.plurals.conversation__degraded_confirmation__negative_action,
                                                                       userCount);
        final ConfirmationRequest request = new ConfirmationRequest.Builder(IConfirmationController.SEND_MESSAGES_TO_DEGRADED_CONVERSATION)
            .withHeader(header)
            .withMessage(message)
            .withPositiveButton(positiveButton)
            .withNegativeButton(negativeButton)
            .withConfirmationCallback(callback)
            .withCancelButton()
            .withHeaderIcon(R.drawable.shield_half)
            .withWireTheme(getControllerFactory().getThemeController().getThemeDependentOptionsTheme())
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
        imagePreviewLayout.setTitle(getStoreFactory().getConversationStore().getCurrentConversation().getName());

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
    public void onSketchPictureFromPreview(ImageAsset imageAsset, ImagePreviewLayout.Source source) {
        getControllerFactory().getDrawingController().showDrawing(imageAsset,
                                                                  IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW);
        extendedCursorContainer.close(true);
    }

    @Override
    public void onSendPictureFromPreview(ImageAsset imageAsset, ImagePreviewLayout.Source source) {
        getStoreFactory().getConversationStore().sendMessage(imageAsset);
        TrackingUtils.onSentPhotoMessage(getControllerFactory().getTrackingController(),
                                         getStoreFactory().getConversationStore().getCurrentConversation(),
                                         source);
        extendedCursorContainer.close(true);

        onCancelPreview();
    }

    @Override
    public void onDataReceived(AssetIntentsManager.IntentType type, Uri uri) {
        switch (type) {
            case FILE_SHARING:
                sharingUris.clear();
                if (PermissionUtils.hasSelfPermissions(getActivity(), FILE_SHARING_PERMISSION)) {
                    getStoreFactory().getConversationStore().sendMessage(AssetFactory.fromContentUri(uri),
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
                getControllerFactory().getTrackingController().tagEvent(new SentVideoMessageEvent((int) (AssetUtils.getVideoAssetDurationMilliSec(
                    getContext(),
                    uri) / 1000),
                                                                                                  getStoreFactory().getConversationStore().getCurrentConversation(),
                                                                                                  SentVideoMessageEvent.Source.CURSOR_BUTTON));
                break;
            case VIDEO:
                sendVideo(uri);
                getControllerFactory().getTrackingController().tagEvent(new SentVideoMessageEvent((int) (AssetUtils.getVideoAssetDurationMilliSec(
                    getContext(),
                    uri) / 1000),
                                                                                                  getStoreFactory().getConversationStore().getCurrentConversation(),
                                                                                                  SentVideoMessageEvent.Source.KEYBOARD));
                break;
            case CAMERA:
                sendImage(uri);
                TrackingUtils.onSentPhotoMessage(getControllerFactory().getTrackingController(),
                                                 getStoreFactory().getConversationStore().getCurrentConversation(),
                                                 SentPictureEvent.Source.CAMERA,
                                                 SentPictureEvent.Method.FULL_SCREEN);
                extendedCursorContainer.close(true);
                break;
        }
    }

    private void sendVideo(Uri uri) {
        Timber.i("  uri.getPath %s", uri.getPath());
        Timber.i("          uri %s", uri);

        AssetForUpload assetForUpload = AssetFactory.fromContentUri(uri);
        getStoreFactory().getConversationStore().sendMessage(assetForUpload, assetErrorHandlerVideo);

        getControllerFactory().getNavigationController().setRightPage(Page.MESSAGE_STREAM, TAG);
        getStoreFactory().getInAppNotificationStore().setUserSendingPicture(false);
        extendedCursorContainer.close(true);
    }

    private void sendImage(Uri uri) {
        ImageAsset imageAsset = ImageAssetFactory.getImageAsset(uri);

        getStoreFactory().getConversationStore().sendMessage(imageAsset);
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
    public void onExtendedCursorClosed() {
        cursorLayout.onExtendedCursorClosed();
    }

    private void editMessage(final Message message) {
        if (cursorLayout == null) {
            return;
        }
        cursorLayout.editMessage(message);

        getControllerFactory().getConversationScreenController().setMessageBeingEdited(message);
        messageAdapter.notifyDataSetChanged();

        // Add small delay so triggering keyboard works
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                KeyboardUtils.showKeyboard(getActivity());
            }
        }, 200);
    }

    private void deleteMessage(final Message message, final boolean forEveryone) {
        Dialog dialog = new AlertDialog.Builder(getContext())
            .setTitle(forEveryone ? R.string.conversation__message_action__delete_for_everyone : R.string.conversation__message_action__delete_for_me)
            .setMessage(R.string.conversation__message_action__delete_details)
            .setCancelable(true)
            .setNegativeButton(R.string.conversation__message_action__delete__dialog__cancel, null)
            .setPositiveButton(R.string.conversation__message_action__delete__dialog__ok,
                               new DialogInterface.OnClickListener() {
                                   @Override
                                   public void onClick(DialogInterface dialog, int which) {
                                       if (forEveryone) {
                                           message.recall();
                                       } else {
                                           message.delete();
                                       }
                                       Toast.makeText(getContext(), R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show();
                                       getControllerFactory().getTrackingController().tagEvent(new DeletedMessageEvent(message, forEveryone));
                                   }
                               })
            .create();
        dialog.show();
    }

    private void copyMessage(Message message) {
        getControllerFactory().getTrackingController().tagEvent(new CopiedMessageEvent(message.getMessageType().name()));

        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getContext().getString(R.string.conversation__action_mode__copy__description,
                                                                     message.getUser().getDisplayName()),
                                              message.getBody());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.conversation__action_mode__copy__toast, Toast.LENGTH_SHORT).show();
    }

    private void forwardMessage(final Message message) {
        getControllerFactory().getTrackingController().tagEvent(new ForwardedMessageEvent(message.getMessageType().name()));

        final ShareCompat.IntentBuilder intentBuilder = ShareCompat.IntentBuilder.from(getActivity());
        intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title);
        switch (message.getMessageType()) {
            case TEXT:
            case RICH_MEDIA:
                intentBuilder.setType("text/plain");
                intentBuilder.setText(message.getBody());
                intentBuilder.startChooser();
                break;
            case ANY_ASSET:
            case VIDEO_ASSET:
            case AUDIO_ASSET:
            case ASSET:
                final ProgressDialog dialog = ProgressDialog.show(getContext(),
                                                                  getString(R.string.conversation__action_mode__fwd__dialog__title),
                                                                  getString(R.string.conversation__action_mode__fwd__dialog__message),
                                                                  true, true, null);
                // TODO: Once https://wearezeta.atlassian.net/browse/CM-976 is resolved, this 'if' block can be removed
                if (message.getMessageType() == Message.Type.ASSET) {
                    final ImageAsset imageAsset = message.getImage();
                    intentBuilder.setType(imageAsset.getMimeType());
                    imageAsset.saveImageToGallery(new ImageAsset.SaveCallback() {
                        @Override
                        public void imageSaved(Uri uri) {
                            if (getActivity() == null) {
                                return;
                            }
                            dialog.dismiss();
                            intentBuilder.addStream(uri);
                            intentBuilder.startChooser();
                        }

                        @Override
                        public void imageSavingFailed(Exception ex) {
                            if (getActivity() == null) {
                                return;
                            }
                            dialog.dismiss();
                        }
                    });
                } else {
                    final Asset messageAsset = message.getAsset();
                    intentBuilder.setType(messageAsset.getMimeType());
                    messageAsset.getContentUri(new Asset.LoadCallback<Uri>() {
                        @Override
                        public void onLoaded(Uri uri) {
                            if (getActivity() == null) {
                                return;
                            }
                            dialog.dismiss();
                            intentBuilder.addStream(uri);
                            intentBuilder.startChooser();
                        }

                        @Override
                        public void onLoadFailed() {
                            if (getActivity() == null) {
                                return;
                            }
                            dialog.dismiss();
                        }
                    });
                }
                break;
        }
    }

    private void saveMessage(Message message) {
        if (message.getMessageType() == Message.Type.ASSET) {
            imageAssetToSave = message.getImage();
            if (PermissionUtils.hasSelfPermissions(getActivity(), SAVE_IMAGE_PERMISSIONS)) {
                saveImageAssetToGallery();
            } else {
                ActivityCompat.requestPermissions(getActivity(),
                                                  SAVE_IMAGE_PERMISSIONS,
                                                  SAVE_IMAGE_PERMISSION_REQUEST_ID);
            }
        } else {
            final ProgressDialog dialog = ProgressDialog.show(getContext(),
                                                              getString(R.string.conversation__action_mode__fwd__dialog__title),
                                                              getString(R.string.conversation__action_mode__fwd__dialog__message),
                                                              true, true, null);
            final Asset asset = message.getAsset();
            asset.saveToDownloads(new Asset.LoadCallback() {

                @Override
                public void onLoaded(Object o) {
                    if (getActivity() == null ||
                        getControllerFactory() == null ||
                        getControllerFactory().isTornDown()) {
                        return;
                    }
                    getControllerFactory().getTrackingController().tagEvent(new SavedFileEvent(asset.getMimeType(),
                                                                                               (int) asset.getSizeInBytes()));
                    Toast.makeText(getActivity(),
                                   com.waz.zclient.ui.R.string.content__file__action__save_completed,
                                   Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }

                @Override
                public void onLoadFailed() {
                    if (getActivity() == null ||
                        getControllerFactory() == null ||
                        getControllerFactory().isTornDown()) {
                        return;
                    }
                    Toast.makeText(getActivity(),
                                   com.waz.zclient.ui.R.string.content__file__action__save_error,
                                   Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            });
        }
    }

    private void saveImageAssetToGallery() {
        if (imageAssetToSave == null) {
            return;
        }
        imageAssetToSave.saveImageToGallery(new ImageAsset.SaveCallback() {

            @Override
            public void imageSaved(Uri uri) {
                if (getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                ((BaseScalaActivity) getActivity())
                    .injectJava(ImageNotificationsController.class)
                    .showImageSavedNotification(imageAssetToSave.getId(), uri);
                Toast.makeText(getContext(), R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show();
                imageAssetToSave = null;
            }

            @Override
            public void imageSavingFailed(Exception ex) {
                unableToSaveImageNoPermissions();
            }
        });
    }

    private void unableToSaveImageNoPermissions() {
        imageAssetToSave = null;
        Toast.makeText(getContext(), R.string.message_bottom_menu_action_save_fail, Toast.LENGTH_SHORT).show();
    }

    public interface Container {
        void onOpenUrl(String url);
    }

}
