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
package com.waz.zclient.pages.extendedcursor.image;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.waz.zclient.R;
import com.waz.zclient.camera.CameraPreviewObserver;
import com.waz.zclient.camera.FlashMode;
import com.waz.zclient.camera.views.CameraPreviewTextureView;
import com.waz.zclient.utils.ViewUtils;

import java.util.Set;

public class CursorCameraLayout extends FrameLayout implements View.OnClickListener, CameraPreviewObserver {

    private CameraPreviewTextureView cameraPreview;
    private ProgressBar progressBar;
    private TextView cameraNotAvailableTextView;
    private View buttonFrontBack;
    private View buttonGoToCamera;
    private View buttonGoToVideo;
    private View buttonTakePicture;
    private Callback callback;
    private boolean pendingPhotoTaking;
    private boolean isOpenVideoClicked;

    public CursorCameraLayout(Context context) {
        this(context, null);
    }

    public CursorCameraLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorCameraLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY), heightMeasureSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        cameraPreview = ViewUtils.getView(this, R.id.cp__small_camera_preview);
        cameraPreview.setObserver(this);
        cameraPreview.setFlashMode(FlashMode.OFF); //TODO maybe add a flash button in this preview

        progressBar = ViewUtils.getView(this, R.id.pb__cursor_images);
        cameraNotAvailableTextView = ViewUtils.getView(this, R.id.ttv__camera_not_available_message);

        buttonFrontBack = ViewUtils.getView(this, R.id.gtv__cursor_image__front_back);
        buttonGoToCamera = ViewUtils.getView(this, R.id.gtv__cursor_image__open_camera);
        buttonGoToVideo = ViewUtils.getView(this, R.id.gtv__cursor_image__open_video);
        buttonTakePicture = ViewUtils.getView(this, R.id.gtv__cursor_image__take_picture);

        buttonFrontBack.setOnClickListener(this);
        buttonGoToCamera.setOnClickListener(this);
        buttonGoToVideo.setOnClickListener(this);
        buttonTakePicture.setOnClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        buttonFrontBack.setVisibility(View.INVISIBLE);
        buttonTakePicture.setVisibility(View.INVISIBLE);
        showProgress(true);

        if (callback != null) {
            callback.onCameraPreviewAttached();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (callback != null) {
            callback.onCameraPreviewDetached();
        }
        showProgress(false);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.gtv__cursor_image__front_back:
                flip();
                break;
            case R.id.gtv__cursor_image__open_camera:
                if (this.callback != null) {
                    showProgress(false);
                    callback.openCamera();
                }
                break;
            case R.id.gtv__cursor_image__open_video:
                if (this.callback != null) {
                    showProgress(true);
                    isOpenVideoClicked = true;
                    cameraPreview.closeCamera();
                }
                break;
            case R.id.gtv__cursor_image__take_picture:
                if (pendingPhotoTaking) {
                    return;
                }
                showProgress(true);
                pendingPhotoTaking = true;
                cameraPreview.takePicture();
                break;
        }
    }

    private void flip() {
        cameraPreview.nextCamera();
        buttonFrontBack.setVisibility(View.INVISIBLE);
        buttonTakePicture.setVisibility(View.INVISIBLE);
        showProgress(true);
    }

    public void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void onClose() {
        showProgress(false);
    }

    @Override
    public void onCameraLoaded(Set<FlashMode> flashModes) {
        showProgress(false);
        cameraNotAvailableTextView.setVisibility(GONE);

        buttonFrontBack.animate().alpha(1).withStartAction(new Runnable() {
            @Override
            public void run() {
                buttonFrontBack.setAlpha(0);
                buttonFrontBack.setVisibility(View.VISIBLE);
            }
        });

        buttonTakePicture.animate().alpha(1).withStartAction(new Runnable() {
            @Override
            public void run() {
                buttonTakePicture.setAlpha(0);
                buttonTakePicture.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onCameraLoadingFailed() {
        showProgress(false);
        cameraNotAvailableTextView.setVisibility(VISIBLE);
    }

    @Override
    public void onCameraReleased() {
        if (!isOpenVideoClicked) {
            return;
        }

        showProgress(false);
        isOpenVideoClicked = false;
        callback.openVideo();
    }

    @Override
    public void onPictureTaken(byte[] imageData) {
        if (callback != null) {
            callback.onPictureTaken(imageData);
        }
        pendingPhotoTaking = false;
        showProgress(false);
    }

    @Override
    public void onFocusBegin(Rect focusArea) {
    }

    @Override
    public void onFocusComplete() {
    }

    public interface Callback {
        void openCamera();

        void openVideo();

        void onCameraPreviewAttached();

        void onCameraPreviewDetached();

        void onPictureTaken(byte[] imageData);
    }
}
