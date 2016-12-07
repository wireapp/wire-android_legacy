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
package com.waz.zclient.controllers.notifications;


import com.waz.api.EphemeralExpiration;
import com.waz.api.ErrorResponse;
import com.waz.api.IConversation;
import com.waz.api.KindOfTrackingEvent;
import com.waz.api.TrackingEvent;
import com.waz.api.TrackingEventsHandler;
import com.waz.service.call.AvsMetrics;
import com.waz.service.push.PushTrackingService;
import com.waz.zclient.controllers.tracking.ITrackingController;
import com.waz.zclient.controllers.tracking.events.calling.EndedCallAVSMetricsEvent;
import com.waz.zclient.core.controllers.tracking.attributes.CompletedMediaType;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.CancelledFileUploadEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.FailedFileDownloadEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.FailedFileUploadEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.InitiatedFileDownloadEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.InitiatedFileUploadEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SuccessfullyDownloadedFileEvent;
import com.waz.zclient.core.controllers.tracking.events.filetransfer.SuccessfullyUploadedFileEvent;
import com.waz.zclient.core.controllers.tracking.events.media.CompletedMediaActionEvent;
import com.waz.zclient.core.controllers.tracking.events.media.SentPictureEvent;
import com.waz.zclient.core.controllers.tracking.events.notifications.NotificationInformationEvent;
import org.threeten.bp.Duration;
import timber.log.Timber;

public class AppTrackingEventsHandler implements TrackingEventsHandler {

    private static final String UNDEFINED_ASSET_MIMETYPE = "";
    private static final int UNDEFINED_ASSET_SIZE = -1;
    private static final String ASSET_MIME_TYPE_AUDIO = "audio";
    private static final String ASSET_MIME_TYPE_VIDEO = "video";

    private ITrackingController trackingController;

    public AppTrackingEventsHandler(ITrackingController trackingController) {
        this.trackingController = trackingController;
    }

    @Override
    public void onNotificationsEvent(PushTrackingService.NotificationsEvent ev) {
        trackingController.tagEvent(new NotificationInformationEvent(ev));
    }

