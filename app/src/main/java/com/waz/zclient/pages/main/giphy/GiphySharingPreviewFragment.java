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
package com.waz.zclient.pages.main.giphy;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.api.GiphyResults;
import com.waz.api.ImageAsset;
import com.waz.api.UpdateListener;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.core.stores.network.NetworkStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.pages.main.profile.views.ConfirmationMenu;
import com.waz.zclient.pages.main.profile.views.ConfirmationMenuListener;
import com.waz.zclient.ui.theme.ThemeUtils;
import com.waz.zclient.ui.utils.KeyboardUtils;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import com.waz.zclient.views.images.ImageAssetView;

public class GiphySharingPreviewFragment extends BaseFragment<GiphySharingPreviewFragment.Container> implements AccentColorObserver,
                                                                                                                ImageAssetView.BitmapLoadedCallback,
                                                                                                                NetworkStoreObserver,
                                                                                                                GiphyGridViewAdapter.ScrollGifCallback,
                                                                                                                TextWatcher,
                                                                                                                View.OnClickListener,
                                                                                                                OnBackPressedListener {

    public static final String TAG = GiphySharingPreviewFragment.class.getSimpleName();
    public static final String ARG_SEARCH_TERM = "SEARCH_TERM";
    public static final int GIPHY_SEARCH_DELAY_MIN_SEC = 800;
    private String searchTerm;
    private ImageAssetView previewImageAssetView;
    private TextView giphyTitle;
    private ConfirmationMenu confirmationMenu;
    private Toolbar toolbar;
    private EditText giphySearchEditText;
    private ImageAsset foundImage;
    private LoadingIndicatorView loadingIndicator;
    private TextView errorView;
    private RecyclerView recyclerView;
    private GiphyGridViewAdapter giphyGridViewAdapter;
    private GiphyResults giphyResults;
    private Handler giphySearchHandler;

    private final UpdateListener giphyResultUpdateListener = new UpdateListener() {
        @Override
        public void updated() {
            if (giphyGridViewAdapter == null) {
                return;
            }
            if (giphyResults == null ||
                giphyResults.size() == 0) {
                showError();
                return;
            }
            errorView.setVisibility(View.GONE);
            giphyGridViewAdapter.setGiphyResults(giphyResults);
        }
    };

    private ConfirmationMenuListener confirmationMenuListener = new ConfirmationMenuListener() {
        @Override
        public void confirm() {
            sendGif();
        }

        @Override
        public void cancel() {
           showGrid();
        }
    };

    private Runnable giphySearchRunnable = new Runnable() {
        @Override
        public void run() {
            updateGiphyResults();
            updateGiphyTitle();
        }
    };

    public static GiphySharingPreviewFragment newInstance() {
        GiphySharingPreviewFragment fragment = new GiphySharingPreviewFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public static GiphySharingPreviewFragment newInstance(String searchTerm) {
        GiphySharingPreviewFragment fragment = new GiphySharingPreviewFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SEARCH_TERM, searchTerm);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            args = savedInstanceState;
        }
        searchTerm = args.getString(ARG_SEARCH_TERM, null);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_giphy_preview, container, false);
        loadingIndicator = ViewUtils.getView(view, R.id.liv__giphy_preview__loading);
        toolbar = ViewUtils.getView(view, R.id.t__giphy__toolbar);
        giphySearchEditText = ViewUtils.getView(view, R.id.cet__giphy_preview__search);
        previewImageAssetView = ViewUtils.getView(view, R.id.iv__giphy_preview__preview);
        errorView = ViewUtils.getView(view, R.id.ttv__giphy_preview__error);
        giphyTitle = ViewUtils.getView(view, R.id.ttv__giphy_preview__title);
        recyclerView = ViewUtils.getView(view, R.id.rv__giphy_image_preview);
        View closeButton = ViewUtils.getView(view, R.id.gtv__giphy_preview__close_button);
        confirmationMenu = ViewUtils.getView(view, R.id.cm__giphy_preview__confirmation_menu);

        giphySearchHandler = new Handler();

        loadingIndicator.setType(LoadingIndicatorView.INFINITE_LOADING_BAR);

        giphySearchEditText.addTextChangedListener(this);
        giphySearchEditText.setText(searchTerm);

        previewImageAssetView.setShowPreview(true);

        giphyGridViewAdapter = new GiphyGridViewAdapter(getActivity());
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        recyclerView.setAdapter(giphyGridViewAdapter);

        closeButton.setOnClickListener(this);

        confirmationMenu.setConfirmationMenuListener(confirmationMenuListener);
        confirmationMenu.setConfirm(getString(R.string.sharing__image_preview__confirm_action));
        confirmationMenu.setCancel(getString(R.string.confirmation_menu__cancel));
        confirmationMenu.setWireTheme(getControllerFactory().getThemeController().getThemeDependentOptionsTheme());

        errorView.setVisibility(View.GONE);
        previewImageAssetView.setVisibility(View.GONE);
        confirmationMenu.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        giphyTitle.setVisibility(View.GONE);

        if (ThemeUtils.isDarkTheme(getContext())) {
            toolbar.setNavigationIcon(R.drawable.ic_action_search_light);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_action_search_dark);
        }

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (giphySearchEditText.getVisibility() == View.VISIBLE) {
                    return;
                }
                showGrid();
            }
        });

        updateGiphyResults();
        updateGiphyTitle();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        KeyboardUtils.hideKeyboard(getActivity());
        giphyGridViewAdapter.setScrollGifCallback(this);
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getStoreFactory().getInAppNotificationStore().setUserSendingPicture(true);
        getStoreFactory().getNetworkStore().addNetworkStoreObserver(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (giphySearchEditText.getVisibility() == View.VISIBLE) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    giphySearchEditText.requestFocus();
                    giphySearchEditText.setSelection(giphySearchEditText.getText().length());
                    if (TextUtils.isEmpty(searchTerm)) {
                        KeyboardUtils.showKeyboard(getActivity());
                    }
                }
            }, getResources().getInteger(R.integer.people_picker__keyboard__show_delay));
        }
    }

    @Override
    public void onStop() {
        getStoreFactory().getInAppNotificationStore().setUserSendingPicture(false);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        getStoreFactory().getNetworkStore().removeNetworkStoreObserver(this);

        giphyGridViewAdapter.setScrollGifCallback(null);
        if (giphyResults != null) {
            giphyResults.removeUpdateListener(giphyResultUpdateListener);
        }
        KeyboardUtils.hideKeyboard(getActivity());
        super.onStop();
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        confirmationMenu.setAccentColor(color);
        if (!getControllerFactory().getThemeController().isDarkTheme()) {
            confirmationMenu.setCancelColor(color, color);
            confirmationMenu.setConfirmColor(getResources().getColor(R.color.white), color);
        }
        loadingIndicator.setColor(color);
    }

    @Override
    public void onDestroyView() {
        previewImageAssetView.clearImage();
        previewImageAssetView = null;
        giphyTitle = null;
        confirmationMenu = null;
        foundImage = null;
        recyclerView = null;
        giphyGridViewAdapter = null;
        giphyResults = null;
        giphySearchHandler.removeCallbacks(giphySearchRunnable);
        super.onDestroyView();
    }

    @Override
    public void onConnectivityChange(boolean hasInternet, boolean isServerError) {
        if (!hasInternet) {
            onLossOfNetworkConnection();
            return;
        }
        onResumedNetworkConnection();
    }

    @Override
    public void onNoInternetConnection(boolean isServerError) {

    }

    @Override
    public void onBitmapLoadFinished(boolean bitmapLoaded) {
        confirmationMenu.setConfirmEnabled(true);
        loadingIndicator.hide();
        previewImageAssetView.setBitmapLoadedCallback(null);
    }

    @Override
    public void setSelectedGifFromGridView(ImageAsset gifAsset) {
        foundImage = gifAsset;
        confirmationMenu.setConfirmEnabled(false);
        previewImageAssetView.setBitmapLoadedCallback(this);
        previewImageAssetView.setImageAsset(gifAsset);
        loadingIndicator.show();
        KeyboardUtils.closeKeyboardIfShown(getActivity());
        if (ThemeUtils.isDarkTheme(getContext())) {
            toolbar.setNavigationIcon(R.drawable.action_back_light);
        } else {
            toolbar.setNavigationIcon(R.drawable.action_back_dark);
        }
        ViewUtils.fadeInView(previewImageAssetView);
        ViewUtils.fadeInView(confirmationMenu);
        ViewUtils.fadeInView(giphyTitle);
        ViewUtils.fadeOutView(recyclerView);
        ViewUtils.fadeOutView(giphySearchEditText);
        ViewUtils.fadeOutView(errorView);
    }

    @Override
    public boolean onBackPressed() {
        if (previewImageAssetView.getVisibility() == View.VISIBLE) {
            showGrid();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.gtv__giphy_preview__close_button:
                getControllerFactory().getGiphyController().cancel();
                break;
        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        searchTerm = giphySearchEditText.getText().toString();
        giphySearchHandler.removeCallbacks(giphySearchRunnable);
        giphySearchHandler.postDelayed(giphySearchRunnable, GIPHY_SEARCH_DELAY_MIN_SEC);
    }

    @Override
    public void afterTextChanged(Editable editable) {

    }

    private void updateGiphyResults() {
        errorView.setVisibility(View.GONE);
        previewImageAssetView.clearImage();
        loadingIndicator.show();
        if (TextUtils.isEmpty(searchTerm) || searchTerm == null) {
            giphyResults = getStoreFactory().getZMessagingApiStore()
                                            .getApi()
                                            .getGiphy()
                                            .trending();

        } else {
            giphyResults = getStoreFactory().getZMessagingApiStore()
                                            .getApi()
                                            .getGiphy()
                                            .search(searchTerm);
        }
        giphyResults.whenReady(new Runnable() {
            @Override
            public void run() {
                loadingIndicator.hide();
                giphyResultUpdateListener.updated();
            }
        });
        giphyResults.addUpdateListener(giphyResultUpdateListener);
        giphyGridViewAdapter.setGiphyResults(giphyResults);
    }

    private void updateGiphyTitle() {
        String gifTitle;
        if (TextUtils.isEmpty(searchTerm) || searchTerm == null) {
            gifTitle = "";
        } else {
            gifTitle = getString(R.string.giphy_preview__title_search, searchTerm);
        }
        giphyTitle.setText(gifTitle);
    }

    private void showError() {
        loadingIndicator.hide();
        previewImageAssetView.clearImage();
        errorView.setText(R.string.giphy_preview__error);
        TextViewUtils.mediumText(errorView);
        errorView.setVisibility(View.VISIBLE);
    }

    private void showGrid() {
        foundImage = null;
        previewImageAssetView.clearImage();

        if (ThemeUtils.isDarkTheme(getContext())) {
            toolbar.setNavigationIcon(R.drawable.ic_action_search_light);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_action_search_dark);
        }
        ViewUtils.fadeOutView(previewImageAssetView);
        ViewUtils.fadeOutView(confirmationMenu);
        ViewUtils.fadeOutView(giphyTitle);
        ViewUtils.fadeInView(recyclerView);
        ViewUtils.fadeInView(giphySearchEditText);
        giphySearchEditText.requestFocus();
    }

    private void sendGif() {
        TrackingUtils.onSentGifMessage(getControllerFactory().getTrackingController(),
                                       getStoreFactory().getConversationStore().getCurrentConversation());

        if (TextUtils.isEmpty(searchTerm) || searchTerm == null) {
            getStoreFactory().getConversationStore().sendMessage(getString(R.string.giphy_preview__message_via_random_trending));
        } else {
            getStoreFactory().getConversationStore().sendMessage(getString(R.string.giphy_preview__message_via_search,
                                                                           searchTerm));
        }
        getStoreFactory().getNetworkStore().doIfHasInternetOrNotifyUser(null);
        getStoreFactory().getConversationStore().sendMessage(foundImage);
        getControllerFactory().getGiphyController().close();
    }

    private void onLossOfNetworkConnection() {
        confirmationMenu.setConfirmationMenuListener(null);
        previewImageAssetView.setClickable(false);
    }

    private void onResumedNetworkConnection() {
        confirmationMenu.setConfirmationMenuListener(confirmationMenuListener);
        previewImageAssetView.setClickable(true);
    }

    public interface Container { }
}
