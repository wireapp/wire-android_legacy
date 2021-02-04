/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import java.io.File
import java.net.Proxy
import java.util.concurrent.Executors

import android.content.{Context => AContext}
import com.softwaremill.macwire._
import com.waz.bitmap.BitmapDecoder
import com.waz.bitmap.video.VideoTranscoder
import com.waz.cache.CacheService
import com.waz.client.{RegistrationClient, RegistrationClientImpl}
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.{LogsService, LogsServiceImpl}
import com.waz.permissions.PermissionsService
import com.waz.service.assets.{AudioTranscoder, FileRestrictionList, GeneralFileCacheImpl, GlobalRecordAndPlayService}
import com.waz.service.call._
import com.waz.service.push._
import com.waz.service.tracking.{TrackingService, TrackingServiceImpl}
import com.waz.sync.client._
import com.waz.sync.{AccountSyncHandler, SyncHandler, SyncRequestService}
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache
import com.waz.ui.MemoryImageCache.{Entry, Key}
import com.waz.utils.wrappers.{Context, GoogleApi}
import com.waz.utils.{Cache, IoUtils}
import com.waz.zms.BuildConfig
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{HttpClient, RequestInterceptor}
import com.waz.znet2.{HttpClientOkHttpImpl, OkHttpUserAgentInterceptor}
import okhttp3.Interceptor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait GlobalModule {
  def context:                  AContext
  def backend:                  BackendConfig

  def syncRequests:             SyncRequestService
  def syncHandler:              SyncHandler

  def ssoService:               SSOService
  def tokenService:             GlobalTokenService
  def notificationsUi:          NotificationUiController
  def accountsService:          AccountsService
  def calling:                  GlobalCallingService
  def prefs:                    GlobalPreferences
  def googleApi:                GoogleApi
  def storage:                  Database
  def metadata:                 MetaDataService
  def cache:                    CacheService
  def bitmapDecoder:            BitmapDecoder
  def trimmingLruCache:         Cache[Key, Entry]
  def imageCache:               MemoryImageCache
  def network:                  DefaultNetworkModeService
  def phoneNumbers:             PhoneNumberService
  def timeouts:                 Timeouts
  def permissions:              PermissionsService
  def avs:                      Avs
  def reporting:                GlobalReportingService
  def loginClient:              LoginClient
  def regClient:                RegistrationClient
  def urlCreator:               UrlCreator
  def httpClient:               HttpClient
  def httpClientForLongRunning: HttpClient
  def videoTranscoder:          VideoTranscoder
  def audioTranscoder:          AudioTranscoder
  def cacheCleanup:             CacheCleaningService
  def accountsStorage:          AccountStorage
  def teamsStorage:             TeamsStorage
  def recordingAndPlayback:     GlobalRecordAndPlayService
  def tempFiles:                TempFileService
  def blacklistClient:          VersionBlacklistClient
  def blacklist:                VersionBlacklistService
  def factory:                  ZMessagingFactory
  def lifecycle:                UiLifeCycle

  def flowmanager:              FlowManagerService
  def mediaManager:             MediaManagerService

  def trackingService:          TrackingService

  def logsService:              LogsService
  def customBackendClient:      CustomBackendClient
  def httpProxy:                Option[Proxy]

  def fileRestrictionList:      FileRestrictionList
}