    @Override
    public void onTrackingEvent(TrackingEvent trackingEvent) {

        String assetMimeType = trackingEvent.getAssetMimeType().getOrElse(UNDEFINED_ASSET_MIMETYPE).toString();
        int assetSize = trackingEvent.getAssetSizeInBytes().getOrElse(Long.valueOf(UNDEFINED_ASSET_SIZE)).intValue();

        switch (trackingEvent.getKind()) {
            case ASSET_UPLOAD_STARTED:
                String conversationType = trackingEvent.getConversationType().getOrElse(IConversation.Type.UNKNOWN).toString();
                boolean withOtto = trackingEvent.isInConversationWithOtto().getOrElse(false);
                trackingController.tagEvent(new InitiatedFileUploadEvent(assetMimeType,
                                                                         assetSize,
                                                                         conversationType,
                                                                         isEphemeral(trackingEvent),
                                                                         getEphemeraDurationAsSec(trackingEvent)));
                CompletedMediaType mediaType = CompletedMediaType.FILE;
                if (assetMimeType.contains(ASSET_MIME_TYPE_AUDIO)) {
                    mediaType = CompletedMediaType.AUDIO;
                } else if (assetMimeType.contains(ASSET_MIME_TYPE_VIDEO)) {
                    mediaType = CompletedMediaType.VIDEO;
                }

                trackingController.tagEvent(new CompletedMediaActionEvent(mediaType,
                                                                          conversationType,
                                                                          withOtto,
                                                                          isEphemeral(trackingEvent),
                                                                          getEphemeraDurationAsSec(trackingEvent)));
                break;
            case IMAGE_UPLOAD_AS_ASSET:
                String type = trackingEvent.getConversationType().getOrElse(IConversation.Type.UNKNOWN).toString();
                boolean withBot = trackingEvent.isInConversationWithOtto().getOrElse(false);
                trackingController.tagEvent(new InitiatedFileUploadEvent(assetMimeType,
                                                                         assetSize,
                                                                         type,
                                                                         isEphemeral(trackingEvent),
                                                                         getEphemeraDurationAsSec(trackingEvent)));

                trackingController.tagEvent(new SentPictureEvent(SentPictureEvent.Source.CLIP, type,
                                                                 SentPictureEvent.Method.DEFAULT,
                                                                 SentPictureEvent.SketchSource.NONE,
                                                                 withBot,
                                                                 isEphemeral(trackingEvent),
                                                                 getEphemeraDurationAsSec(trackingEvent)));
                trackingController.tagEvent(new CompletedMediaActionEvent(CompletedMediaType.PHOTO,
                                                                          type,
                                                                          withBot,
                                                                          isEphemeral(trackingEvent),
                                                                          getEphemeraDurationAsSec(trackingEvent)));
                break;
            case ASSET_UPLOAD_SUCCESSFUL:
                int durationInSeconds = (int) (trackingEvent.getDuration().getOrElse(Duration.ofSeconds(-1)).toMillis() / 1000);
                trackingController.tagEvent(new SuccessfullyUploadedFileEvent(assetMimeType, assetSize, durationInSeconds));
                break;
            case ASSET_UPLOAD_CANCELLED:
                trackingController.tagEvent(new CancelledFileUploadEvent(assetMimeType));
                break;
            case ASSET_UPLOAD_FAILED:
                String exceptionType = "";
                String exceptionDetails = "";
                ErrorResponse errorResponse = trackingEvent.getErrorResponse().getOrElse(null);
                if (errorResponse != null) {
                    exceptionType = String.valueOf(errorResponse.getCode());
                    exceptionDetails = errorResponse.getLabel() + ", " + errorResponse.getMessage();
                }
                trackingController.tagEvent(new FailedFileUploadEvent(assetMimeType, exceptionType, exceptionDetails));
                break;
            case ASSET_DOWNLOAD_STARTED:
                trackingController.tagEvent(new InitiatedFileDownloadEvent(assetMimeType, assetSize));
                break;
            case ASSET_DOWNLOAD_SUCCESSFUL:
                trackingController.tagEvent(new SuccessfullyDownloadedFileEvent(assetMimeType, assetSize));
                break;
            case ASSET_DOWNLOAD_FAILED:
                trackingController.tagEvent(new FailedFileDownloadEvent(assetMimeType));
                break;
        }


    }

    @Override
    public void onAvsMetricsEvent(AvsMetrics avsMetrics) {
        Timber.i("AVS metrics: %s", avsMetrics);

        // TODO: Separate video call when avsMetrics.isVideoCall() works
        /*
        if (avsMetrics.isVideoCall()) {
            trackingController.tagAVSMetricEvent(new EndedVideoCallAVSMetricsEvent(avsMetrics));
        } else {
            trackingController.tagAVSMetricEvent(new EndedCallAVSMetricsEvent(avsMetrics));
        }
        */
        trackingController.tagAVSMetricEvent(new EndedCallAVSMetricsEvent(avsMetrics));
    }

    private boolean isEphemeral(TrackingEvent trackingEvent) {
        if (trackingEvent.getKind() != KindOfTrackingEvent.ASSET_UPLOAD_STARTED &&
            trackingEvent.getKind() != KindOfTrackingEvent.IMAGE_UPLOAD_AS_ASSET) {
            return false;
        }
        return trackingEvent.getEphemeralExpiration() != EphemeralExpiration.NONE;
    }

    private String getEphemeraDurationAsSec(TrackingEvent trackingEvent) {
        if (trackingEvent.getKind() != KindOfTrackingEvent.ASSET_UPLOAD_STARTED &&
            trackingEvent.getKind() != KindOfTrackingEvent.IMAGE_UPLOAD_AS_ASSET) {
            return "";
        }
        if (isEphemeral(trackingEvent)) {
            return String.valueOf(trackingEvent.getEphemeralExpiration().duration().toSeconds());
        }
        return "";
    }
}
