package com.waz.services.fcm

import com.waz.content.Preferences.{PrefKey, Preference}
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.wire.signals.{CancellableFuture, DispatchQueue, SerialDispatchQueue}

import scala.concurrent.Future

// A copy of com.waz.testutils.{TestGlobalPreferences, TestUserPreferences}
// Due to the project structure problems I can't access them from here
class TestGlobalPreferences extends GlobalPreferences(null, null) {
  override implicit val dispatcher: DispatchQueue = SerialDispatchQueue(name = "TestGlobalPreferenceQueue")

  private var values = Map.empty[String, String]

  override def buildPreference[A: PrefCodec](key: PrefKey[A]): Preference[A] =
    Preference[A](this, key)

  override protected def getValue[A: PrefCodec](key: PrefKey[A]): Future[A] =
    dispatcher(values.get(key.str).map(implicitly[PrefCodec[A]].decode).getOrElse(key.default))

  override def setValue[A: PrefCodec](key: PrefKey[A], value: A): Future[Unit] =
    dispatcher(values += (key.str -> implicitly[PrefCodec[A]].encode(value)))

  def print(): CancellableFuture[Unit] = dispatcher(println(values))
}

class TestUserPreferences extends UserPreferences(null, null) {
  override implicit val dispatcher: DispatchQueue = SerialDispatchQueue(name = "TestUserPreferenceQueue")

  private var valuesMap = Map.empty[String, String]

  override protected def getValue[A: PrefCodec](key: PrefKey[A]): Future[A] =
    dispatcher(valuesMap.get(key.str).map(implicitly[PrefCodec[A]].decode).getOrElse(key.default))

  override def setValue[A: PrefCodec](key: PrefKey[A], value: A): Future[Unit] =
    dispatcher(valuesMap += (key.str -> implicitly[PrefCodec[A]].encode(value)))

  def print(): CancellableFuture[Unit] = dispatcher(println(valuesMap))
}
