/**
 * Wire
 * Copyright (C) 2018, 2021 Wire Swiss GmbH
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
package com.waz.zclient.pages.main.conversation

import java.util._

import android.Manifest
import android.content.{Context, DialogInterface, Intent}
import android.graphics.drawable.BitmapDrawable
import android.graphics.{Bitmap, Canvas, Paint}
import android.location.{Location, LocationListener, LocationManager}
import android.os.{Bundle, Handler, HandlerThread, Looper}
import android.provider.Settings
import android.view.{LayoutInflater, MotionEvent, View, ViewGroup}
import android.widget.{LinearLayout, TextView, Toast}
import androidx.annotation.Nullable
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.waz.api.MessageContent
import com.waz.model.{AccentColor, ConversationData}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.tracking.ContributionEvent
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.core.logging.Logger.info
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.views.TouchRegisteringFrameLayout
import com.waz.zclient.utils.{Callback, RichView, StringUtils, ViewUtils}
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}
import org.osmdroid.bonuspack.location.GeocoderNominatim
import org.osmdroid.config.Configuration
import org.osmdroid.events.{MapListener, ScrollEvent, ZoomEvent}
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController.Visibility
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

import scala.util.Try
import scala.util.control.Exception._

// FIXME: use more idiomatic scala (Option, lazy val etc.)
class LocationFragment extends BaseFragment[LocationFragment.Container]
  with FragmentHelper
  with LocationListener
  with MapListener
  with TouchRegisteringFrameLayout.TouchCallback
  with OnBackPressedListener
  with View.OnClickListener { self =>

  private implicit def context: Context = getActivity
  import LocationFragment._

  private lazy val conversationController = inject[ConversationController]
  private lazy val locationController = inject[ILocationController]
  private lazy val userPreferencesController  = inject[IUserPreferencesController]
  private lazy val permissions = inject[PermissionsService]
  private lazy val accentColorsController = inject[AccentColorController]

  private lazy val mapView = returning(view[MapView](R.id.mv_map)) { vh =>
    vh.foreach { mv =>
      mv.setTileSource(TileSourceFactory.MAPNIK)
      mv.addMapListener(this)
      mv.getController.setZoom(INIT_MAP_ZOOM_LEVEL)
      mv.setMultiTouchControls(true)
      mv.getZoomController.setVisibility(Visibility.SHOW_AND_FADEOUT) // FIXME
    }
  }

  private lazy val requestCurrentLocationButton = returning(view[TextView](R.id.gtv__location__current__button)) { vh =>
    vh.foreach(_.setOnClickListener(self))
  }
  private lazy val selectedLocationAddress = view[TextView](R.id.ttv__location_address)
  private lazy val selectedLocationBackground = view[View](R.id.iv__selected_location__background)
  private lazy val selectedLocationDetails = returning(view[LinearLayout](R.id.ll_selected_location_details)) { vh =>
    vh.foreach(_.setVisible(false))
  }
  private lazy val selectedLocationPin = view[GlyphTextView](R.id.gtv__selected_location__pin)
  private lazy val sendSelectedLocationButton = returning(view[TextView](R.id.ttv__location_send_button)) { vh =>
    vh.foreach(_.setOnClickListener(self))
  }
  private lazy val toolbar = returning(view[Toolbar](R.id.t_location_toolbar)) { vh =>
    vh.foreach(_.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit =
        if (Option(getActivity).isDefined) locationController.hideShareLocation(null)
    }))
  }
  private lazy val toolbarTitle = view[TextView](R.id.tv__location_toolbar__title)
  private lazy val touchRegisteringFrameLayout = returning(view[TouchRegisteringFrameLayout](R.id.trfl_location_touch_registerer)) { vh =>
    vh.foreach(_.setTouchCallback(self))
  }

  private lazy val mainHandler = new Handler(Looper.myLooper)
  private lazy val handlerThread = returning(new HandlerThread("Background handler")) { _.start() }
  private lazy val backgroundHandler = new Handler(handlerThread.getLooper)
  private lazy val geocoder = new GeocoderNominatim(Locale.getDefault, getContext.getPackageName)
  private lazy val locationManager =
    Try(getActivity.getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]).toOption

  private var currentLocation: Option[Location] = None
  private var selectedLocation: Option[LocationInfo] = None
  private var animateToCurrentLocation: Option[Boolean] = None
  private var checkIfLocationServicesEnabled = false
  private var accentColor = 0

  private val updateSelectedLocationBubbleRunnable = new Runnable {
    override def run(): Unit =
      if (Option(getActivity).isDefined) {
        for { l <- selectedLocation; z <- mapView.map(_.getZoomLevelDouble.toInt)}
          setTextAddressBubble(l.name(z))
      }
  }

  private val retrieveSelectedLocationNameRunnable = new Runnable {
    override def run(): Unit = {
      try {
        mapView.map(_.getMapCenter).foreach { center =>
          val addresses = geocoder.getFromLocation(center.getLatitude, center.getLongitude, 1)
          if (addresses != null && addresses.size > 0) {
            val adr = addresses.get(0)
            selectedLocation = Some(LocationInfo(
              if (adr.getMaxAddressLineIndex >= 0) adr.getAddressLine(0) else "",
              adr.getSubLocality,
              adr.getLocality,
              adr.getCountryName
            ))
          } else {
            info(TAG, "No locations returned by geocoder")
          }
        }
      } catch {
        case e: Exception => info(TAG, s"Unable to retrieve location name: $e")
      }
      mainHandler.removeCallbacksAndMessages(null)
      mainHandler.post(updateSelectedLocationBubbleRunnable)
    }
  }

  private val callback: Callback[ConversationChange] = new Callback[ConversationChange] {
    override def callback(change: ConversationChange): Unit =
      if (change.toConvId != null) {
        conversationController.withConvLoaded(change.toConvId, new Callback[ConversationData] {
          override def callback(conversationData: ConversationData): Unit =
            toolbarTitle.foreach(_.setText(conversationData.getName()))
        })
      }
  }

  override def onCreate(@Nullable savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val ctx = getActivity.getApplicationContext
    Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

    locationManager
    mainHandler
    handlerThread
    backgroundHandler
    geocoder

    // retrieve the accent color to be used for the paint
    accentColor = AccentColor.defaultColor.color
    accentColorsController.accentColor.head.foreach { color => accentColor = color.color }(Threading.Background)
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_location, viewGroup, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    toolbar
    toolbarTitle
    selectedLocationBackground
    selectedLocationPin
    selectedLocationDetails
    touchRegisteringFrameLayout
    requestCurrentLocationButton
    sendSelectedLocationButton
    selectedLocationAddress
    mapView
  }

  override def onStart(): Unit = {
    super.onStart()
    if (hasLocationPermission) {
      updateLastKnownLocation()
      if (!isLocationServicesEnabled) showLocationServicesDialog()
      requestCurrentLocationButton.foreach(_.setVisible(true))
    } else {
      requestLocationPermission()
      checkIfLocationServicesEnabled = true
      requestCurrentLocationButton.foreach(_.setVisible(false))
    }
    conversationController.addConvChangedCallback(callback)
  }

  override def onResume(): Unit = {
    super.onResume()
    mapView.foreach(_.onResume())
    inject(classOf[ConversationController]).withCurrentConvName(new Callback[String] {
      override def callback(convName: String): Unit = toolbarTitle.foreach(_.setText(convName))
    })
    if (!userPreferencesController.hasPerformedAction(IUserPreferencesController.SEND_LOCATION_MESSAGE)) {
      userPreferencesController.setPerformedAction(IUserPreferencesController.SEND_LOCATION_MESSAGE)
      Toast.makeText(getContext, R.string.location_sharing__tip, Toast.LENGTH_LONG).show()
    }
    startLocationManagerListeningForCurrentLocation()
  }

  override def onPause(): Unit = {
    stopLocationManagerListeningForCurrentLocation()
    super.onPause()
    mapView.foreach(_.onPause())
  }

  override def onStop(): Unit = {
    conversationController.removeConvChangedCallback(callback)
    super.onStop()
  }

  override def onProviderEnabled(provider: String): Unit = {}
  override def onProviderDisabled(provider: String): Unit = {}

  private def startLocationManagerListeningForCurrentLocation(): Unit = {
    info(TAG, "startLocationManagerListeningForCurrentLocation")
    locationManager.foreach { lm =>
      if (hasLocationPermission) {
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
      }
    }
  }

  private def stopLocationManagerListeningForCurrentLocation(): Unit = {
    info(TAG, "stopLocationManagerListeningForCurrentLocation")
    locationManager.foreach { lm =>
      if (hasLocationPermission) {
        lm.removeUpdates(this)
      }
    }
  }

  // FIXME: NETWORK_PROVIDER does not seem to be used
  // We are creating a local locationManager here, as it's not sure we already have one
  private def isLocationServicesEnabled: Boolean =
    hasLocationPermission && Option(getActivity.getSystemService(Context.LOCATION_SERVICE)
                                      .asInstanceOf[LocationManager]).fold(false) { lm =>
      failAsValue(classOf[Exception])(false)(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) ||
      failAsValue(classOf[Exception])(false)(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

  private def showLocationServicesDialog(): Unit =
    ViewUtils.showAlertDialog(
      getContext,
      R.string.location_sharing__enable_system_location__title,
      R.string.location_sharing__enable_system_location__message,
      R.string.location_sharing__enable_system_location__confirm,
      R.string.location_sharing__enable_system_location__cancel,
      new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit =
          getContext.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
      },
      null
    )

  override def onInterceptTouchEvent(event: MotionEvent): Unit = {
    animateToCurrentLocation = Some(false)
  }

  override def onClick(view: View): Unit =
    view.getId match {
      case R.id.gtv__location__current__button =>
        animateToCurrentLocation = Some(true)
        if (hasLocationPermission) updateLastKnownLocation() else requestLocationPermission()
      case R.id.ttv__location_send_button =>
        val location = mapView.fold(
          new MessageContent.Location(0f, 0f, "", 0)
        ) { mv =>
          val center = mv.getMapCenter
          val zoom = mv.getZoomLevelDouble.toInt
          // NB: longitude before latitude in this API
          new MessageContent.Location(
            center.getLongitude.toFloat,
            center.getLatitude.toFloat,
            selectedLocation.fold("")(_.name(zoom)),
            zoom
          )
        }
        locationController.hideShareLocation(location)
        ZMessaging.currentGlobal.trackingService.contribution(
          ContributionEvent.Action.Location,
          Option.empty
        )
      case _ =>
    }

  private def updateLastKnownLocation(): Unit =
    locationManager.flatMap(lm => Option(lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)))
      .foreach(onLocationChanged)

  override def onScroll(event: ScrollEvent): Boolean = onCameraChange()
  override def onZoom(event: ZoomEvent): Boolean = onCameraChange()

  private def onCameraChange(): Boolean = {
    info(TAG, "onCameraChange")
    selectedLocation = None
    mainHandler.postDelayed(new Runnable {
      override def run(): Unit = selectedLocationAddress.foreach(_.setVisible(false))
    }, LOCATION_REQUEST_TIMEOUT_MS)
    backgroundHandler.removeCallbacksAndMessages(null)
    backgroundHandler.post(retrieveSelectedLocationNameRunnable)
    true // ???
  }

  // FIXME: what's the expected behaviour?
  override def onLocationChanged(location: Location): Unit = {
    val distanceToCurrent = currentLocation.fold(0f)(location.distanceTo)
    info(TAG, s"onLocationChanged, lat=${location.getLatitude}, lon=${location.getLongitude}, accuracy=${location.getAccuracy}, distanceToCurrent=$distanceToCurrent")
    var distanceFromCenterOfScreen = Float.MaxValue
    mapView.foreach { mv =>
      val center = mv.getMapCenter
      val distance = new Array[Float](1)
      Location.distanceBetween(center.getLatitude, center.getLongitude, location.getLatitude, location.getLongitude, distance)
      distanceFromCenterOfScreen = distance(0)
      info(TAG, s"current location distance from map center: ${distance(0)}")
      if (currentLocation.isEmpty || distanceToCurrent != 0) {
        mv.getOverlays.clear()
        mv.getOverlays.add(returning(new Marker(mv)){ marker =>
          marker.setPosition(new GeoPoint(location.getLatitude, location.getLongitude))
          marker.setIcon(new BitmapDrawable(getResources, getMarker))
          marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        })
        mv.invalidate()
      }
      if (currentLocation.isEmpty && animateToCurrentLocation.isEmpty) {
        info(TAG, "zooming in to current location")
        mv.getController.setZoom(DEFAULT_MAP_ZOOM_LEVEL)
        animateToCurrentLocation = Some(true)
      }
    }
    currentLocation = Some(location)
    mapView.foreach { mv =>
      if (animateToCurrentLocation.getOrElse(false) && distanceFromCenterOfScreen > DEFAULT_MINIMUM_CAMERA_MOVEMENT) {
        info(TAG, "moving to current location")
        mv.getController.animateTo(new GeoPoint(location.getLatitude, location.getLongitude))
        animateToCurrentLocation = Some(false)
      }
    }
  }

  private def setTextAddressBubble(name: String): Unit = {
    val isBlank = StringUtils.isBlank(name)
    selectedLocationBackground.foreach(_.setVisible(isBlank))
    selectedLocationDetails.foreach { sld =>
      if (!isBlank) sld.requestLayout()
      sld.setVisible(!isBlank)
    }
    selectedLocationPin.foreach(_.setVisible(isBlank))
    selectedLocationAddress.foreach { sla =>
      sla.setText(name)
      sla.setVisible(!isBlank)
    }
  }

  private lazy val getMarker: Bitmap = {
    val size = getResources.getDimensionPixelSize(R.dimen.share_location__current_location_marker__size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = new Canvas(bitmap)
    val paint = returning(new Paint) { p =>
      p.setColor(accentColor)
      p.setAntiAlias(true)
      p.setStyle(Paint.Style.FILL)
    }

    def drawCircle(alphaId: Int, ringRadiusId: Int): Unit = {
      paint.setAlpha(getResources.getInteger(alphaId))
      val radius = getResources.getDimensionPixelSize(ringRadiusId)
      canvas.drawCircle((size / 2).toFloat, (size / 2).toFloat, radius.toFloat, paint)
    }

    drawCircle(
      R.integer.share_location__current_location_marker__outer_ring_alpha,
      R.dimen.share_location__current_location_marker__outer_ring_radius
    )
    drawCircle(
      R.integer.share_location__current_location_marker__mid_ring_alpha,
      R.dimen.share_location__current_location_marker__mid_ring_radius
    )
    drawCircle(
      R.integer.share_location__current_location_marker__inner_ring_alpha,
      R.dimen.share_location__current_location_marker__inner_ring_radius
    )

    bitmap
  }

  override def onBackPressed(): Boolean = {
    locationController.hideShareLocation(null)
    true
  }

  private def hasLocationPermission: Boolean =
    permissions.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)

  private def requestLocationPermission(): Unit =
    permissions.requestPermission(
      Manifest.permission.ACCESS_FINE_LOCATION,
      new PermissionsService.PermissionsCallback {
        override def onPermissionResult (granted: Boolean): Unit =
          if (getActivity != null) {
            if (granted) {
              requestCurrentLocationButton.foreach(_.setVisible(true))
              updateLastKnownLocation()
              if (locationManager.nonEmpty) {
                startLocationManagerListeningForCurrentLocation()
              }
              if (checkIfLocationServicesEnabled) {
                checkIfLocationServicesEnabled = false
                if (!isLocationServicesEnabled) {
                  showLocationServicesDialog()
                }
              }
            } else {
              Toast.makeText(getContext, R.string.location_sharing__permission_error, Toast.LENGTH_SHORT).show()
            }
          }
      })
}

object LocationFragment {
  trait Container

  case class LocationInfo(firstAddressLine: String, subLocality: String, locality: String, countryName: String) {
    def name(zoom: Int): String = {
      info(TAG, s"Zoom level: $zoom")
      if (zoom >= ZOOM_STREET && !StringUtils.isBlank(firstAddressLine)) {
        firstAddressLine
      } else if (zoom >= ZOOM_CITY && !StringUtils.isBlank(subLocality)) {
        subLocality
      } else if (zoom >= ZOOM_CITY && !StringUtils.isBlank(locality)) {
        locality
      } else {
        countryName
      }
    }
  }

  val TAG = "LocationFragment"

  // FIXME: zoom levels seem to differ between google maps & openstreetmap
  // https://wiki.openstreetmap.org/wiki/Zoom_levels
  private val DEFAULT_MAP_ZOOM_LEVEL = 17f // FIXME
  private val INIT_MAP_ZOOM_LEVEL = 2f
  private val ZOOM_CITY = 11 // FIXME
  private val ZOOM_STREET = 16 // FIXME

  private val DEFAULT_MINIMUM_CAMERA_MOVEMENT = 5f // FIXME
  private val LOCATION_REQUEST_TIMEOUT_MS = 1500

  def newInstance(): LocationFragment = new LocationFragment
}