class GlobalModuleImpl(val context:             AContext,
                       val backend:             BackendConfig,
                       val prefs:               GlobalPreferences,
                       val googleApi:           GoogleApi,
                       val syncRequests:        SyncRequestService,
                       val notificationsUi:     NotificationUiController,
                       val fileRestrictionList: FileRestrictionList,
                       val defaultProxyDetails: ProxyDetails
                      ) extends GlobalModule with DerivedLogTag { global =>

  //trigger initialization of Firebase in onCreate - should prevent problems with Firebase setup
  val lifecycle:                UiLifeCycle                      = new UiLifeCycleImpl()
  val network:                  DefaultNetworkModeService        = wire[DefaultNetworkModeService]
  val trackingService:          TrackingService                  = TrackingServiceImpl(accountsService, metadata.versionName)

  val tokenService:             GlobalTokenService               = wire[GlobalTokenServiceImpl]
  val storage:                  Database                         = new GlobalDatabase(context, tracking = trackingService)

  lazy val ssoService:          SSOService                       = wire[SSOService]
  lazy val accountsService:     AccountsService                  = new AccountsServiceImpl(this, BuildConfig.KOTLIN_SETTINGS)
  lazy val syncHandler:         SyncHandler                      = new AccountSyncHandler(accountsService)
  lazy val calling:             GlobalCallingService             = new GlobalCallingService

  lazy val contextWrapper:      Context                          = Context.wrap(context)

  lazy val metadata:            MetaDataService                  = wire[MetaDataService]
  lazy val cache:               CacheService                     = CacheService(context, storage)
  lazy val bitmapDecoder:       BitmapDecoder                    = wire[BitmapDecoder]

  lazy val trimmingLruCache:    Cache[Key, Entry]                = MemoryImageCache.newTrimmingLru(context)
  lazy val imageCache:          MemoryImageCache                 = wire[MemoryImageCache]

  lazy val phoneNumbers:        PhoneNumberService               = wire[PhoneNumberServiceImpl]
  lazy val timeouts                                              = wire[Timeouts]
  lazy val permissions:         PermissionsService               = new PermissionsService
  lazy val avs:                 Avs                              = wire[AvsImpl]

  lazy val reporting                                             = wire[GlobalReportingService]

  lazy val loginClient:         LoginClient                      = new LoginClientImpl()(urlCreator, httpClient)
  lazy val regClient:           RegistrationClient               = new RegistrationClientImpl()(urlCreator, httpClient)

  lazy val urlCreator:          UrlCreator                       = UrlCreator.simpleAppender(() => backend.baseUrl.toString)
  private val customUserAgentHttpInterceptor: Interceptor        = new OkHttpUserAgentInterceptor(metadata)
  implicit lazy val httpClient: HttpClient                       = HttpClientOkHttpImpl(enableLogging = BuildConfig.DEBUG, pin = backend.pin, customUserAgentInterceptor = Some(customUserAgentHttpInterceptor), proxy = httpProxy)(Threading.IO)
  lazy val httpClientForLongRunning: HttpClient                  = HttpClientOkHttpImpl(enableLogging = BuildConfig.DEBUG, timeout = Some(30.seconds), pin = backend.pin, customUserAgentInterceptor = Some(customUserAgentHttpInterceptor), proxy = httpProxy)(ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4)))

  implicit lazy val requestInterceptor: RequestInterceptor       = RequestInterceptor.identity

  lazy val tempFiles:           TempFileService                  = wire[TempFileService]
  lazy val videoTranscoder:     VideoTranscoder                  = VideoTranscoder(context)
  lazy val audioTranscoder:     AudioTranscoder                  = wire[AudioTranscoder]

  lazy val cacheCleanup                                          = wire[CacheCleaningService]

  lazy val accountsStorage:     AccountStorage                   = wire[AccountStorageImpl]

  val generalCacheDir = new File(context.getExternalCacheDir, s"general_cache")
  IoUtils.createDirectory(generalCacheDir )

  lazy val generalFileCache =
    new GeneralFileCacheImpl(generalCacheDir)(Threading.Background)

  lazy val teamsStorage:        TeamsStorage                     = wire[TeamsStorageImpl]
  lazy val recordingAndPlayback                                  = wire[GlobalRecordAndPlayService]

  lazy val blacklistClient                                       = new VersionBlacklistClientImpl(backend)(httpClient)
  lazy val blacklist                                             = new VersionBlacklistService(metadata, prefs, blacklistClient)

  lazy val factory                                               = new ZMessagingFactory(this)

  lazy val flowmanager:         FlowManagerService               = wire[DefaultFlowManagerService]
  lazy val mediaManager:        MediaManagerService              = wire[DefaultMediaManagerService]

  lazy val logsService:         LogsService                      = new LogsServiceImpl(prefs)
  lazy val customBackendClient: CustomBackendClient              = new CustomBackendClientImpl()

  lazy val httpProxy:           Option[Proxy]                    = HttpProxy(metadata, defaultProxyDetails).proxy
}

class EmptyGlobalModule extends GlobalModule {
  override def accountsService:          AccountsService                                     = ???
  override def trackingService:          TrackingService                                     = ???
  override def context:                  AContext                                            = ???
  override def backend:                  BackendConfig                                       = ???
  override def ssoService:               SSOService                                          = ???
  override def tokenService:             GlobalTokenServiceImpl                              = ???
  override def notificationsUi:          NotificationUiController                            = ???
  override def calling:                  GlobalCallingService                                = ???
  override def prefs:                    GlobalPreferences                                   = ???
  override def googleApi:                GoogleApi                                           = ???
  override def storage:                  Database                                            = ???
  override def metadata:                 MetaDataService                                     = ???
  override def cache:                    CacheService                                        = ???
  override def bitmapDecoder:            BitmapDecoder                                       = ???
  override def trimmingLruCache:         Cache[MemoryImageCache.Key, MemoryImageCache.Entry] = ???
  override def imageCache:               MemoryImageCache                                    = ???
  override def network:                  DefaultNetworkModeService                           = ???
  override def phoneNumbers:             PhoneNumberService                                  = ???
  override def timeouts:                 Timeouts                                            = ???
  override def permissions:              PermissionsService                                  = ???
  override def avs:                      Avs                                                 = ???
  override def reporting:                GlobalReportingService                              = ???
  override def loginClient:              LoginClient                                         = ???
  override def regClient:                RegistrationClient                                  = ???
  override def videoTranscoder:          VideoTranscoder                                     = ???
  override def audioTranscoder:          AudioTranscoder                                     = ???
  override def cacheCleanup:             CacheCleaningService                                = ???
  override def accountsStorage:          AccountStorage                                      = ???
  override def teamsStorage:             TeamsStorage                                        = ???
  override def recordingAndPlayback:     GlobalRecordAndPlayService                          = ???
  override def tempFiles:                TempFileService                                     = ???
  override def blacklistClient:          VersionBlacklistClient                              = ???
  override def blacklist:                VersionBlacklistService                             = ???
  override def factory:                  ZMessagingFactory                                   = ???
  override def lifecycle:                UiLifeCycle                                         = ???
  override def flowmanager:              FlowManagerService                                  = ???
  override def mediaManager:             MediaManagerService                                 = ???
  override def urlCreator:               UrlCreator                                          = ???
  override def httpClient:               HttpClient                                          = ???
  override def httpClientForLongRunning: HttpClient                                          = ???
  override def syncRequests:             SyncRequestService                                  = ???
  override def syncHandler:              SyncHandler                                         = ???
  override def logsService:              LogsService                                         = ???
  override def customBackendClient:      CustomBackendClient                                 = ???
  override def httpProxy:                Option[Proxy]                                       = ???
  override def fileRestrictionList:      FileRestrictionList                                 = ???
}

