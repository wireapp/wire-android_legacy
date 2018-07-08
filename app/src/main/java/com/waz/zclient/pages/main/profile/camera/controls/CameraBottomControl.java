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
package com.waz.zclient.pages.main.profile.camera.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ViewAnimator;
import com.waz.zclient.R;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.utils.SquareOrientation;
import com.waz.zclient.utils.ViewUtils;

public class CameraBottomControl extends ViewAnimator {
    private CameraBottomControlCallback cameraBottomControlCallback;
    private SquareOrientation currentConfigOrientation = SquareOrientation.NONE;
    private View closeButton;
    private View takeAPictureButton;
    private View galleryPickerButton;

    public CameraBottomControl(Context context) {
        super(context);
    }

    public CameraBottomControl(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCameraBottomControlCallback(CameraBottomControlCallback cameraBottomControlCallback) {
        this.cameraBottomControlCallback = cameraBottomControlCallback;
    }

    public void setMode(CameraContext cameraContext) {
        switch (cameraContext) {
            case SETTINGS:
                setMode(true);
                break;
            case SIGN_UP:
                setMode(false);
                break;
            case MESSAGE:
                setMode(true);
                break;
        }
    }

    private void setMode(boolean allowCloseButton) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        addCameraControlsLayout(inflater, allowCloseButton, true);
        setConfirmationMenuVisible(false);
    }

    private void addCameraControlsLayout(LayoutInflater inflater, boolean allowCloseButton, boolean showGalleryButton) {
        inflater.inflate(R.layout.camera_control_choose_image_source, this, true);

        galleryPickerButton = ViewUtils.getView(this, R.id.gtv__camera_control__gallery);
        if (showGalleryButton) {
            galleryPickerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cameraBottomControlCallback.onOpenImageGallery();
                }
            });
        } else {
            galleryPickerButton.setVisibility(View.GONE);
        }

        takeAPictureButton = ViewUtils.getView(this, R.id.gtv__camera_control__take_a_picture);
        takeAPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraBottomControlCallback != null) {
                    disableShutterButton();
                    cameraBottomControlCallback.onTakePhoto();
                }
            }
        });

        closeButton = ViewUtils.getView(this, R.id.gtv__camera_control__close);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraBottomControlCallback != null) {
                    cameraBottomControlCallback.onClose();
                }
            }
        });
        if (!allowCloseButton) {
            closeButton.setVisibility(GONE);
        }
    }

    public void setConfirmationMenuVisible(boolean isConfirming) {
        if (isConfirming) {
            setDisplayedChild(1);
        } else {
            enableShutterButton();
            setDisplayedChild(0);
        }
    }

    public void setConfigOrientation(SquareOrientation configOrientation) {
        if (configOrientation.equals(currentConfigOrientation) ||
            closeButton == null ||
            takeAPictureButton == null ||
            galleryPickerButton == null) {
            return;
        }

        int currentOrientation = (int) closeButton.getRotation();
        int rotation = 0;

        switch (configOrientation) {

            case NONE:
                break;
            case PORTRAIT_STRAIGHT:
                rotation = 0;
                break;
            case PORTRAIT_UPSIDE_DOWN:
                rotation = 2 * currentOrientation;
                break;
            case LANDSCAPE_LEFT:
                if (currentOrientation == -180) {
                    setRotation(180);
                }
                rotation = 90;
                break;
            case LANDSCAPE_RIGHT:
                if (currentOrientation == 180) {
                    setRotation(-180);
                }
                rotation = -90;
                break;
        }

        currentConfigOrientation = configOrientation;

        closeButton.animate().rotation(rotation).start();
        takeAPictureButton.animate().rotation(rotation).start();
        galleryPickerButton.animate().rotation(rotation).start();
    }

    private void setRotation(int rotation) {
        closeButton.setRotation(rotation);
        takeAPictureButton.setRotation(rotation);
        galleryPickerButton.setRotation(rotation);
    }

    @Override
    public void setInAnimation(Animation inAnimation) {
        inAnimation.setStartOffset(getResources().getInteger(R.integer.camera__control__ainmation__in_delay));
        inAnimation.setInterpolator(new Expo.EaseOut());
        inAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setInAnimation(inAnimation);
    }

    @Override
    public void setOutAnimation(Animation outAnimation) {
        outAnimation.setInterpolator(new Expo.EaseIn());
        outAnimation.setDuration(getContext().getResources().getInteger(R.integer.calling_animation_duration_medium));
        super.setOutAnimation(outAnimation);
    }

    public void enableShutterButton() {
        if (takeAPictureButton != null) {
            takeAPictureButton.setEnabled(true);
        }
    }

    private void disableShutterButton() {
        if (takeAPictureButton != null) {
            takeAPictureButton.setEnabled(false);
        }
    }

    public interface CameraBottomControlCallback {
        void onClose();
        void onTakePhoto();
        void onOpenImageGallery();
    }
}
