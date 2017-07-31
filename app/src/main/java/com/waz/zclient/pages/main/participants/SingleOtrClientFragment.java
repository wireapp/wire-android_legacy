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
package com.waz.zclient.pages.main.participants;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import com.waz.api.OtrClient;
import com.waz.api.Subscriber;
import com.waz.api.Subscription;
import com.waz.api.UiSignal;
import com.waz.api.UpdateListener;
import com.waz.api.User;
import com.waz.api.Verification;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.controllers.ThemeController;
import com.waz.zclient.preferences.PreferencesActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.tracking.events.otr.UnverifiedOtherOtrClientEvent;
import com.waz.zclient.controllers.tracking.events.otr.VerifiedOtherOtrClientEvent;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.utils.TextViewUtils;
import com.waz.zclient.ui.views.e2ee.OtrSwitch;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.ViewUtils;
import com.waz.zclient.utils.OtrUtils;
import com.waz.zclient.views.LoadingIndicatorView;
import com.waz.zclient.views.e2ee.FingerprintView;

import java.util.Locale;

public class SingleOtrClientFragment extends BaseFragment<SingleOtrClientFragment.Container> implements View.OnClickListener,
                                                                                                        OnBackPressedListener,
                                                                                                        UpdateListener,
                                                                                                        AccentColorObserver,
                                                                                                        CompoundButton.OnCheckedChangeListener {

    public static final String TAG = SingleOtrClientFragment.class.getName();
    private static final String ARG_OTR_CLIENT = "ARG_OTR_CLIENT";
    private static final String ARG_USER = "ARG_USER";

    private TextView backButton;
    private TextView closeButton;
    private TextView myFingerprintButton;
    private TextView resetSessionButton;
    private TextView howToLinkButton;
    private OtrSwitch verifySwitch;
    private TextView myDevicesButton;

    private TextView descriptionTextview;
    private TextView typeTextView;
    private FingerprintView idTextView;
    private FingerprintView fingerprintView;

    private OtrClient otrClient;
    private UiSignal<OtrClient> otrClientUiSignal;
    private Subscription otrClientSubscription;
    private User user;
    private boolean isSelf;

    public static SingleOtrClientFragment newInstance() {
        return new SingleOtrClientFragment();
    }

    public static SingleOtrClientFragment newInstance(OtrClient otrClient, User user) {
        SingleOtrClientFragment fragment = new SingleOtrClientFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_OTR_CLIENT, otrClient);
        args.putParcelable(ARG_USER, user);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_single_otr_client, viewGroup, false);

        backButton = ViewUtils.getView(view, R.id.gtv__single_otr_client__back);
        closeButton = ViewUtils.getView(view, R.id.gtv__single_otr_client__close);

        myFingerprintButton = ViewUtils.getView(view, R.id.ttv__single_otr_client__my_fingerprint);

        resetSessionButton = ViewUtils.getView(view, R.id.ttv__single_otr_client__reset);

        howToLinkButton = ViewUtils.getView(view, R.id.ttv__single_otr_client__how_to_link);

        verifySwitch = ViewUtils.getView(view, R.id.os__single_otr_client__verify);
        myDevicesButton = ViewUtils.getView(view, R.id.ttv__single_otr_client__my_devices);

        descriptionTextview = ViewUtils.getView(view, R.id.ttv__single_otr_client__description);

        typeTextView = ViewUtils.getView(view, R.id.ttv__single_otr_client__type);

        idTextView = ViewUtils.getView(view, R.id.ttv__single_otr_client__id);
        if (otrClient != null) {
            idTextView.setOtrClient(otrClient, FingerprintView.DisplayType.DEVICE_ID);
        }

        fingerprintView = ViewUtils.getView(view, R.id.ttv__single_otr_client__fingerprint);
        if (otrClient != null) {
            fingerprintView.setOtrClient(otrClient, FingerprintView.DisplayType.FINGERPRINT);
        }

        if (LayoutSpec.isTablet(getActivity())) {
            view.setBackgroundResource(R.drawable.rounded_corner_background_light);
        }

        view.setOnClickListener(this);
        updated();
        return view;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() == null || getArguments().getParcelable(ARG_USER) == null) {
            isSelf = true;
        } else {
            otrClient = getArguments().getParcelable(ARG_OTR_CLIENT);
            user = getArguments().getParcelable(ARG_USER);
            isSelf = false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        howToLinkButton.setText(
            TextViewUtils.getHighlightText(getActivity(),
                                           getActivity().getString(R.string.otr__participant__single_device__how_to_link),
                                           getControllerFactory().getAccentColorController().getColor(),
                                           false));
        if (isSelf) {
            user = getStoreFactory().profileStore().getSelfUser();
            otrClientUiSignal = getStoreFactory().zMessagingApiStore().getApi().getSelf().getOtrClient();
            subscribeToOtrClient();
            resetSessionButton.setVisibility(View.GONE);
            howToLinkButton.setVisibility(View.GONE);
        } else {
            otrClient.addUpdateListener(this);
            resetSessionButton.setVisibility(View.VISIBLE);
            howToLinkButton.setVisibility(View.VISIBLE);
        }
        user.addUpdateListener(this);
    }



    @Override
    public void onResume() {
        super.onResume();
        backButton.setOnClickListener(this);
        closeButton.setOnClickListener(this);
        myFingerprintButton.setOnClickListener(this);
        resetSessionButton.setOnClickListener(this);
        verifySwitch.setOnCheckedListener(this);
        myDevicesButton.setOnClickListener(this);
        fingerprintView.setOnClickListener(this);
        howToLinkButton.setOnClickListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        backButton.setOnClickListener(null);
        closeButton.setOnClickListener(null);
        myFingerprintButton.setOnClickListener(null);
        resetSessionButton.setOnClickListener(null);
        verifySwitch.setOnCheckedListener(null);
        myDevicesButton.setOnClickListener(null);
        fingerprintView.setOnClickListener(null);
        howToLinkButton.setOnClickListener(null);
    }

    @Override
    public void onStop() {
        if (otrClient != null) {
            otrClient.removeUpdateListener(this);
        }
        if (user != null) {
            user.removeUpdateListener(this);
        }
        if (otrClientSubscription != null) {
            otrClientSubscription.cancel();
            otrClientSubscription = null;
        }
        if (otrClientUiSignal != null) {
            otrClientUiSignal = null;
        }
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        backButton = null;
        closeButton = null;
        myFingerprintButton = null;
        resetSessionButton = null;
        verifySwitch = null;
        myDevicesButton = null;
        descriptionTextview = null;
        typeTextView = null;
        idTextView = null;
        fingerprintView = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        otrClient = null;
        user = null;
    }

    @Override
    public void onClick(View view) {
        if (view == null) {
            return;
        }
        switch (view.getId()) {
            case R.id.gtv__single_otr_client__back:
            case R.id.gtv__single_otr_client__close:
                getControllerFactory().getConversationScreenController().hideOtrClient();
                break;
            case R.id.ttv__single_otr_client__my_fingerprint:
                getControllerFactory().getConversationScreenController().showCurrentOtrClient();
                break;
            case R.id.ttv__single_otr_client__reset:
                resetSession();
                break;
            case R.id.ttv__single_otr_client__fingerprint:
                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(getString(R.string.pref_dev_avs_last_call_session_id_title),
                                                      fingerprintView.getText().toString());
                clipboard.setPrimaryClip(clip);
                break;
            case R.id.ttv__single_otr_client__my_devices:
                startActivity(PreferencesActivity.getOtrDevicesPreferencesIntent(getActivity()));
                break;
            case R.id.ttv__single_otr_client__how_to_link:
                getContainer().onOpenUrl(getString(R.string.url_otr_learn_how));
                break;
        }
    }

    private void subscribeToOtrClient() {
        otrClientSubscription = otrClientUiSignal.subscribe(new Subscriber<OtrClient>() {
            @Override
            public void next(OtrClient otrClient) {
                SingleOtrClientFragment.this.otrClient = otrClient;
                idTextView.setOtrClient(otrClient, FingerprintView.DisplayType.DEVICE_ID);
                fingerprintView.setOtrClient(otrClient, FingerprintView.DisplayType.FINGERPRINT);
                updated();
            }
        });
    }

    private void resetSession() {
        getContainer().getLoadingViewIndicator().show(LoadingIndicatorView.SPINNER_WITH_DIMMED_BACKGROUND,
            ((BaseActivity) getActivity()).injectJava(ThemeController.class).isDarkTheme());
        resetSessionButton.setEnabled(false);
        otrClient.resetSession(new OtrClient.ResetCallback() {
            @Override
            public void onSessionReset(OtrClient otrClient) {
                resetSessionButton.setEnabled(true);
                getContainer().getLoadingViewIndicator().hide();
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.empty_string,
                                          R.string.otr__reset_session__message_ok,
                                          R.string.otr__reset_session__button_ok, null, true);
            }

            @Override
            public void onSessionResetFailed(int i, String s, String s1) {
                resetSessionButton.setEnabled(true);
                getContainer().getLoadingViewIndicator().hide();
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.empty_string,
                                          R.string.otr__reset_session__message_fail,
                                          R.string.otr__reset_session__button_ok,
                                          R.string.otr__reset_session__button_fail, null, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            resetSession();
                        }
                    });
            }
        });
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        otrClient.setVerified(isChecked);
        trackVerified(isChecked);
    }

    // For Otr Client and User updates
    @Override
    public void updated() {
        updateViewVisibility();
        if (isSelf) {
            descriptionTextview.setText(getString(R.string.otr__participant__my_device__description));
        } else {
            descriptionTextview.setText(getString(R.string.otr__participant__single_device__description,
                                                  user.getDisplayName()));
            verifySwitch.setChecked(otrClient != null && otrClient.getVerified() == Verification.VERIFIED);
        }
        typeTextView.setText(OtrUtils.getDeviceClassName(getActivity(), otrClient).toUpperCase(Locale.getDefault()));
    }

    private void updateViewVisibility() {
        if (isSelf) {
            verifySwitch.setVisibility(View.GONE);
            backButton.setVisibility(View.GONE);
            myFingerprintButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.VISIBLE);
            myDevicesButton.setVisibility(View.VISIBLE);
            howToLinkButton.setVisibility(View.GONE);
        } else {
            verifySwitch.setVisibility(View.VISIBLE);
            backButton.setVisibility(View.VISIBLE);
            myFingerprintButton.setVisibility(View.VISIBLE);
            closeButton.setVisibility(View.GONE);
            myDevicesButton.setVisibility(View.GONE);
            howToLinkButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        myFingerprintButton.setTextColor(color);
        resetSessionButton.setTextColor(color);
        myDevicesButton.setTextColor(color);
        howToLinkButton.setText(
            TextViewUtils.getHighlightText(getActivity(),
                                           getActivity().getString(R.string.otr__participant__single_device__how_to_link),
                                           color,
                                           false));
    }

    private void trackVerified(boolean verified) {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }
        if (verified) {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new VerifiedOtherOtrClientEvent());
        } else {
            ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new UnverifiedOtherOtrClientEvent());
        }
    }

    public interface Container {
        LoadingIndicatorView getLoadingViewIndicator();
        void onOpenUrl(String url);
    }
}
