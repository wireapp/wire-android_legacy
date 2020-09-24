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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.camera.FlashMode;
import com.waz.zclient.utils.SquareOrientation;
import com.waz.zclient.utils.ViewUtils;

import java.util.HashSet;
import java.util.Set;

public class CameraTopControl extends FrameLayout {

    private CameraTopControlCallback cameraTopControlCallback;
    private TextView cameraDirectionButton;
    private TextView cameraFlashButton;
    private SquareOrientation currentConfigOrientation = SquareOrientation.NONE;
    private Set<FlashMode> flashModes = new HashSet<>();

    public void setCameraTopControlCallback(CameraTopControlCallback cameraTopControlCallback) {
        this.cameraTopControlCallback = cameraTopControlCallback;
    }

    public CameraTopControl(Context context) {
        this(context, null);
    }

    public CameraTopControl(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraTopControl(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.camera_top_control, this, true);
        cameraDirectionButton = ViewUtils.getView(this, R.id.gtv__camera__top_control__change_camera);
        cameraDirectionButton.setOnClickListener(v -> nextCamera());

        cameraFlashButton = ViewUtils.getView(this, R.id.gtv__camera__top_control__flash_setting);
        cameraFlashButton.setOnClickListener(v -> {
            FlashMode nextFlashMode = getNextFlashState(cameraTopControlCallback.getFlashMode());
            if (cameraTopControlCallback != null) {
                cameraTopControlCallback.setFlashMode(nextFlashMode);
            }
            setFlashModeButton(nextFlashMode);
        });

        cameraFlashButton.setVisibility(View.GONE);
    }

    private void setFlashModeButton(FlashMode flashMode) {
        switch (flashMode) {
            case OFF:
                cameraFlashButton.setText(getResources().getString(R.string.glyph__flash_off));
                break;
            case ON:
                cameraFlashButton.setText(getResources().getString(R.string.glyph__flash));
                break;
            case AUTO:
                cameraFlashButton.setText(getResources().getString(R.string.glyph__flash_auto));
                break;
            case TORCH:
                cameraFlashButton.setText(getResources().getString(R.string.glyph__plus));
                break;
        }

        ObjectAnimator.ofFloat(cameraFlashButton, View.ALPHA, 0, 1).setDuration(getResources().getInteger(R.integer.camera__control__ainmation__duration_long)).start();
    }


    private FlashMode getNextFlashState(FlashMode currentFlashMode) {
        switch (currentFlashMode) {
            case OFF:
                if (flashModes.contains(FlashMode.ON)) {
                    return FlashMode.ON;
                }
            case ON:
                if (flashModes.contains(FlashMode.AUTO)) {
                    return FlashMode.AUTO;
                }
                return FlashMode.OFF;
            case AUTO:
                return FlashMode.OFF;
            case TORCH:
                break;
        }
        return FlashMode.OFF;
    }

    private void nextCamera() {
        if (cameraTopControlCallback != null) {
            cameraTopControlCallback.nextCamera();
        }
    }

    public void setFlashStates(Set<FlashMode> availableFlashModes, FlashMode currentFlashMode) {
        this.flashModes = availableFlashModes;
        if (availableFlashModes.isEmpty()) {
            cameraFlashButton.setVisibility(View.GONE);
        } else {
            cameraFlashButton.setVisibility(View.VISIBLE);
        }
        setFlashModeButton(currentFlashMode);
    }

    public void enableCameraSwitchButtion(boolean enableCameraSwitch) {
        if (cameraDirectionButton == null) {
            return;
        }
        cameraDirectionButton.setVisibility(enableCameraSwitch ? VISIBLE : GONE);
    }

    public void setConfigOrientation(SquareOrientation configOrientation) {
        if (configOrientation.equals(currentConfigOrientation)) {
            return;
        }

        int currentOrientation = (int) cameraDirectionButton.getRotation();
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

        cameraDirectionButton.animate().rotation(rotation).start();
        cameraFlashButton.animate().rotation(rotation).start();
    }

    private void setRotation(int rotation) {
        cameraDirectionButton.setRotation(rotation);
        cameraFlashButton.setRotation(rotation);
    }

    public interface CameraTopControlCallback {
        void nextCamera();
        void setFlashMode(FlashMode mode);
        FlashMode getFlashMode();
    }

}
