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
package com.waz.zclient.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.waz.api.Asset;
import com.waz.api.Message;
import com.waz.api.PlaybackControls;
import com.waz.api.ProgressIndicator;
import com.waz.zclient.R;
import com.waz.zclient.core.api.scala.ModelObserver;
import com.waz.zclient.ui.utils.TypefaceUtils;

import java.util.Locale;

public class AssetActionButton extends GlyphProgressView {

    private Asset asset;
    private Message message;

    private Drawable normalButtonBackground;
    private Drawable errorButtonBackground;
    private Drawable fileDrawable;

    private String overrideGlyph;

    private final ModelObserver<Message> messageModelObserver = new ModelObserver<Message>() {
        @Override
        public void updated(Message message) {
            if (asset == null) {
                asset = message.getAsset();
                assetModelObserver.setAndUpdate(asset);
            }
        }
    };

    private final ModelObserver<Asset> assetModelObserver = new ModelObserver<Asset>() {
        @Override
        public void updated(Asset model) {
            updateAssetStatus();
        }
    };

    private final ModelObserver<ProgressIndicator> progressIndicatorObserver = new ModelObserver<ProgressIndicator>() {
        @Override
        public void updated(ProgressIndicator progressIndicator) {
            switch (progressIndicator.getState()) {
                case CANCELLED:
                case FAILED:
                case COMPLETED:
                    clearProgress();
                    break;
                case RUNNING:
                    if (progressIndicator.isIndefinite()) {
                        startEndlessProgress();
                    } else {
                        float progress = progressIndicator.getTotalSize() == 0 ? 0 : (float) progressIndicator.getProgress() / (float) progressIndicator.getTotalSize();
                        setProgress(progress);
                    }
                    break;
                case UNKNOWN:
                default:
                    break;
            }
        }
    };

    public AssetActionButton(Context context) {
        this(context, null);
    }

    public AssetActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AssetActionButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setMessage(Message message) {
        this.message = message;
        assetModelObserver.clear();
        progressIndicatorObserver.clear();
        asset = null;
        messageModelObserver.setAndUpdate(message);
    }

    @Override
    protected void init() {
        super.init();
        normalButtonBackground = getContext().getResources().getDrawable(R.drawable.selector__icon_button__background__video_message);
        errorButtonBackground = getContext().getResources().getDrawable(R.drawable.selector__icon_button__background__video_message__error);
        setBackground(normalButtonBackground);
    }

    private void resetOverride() {
        overrideGlyph = null;
        fileDrawable = null;
        updateAssetStatus();
    }

    public void setPlaybackControls(PlaybackControls playbackControls) {
        if (playbackControls.isPlaying()) {
            showPauseButton();
        } else {
            resetOverride();
        }
    }

    private void showPauseButton() {
        overrideGlyph = getContext().getString(R.string.glyph__pause);
        updateAssetStatus();
    }

    public void setFileExtension(String fileExtension) {
        fileDrawable = new FileDrawable(getContext(), fileExtension);
        updateAssetStatus();
    }

    private void updateAssetStatus() {
        if (overrideGlyph != null) {
            updateViews(overrideGlyph, normalButtonBackground, null);
            return;
        }

        if (asset == null) {
            return;
        }

        switch (asset.getStatus()) {
            case UPLOAD_CANCELLED:
                updateViews(getContext().getString(R.string.glyph__close), normalButtonBackground, null);
                break;
            case UPLOAD_FAILED:
                String action;
                if (message.getMessageStatus() == Message.Status.SENT) {
                    // receiver
                    action = getContext().getString(R.string.glyph__play);
                } else {
                    // sender
                    action = getContext().getString(R.string.glyph__redo);
                }
                updateViews(action, errorButtonBackground, null);
                break;
            case UPLOAD_NOT_STARTED:
            case UPLOAD_IN_PROGRESS:
                if (message.getMessageStatus() == Message.Status.FAILED) {
                    updateViews(getContext().getString(R.string.glyph__redo), errorButtonBackground, null);
                }
                else if (message.getMessageStatus() == Message.Status.SENT) {
                    // receiver
                    updateViews(getContext().getString(R.string.glyph__play),
                                normalButtonBackground,
                                asset.getUploadProgress());
                } else {
                    // sender
                    updateViews(getContext().getString(R.string.glyph__close),
                                normalButtonBackground,
                                asset.getUploadProgress());
                }
                break;
            case UPLOAD_DONE:
            case DOWNLOAD_DONE:
                if (fileDrawable != null) {
                    updateViews("", fileDrawable, null);
                    break;
                }
                updateViews(getContext().getString(R.string.glyph__play), normalButtonBackground, null);
                break;
            case DOWNLOAD_FAILED:
                updateViews(getContext().getString(R.string.glyph__redo), errorButtonBackground, null);
                break;
            case DOWNLOAD_IN_PROGRESS:
                updateViews(getContext().getString(R.string.glyph__close), normalButtonBackground, asset.getDownloadProgress());
                break;
            default:
                break;
        }
    }

    private void updateViews(String action, Drawable background, ProgressIndicator progressIndicator) {
        setText(action);
        setBackground(background);
        if (progressIndicator == null) {
            clearProgress();
            progressIndicatorObserver.clear();
        } else {
            progressIndicatorObserver.addAndUpdate(progressIndicator);
        }
    }

    private static class FileDrawable extends Drawable {

        private final int textCorrectionSpacing; //to align the text nicely within the glyph bounds

        private final String fileGlyph;
        private final String extension;

        private final Paint glyphPaint = new Paint();
        private final Paint textPaint = new Paint();

        FileDrawable(Context context, String extension) {
            this.fileGlyph = context.getResources().getString(R.string.glyph__file);
            this.extension = extension;
            this.textCorrectionSpacing = context.getResources().getDimensionPixelSize(R.dimen.wire__padding__4);

            glyphPaint.setTypeface(TypefaceUtils.getTypeface(TypefaceUtils.getGlyphsTypefaceName()));
            glyphPaint.setColor(context.getResources().getColor(R.color.black_48));
            glyphPaint.setAntiAlias(true);
            glyphPaint.setTextAlign(Paint.Align.CENTER);
            glyphPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.content__audio_message__button__size));

            textPaint.setColor(context.getResources().getColor(R.color.white));
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(context.getResources().getDimensionPixelSize(R.dimen.wire__text_size__tiny));
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawText(fileGlyph, getBounds().width() / 2, getBounds().height(), glyphPaint);
            if (extension != null) {
                canvas.drawText(extension.toUpperCase(Locale.getDefault()),
                                getBounds().width() / 2,
                                getBounds().height() - textCorrectionSpacing,
                                textPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

}
