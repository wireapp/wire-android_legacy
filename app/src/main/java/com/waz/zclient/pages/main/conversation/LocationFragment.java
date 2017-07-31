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
package com.waz.zclient.pages.main.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.waz.api.ConversationsList;
import com.waz.api.IConversation;
import com.waz.api.MessageContent;
import com.waz.api.SyncState;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.accentcolor.AccentColorObserver;
import com.waz.zclient.controllers.permission.RequestPermissionsObserver;
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController;
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester;
import com.waz.zclient.core.stores.conversation.ConversationStoreObserver;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.tracking.GlobalTrackingController;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.views.TouchRegisteringFrameLayout;
import com.waz.zclient.utils.LayoutSpec;
import com.waz.zclient.utils.PermissionUtils;
import com.waz.zclient.utils.StringUtils;
import com.waz.zclient.utils.TrackingUtils;
import com.waz.zclient.utils.ViewUtils;

import java.util.List;
import java.util.Locale;

import timber.log.Timber;

@SuppressLint("All")
public class LocationFragment extends BaseFragment<LocationFragment.Container> implements com.google.android.gms.location.LocationListener,
                                                                                          LocationListener,
                                                                                          TouchRegisteringFrameLayout.TouchCallback,
                                                                                          GoogleMap.OnCameraChangeListener,
                                                                                          GoogleApiClient.ConnectionCallbacks,
                                                                                          GoogleApiClient.OnConnectionFailedListener,
                                                                                          OnMapReadyCallback,
                                                                                          ConversationStoreObserver,
                                                                                          RequestPermissionsObserver,
                                                                                          OnBackPressedListener,
                                                                                          AccentColorObserver,
                                                                                          View.OnClickListener {

    public static final String TAG = LocationFragment.class.getName();

    private static final String[] LOCATION_PERMISSIONS = new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
    private static final int LOCATION_PERMISSION_REQUEST_ID = 532;
    private static final float DEFAULT_MAP_ZOOM_LEVEL = 15F;
    private static final float DEFAULT_MIMIMUM_CAMERA_MOVEMENT = 2F;
    private static final int LOCATION_REQUEST_TIMEOUT_MS = 1500;
    private static final String MAP_VIEW_SAVE_STATE = "mapViewSaveState";

    private Toolbar toolbar;
    private MapView mapView;
    private View selectedLocationBackground;
    private GlyphTextView selectedLocationPin;
    private LinearLayout selectedLocationDetails;
    private TextView selectedLocationAddress;
    private TouchRegisteringFrameLayout touchRegisteringFrameLayout;
    private TextView requestCurrentLocationButton;
    private TextView sendCurrentLocationButton;
    private TextView toolbarTitle;
    private Bitmap marker;

    private GoogleMap map;
    private LocationManager locationManager;
    private Location currentLocation;

    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;

    private String currentLocationCountryName;
    private String currentLocationLocality;
    private String currentLocationSubLocality;
    private String currentLocationFirstAddressLine;
    private String currentLocationName;
    private LatLng currentLatLng;

    private boolean animateTocurrentLocation;
    private boolean zoom;
    private boolean animating;
    private boolean checkIfLocationServicesEnabled;
    private Handler mainHandler;
    private Handler backgroundHandler;
    private HandlerThread handlerThread;
    private Geocoder geocoder;

    private final Runnable updateCurrentLocationBubbleRunnable = new Runnable() {
        @Override
        public void run() {
            if (getActivity() == null || getContainer() == null) {
                return;
            }
            updateCurrentLocationName((int) map.getCameraPosition().zoom);
            setTextAddressBubble(currentLocationName);
        }
    };

    private final Runnable retrieveCurrentLocationNameRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                final List<Address> addresses = geocoder.getFromLocation(currentLatLng.latitude,
                                                                         currentLatLng.longitude,
                                                                         1);
                if (addresses != null && addresses.size() > 0) {
                    Address adr = addresses.get(0);
                    if (adr.getMaxAddressLineIndex() >= 0) {
                        currentLocationFirstAddressLine = adr.getAddressLine(0);
                    } else {
                        currentLocationFirstAddressLine = "";
                    }
                    currentLocationCountryName = adr.getCountryName();
                    currentLocationLocality =  adr.getLocality();
                    currentLocationSubLocality = adr.getSubLocality();
                } else {
                    currentLocationFirstAddressLine = "";
                    currentLocationCountryName = "";
                    currentLocationLocality = "";
                    currentLocationSubLocality = "";
                }

            } catch (Exception e) {
                currentLocationFirstAddressLine = "";
                currentLocationCountryName = "";
                currentLocationLocality = "";
                currentLocationSubLocality = "";
                Timber.i(e, "Unable to retrieve location name");
            }
            mainHandler.removeCallbacksAndMessages(null);
            mainHandler.post(updateCurrentLocationBubbleRunnable);
        }
    };

    public static LocationFragment newInstance() {
        return new LocationFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isGooglePlayServicesAvailable()) {
            googleApiClient = new GoogleApiClient.Builder(getContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        } else {
            locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        }
        mainHandler = new Handler();
        handlerThread = new HandlerThread("Background handler");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        geocoder = new Geocoder(getContext(), Locale.getDefault());
        zoom = true;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup viewGroup, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_location, viewGroup, false);

        toolbar = ViewUtils.getView(view, R.id.t_location_toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getActivity() == null) {
                    return;
                }
                getControllerFactory().getLocationController().hideShareLocation(null);
            }
        });
        if (LayoutSpec.isTablet(getContext())) {
            toolbar.setNavigationIcon(R.drawable.action_back_dark);
        }

        toolbarTitle = ViewUtils.getView(view, R.id.tv__location_toolbar__title);

        selectedLocationBackground = ViewUtils.getView(view, R.id.iv__selected_location__background);
        selectedLocationPin = ViewUtils.getView(view, R.id.gtv__selected_location__pin);

        selectedLocationDetails = ViewUtils.getView(view, R.id.ll_selected_location_details);
        selectedLocationDetails.setVisibility(View.INVISIBLE);

        touchRegisteringFrameLayout = ViewUtils.getView(view, R.id.trfl_location_touch_registerer);
        touchRegisteringFrameLayout.setTouchCallback(this);

        requestCurrentLocationButton = ViewUtils.getView(view, R.id.gtv__location__current__button);
        requestCurrentLocationButton.setOnClickListener(this);

        sendCurrentLocationButton = ViewUtils.getView(view, R.id.ttv__location_send_button);
        sendCurrentLocationButton.setOnClickListener(this);

        selectedLocationAddress = ViewUtils.getView(view, R.id.ttv__location_address);

        final Bundle mapViewSavedInstanceState = savedInstanceState != null ? savedInstanceState.getBundle(MAP_VIEW_SAVE_STATE) : null;
        mapView = ViewUtils.getView(view, R.id.mv_map);
        mapView.onCreate(mapViewSavedInstanceState);
        mapView.getMapAsync(this);

        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Dirty hack to avoid crash in MapView
        // See https://code.google.com/p/gmaps-api-issues/issues/detail?id=6237#c9
        final Bundle mapViewSaveState = new Bundle(outState);
        mapView.onSaveInstanceState(mapViewSaveState);
        outState.putBundle(MAP_VIEW_SAVE_STATE, mapViewSaveState);

        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        getControllerFactory().getAccentColorController().addAccentColorObserver(this);
        getControllerFactory().getRequestPermissionsController().addObserver(this);
        getStoreFactory().conversationStore().addConversationStoreObserver(this);
        if (PermissionUtils.hasSelfPermissions(getActivity(), LOCATION_PERMISSIONS)) {
            updateLastKnownLocation();
            if (!isLocationServicesEnabled()) {
                showLocationServicesDialog();
            }
            requestCurrentLocationButton.setVisibility(View.VISIBLE);
        } else {
            ActivityCompat.requestPermissions(getActivity(), LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST_ID);
            checkIfLocationServicesEnabled = true;
            requestCurrentLocationButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        IConversation currentConversation = getStoreFactory().conversationStore().getCurrentConversation();
        if (currentConversation != null) {
            toolbarTitle.setText(currentConversation.getName());
        }
        if (!getControllerFactory().getUserPreferencesController().hasPerformedAction(IUserPreferencesController.SEND_LOCATION_MESSAGE)) {
            getControllerFactory().getUserPreferencesController().setPerformedAction(IUserPreferencesController.SEND_LOCATION_MESSAGE);
            Toast.makeText(getContext(), R.string.location_sharing__tip, Toast.LENGTH_LONG).show();
        }
        if (PermissionUtils.hasSelfPermissions(getActivity(), LOCATION_PERMISSIONS)) {
            if (googleApiClient != null) {
                googleApiClient.connect();
            } else if (locationManager != null) {
                startLocationManagerListeningForCurrentLocation();
            }
        }
    }

    @Override
    public void onPause() {
        stopLocationManagerListeningForCurrentLocation();
        stopPlayServicesListeningForCurrentLocation();
        if (googleApiClient != null) {
            googleApiClient.disconnect();
        }
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        getStoreFactory().conversationStore().removeConversationStoreObserver(this);
        getControllerFactory().getRequestPermissionsController().removeObserver(this);
        getControllerFactory().getAccentColorController().removeAccentColorObserver(this);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return ConnectionResult.SUCCESS == apiAvailability.isGooglePlayServicesAvailable(getContext());
    }

    @SuppressWarnings("ResourceType")
    @SuppressLint("MissingPermission")
    private void startLocationManagerListeningForCurrentLocation() {
        Timber.i("startLocationManagerListeningForCurrentLocation");
        if (locationManager != null && PermissionUtils.hasSelfPermissions(getContext(), LOCATION_PERMISSIONS)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
    }

    private void startPlayServicesListeningForCurrentLocation() {
        Timber.i("startPlayServicesListeningForCurrentLocation");
        if (locationRequest != null) {
            return;
        }
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    @SuppressWarnings("ResourceType")
    @SuppressLint("MissingPermission")
    private void stopLocationManagerListeningForCurrentLocation() {
        Timber.i("stopLocationManagerListeningForCurrentLocation");
        if (locationManager != null && PermissionUtils.hasSelfPermissions(getContext(), LOCATION_PERMISSIONS)) {
            locationManager.removeUpdates(this);
        }
    }

    private void stopPlayServicesListeningForCurrentLocation() {
        Timber.i("stopPlayServicesListeningForCurrentLocation");
        if (locationRequest == null) {
            return;
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        locationRequest = null;
    }

    private boolean isLocationServicesEnabled() {
        if (!PermissionUtils.hasSelfPermissions(getContext(), LOCATION_PERMISSIONS)) {
            return false;
        }
        // We are creating a local locationManager here, as it's not sure we already have one
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        boolean gpsEnabled;
        boolean netEnabled;

        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            gpsEnabled = false;
        }

        try {
            netEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            netEnabled = false;
        }
        return netEnabled || gpsEnabled;
    }

    private void showLocationServicesDialog() {
        ViewUtils.showAlertDialog(getContext(),
                                  R.string.location_sharing__enable_system_location__title,
                                  R.string.location_sharing__enable_system_location__message,
                                  R.string.location_sharing__enable_system_location__confirm,
                                  R.string.location_sharing__enable_system_location__cancel,
                                  new DialogInterface.OnClickListener() {
                                      @Override
                                      public void onClick(DialogInterface dialog, int which) {
                                          Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                          getContext().startActivity(myIntent);
                                      }
                                  },
                                  null);
    }

    private void updateCurrentLocationName(int zoom) {
        if (zoom >= 12) {
            // Local address
            if (!StringUtils.isBlank(currentLocationFirstAddressLine)) {
                currentLocationName = currentLocationFirstAddressLine;
            } else if (!StringUtils.isBlank(currentLocationSubLocality)) {
                currentLocationName = currentLocationSubLocality;
            } else if (!StringUtils.isBlank(currentLocationLocality)) {
                currentLocationName = currentLocationLocality;
            } else {
                currentLocationName = currentLocationCountryName;
            }
        } else if (zoom >= 6) {
            // City-ish
            if (!StringUtils.isBlank(currentLocationSubLocality)) {
                currentLocationName = currentLocationSubLocality;
            } else if (!StringUtils.isBlank(currentLocationLocality)) {
                currentLocationName = currentLocationLocality;
            } else {
                currentLocationName = currentLocationCountryName;
            }
        } else {
            // Country
            currentLocationName = currentLocationCountryName;
        }
    }

    @Override
    public void onInterceptTouchEvent(MotionEvent event) {
        animateTocurrentLocation = false;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.gtv__location__current__button:
                animateTocurrentLocation = true;
                zoom = true;
                if (PermissionUtils.hasSelfPermissions(getActivity(), LOCATION_PERMISSIONS)) {
                    updateLastKnownLocation();
                } else {
                    ActivityCompat.requestPermissions(getActivity(), LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST_ID);
                }
                break;
            case R.id.ttv__location_send_button:
                if (getStoreFactory() == null || getStoreFactory().isTornDown()) {
                    return;
                }
                MessageContent.Location location;
                if (currentLatLng == null) {
                    if (!BuildConfig.DEBUG) {
                        return;
                    }
                    location = new MessageContent.Location(0.0f, 0.0f, "", 0);
                } else {
                    location = new MessageContent.Location((float) currentLatLng.longitude,
                        (float) currentLatLng.latitude,
                        currentLocationName,
                        (int) map.getCameraPosition().zoom);
                }

                getControllerFactory().getLocationController().hideShareLocation(location);
                TrackingUtils.onSentLocationMessage(((BaseActivity) getActivity()).injectJava(GlobalTrackingController.class),
                                                    getStoreFactory().conversationStore().getCurrentConversation());
                break;
        }
    }

    @SuppressWarnings("ResourceType")
    @SuppressLint("MissingPermission")
    private void updateLastKnownLocation() {
        if (locationManager != null) {
            currentLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        } else if (googleApiClient != null && locationRequest != null) {
            currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        }
        if (currentLocation != null) {
            onLocationChanged(currentLocation);
        }
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        Timber.i("onCameraChange");
        animating = false;
        currentLatLng = cameraPosition.target;
        currentLocationName = "";

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                selectedLocationAddress.setVisibility(View.INVISIBLE);
            }
        }, LOCATION_REQUEST_TIMEOUT_MS);
        backgroundHandler.removeCallbacksAndMessages(null);
        backgroundHandler.post(retrieveCurrentLocationNameRunnable);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Timber.i("onMapReady");
        map = googleMap;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        try {
            map.setMyLocationEnabled(false);
        } catch (SecurityException se) {
            // ignore
        }
        if (currentLocation != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()), DEFAULT_MAP_ZOOM_LEVEL));
            animateTocurrentLocation = false;
            onLocationChanged(currentLocation);
        }
        map.setOnCameraChangeListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Timber.i("onLocationChanged, lat=%f, lon=%f, accuracy=%f, distanceToCurrent=%f", location.getLatitude(), location.getLongitude(), location.getAccuracy(), (currentLocation == null) ? 0 : location.distanceTo(currentLocation));

        float distanceFromCenterOfScreen = Float.MAX_VALUE;
        if (currentLatLng != null) {
            float[] distance = new float[1];
            Location.distanceBetween(currentLatLng.latitude,
                                     currentLatLng.longitude,
                                     location.getLatitude(),
                                     location.getLongitude(),
                                     distance);
            distanceFromCenterOfScreen = distance[0];
            Timber.i("current location distance from map center %f", distance[0]);
        }

        currentLocation = location;
        if (map != null) {
            map.clear();
            LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
            map.addMarker(new MarkerOptions()
                              .position(position)
                              .icon(BitmapDescriptorFactory.fromBitmap(getMarker()))
                              .anchor(0.5f, 0.5f));
            if (animateTocurrentLocation && distanceFromCenterOfScreen > DEFAULT_MIMIMUM_CAMERA_MOVEMENT) {
                if (zoom || animating) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLocation.getLatitude(),
                                                                                   currentLocation.getLongitude()),
                                                                        DEFAULT_MAP_ZOOM_LEVEL));
                    animating = true;
                    zoom = false;
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(currentLocation.getLatitude(),
                                                                               currentLocation.getLongitude())));
                }
            }
        }
    }

    private void setTextAddressBubble(String name) {
        if (StringUtils.isBlank(name)) {
            selectedLocationDetails.setVisibility(View.INVISIBLE);
            selectedLocationBackground.setVisibility(View.VISIBLE);
            selectedLocationPin.setVisibility(View.VISIBLE);
        } else {
            selectedLocationAddress.setText(name);
            selectedLocationAddress.setVisibility(View.VISIBLE);
            selectedLocationDetails.requestLayout();
            selectedLocationDetails.setVisibility(View.VISIBLE);
            selectedLocationBackground.setVisibility(View.INVISIBLE);
            selectedLocationPin.setVisibility(View.INVISIBLE);
        }
    }

    private Bitmap getMarker() {
        if (marker != null) {
            return marker;
        }
        int size = getResources().getDimensionPixelSize(R.dimen.share_location__current_location_marker__size);
        int outerCircleRadius = getResources().getDimensionPixelSize(R.dimen.share_location__current_location_marker__outer_ring_radius);
        int midCircleRadius = getResources().getDimensionPixelSize(R.dimen.share_location__current_location_marker__mid_ring_radius);
        int innerCircleRadius = getResources().getDimensionPixelSize(R.dimen.share_location__current_location_marker__inner_ring_radius);

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(getControllerFactory().getAccentColorController().getColor());
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(getResources().getInteger(R.integer.share_location__current_location_marker__outer_ring_alpha));
        canvas.drawCircle(size / 2, size / 2, outerCircleRadius, paint);
        paint.setAlpha(getResources().getInteger(R.integer.share_location__current_location_marker__mid_ring_alpha));
        canvas.drawCircle(size / 2, size / 2, midCircleRadius, paint);
        paint.setAlpha(getResources().getInteger(R.integer.share_location__current_location_marker__inner_ring_alpha));
        canvas.drawCircle(size / 2, size / 2, innerCircleRadius, paint);
        marker = bitmap;
        return marker;
    }

    @Override
    public void onConversationListUpdated(@NonNull ConversationsList conversationsList) {

    }

    @Override
    public void onConversationListStateHasChanged(ConversationsList.ConversationsListState state) {

    }

    @Override
    public void onCurrentConversationHasChanged(IConversation fromConversation,
                                                IConversation toConversation,
                                                ConversationChangeRequester conversationChangerSender) {
        if (toConversation != null) {
            toolbarTitle.setText(toConversation.getName());
        }
    }

    @Override
    public void onConversationSyncingStateHasChanged(SyncState syncState) {

    }

    @Override
    public void onMenuConversationHasChanged(IConversation fromConversation) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (getActivity() == null) {
            return;
        }
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_ID:
                if (PermissionUtils.verifyPermissions(grantResults)) {
                    requestCurrentLocationButton.setVisibility(View.VISIBLE);
                    zoom = true;
                    updateLastKnownLocation();
                    if (googleApiClient != null && googleApiClient.isConnected()) {
                        startPlayServicesListeningForCurrentLocation();
                    } else if (locationManager != null) {
                        startLocationManagerListeningForCurrentLocation();
                    }
                    if (checkIfLocationServicesEnabled) {
                        checkIfLocationServicesEnabled = false;
                        if (!isLocationServicesEnabled()) {
                            showLocationServicesDialog();
                        }
                    }
                } else {
                    Toast.makeText(getContext(), R.string.location_sharing__permission_error, Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public boolean onBackPressed() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return false;
        }
        getControllerFactory().getLocationController().hideShareLocation(null);
        return true;
    }

    @Override
    public void onAccentColorHasChanged(Object sender, int color) {
        selectedLocationPin.setTextColor(color);
        marker = null;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Timber.i("onConnected");
        if (PermissionUtils.hasSelfPermissions(getActivity(), LOCATION_PERMISSIONS)) {
            animateTocurrentLocation = true;
            startPlayServicesListeningForCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(getActivity(), LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST_ID);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Timber.i("onConnectionSuspended");
        // goodbye
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // fallback to LocationManager
        Timber.e("Google API Client connection failed");
        googleApiClient.unregisterConnectionFailedListener(this);
        googleApiClient.unregisterConnectionCallbacks(this);
        googleApiClient = null;
        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        if (PermissionUtils.hasSelfPermissions(getActivity(), LOCATION_PERMISSIONS)) {
            startLocationManagerListeningForCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(getActivity(), LOCATION_PERMISSIONS, LOCATION_PERMISSION_REQUEST_ID);
        }
    }

    public interface Container {

    }
}
