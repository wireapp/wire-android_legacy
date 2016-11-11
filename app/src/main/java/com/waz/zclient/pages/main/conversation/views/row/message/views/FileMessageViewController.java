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
package com.waz.zclient.pages.main.conversation.views.row.message.views;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.waz.api.Asset;
import com.waz.api.AssetStatus;
import com.waz.api.Message;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.conversation.ReactedToMessageEvent;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.OpenedFileEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SavedFileEvent;
import com.waz.zclient.pages.main.conversation.views.MessageViewsContainer;
import com.waz.zclient.pages.main.conversation.views.row.message.MessageViewController;
import com.waz.zclient.pages.main.conversation.views.row.separator.Separator;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.AssetDialogUtils;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.EphemeralDotAnimationView;
import com.waz.zclient.utils.AssetUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.AssetActionButton;
import com.waz.zclient.ui.views.OnDoubleClickListener;
import timber.log.Timber;

import java.util.Locale;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class FileMessageViewController extends MessageViewController implements AccentColorObserver {

    private Asset asset;
    private View view;

    private ProgressDotsView progressDotsView;
    private AssetActionButton actionButton;
    private View downloadDoneIndicatorView;
    private EphemeralDotAnimationView ephemeralDotAnimationView;
    private View ephemeralTypeView;

    private TextView fileNameTextView;
    private TextView fileInfoTextView;

    private LinearLayout selectionContainer;

    private int failedTextColor;
    private Uri localAssetUri;

    private final ModelObserver<Message> messageObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            Timber.i("Message status %s", message.getMessageStatus());
            if (message.isEphemeral() && message.isExpired()) {
                messageExpired();
            } else {
                assetObserver.setAndUpdate(message.getAsset());
                updateFileStatus();
            }
        }
    };

    private final ModelObserver<Asset> assetObserver = new ModelObserver<Asset>() {
        @Override
        public void updated(Asset asset) {
            Timber.i("Asset %s status %s", asset.getName(), asset.getStatus());
            setProgressDotsVisible(receivingMessage(asset));
            FileMessageViewController.this.asset = asset;
            updateFileStatus();
        }
    };

    private final Asset.LoadCallback<Uri> loadToCacheCallback = new Asset.LoadCallback<Uri>() {
        @Override
        public void onLoaded(Uri uri) {
            localAssetUri = uri;
        }

        @Override
        public void onLoadFailed() {
        }
    };

    private final AssetDialogUtils.AssetDialogCallback assetDialogCallback = new AssetDialogUtils.AssetDialogCallback() {
        @Override
        public void onOpenedFile(Uri uri) {
            if (messageViewsContainer == null ||
                asset == null) {
                return;
            }
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(new OpenedFileEvent(asset.getMimeType(),
                                                                                                              (int) asset.getSizeInBytes()));

            final Intent intent = AssetUtils.getOpenFileIntent(uri, asset.getMimeType());
            context.startActivity(intent);
        }

        @Override
        public void onSavedFile(Uri uri) {
            if (messageViewsContainer == null ||
                asset == null) {
                return;
            }
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(new SavedFileEvent(asset.getMimeType(),
                                                                                                             (int) asset.getSizeInBytes()));

            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadManager.addCompletedDownload(asset.getName(),
                                                 asset.getName(),
                                                 false,
                                                 asset.getMimeType(),
                                                 uri.getPath(),
                                                 asset.getSizeInBytes(),
                                                 true);
            Toast.makeText(context,
                           com.waz.zclient.ui.R.string.content__file__action__save_completed,
                           Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(uri);
            context.sendBroadcast(intent);
        }
    };

    private final Asset.LoadCallback<Uri> loadToOpenCallback = new Asset.LoadCallback<Uri>() {
        @Override
        public void onLoaded(Uri uri) {
            final Intent intent = AssetUtils.getOpenFileIntent(uri, asset.getMimeType());
            final boolean fileCanBeOpened = AssetUtils.fileTypeCanBeOpened(context.getPackageManager(), intent);
            AssetDialogUtils.showFileActionSheet(context, asset, uri, fileCanBeOpened, assetDialogCallback);
        }

        @Override
        public void onLoadFailed() {
        }
    };

    private final View.OnClickListener actionButtonOnClickListener = new OnDoubleClickListener() {
        @Override
        public void onDoubleClick() {
            toggleMessageLikeStatusViaDoubleTap();
        }

        @Override
        public void onSingleClick() {
            onActionButtonClicked();
        }
    };

    private final View.OnClickListener containerOnClickListener = new OnDoubleClickListener() {
        @Override
        public void onDoubleClick() {
            toggleMessageLikeStatusViaDoubleTap();
        }

        @Override
        public void onSingleClick() {
            if (footerActionCallback != null) {
                footerActionCallback.toggleVisibility();
            }
        }
    };

    public FileMessageViewController(Context context, MessageViewsContainer messageViewContainer) {
        super(context, messageViewContainer);
        view = View.inflate(context, R.layout.row_conversation_file, null);
        selectionContainer = ViewUtils.getView(view, R.id.ll__row_conversation__file__message_container);
        selectionContainer.setOnLongClickListener(this);
        selectionContainer.setOnClickListener(containerOnClickListener);
        progressDotsView = ViewUtils.getView(view, R.id.pdv__row_conversation__file_placeholder_dots);
        actionButton = ViewUtils.getView(view, R.id.aab__row_conversation__action_button);
        actionButton.setOnClickListener(actionButtonOnClickListener);
        downloadDoneIndicatorView = ViewUtils.getView(view, R.id.gtv__row_conversation__download_done_indicator);
        fileNameTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__file__filename);
        fileInfoTextView = ViewUtils.getView(view, R.id.ttv__row_conversation__file__fileinfo);
        ephemeralDotAnimationView = ViewUtils.getView(view, R.id.edav__ephemeral_view);
        ephemeralTypeView = ViewUtils.getView(view, R.id.gtv__row_conversation__file__ephemeral_type);
        ephemeralTypeView.setVisibility(GONE);

        failedTextColor = ContextCompat.getColor(context, R.color.accent_red);
    }

    @Override
    protected void onSetMessage(Separator separator) {
        messageObserver.setAndUpdate(message);
        actionButton.setMessage(message);
        messageViewsContainer.getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        ephemeralDotAnimationView.setMessage(message);
    }

    @Override
    public void recycle() {
        if (!messageViewsContainer.isTornDown()) {
            messageViewsContainer.getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        }
        ephemeralDotAnimationView.setMessage(null);
        messageObserver.clear();
        assetObserver.clear();
        message = null;
        localAssetUri = null;
        fileNameTextView.setText("");
        fileInfoTextView.setText("");
        downloadDoneIndicatorView.setVisibility(GONE);
        progressDotsView.setExpired(false);
        super.recycle();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        actionButton.setProgressColor(color);
        ephemeralDotAnimationView.setPrimaryColor(color);
        ephemeralDotAnimationView.setSecondaryColor(ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__timer_alpha),
                                                                           color));
        progressDotsView.setAccentColor(ColorUtils.injectAlpha(ThemeUtils.getEphemeralBackgroundAlpha(context),
                                                               color));
    }

    @Override
    public View getView() {
        return view;
    }

    public void startAssetDownLoad() {
        asset.getContentUri(loadToOpenCallback);
    }

    private void updateFileStatus() {
        if (asset == null) {
            return;
        }
        fileNameTextView.setText(asset.getName());
        fileInfoTextView.setText(TextViewUtils.getHighlightText(context,
                                                                getFileInfoString(asset),
                                                                failedTextColor,
                                                                false));
        actionButton.setFileExtension(getFileExtension(asset));
        downloadDoneIndicatorView.setVisibility(asset.getStatus() == AssetStatus.DOWNLOAD_DONE ? VISIBLE : GONE);
    }

    private void onActionButtonClicked() {
        switch (message.getMessageStatus()) {
            case PENDING:
                cancelAssetUpload();
                break;
            case FAILED:
                message.retry();
                break;
            default:
                onMessageContentClicked();
                break;
        }
    }

    private void cancelAssetUpload() {
        if (asset == null || !(asset.getStatus() == AssetStatus.UPLOAD_IN_PROGRESS ||
                               asset.getStatus() == AssetStatus.UPLOAD_NOT_STARTED)) {
            return;
        }
        asset.getUploadProgress().cancel();
    }

    private void onMessageContentClicked() {
        if (message.getMessageStatus() != Message.Status.SENT) {
            return;
        }

        if (asset.getStatus() == AssetStatus.UPLOAD_DONE) {
            // Ready for download to cache
            asset.getContentUri(loadToCacheCallback);
        } else if (asset.getStatus() == AssetStatus.DOWNLOAD_DONE) {
            // File is already downloaded to cache
            if (localAssetUri == null) {
                startAssetDownLoad();
            } else {
                final Intent intent = AssetUtils.getOpenFileIntent(localAssetUri, asset.getMimeType());
                final boolean fileCanBeOpened = AssetUtils.fileTypeCanBeOpened(context.getPackageManager(), intent);
                AssetDialogUtils.showFileActionSheet(context,
                                                     asset,
                                                     localAssetUri,
                                                     fileCanBeOpened,
                                                     assetDialogCallback);
            }
        } else if (asset.getStatus() == AssetStatus.DOWNLOAD_IN_PROGRESS) {
            asset.getDownloadProgress().cancel();
        }
    }

    private String getFileExtension(Asset asset) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(asset.getMimeType());
    }

    private void setProgressDotsVisible(boolean isVisible) {
        if (isVisible) {
            progressDotsView.setVisibility(VISIBLE);
            selectionContainer.setVisibility(GONE);
        } else {
            progressDotsView.setVisibility(GONE);
            selectionContainer.setVisibility(VISIBLE);
        }
    }

    private String getFileInfoString(Asset asset) {
        boolean fileSizeNotAvailable = asset.getSizeInBytes() < 0;
        String fileExtension = getFileExtension(asset);

        int infoStringId;
        switch (message.getMessageStatus()) {
            case PENDING:
                if (asset.getStatus() == AssetStatus.UPLOAD_CANCELLED) {
                    infoStringId = getCancelledStatusStringResource(fileSizeNotAvailable, fileExtension);
                } else {
                    infoStringId = getUploadingStatusStringResource(fileSizeNotAvailable, fileExtension);
                }
                break;
            case FAILED:
                infoStringId = getUploadFailedStatusStringResource(fileSizeNotAvailable, fileExtension);
                break;
            case SENT:
                // File already uploaded
                switch (asset.getStatus()) {
                    case UPLOAD_IN_PROGRESS:
                        infoStringId = getUploadingStatusStringResource(fileSizeNotAvailable, fileExtension);
                        break;
                    case UPLOAD_FAILED:
                        infoStringId = getUploadFailedStatusStringResource(fileSizeNotAvailable, fileExtension);
                        break;
                    case DOWNLOAD_IN_PROGRESS:
                        infoStringId = getDownloadingStatusStringResource(fileSizeNotAvailable, fileExtension);
                        break;
                    default:
                        infoStringId = getDefaulStatusStringResource(fileSizeNotAvailable, fileExtension);
                        break;
                }
                break;
            default:
                infoStringId = getDefaulStatusStringResource(fileSizeNotAvailable, fileExtension);
                break;
        }

        if (infoStringId == 0) {
            return "";
        }

        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   context.getString(infoStringId) :
                   context.getString(infoStringId, fileExtension.toUpperCase(Locale.getDefault()));
        } else {
            String fileSize = Formatter.formatFileSize(context, asset.getSizeInBytes());
            return TextUtils.isEmpty(fileExtension) ?
                   context.getString(infoStringId, fileSize) :
                   context.getString(infoStringId, fileSize, fileExtension.toUpperCase(Locale.getDefault()));
        }
    }

    private int getUploadingStatusStringResource(boolean fileSizeNotAvailable, String fileExtension) {
        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   R.string.content__file__status__uploading__minimized :
                   R.string.content__file__status__uploading;
        }
        return TextUtils.isEmpty(fileExtension) ?
               R.string.content__file__status__uploading :
               R.string.content__file__status__uploading__size_and_extension;
    }

    private int getCancelledStatusStringResource(boolean fileSizeNotAvailable, String fileExtension) {
        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   R.string.content__file__status__cancelled__minimized :
                   R.string.content__file__status__cancelled;
        }
        return TextUtils.isEmpty(fileExtension) ?
               R.string.content__file__status__cancelled :
               R.string.content__file__status__cancelled__size_and_extension;
    }

    private int getDownloadingStatusStringResource(boolean fileSizeNotAvailable, String fileExtension) {
        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   R.string.content__file__status__downloading__minimized :
                   R.string.content__file__status__downloading;
        }
        return TextUtils.isEmpty(fileExtension) ?
               R.string.content__file__status__downloading :
               R.string.content__file__status__downloading__size_and_extension;
    }

    private int getUploadFailedStatusStringResource(boolean fileSizeNotAvailable, String fileExtension) {
        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   R.string.content__file__status__upload_failed__minimized :
                   R.string.content__file__status__upload_failed;
        }
        return TextUtils.isEmpty(fileExtension) ?
               R.string.content__file__status__upload_failed :
               R.string.content__file__status__upload_failed__size_and_extension;
    }

    private int getDefaulStatusStringResource(boolean fileSizeNotAvailable, String fileExtension) {
        if (fileSizeNotAvailable) {
            return TextUtils.isEmpty(fileExtension) ?
                   0 :
                   R.string.content__file__status__default;
        }
        return TextUtils.isEmpty(fileExtension) ?
               R.string.content__file__status__default :
               R.string.content__file__status__default__size_and_extension;
    }

    private void toggleMessageLikeStatusViaDoubleTap() {
        if (message.isEphemeral()) {
            return;
        } else if (message.isLikedByThisUser()) {
            message.unlike();
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.unlike(message.getConversation(),
                                                                                                                       message,
                                                                                                                       ReactedToMessageEvent.Method.DOUBLE_TAP));
        } else {
            message.like();
            messageViewsContainer.getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.LIKED_MESSAGE);
            messageViewsContainer.getControllerFactory().getTrackingController().tagEvent(ReactedToMessageEvent.like(message.getConversation(),
                                                                                                                     message,
                                                                                                                     ReactedToMessageEvent.Method.DOUBLE_TAP));
        }
    }

    private void messageExpired() {
        assetObserver.clear();
        setProgressDotsVisible(true);
        progressDotsView.setExpired(true);
        ephemeralTypeView.setVisibility(VISIBLE);
    }
}
