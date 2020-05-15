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
package com.waz

import java.io._
import java.lang.reflect.{Field, Modifier}
import java.net.URL
import java.util.Properties

import android.os.Build
import org.robolectric._
import org.robolectric.annotation.Config
import org.robolectric.annotation.Config.Implementation
import org.robolectric.bytecode._
import org.robolectric.internal.{ParallelUniverseInterface, ReflectionHelpers, RoboProcessUniverse}
import org.robolectric.res._
import org.robolectric.shadows._
import org.robolectric.util.AnnotationUtil
import org.scalatest.{Informer, Informing, RoboSuiteRunner}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import scala.sys.process._
import scala.util.Try
import org.robolectric.annotation.{Config => RoboConfig}
import org.scalactic.source

trait RoboProcess extends RobolectricUtils with Informing {

  override protected def info: Informer = new Informer {
    override def apply(message: String, payload: Option[Any])(implicit pos: source.Position): Unit = println(message)
  }
  def run(args: Seq[String]): Unit = {}
}

object RoboProcess {
  def apply[T <: RoboProcess : ClassTag](args: String*) = {
    val cls = implicitly[ClassTag[T]].runtimeClass

    val cmd = Seq(
      "java",
      //System properties
      s"-Djava.library.path=${System.getProperty("java.library.path")}",
      s"-Drobolectric.logging.enabled=true",
      //Classpath
      "-cp", System.getProperty("java.class.path"),
      //targetClass
      classOf[RoboProcessRunner].getName,
      //Arguments
      cls.getName) ++: args

    val outputFile = new File(s"target/logcat/${Option(args(0)).getOrElse("unnamed_file")}.alog")
    outputFile.getParentFile.mkdirs()
    val printWriter = new PrintWriter(outputFile)

    val writeToStream: String => Unit =
      line => {
        printWriter.println(line)
        printWriter.flush()
      }

    val processLogger = ProcessLogger(writeToStream, writeToStream)

    new Thread() {
      override def run(): Unit = Process(cmd, None, "LD_LIBRARY_PATH" -> System.getProperty("java.library.path")) ! processLogger
    }.start()
  }
}

object RoboProcessRunner extends App {
  Setup.CLASSES_TO_ALWAYS_DELEGATE.add(classOf[RoboProcessRunner].getName)
  val className = args(0)
  new RoboProcessRunner(Class.forName(className)).run(args)
}

class RoboProcessRunner(suiteClass: Class[_], providedConfig: Option[Config] = None) {
  runner =>
  val shouldAcquire: String => Option[Boolean] = _ => None
  val shadows: Seq[Class[_]] = Seq(classOf[ShadowApplication],
    classOf[ShadowAudioManager2], classOf[ShadowLooper2], classOf[ShadowSQLiteConnection2], classOf[ShadowGeocoder2],
    classOf[ShadowFileProvider], classOf[ShadowContentResolver2], classOf[ShadowMediaMetadataRetriever2])

  val configProperties: Properties =
    Option(suiteClass.getClassLoader.getResourceAsStream("org.robolectric.Config.properties")).map { resourceAsStream =>
      val properties = new Properties
      properties.load(resourceAsStream)
      properties
    }.orNull

  val config: Config = providedConfig.getOrElse {
    Seq(AnnotationUtil.defaultsFor(classOf[Config]),
      RoboConfig.Implementation.fromProperties(configProperties),
      suiteClass.getAnnotation(classOf[Config])
    ) reduceLeft { (config, opt) =>
      Option(opt).fold(config)(new Implementation(config, _))
    }
  }

  val appManifest: AndroidManifest = {

    def libraryDirs(baseDir: FsFile) = config.libraries().map(baseDir.join(_)).toSeq

    def createAppManifest(manifestFile: FsFile, resDir: FsFile, assetsDir: FsFile): AndroidManifest = {
      if (manifestFile.exists) {
        val manifest = new AndroidManifest(manifestFile, resDir, assetsDir)
        manifest.setPackageName(System.getProperty("android.package"))
        if (config.libraries().nonEmpty) {
          manifest.setLibraryDirectories(libraryDirs(manifestFile.getParent).asJava)
        }
        manifest
      } else {
        System.out.print("WARNING: No manifest file found at " + manifestFile.getPath + ".")
        System.out.println("Falling back to the Android OS resources only.")
        System.out.println("To remove this warning, annotate your test class with @Config(manifest=Config.NONE).")
        null
      }
    }

    if (config.manifest == RoboConfig.NONE) null
    else {
      val manifestProperty = System.getProperty("android.manifest")
      val resourcesProperty = System.getProperty("android.resources")
      val assetsProperty = System.getProperty("android.assets")
      val defaultManifest = config.manifest == RoboConfig.DEFAULT
      if (defaultManifest && manifestProperty != null) {
        createAppManifest(Fs.fileFromPath(manifestProperty), Fs.fileFromPath(resourcesProperty), Fs.fileFromPath(assetsProperty))
      } else {
        val manifestFile =
          if (config.manifest().startsWith("/")) Fs.fileFromPath(config.manifest)
          else Fs.currentDirectory().join(if (defaultManifest) AndroidManifest.DEFAULT_MANIFEST_NAME else config.manifest)
        val baseDir = manifestFile.getParent
        createAppManifest(manifestFile, baseDir.join(config.resourceDir), baseDir.join(AndroidManifest.DEFAULT_ASSETS_FOLDER))
      }
    }
  }

