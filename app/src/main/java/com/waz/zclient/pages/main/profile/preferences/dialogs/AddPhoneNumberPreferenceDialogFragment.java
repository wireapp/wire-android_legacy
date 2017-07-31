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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.graphics.drawable.DrawableWrapper;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatDrawableManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.api.CredentialsUpdateListener;
import com.waz.zclient.R;
import com.waz.zclient.core.stores.appentry.AppEntryError;
import com.waz.zclient.newreg.fragments.country.Country;
import com.waz.zclient.newreg.fragments.country.CountryController;
import com.waz.zclient.pages.BaseDialogFragment;
import com.waz.zclient.ui.utils.DrawableUtils;
import com.waz.zclient.ui.utils.MathUtils;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.ViewUtils;

public class AddPhoneNumberPreferenceDialogFragment extends BaseDialogFragment<AddPhoneNumberPreferenceDialogFragment.Container> implements CountryController.Observer {

    public static final String TAG = AddPhoneNumberPreferenceDialogFragment.class.getSimpleName();
    private static final String ARG_PHONE = "ARG_PHONE";

    private static final String[] GET_PHONE_NUMBER_PERMISSIONS = new String[] {Manifest.permission.READ_PHONE_STATE};

    // values from TextInputLayout to act the same
    private static final long ANIMATION_DURATION = 200L;
    private static final Interpolator FAST_OUT_LINEAR_IN_INTERPOLATOR = new FastOutLinearInInterpolator();
    private static final Interpolator LINEAR_OUT_SLOW_IN_INTERPOLATOR = new LinearOutSlowInInterpolator();

    private boolean isEditMode;
    private View containerView;
    private EditText phoneEditText;
    private EditText countryEditText;
    private TextView errorView;
    private CountryController countryController;

    public static Fragment newInstance() {
        return newInstance(null);
    }

    public static Fragment newInstance(String phoneNumber) {
        final AddPhoneNumberPreferenceDialogFragment fragment = new AddPhoneNumberPreferenceDialogFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_PHONE, phoneNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isEditMode = !TextUtils.isEmpty(getArguments().getString(ARG_PHONE));
    }

    @SuppressLint("InflateParams")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final View view = inflater.inflate(R.layout.preference_dialog_add_phone, null);

        containerView = ViewUtils.getView(view, R.id.ll__preferences__container);
        errorView = ViewUtils.getView(view, R.id.tv__preferences__error);
        errorView.setVisibility(View.GONE);
        countryController = new CountryController(getActivity());

