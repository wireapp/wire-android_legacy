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
package com.waz.zclient.pages.main.profile.preferences.dialogs;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.api.OtrClient;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.controllers.tracking.events.otr.RemovedOwnOtrClientEvent;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.utils.ViewUtils;
import timber.log.Timber;

public class RemoveDevicePreferenceDialogFragment extends BaseDialogFragment<RemoveDevicePreferenceDialogFragment.Container> {

    public static final String TAG = RemoveDevicePreferenceDialogFragment.class.getName();
    private static final String ARG_NAME = "ARG_NAME";
    private EditText passwordEditText;
    private TextInputLayout textInputLayout;

    public static RemoveDevicePreferenceDialogFragment newInstance(String deviceName) {
        RemoveDevicePreferenceDialogFragment f = new RemoveDevicePreferenceDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, deviceName);
        f.setArguments(args);
        return f;
    }

    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
        View dialog = layoutInflater.inflate(R.layout.remove_otr_device_dialog, null);
        textInputLayout = ViewUtils.getView(dialog, R.id.til__remove_otr_device);
        passwordEditText = ViewUtils.getView(dialog, R.id.acet__remove_otr__password);
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    textInputLayout.setError(null);
                    deleteOtrClient(getContainer().getOtrClient(), passwordEditText.getText().toString());
                    return true;
                } else {
                    return false;
                }
            }
        });
        return new AlertDialog.Builder(getActivity())
            .setView(dialog)
            .setTitle(getString(R.string.otr__remove_device__title, getArguments().getString(ARG_NAME, getString(R.string.otr__remove_device__default))))
            .setMessage(R.string.otr__remove_device__message)
            .setPositiveButton(R.string.otr__remove_device__button_delete, null)
            .setNegativeButton(R.string.otr__remove_device__button_cancel, null)
            .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        final Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textInputLayout.setError(null);
                deleteOtrClient(getContainer().getOtrClient(), passwordEditText.getText().toString());
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    @Override
    public void onDestroy() {
        passwordEditText = null;
        textInputLayout = null;
        super.onDestroy();
    }

    private void deleteOtrClient(OtrClient otrClient, final String password) {
        if (otrClient == null) {
            return;
        }
        otrClient.delete(password, new OtrClient.DeleteCallback() {
            @Override
            public void onClientDeleted(OtrClient otrClient) {
                if (getActivity() == null ||
                    getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                dismiss();
                getControllerFactory().getPasswordController().setPassword(password);
                ((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class).tagEvent(new RemovedOwnOtrClientEvent());
                getContainer().onCurrentDeviceDeleted();
            }

            @Override
            public void onDeleteFailed(String error) {
                Timber.e("Remove client failed: %s", error);
                if (getActivity() == null ||
                    getControllerFactory() == null ||
                    getControllerFactory().isTornDown()) {
                    return;
                }
                textInputLayout.setError(getString(R.string.otr__remove_device__error));
            }
        });
    }

    public interface Container {
        OtrClient getOtrClient();
        void onCurrentDeviceDeleted();
    }
}
