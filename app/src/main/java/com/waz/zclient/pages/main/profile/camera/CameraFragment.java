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
package com.waz.zclient.pages.main.profile.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.camera.CameraPreviewObserver;
import com.waz.zclient.camera.FlashMode;
import com.waz.zclient.camera.views.CameraPreviewTextureView;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.camera.CameraActionObserver;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.drawing.IDrawingController;
import com.waz.zclient.controllers.orientation.OrientationControllerObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.extendedcursor.image.ImagePreviewLayout;
import com.waz.zclient.pages.main.conversation.AssetIntentsManager;
import com.waz.zclient.pages.main.profile.camera.controls.CameraBottomControl;
import com.waz.zclient.pages.main.profile.camera.controls.CameraTopControl;
import com.waz.zclient.ui.animation.interpolators.penner.Expo;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.SquareOrientation;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.ProgressView;

import java.util.Set;

public class CameraFragment extends BaseFragment<CameraFragment.Container> implements CameraPreviewObserver,
                                                                                      OrientationControllerObserver,
                                                                                      AccentColorObserver,
                                                                                      OnBackPressedListener,
                                                                                      ImagePreviewLayout.Callback,
                                                                                      CameraTopControl.CameraTopControlCallback,
                                                                                      CameraBottomControl.CameraBottomControlCallback {
    public static final String TAG = CameraFragment.class.getName();
    private static final String CAMERA_CONTEXT = "CAMERA_CONTEXT";
    private static final String SHOW_GALLERY = "SHOW_GALLERY";
    private static final String ALREADY_OPENED_GALLERY = "ALREADY_OPENED_GALLERY";

    private FrameLayout imagePreviewContainer;
    private ProgressView previewProgressBar;

    private CameraPreviewTextureView cameraPreview;
    private TextView cameraNotAvailableTextView;
    private ImageAsset imageAsset;
    private CameraTopControl cameraTopControl;
    private CameraBottomControl cameraBottomControl;
    private CameraFocusView focusView;
    private AssetIntentsManager intentsManager;

    private CameraContext cameraContext = null;
    private boolean alreadyOpenedGallery;

    private int cameraPreviewAnimationDuration;
    private int cameraControlAnimationDuration;

    //TODO pictureFromCamera is for tracking only, try to remove
    private boolean pictureFromCamera;

    public static CameraFragment newInstance(CameraContext cameraContext) {
        return newInstance(cameraContext, false);
    }

    public static CameraFragment newInstance(CameraContext cameraContext, boolean showGallery) {
        CameraFragment fragment = new CameraFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(CAMERA_CONTEXT, cameraContext.ordinal());
        bundle.putBoolean(SHOW_GALLERY, showGallery);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        // opening from profile
        ensureCameraContext();
        if (nextAnim == R.anim.camera__from__profile__transition) {
            int controlHeight = getResources().getDimensionPixelSize(R.dimen.camera__control__height);
            return new ProfileToCameraAnimation(enter,
                                                getResources().getInteger(R.integer.framework_animation_duration_medium),
                                                0,
                                                controlHeight, 0);
        }


        return super.onCreateAnimation(transit, enter, nextAnim);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        intentsManager = new AssetIntentsManager(getActivity(), new AssetIntentsManager.Callback() {
            @Override
            public void onDataReceived(AssetIntentsManager.IntentType type, URI uri) {
                processGalleryImage(uri);
            }

            @Override
            public void onCanceled(AssetIntentsManager.IntentType type) {
                showCameraFeed();
            }

            @Override
            public void onFailed(AssetIntentsManager.IntentType type) {
                showCameraFeed();
            }

            @Override
            public void openIntent(Intent intent, AssetIntentsManager.IntentType intentType) {
                startActivityForResult(intent, intentType.requestCode);
            }

            @Override
            public void onPermissionFailed(AssetIntentsManager.IntentType type) {}
        }, savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup c, Bundle savedInstanceState) {
        ensureCameraContext();
        final View view = inflater.inflate(R.layout.fragment_camera, c, false);

        //TODO allow selection of a camera 'facing' for different cameraContexts
        cameraPreview = ViewUtils.getView(view, R.id.cptv__camera_preview);
        cameraPreview.setObserver(this);
        cameraNotAvailableTextView = ViewUtils.getView(view, R.id.ttv__camera_not_available_message);

        cameraTopControl = ViewUtils.getView(view, R.id.ctp_top_controls);
        cameraTopControl.setCameraTopControlCallback(this);
        cameraTopControl.setAlpha(0);
        cameraTopControl.setVisibility(View.VISIBLE);

        cameraBottomControl = ViewUtils.getView(view, R.id.cbc__bottom_controls);
        cameraBottomControl.setCameraBottomControlCallback(this);
        cameraBottomControl.setMode(cameraContext);
        cameraBottomControl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // do nothing but consume event
            }
        });

        imagePreviewContainer = ViewUtils.getView(view, R.id.fl__preview_container);
        if (cameraContext != CameraContext.MESSAGE) {
            imagePreviewContainer.setVisibility(View.GONE);
        }
        imagePreviewContainer.setVisibility(View.GONE);

        previewProgressBar = ViewUtils.getView(view, R.id.pv__preview);
        previewProgressBar.setVisibility(View.GONE);

        focusView = ViewUtils.getView(view, R.id.cfv__focus);

        if (savedInstanceState != null) {
            alreadyOpenedGallery = savedInstanceState.getBoolean(ALREADY_OPENED_GALLERY);
        }

        cameraControlAnimationDuration = getResources().getInteger(R.integer.camera__control__ainmation__duration);
        cameraPreviewAnimationDuration = getResources().getInteger(R.integer.camera__preview__ainmation__duration);

        view.setBackgroundResource(R.color.black);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        if (LayoutSpec.isPhone(getActivity())) {
            getControllerFactory().getOrientationController().addOrientationControllerObserver(this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(ALREADY_OPENED_GALLERY, alreadyOpenedGallery);
        intentsManager.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getControllerFactory().getOrientationController().removeOrientationControllerObserver(this);

        super.onStop();
    }

    @Override
    public void onDestroyView() {
        hideCameraFeed();
        imagePreviewContainer = null;
        previewProgressBar = null;
        imageAsset = null;
        focusView = null;
        cameraTopControl = null;


        cameraBottomControl.animate()
                           .translationY(getView().getMeasuredHeight())
                           .setDuration(cameraControlAnimationDuration)
                           .setInterpolator(new Expo.EaseIn());

        super.onDestroyView();
    }

    @Override
    public boolean onBackPressed() {
        onClose();
        return true;
    }

    public CameraContext getCameraContext() {
        return cameraContext;
    }

    private void ensureCameraContext() {
        if (cameraContext != null) {
            return;
        }
        cameraContext = CameraContext.values()[getArguments().getInt(CAMERA_CONTEXT)];
    }

    private void disableCameraButtons() {
        if (getView() == null) {
            return;
        }
        ViewUtils.getView(getView(), R.id.gtv__camera_control__take_a_picture).setVisibility(View.GONE);
        ViewUtils.getView(getView(), R.id.gtv__camera__top_control__change_camera).setVisibility(View.GONE);
        ViewUtils.getView(getView(), R.id.gtv__camera__top_control__flash_setting).setVisibility(View.GONE);
    }

    @Override
    public void onCameraLoaded(Set<FlashMode> flashModes) {
        cameraTopControl.setFlashStates(flashModes, cameraPreview.getCurrentFlashMode());
        cameraTopControl.enableCameraSwitchButtion(cameraPreview.getNumberOfCameras() > 1);
        showCameraFeed();

        boolean openGalleryArg = getArguments().getBoolean(SHOW_GALLERY);
        if (!alreadyOpenedGallery && openGalleryArg) {
            alreadyOpenedGallery = true;
            openGallery();
        }
        cameraNotAvailableTextView.setVisibility(View.GONE);
    }

    @Override
    public void onCameraLoadingFailed() {
        if (getContainer() != null) {
            getControllerFactory().getCameraController().onCameraNotAvailable(cameraContext);
        }
        disableCameraButtons();
        cameraNotAvailableTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCameraReleased() {
        //no need to override since we don't exit the app
    }

    @Override
    public void onPictureTaken(ImageAsset imageAsset) {
        this.imageAsset = imageAsset;
        showPreview(imageAsset, true);
    }

    @Override
    public void onFocusBegin(Rect focusArea) {
        focusView.setColor(getControllerFactory().getAccentColorController().getColor());
        int x = focusArea.centerX();
        int y = focusArea.centerY();
        focusView.setX(x - focusView.getWidth() / 2);
        focusView.setY(y - focusView.getHeight() / 2);
        focusView.showFocusView();
    }

    @Override
    public void onFocusComplete() {
        if (focusView != null) {
            focusView.hideFocusView();
        }
    }

    public void openGallery() {
        intentsManager.openGallery();
        getActivity().overridePendingTransition(R.anim.camera_in, R.anim.camera_out);
    }

    @Override
    public void nextCamera() {
        cameraPreview.nextCamera();
    }

    @Override
    public void setFlashMode(FlashMode mode) {
        cameraPreview.setFlashMode(mode);
    }

    @Override
    public FlashMode getFlashMode() {
        return cameraPreview.getCurrentFlashMode();
    }

    @Override
    public void onClose() {
        cameraPreview.setFlashMode(FlashMode.OFF); //set back to default off when leaving camera
        getControllerFactory().getCameraController().closeCamera(cameraContext);
    }

    @Override
    public void onTakePhoto() {
        if (cameraContext != CameraContext.SIGN_UP) {
            previewProgressBar.setVisibility(View.VISIBLE);
        }
        ViewUtils.fadeOutView(cameraTopControl, cameraControlAnimationDuration);
        cameraPreview.takePicture();
    }

    @Override
    public void onOpenImageGallery() {
        openGallery();
    }

    @Override
    public void onCancelPreview() {
        previewProgressBar.setVisibility(View.GONE);

        ObjectAnimator animator = ObjectAnimator.ofFloat(imagePreviewContainer, View.ALPHA, 1, 0);
        animator.setDuration(cameraControlAnimationDuration);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                hideImagePreviewOnAnimationEnd();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                hideImagePreviewOnAnimationEnd();
            }
        });
        animator.start();

        showCameraFeed();
    }

    @Override
    public void onSketchOnPreviewPicture(ImageAsset imageAsset,
                                         ImagePreviewLayout.Source source,
                                         DrawingController.DrawingMethod method) {
        getControllerFactory().getDrawingController().showDrawing(imageAsset,
                                                                  IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW);
    }

    @Override
    public void onSendPictureFromPreview(ImageAsset imageAsset, ImagePreviewLayout.Source source) {
        getControllerFactory().getCameraController().onBitmapSelected(imageAsset, pictureFromCamera, cameraContext);
    }

    private void showPreview(ImageAsset imageAsset, boolean bitmapFromCamera) {
        pictureFromCamera = bitmapFromCamera;
        hideCameraFeed();

        previewProgressBar.setVisibility(View.GONE);

        ImagePreviewLayout imagePreviewLayout = (ImagePreviewLayout) LayoutInflater.from(getContext()).inflate(
            R.layout.fragment_cursor_images_preview,
            imagePreviewContainer,
            false);
        imagePreviewLayout.showSketch(cameraContext == CameraContext.MESSAGE);
        String previewTitle = cameraContext == CameraContext.MESSAGE ?
                                  getStoreFactory().conversationStore().getCurrentConversation().getName() :
                                  "";
        imagePreviewLayout.setImageAsset(imageAsset,
                                         ImagePreviewLayout.Source.CAMERA,
                                         this);
        imagePreviewLayout.setAccentColor(getControllerFactory().getAccentColorController().getAccentColor().getColor());
        imagePreviewLayout.setTitle(previewTitle);

        imagePreviewContainer.removeAllViews();
        imagePreviewContainer.addView(imagePreviewLayout);
        imagePreviewContainer.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(imagePreviewContainer,
                               View.ALPHA,
                               0,
                               1).setDuration(cameraPreviewAnimationDuration).start();
        cameraBottomControl.setVisibility(View.GONE);
    }

    private void hideImagePreviewOnAnimationEnd() {
        if (imagePreviewContainer != null &&
            cameraBottomControl != null) {
            imagePreviewContainer.setVisibility(View.GONE);
            cameraBottomControl.setVisibility(View.VISIBLE);
        }
    }

    private void showCameraFeed() {
        final Activity activity = getActivity();
        if (activity != null && LayoutSpec.isTablet(activity)) {
            ViewUtils.lockCurrentOrientation(activity, SquareOrientation.PORTRAIT_STRAIGHT);
        }
        ViewUtils.fadeInView(cameraTopControl, cameraControlAnimationDuration);
        if (cameraPreview != null) {
            cameraPreview.setVisibility(View.VISIBLE);
        }
        cameraBottomControl.enableShutterButton();
    }

    private void hideCameraFeed() {
        if (LayoutSpec.isTablet(getActivity())) {
            ViewUtils.unlockOrientation(getActivity());
        }
        ViewUtils.fadeOutView(cameraTopControl, cameraControlAnimationDuration);
        if (cameraPreview != null) {
            cameraPreview.setVisibility(View.GONE);
        }
    }

    @Override
    public void onOrientationHasChanged(SquareOrientation squareOrientation) {
        cameraTopControl.setConfigOrientation(squareOrientation);
        cameraBottomControl.setConfigOrientation(squareOrientation);
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        previewProgressBar.setTextColor(color);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        intentsManager.onActivityResult(requestCode, resultCode, data);
    }

    private void processGalleryImage(URI uri) {
        imageAsset = null;
        hideCameraFeed();
        if (cameraContext != CameraContext.SIGN_UP) {
            previewProgressBar.setVisibility(View.VISIBLE);
        }

        imageAsset = ImageAssetFactory.getImageAsset(uri);
        showPreview(imageAsset, false);
    }

    public interface Container extends CameraActionObserver {
    }
}