        countryEditText = ViewUtils.getView(view, R.id.acet__preferences__country);
        phoneEditText = ViewUtils.getView(view, R.id.acet__preferences__phone);
        phoneEditText.requestFocus();
        phoneEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    handleInput();
                    return true;
                } else {
                    return false;
                }
            }
        });

        final String phoneNumber = getArguments().getString(ARG_PHONE, "");
        final String number = countryController.getPhoneNumberWithoutCountryCode(phoneNumber);
        final String countryCode = phoneNumber.substring(0, phoneNumber.length() - number.length()).replace("+", "");
        phoneEditText.setText(number);
        phoneEditText.setSelection(number.length());
        countryEditText.setText(String.format("+%s", countryCode));

        if (isEditMode) {
            phoneEditText.requestFocus();
        } else {
            countryEditText.requestFocus();
        }

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity())
            .setTitle(isEditMode ? R.string.pref__account_action__dialog__edit_phone__title
                                 : R.string.pref__account_action__dialog__add_phone__title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);
        if (isEditMode && getStoreFactory() != null &&
            !StringUtils.isBlank(getStoreFactory().profileStore().getMyEmail())) {
            alertDialogBuilder.setNeutralButton(R.string.pref_account_delete, null);
        }
        final AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        return alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!isEditMode && PermissionUtils.hasSelfPermissions(getActivity(), GET_PHONE_NUMBER_PERMISSIONS)) {
            setSimPhoneNumber();
        }
        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog == null) {
            return;
        }
        final Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (containerView == null) {
                    dismiss();
                    return;
                }
                handleInput();
            }
        });

        final Button clearButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
        if (clearButton != null) {
            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearPhoneNumber();
                }
            });
        }
        countryController.addObserver(this);
    }

    private void setSimPhoneNumber() {
        if (containerView == null) {
            return;
        }
        final String abbreviation = getControllerFactory().getDeviceUserController().getPhoneCountryISO();
        final String countryCode = new CountryController(getActivity()).getCodeForAbbreviation(abbreviation);
        if (countryCode == null) {
            return;
        }
        final String rawPhoneNumber = getControllerFactory().getDeviceUserController().getPhoneNumber(countryCode);
        phoneEditText.setText(rawPhoneNumber);
        phoneEditText.setSelection(rawPhoneNumber.length());
        countryEditText.setText(String.format("+%s", countryCode.replace("+", "")));
    }

    @Override
    public void onStop() {
        countryController.removeObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        containerView = null;
        countryEditText = null;
        phoneEditText = null;
        errorView = null;
        super.onDestroyView();
    }

    private void clearPhoneNumber() {
        if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
            return;
        }
        if (!getStoreFactory().networkStore().hasInternetConnection()) {
            showError(getString(R.string.pref__account_action__dialog__delete_phone__no_internet_error));
            return;
        }
        String number = getStoreFactory().profileStore().getMyPhoneNumber();
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__title),
                                  getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__message, number),
                                  getString(android.R.string.ok),
                                  getString(android.R.string.cancel),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
                                              return;
                                          }
                                          getStoreFactory().profileStore().deleteMyPhoneNumber(new CredentialsUpdateListener() {
                                              @Override
                                              public void onUpdated() {
                                                  if (getContainer() == null) {
                                                      return;
                                                  }
                                                  getContainer().onPhoneNumberCleared();
                                              }
                                              @Override
                                              public void onUpdateFailed(int code, String message, String label) {
                                                  if (getContainer() == null) {
                                                      return;
                                                  }
                                                  showError(getString(R.string.pref__account_action__dialog__delete_phone__error));
                                              }
                                          });
                                      }
                                  },
                                  null);
    }

    private void handleInput() {
        if (containerView == null) {
            dismiss();
            return;
        }
        final String countryCode = countryEditText.getText().toString().trim();
        if (TextUtils.isEmpty(countryCode) || !countryCode.matches("\\+([0-9])+")) {
            showError(getString(R.string.pref__account_action__dialog__add_phone__error__country));
            return;
        }
        final String rawNumber = phoneEditText.getText().toString().trim();
        if (TextUtils.isEmpty(rawNumber)) {
            showError(getString(R.string.pref__account_action__dialog__add_phone__error__number));
            return;
        }
        final String number = String.format("%s%s", countryCode, rawNumber);
        if (number.equalsIgnoreCase(getStoreFactory().profileStore().getMyPhoneNumber())) {
            dismiss();
            return;
        }
        showError(null);
        ViewUtils.showAlertDialog(getActivity(),
                                  getString(R.string.pref__account_action__dialog__add_phone__confirm__title),
                                  getString(R.string.pref__account_action__dialog__add_phone__confirm__message, number),
                                  getString(android.R.string.ok),
                                  getString(android.R.string.cancel),
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          if (getStoreFactory() == null) {
                                              return;
                                          }
                                          getStoreFactory().profileStore()
                                                           .setMyPhoneNumber(number,
                                                                             new CredentialsUpdateListener() {
                                                                                 @Override
                                                                                 public void onUpdated() {
                                                                                     if (getContainer() == null) {
                                                                                         return;
                                                                                     }
                                                                                     getContainer().onVerifyPhone(number);
                                                                                 }

                                                                                 @Override
                                                                                 public void onUpdateFailed(int errorCode,
                                                                                                            String message,
                                                                                                            String label) {
                                                                                     if (containerView == null) {
                                                                                         return;
                                                                                     }
                                                                                     if (AppEntryError.PHONE_EXISTS.correspondsTo(errorCode, label)) {
                                                                                         showError(getString(AppEntryError.PHONE_EXISTS.headerResource));
                                                                                     } else {
                                                                                         showError(getString(AppEntryError.PHONE_REGISTER_GENERIC_ERROR.headerResource));
                                                                                     }
                                                                                 }
                                                                             });
                                      }
                                  },
                                  null);
    }

    // from TextInputLayout
    private void showError(final String error) {
        if (TextUtils.equals(errorView.getText(), error)) {
            return;
        }

        final boolean animate = ViewCompat.isLaidOut(containerView);
        final boolean errorShown = !TextUtils.isEmpty(error);

        ViewCompat.animate(errorView).cancel();
        if (errorShown) {
            errorView.setText(error);
            errorView.setVisibility(View.VISIBLE);

            if (animate) {
                if (MathUtils.floatEqual(ViewCompat.getAlpha(errorView), 1f)) {
                    ViewCompat.setAlpha(errorView, 0f);
                }
                ViewCompat.animate(errorView)
                          .alpha(1f)
                          .setDuration(ANIMATION_DURATION)
                          .setInterpolator(LINEAR_OUT_SLOW_IN_INTERPOLATOR)
                          .setListener(new ViewPropertyAnimatorListenerAdapter() {
                              @Override
                              public void onAnimationStart(View view) {
                                  view.setVisibility(View.VISIBLE);
                              }
                          }).start();
            }
        } else {
            if (errorView.getVisibility() == View.VISIBLE) {
                if (animate) {
                    ViewCompat.animate(errorView)
                              .alpha(0f)
                              .setDuration(ANIMATION_DURATION)
                              .setInterpolator(FAST_OUT_LINEAR_IN_INTERPOLATOR)
                              .setListener(new ViewPropertyAnimatorListenerAdapter() {
                                  @Override
                                  public void onAnimationEnd(View view) {
                                      errorView.setText(error);
                                      view.setVisibility(View.INVISIBLE);

                                      updateEditTextBackground(countryEditText);
                                      updateEditTextBackground(phoneEditText);
                                  }
                              }).start();
                } else {
                    errorView.setVisibility(View.INVISIBLE);
                }
            }
        }

        updateEditTextBackground(countryEditText);
        updateEditTextBackground(phoneEditText);
    }

    // from TextInputLayout
    private void updateEditTextBackground(EditText editText) {
        ensureBackgroundDrawableStateWorkaround(editText);

        Drawable editTextBackground = editText.getBackground();
        if (editTextBackground == null) {
            return;
        }

        if (android.support.v7.widget.DrawableUtils.canSafelyMutateDrawable(editTextBackground)) {
            editTextBackground = editTextBackground.mutate();
        }

        if (errorView != null && errorView.getVisibility() == View.VISIBLE) {
            // Set a color filter of the error color
            editTextBackground.setColorFilter(
                AppCompatDrawableManager.getPorterDuffColorFilter(
                    errorView.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
        } else {
            // Else reset the color filter and refresh the drawable state so that the
            // normal tint is used
            clearColorFilter(editTextBackground);
            editText.refreshDrawableState();
        }
    }

    // from TextInputLayout
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static void clearColorFilter(@NonNull Drawable drawable) {
        drawable.clearColorFilter();

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
            // API 21 + 22 have an issue where clearing a color filter on a DrawableContainer
            // will not propagate to all of its children. To workaround this we unwrap the drawable
            // to find any DrawableContainers, and then unwrap those to clear the filter on its
            // children manually
            if (drawable instanceof InsetDrawable) {
                clearColorFilter(((InsetDrawable) drawable).getDrawable());
            } else if (drawable instanceof DrawableWrapper) {
                clearColorFilter(((DrawableWrapper) drawable).getWrappedDrawable());
            } else if (drawable instanceof DrawableContainer) {
                final DrawableContainer container = (DrawableContainer) drawable;
                final DrawableContainer.DrawableContainerState state =
                    (DrawableContainer.DrawableContainerState) container.getConstantState();
                if (state != null) {
                    for (int i = 0, count = state.getChildCount(); i < count; i++) {
                        clearColorFilter(state.getChild(i));
                    }
                }
            }
        }
    }

    // from TextInputLayout
    private void ensureBackgroundDrawableStateWorkaround(EditText editText) {
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk != Build.VERSION_CODES.LOLLIPOP && sdk != Build.VERSION_CODES.LOLLIPOP_MR1) {
            // The workaround is only required on API 21-22
            return;
        }
        final Drawable bg = editText.getBackground();
        if (bg == null) {
            return;
        }

        // There is an issue in the platform which affects container Drawables
        // where the first drawable retrieved from resources will propogate any changes
        // (like color filter) to all instances from the cache. We'll try to workaround it...

        final Drawable newBg = bg.getConstantState().newDrawable();

        boolean hasReconstructedEditTextBackground = false;
        if (bg instanceof DrawableContainer) {
            // If we have a Drawable container, we can try and set it's constant state via
            // reflection from the new Drawable
            hasReconstructedEditTextBackground =
                DrawableUtils.setContainerConstantState((DrawableContainer) bg, newBg.getConstantState());
        }

        if (!hasReconstructedEditTextBackground) {
            // If we reach here then we just need to set a brand new instance of the Drawable
            // as the background. This has the unfortunate side-effect of wiping out any
            // user set padding, but I'd hope that use of custom padding on an EditText
            // is limited.
            editText.setBackground(newBg);
        }
    }

    @Override
    public void onCountryHasChanged(Country country) {
        final String phoneNumber = getArguments().getString(ARG_PHONE, "");
        final String number = countryController.getPhoneNumberWithoutCountryCode(phoneNumber);
        final String countryCode = phoneNumber.substring(0, phoneNumber.length() - number.length()).replace("+", "");
        if (!TextUtils.isEmpty(countryCode)) {
            return;
        }

        countryEditText.setText(String.format("+%s", country.getCountryCode()));
    }

    public interface Container {
        void onVerifyPhone(String phoneNumber);
        void onPhoneNumberCleared();
    }
}
