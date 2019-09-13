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
package org.robolectric.internal

import java.lang.reflect.Method

import android.app.Application
import android.content.Context
import android.content.pm.{ApplicationInfo, PackageManager}
import android.content.res.{Configuration, Resources}
import com.waz.RoboProcessRunner
import org.robolectric.Robolectric._
import org.robolectric._
import org.robolectric.annotation.Config
import org.robolectric.res.builder.RobolectricPackageManager
import org.robolectric.res.{ResBunch, ResourceLoader}
import org.robolectric.shadows.{ShadowActivityThread, ShadowContextImpl, ShadowLog, ShadowResources}

/**
  */
class RoboProcessUniverse(roboSuiteRunner: RoboProcessRunner) extends ParallelUniverseInterface {
  private final val DEFAULT_PACKAGE_NAME: String = "org.robolectric.default"
  private var loggingInitialized: Boolean = false
  private var sdkConfig: SdkConfig = null

  def resetStaticState(config: Config) = {
    Robolectric.reset(config)
    if (!loggingInitialized) {
      ShadowLog.setupLogging()
      loggingInitialized = true
    }
  }

  /*
   * If the Config already has a version qualifier, do nothing. Otherwise, add a version
   * qualifier for the target api level (which comes from the manifest or Config.emulateSdk()).
   */
  private def addVersionQualifierToQualifiers(qualifiers: String): String =
    ResBunch.getVersionQualifierApiLevel(qualifiers) match {
      case -1 if qualifiers.length > 0 => qualifiers + "-v" + sdkConfig.getApiLevel
      case -1 => qualifiers + "v" + sdkConfig.getApiLevel
      case _ => qualifiers
    }

  def setUpApplicationState(method: Method, testLifecycle: TestLifecycle[_], strictI18n: Boolean, systemResourceLoader: ResourceLoader, appManifest: AndroidManifest, config: Config) = {
    Robolectric.application = null
    Robolectric.packageManager = new RobolectricPackageManager
    Robolectric.packageManager.addPackage(DEFAULT_PACKAGE_NAME)
    val resourceLoader = roboSuiteRunner.resourceLoader
    if (appManifest != null) {
      Robolectric.packageManager.addManifest(appManifest, resourceLoader)
    }
    ShadowResources.setSystemResources(systemResourceLoader)
    val qualifiers: String = addVersionQualifierToQualifiers(config.qualifiers)
    val systemResources: Resources = Resources.getSystem
    val configuration: Configuration = systemResources.getConfiguration
    shadowOf(configuration).overrideQualifiers(qualifiers)
    systemResources.updateConfiguration(configuration, systemResources.getDisplayMetrics)
    shadowOf(systemResources.getAssets).setQualifiers(qualifiers)
    val contextImplClass: Class[_] = ReflectionHelpers.loadClassReflectively(getClass.getClassLoader, ShadowContextImpl.CLASS_NAME)
    val activityThreadClass: Class[_] = ReflectionHelpers.loadClassReflectively(getClass.getClassLoader, ShadowActivityThread.CLASS_NAME)
    val activityThread: AnyRef = ReflectionHelpers.callConstructorReflectively(activityThreadClass)
    Robolectric.activityThread = activityThread
    ReflectionHelpers.setFieldReflectively(activityThread, "mInstrumentation", new RoboInstrumentation)
    ReflectionHelpers.setFieldReflectively(activityThread, "mCompatConfiguration", configuration)
    val systemContextImpl: Context = ReflectionHelpers.callStaticMethodReflectively(contextImplClass, "createSystemContext", new ReflectionHelpers.ClassParameter(activityThreadClass, activityThread))
    val application: Application = testLifecycle.createApplication(method, appManifest, config).asInstanceOf[Application]
    if (application != null) {
      var packageName: String = if (appManifest != null) appManifest.getPackageName else null
      if (packageName == null) packageName = DEFAULT_PACKAGE_NAME
      var applicationInfo: ApplicationInfo = null
      try {
        applicationInfo = Robolectric.packageManager.getApplicationInfo(packageName, 0)
      }
      catch {
        case e: PackageManager.NameNotFoundException => {
          throw new RuntimeException(e)
        }
      }
      val compatibilityInfoClass: Class[_] = ReflectionHelpers.loadClassReflectively(getClass.getClassLoader, "android.content.res.CompatibilityInfo")
      val loadedApk: AnyRef = ReflectionHelpers.callInstanceMethodReflectively(activityThread, "getPackageInfo", new ReflectionHelpers.ClassParameter(classOf[ApplicationInfo], applicationInfo), new ReflectionHelpers.ClassParameter(compatibilityInfoClass, null), new ReflectionHelpers.ClassParameter(classOf[ClassLoader], getClass.getClassLoader), new ReflectionHelpers.ClassParameter(classOf[Boolean], false), new ReflectionHelpers.ClassParameter(classOf[Boolean], true))
      shadowOf(application).bind(appManifest, resourceLoader)
      if (appManifest == null) {
        shadowOf(application).setPackageName(applicationInfo.packageName)
      }
      val appResources: Resources = application.getResources
      ReflectionHelpers.setFieldReflectively(loadedApk, "mResources", appResources)
      val contextImpl: Context = ReflectionHelpers.callInstanceMethodReflectively(systemContextImpl, "createPackageContext", new ReflectionHelpers.ClassParameter(classOf[String], applicationInfo.packageName), new ReflectionHelpers.ClassParameter(classOf[Int], Context.CONTEXT_INCLUDE_CODE))
      ReflectionHelpers.setFieldReflectively(activityThread, "mInitialApplication", application)
      ReflectionHelpers.callInstanceMethodReflectively(application, "attach", new ReflectionHelpers.ClassParameter(classOf[Context], contextImpl))
      appResources.updateConfiguration(configuration, appResources.getDisplayMetrics)
      shadowOf(appResources.getAssets).setQualifiers(qualifiers)
      shadowOf(application).setStrictI18n(strictI18n)
      Robolectric.application = application
      application.onCreate()
    }
  }

  def tearDownApplication(): Unit = {
    if (Robolectric.application != null) {
      Robolectric.application.onTerminate()
    }
  }

  def getCurrentApplication = Robolectric.application

  def setSdkConfig(sdkConfig: SdkConfig): Unit = {
    this.sdkConfig = sdkConfig
  }
}