  val sdkVersion = {
    if (config.reportSdk != -1) config.reportSdk
    else Build.VERSION_CODES.KITKAT
  }

  val sdkConfig = new SdkConfig(sdkVersion)
  val shadowMap = {
    val builder = new ShadowMap.Builder()
    shadows foreach builder.addShadowClass
    builder.build()
  }

  val jarResolver =
    if (System.getProperty("robolectric.offline") != "true") new MavenDependencyResolver
    else new LocalDependencyResolver(new File(System.getProperty("robolectric.dependency.dir", ".")))

  val classHandler = new ShadowWrangler(shadowMap, sdkConfig)
  val classLoader = {
    val setup = new Setup() {
      override def shouldAcquire(name: String): Boolean = {
        name != classOf[RoboSuiteRunner].getName &&
          !name.startsWith("org.scala") &&
          runner.shouldAcquire(name).getOrElse(super.shouldAcquire(name))
      }
    }
    val urls = sdkConfig.getSdkClasspathDependencies.toSeq.flatMap { dep => Try(jarResolver.getLocalArtifactUrls(dep)).getOrElse {
      println(s"load artifact failed for: ${dep.getArtifactId}:${dep.getGroupId}:${dep.getVersion}")
      Array.empty[URL]
    }.toSeq }
    new AsmInstrumentingClassLoader(setup, urls: _*)
  }

  val sdkEnvironment = {
    val env = new SdkEnvironment(sdkConfig, classLoader)
    env.setCurrentClassHandler(classHandler)

    val className = classOf[RobolectricInternals].getName
    val robolectricInternalsClass = ReflectionHelpers.loadClassReflectively(classLoader, className)
    ReflectionHelpers.setStaticFieldReflectively(robolectricInternalsClass, "classHandler", classHandler)

    val versionClass = env.bootstrappedClass(classOf[Build.VERSION])
    val sdk_int = versionClass.getDeclaredField("SDK_INT")
    sdk_int.setAccessible(true)
    val modifiers = classOf[Field].getDeclaredField("modifiers")
    modifiers.setAccessible(true)
    modifiers.setInt(sdk_int, sdk_int.getModifiers & ~Modifier.FINAL)
//    sdk_int.setInt(null, sdkVersion)

    env
  }

  val systemResourceLoader = sdkEnvironment.getSystemResourceLoader(jarResolver, null)

  val appResourceLoader = Option(appManifest) map { manifest =>
    val appAndLibraryResourceLoaders = manifest.getIncludedResourcePaths.asScala.map(new PackageResourceLoader(_))
    val overlayResourceLoader = new OverlayResourceLoader(manifest.getPackageName, appAndLibraryResourceLoaders.asJava)
    val resourceLoaders = Map(
      "android" -> systemResourceLoader,
      appManifest.getPackageName -> overlayResourceLoader
    )
    new RoutingResourceLoader(resourceLoaders.asJava)
  }

  val resourceLoader = appResourceLoader.getOrElse(systemResourceLoader)

  val parallelUniverse = {
    val universe = classLoader.loadClass(classOf[RoboProcessUniverse].getName)
      .asSubclass(classOf[ParallelUniverseInterface])
      .getConstructor(classOf[RoboProcessRunner])
      .newInstance(this)
    universe.setSdkConfig(sdkConfig)
    universe
  }

  def run(args: Seq[String]): Unit = {
    Thread.currentThread.setContextClassLoader(sdkEnvironment.getRobolectricClassLoader)

    parallelUniverse.resetStaticState(config)
    val testLifecycle = classLoader.loadClass(classOf[DefaultTestLifecycle].getName).newInstance.asInstanceOf[TestLifecycle[_]]
    val strictI18n = Option(System.getProperty("robolectric.strictI18n")).exists(_.toBoolean)
    parallelUniverse.setUpApplicationState(null, testLifecycle, strictI18n, systemResourceLoader, appManifest, config)

    try {
      Try(resourceLoader.getRawValue(null)) // force resources loading
      val shadowProcess = classLoader.loadClass(suiteClass.getName).newInstance
      shadowProcess.getClass.getMethod("run", classOf[Seq[String]])
        .invoke(shadowProcess, args)
    } finally {
      parallelUniverse.tearDownApplication()
      parallelUniverse.resetStaticState(config)
      Thread.currentThread.setContextClassLoader(classOf[RoboProcess].getClassLoader)
    }
  }
}
