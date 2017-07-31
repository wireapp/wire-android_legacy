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
package com.waz.zclient.pages.main.sharing;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.waz.api.IConversation;
import com.waz.api.ImageAsset;
import com.waz.api.ImageAssetFactory;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.drawing.DrawingController;
import com.waz.zclient.controllers.sharing.SharedContentType;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.extendedcursor.image.ImagePreviewLayout;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;
import java.util.Locale;

public class ImageSharingPreviewFragment extends BaseFragment<ImageSharingPreviewFragment.Container> implements ImagePreviewLayout.Callback  {

    public static final String TAG = ImageSharingPreviewFragment.class.getName();

    private FrameLayout imagePreviewContainer;

    public static ImageSharingPreviewFragment newInstance() {
        return new ImageSharingPreviewFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sharing, container, false);
        imagePreviewContainer = ViewUtils.getView(view, R.id.fl__preview_container);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showShareImagePreview();
    }

    private void showShareImagePreview() {
        SharedContentType sharedContentType = getControllerFactory().getSharingController().getSharedContentType();
        if (sharedContentType == null) {
            return;
        }

        String title = "";
        IConversation currentConversation = getControllerFactory().getSharingController().getDestination();
        List<URI> sharedImageUris = getControllerFactory().getSharingController().getSharedFileUris();
        URI previewImageUri = sharedImageUris.get(0);
        switch (sharedContentType) {
            case IMAGE:
                Locale currentLocale;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    //noinspection deprecation
                    currentLocale = getResources().getConfiguration().locale;
                } else {
                    currentLocale = getResources().getConfiguration().getLocales().get(0);
                }
                title = String.format(getString(R.string.sharing__image_preview__title__single),
                                      currentConversation.getName().toUpperCase(currentLocale));
                break;
        }

        ImagePreviewLayout imagePreview = (ImagePreviewLayout) LayoutInflater.from(getContext()).inflate(
            R.layout.fragment_cursor_images_preview,
            imagePreviewContainer,
            false);
        imagePreview.showSketch(false);
        ImageAsset imageAsset = ImageAssetFactory.getImageAsset(previewImageUri);
        imagePreview.setImageAsset(imageAsset,
                                   ImagePreviewLayout.Source.CAMERA,
                                   this);
        imagePreview.setAccentColor(getControllerFactory().getAccentColorController().getAccentColor().getColor());
        imagePreview.setTitle(title);
        imagePreview.hightlightTitle();
        imagePreview.setTitleIsSingleLine(false);

        imagePreviewContainer.addView(imagePreview);
    }

    @Override
    public void onCancelPreview() {
        if (isDetached()) {
            return;
        }
        getActivity().finish();
    }

    @Override
    public void onSketchOnPreviewPicture(ImageAsset imageAsset,
                                         ImagePreviewLayout.Source source,
                                         DrawingController.DrawingMethod method) {
        // No sketch supported for sharing atm
    }

    @Override
    public void onSendPictureFromPreview(ImageAsset imageAsset, ImagePreviewLayout.Source source) {
        confirmShareImages();
    }

    private void confirmShareImages() {
        SharedContentType sharedContentType = getControllerFactory().getSharingController().getSharedContentType();
        if (sharedContentType == null) {
            return;
        }

        IConversation destination = getControllerFactory().getSharingController().getDestination();
        List<URI> sharedImageUris = getControllerFactory().getSharingController().getSharedFileUris();
        switch (sharedContentType) {
            case IMAGE:
                getStoreFactory().conversationStore().sendMessage(destination, ImageAssetFactory.getImageAsset(sharedImageUris.get(0)));
                TrackingUtils.onSentPhotoMessageFromSharing(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                            destination);
                break;
        }
        getControllerFactory().getSharingController().onContentShared(getActivity(), destination);
        getActivity().finish();
    }

    public interface Container {
    }
}
