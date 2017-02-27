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
package com.waz.zclient.newreg.fragments;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.waz.api.BitmapCallback;
import com.waz.api.ImageAsset;
import com.waz.api.LoadHandle;
import com.waz.zclient.BaseScalaActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.camera.CameraActionObserver;
import com.waz.zclient.core.controllers.tracking.attributes.OutcomeAttribute;
import com.waz.zclient.core.controllers.tracking.attributes.RegistrationEventContext;
import com.waz.zclient.core.controllers.tracking.events.registration.AddedPhotoEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.conversation.AssetIntentsManager;
import com.waz.zclient.pages.main.profile.camera.CameraContext;
import com.waz.zclient.pages.main.profile.camera.CameraFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.BitmapUtils;
import com.waz.zclient.ui.utils.ColorUtils;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.ResourceUtils;
import com.waz.zclient.ui.views.ZetaButton;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.ProgressView;

public class SignUpPhotoFragment extends BaseFragment<SignUpPhotoFragment.Container> implements CameraFragment.Container,
                                                                                                CameraActionObserver,
                                                                                                AccentColorObserver,
                                                                                                OnBackPressedListener {
    public static final String TAG = SignUpPhotoFragment.class.getName();

    public static final String UNSPLASH_API_URL = "https://source.unsplash.com/800x800/?landscape";
    public static final String UNSPLASH_API_URL_LOW_RES = "https://source.unsplash.com/256x256/?landscape";

    private static final String SAVED_INSTANCE_CAMERA_REVEALED = "SAVED_INSTANCE_CAMERA_REVEALED";
    private static final String SAVED_INSTANCE_DIALOG = "SAVED_INSTANCE_DIALOG";
    private static final String SAVED_INSTANCE_IMAGE_LOADED = "SAVED_INSTANCE_IMAGE_LOADED";
    private static final String ARGUMENT_REGISTRATION_TYPE = "ARGUMENT_REGISTRATION_TYPE";

    private FrameLayout initContainer;
    private boolean cameraRevealed;
    private RegistrationType registrationType;
    private ImageView initImage;
    private ZetaButton chooseOwnButton;
    private ZetaButton keepButton;
    private AlertDialog sourceSelectionDialog;
    private LinearLayout progressContainer;
    private ProgressView progressView;
    private LoadHandle unsplashImageLoadHandle;
    private boolean isImageLoaded;

    public enum RegistrationType { Phone, Email }

    public static SignUpPhotoFragment newInstance(RegistrationType registrationType) {
        SignUpPhotoFragment newFragment = new SignUpPhotoFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARGUMENT_REGISTRATION_TYPE, registrationType);
        newFragment.setArguments(args);
        return newFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_up_photo, container, false);

        initContainer = ViewUtils.getView(view, R.id.fl__photo_container);
        progressContainer = ViewUtils.getView(view, R.id.ll__init_photo__loading_container);
        progressView = ViewUtils.getView(view, R.id.pv__init_photo__loading);
        progressContainer.setVisibility(View.VISIBLE);
        initImage = ViewUtils.getView(view, R.id.iv__init_photo);
        final ImageView vignetteOverlay = ViewUtils.getView(view, R.id.iv_background_vignette_overlay);
        vignetteOverlay.setImageBitmap(BitmapUtils.getVignetteBitmap(getResources()));

        chooseOwnButton = ViewUtils.getView(view, R.id.zb__choose_own_picture);
        chooseOwnButton.setIsFilled(true);
        chooseOwnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChooseSourceDialog();
            }
        });
        keepButton = ViewUtils.getView(view, R.id.zb__keep_picture);
        keepButton.setIsFilled(false);
        keepButton.setEnabled(false);
        keepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSelectedBitmap(getContainer().getUnsplashImageAsset(), AddedPhotoEvent.PhotoSource.UNSPLASH);
            }
        });

        if (savedInstanceState == null) {
            registrationType = (RegistrationType) getArguments().getSerializable(ARGUMENT_REGISTRATION_TYPE);
        } else {
            registrationType = (RegistrationType) savedInstanceState.getSerializable(ARGUMENT_REGISTRATION_TYPE);
            cameraRevealed = savedInstanceState.getBoolean(SAVED_INSTANCE_CAMERA_REVEALED);
            isImageLoaded = savedInstanceState.getBoolean(SAVED_INSTANCE_IMAGE_LOADED);
            if (savedInstanceState.getBoolean(SAVED_INSTANCE_DIALOG)) {
                showChooseSourceDialog();
            }
            if (cameraRevealed) {
                initContainer.setVisibility(View.GONE);
                initImage.setVisibility(View.GONE);
            }
        }

        final int darkenColor = ColorUtils.injectAlpha(ResourceUtils.getResourceFloat(getResources(), R.dimen.background_solid_black_overlay_opacity),
                                                       Color.BLACK);
        initImage.setColorFilter(darkenColor, PorterDuff.Mode.DARKEN);
        vignetteOverlay.setColorFilter(darkenColor, PorterDuff.Mode.DARKEN);

        if (!isImageLoaded) {
            initImage.setAlpha(0f);
            initImage.setVisibility(View.GONE);
        }

        final int displayWidth = ViewUtils.getOrientationDependentDisplayWidth(getActivity());
        if (unsplashImageLoadHandle != null) {
            unsplashImageLoadHandle.cancel();
            unsplashImageLoadHandle = null;
        }
        unsplashImageLoadHandle = getContainer().getUnsplashImageAsset().getSingleBitmap(displayWidth, new BitmapCallback() {
            @Override
            public void onBitmapLoaded(final Bitmap bitmap) {
                if (keepButton == null ||
                    progressContainer == null ||
                    initImage == null) {
                    return;
                }
                keepButton.setEnabled(true);
                initImage.setImageBitmap(bitmap);
                if (!isImageLoaded) {
                    ViewUtils.fadeInView(initImage);

                    progressContainer.animate()
                                     .alpha(0)
                                     .withEndAction(new Runnable() {
                                         @Override
                                         public void run() {
                                             progressContainer.setVisibility(View.GONE);
                                             progressView.stopAnimation();
                                         }
                                     })
                                     .start();
                } else {
                    initContainer.setVisibility(View.VISIBLE);
                    progressContainer.setVisibility(View.GONE);
                    progressView.stopAnimation();
                }
                isImageLoaded = true;
            }

            @Override
            public void onBitmapLoadingFailed(BitmapLoadingFailed reason) {
                if (progressContainer == null) {
                    return;
                }
                progressContainer.animate()
                                 .alpha(0)
                                 .withEndAction(new Runnable() {
                                     @Override
                                     public void run() {
                                         progressContainer.setVisibility(View.GONE);
                                         progressView.stopAnimation();
                                     }
                                 })
                                 .start();
            }
        });

        return view;
    }

    private void showChooseSourceDialog() {
        if (sourceSelectionDialog != null &&
            sourceSelectionDialog.isShowing()) {
            return;
        }
        sourceSelectionDialog = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.sign_up__take_picture_dialog__title)
            .setMessage(R.string.sign_up__take_picture_dialog__message)
            .setPositiveButton(R.string.sign_up__take_picture_dialog__camera,
                               new DialogInterface.OnClickListener() {
                                   @Override
                                   public void onClick(DialogInterface dialog, int which) {
                                       ViewUtils.fadeOutView(initContainer);
                                       ViewUtils.fadeOutView(initImage);
                                       cameraRevealed = true;
                                       launchCameraFragment(false);
                                       dialog.dismiss();
                                   }
                               })
            .setNegativeButton(R.string.sign_up__take_picture_dialog__gallery,
                               new DialogInterface.OnClickListener() {
                                   @Override
                                   public void onClick(DialogInterface dialog, int which) {
                                       ViewUtils.fadeOutView(initContainer);
                                       ViewUtils.fadeOutView(initImage);
                                       cameraRevealed = true;
                                       launchCameraFragment(true);
                                       dialog.dismiss();
                                   }
                               })
            .setNeutralButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            })
            .setCancelable(true)
            .create();
        sourceSelectionDialog.show();
    }

    @Override
    public void onStart() {
        super.onStart();

        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getCameraController().addCameraActionObserver(this);
        getControllerFactory().getVerificationController().finishVerification();

        if (KeyboardUtils.keyboardIsVisible(getView())) {
            KeyboardUtils.hideKeyboard(getActivity());
        }

        if (cameraRevealed && !isCameraFragmentAlreadyStarted()) {
            launchCameraFragment(false);
            initContainer.setVisibility(View.GONE);
            initImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        chooseOwnButton.setAccentColor(color);
        keepButton.setAccentColor(color);
        keepButton.setTextColor(getResources().getColor(R.color.text__primary_dark));
    }

    @Override
    public void onStop() {
        getControllerFactory().getCameraController().removeCameraActionObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getChildFragmentManager().findFragmentByTag(CameraFragment.TAG);
        if (fragment != null) {
            if (requestCode == AssetIntentsManager.IntentType.GALLERY.requestCode && resultCode == Activity.RESULT_OK) {
                initContainer.setVisibility(View.GONE);
                initImage.setVisibility(View.GONE);
            }
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onBackPressed() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(CameraFragment.TAG);
        if (fragment != null) {
            dismissCameraFragment();
            return true;
        } else {
            if (getActivity() == null ||
                getStoreFactory() == null ||
                getStoreFactory().isTornDown()) {
                return false;
            }
            getStoreFactory().getAppEntryStore().onBackPressed();
            return true;
        }
    }

    @Override
    public void onDestroyView() {
        if (unsplashImageLoadHandle != null) {
            unsplashImageLoadHandle.cancel();
            unsplashImageLoadHandle = null;
        }
        super.onDestroyView();
    }

    private void dismissCameraFragment() {
        Fragment fragment = getChildFragmentManager().findFragmentByTag(CameraFragment.TAG);
        if (fragment != null) {
            getChildFragmentManager().popBackStack(CameraFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            ViewUtils.fadeInView(initContainer);
            ViewUtils.fadeInView(initImage);
            cameraRevealed = false;
        }
    }

    private void launchCameraFragment(boolean showGallery) {
        getChildFragmentManager().beginTransaction()
                                 .add(R.id.fl__sign_up__camera_container, CameraFragment.newInstance(CameraContext.SIGN_UP, showGallery), CameraFragment.TAG)
                                 .addToBackStack(CameraFragment.TAG)
                                 .commit();
    }

    private boolean isCameraFragmentAlreadyStarted() {
        return getChildFragmentManager().findFragmentByTag(CameraFragment.TAG) != null;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(SAVED_INSTANCE_CAMERA_REVEALED, cameraRevealed);
        outState.putBoolean(SAVED_INSTANCE_DIALOG, sourceSelectionDialog != null && sourceSelectionDialog.isShowing());
        outState.putBoolean(SAVED_INSTANCE_IMAGE_LOADED, isImageLoaded);
        super.onSaveInstanceState(outState);
    }


    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Picture taking
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBitmapSelected(final ImageAsset imageAsset, boolean imageFromCamera, CameraContext cameraContext) {
        if (cameraContext != CameraContext.SIGN_UP) {
            return;
        }
        dismissCameraFragment();
        AddedPhotoEvent.PhotoSource photoSource = imageFromCamera ? AddedPhotoEvent.PhotoSource.CAMERA : AddedPhotoEvent.PhotoSource.GALLERY;
        handleSelectedBitmap(imageAsset, photoSource);
    }

    @Override
    public void onCameraNotAvailable() {

    }

    @Override
    public void onOpenCamera(CameraContext cameraContext) {

    }

    @Override
    public void onCloseCamera(CameraContext cameraContext) {
        getActivity().onBackPressed();
    }

    private void handleSelectedBitmap(final ImageAsset imageAsset, final AddedPhotoEvent.PhotoSource photoSource)  {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (registrationType == RegistrationType.Phone) {
                    getStoreFactory().getAppEntryStore().setPhonePicture(imageAsset);

                } else {
                    getStoreFactory().getAppEntryStore().setEmailPicture(imageAsset);
                }

                RegistrationEventContext registrationEventContext = registrationType == SignUpPhotoFragment.RegistrationType.Phone ?
                                                                    getStoreFactory().getAppEntryStore().getPhoneRegistrationContext() :
                                                                    getStoreFactory().getAppEntryStore().getEmailRegistrationContext();
                ((BaseScalaActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new AddedPhotoEvent(OutcomeAttribute.SUCCESS, "", photoSource, registrationEventContext));

            }
        }, getResources().getInteger(R.integer.signup__photo__selected_photo_display_delay));
    }

    public interface Container {
        ImageAsset getUnsplashImageAsset();
    }
}
