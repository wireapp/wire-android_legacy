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

import java.security.{Permission, PermissionCollection}
import java.util.Date

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Parcel
import android.provider.ContactsContract
import android.provider.ContactsContract.DisplayNameSources
import com.google.protobuf.nano.MessageNano
import com.waz.api.impl._
import com.waz.api.{CoreList, IConversation}
import com.waz.cache.CacheEntryData
import com.waz.content.MsgCursor
import com.waz.model.GenericContent.Text
import com.waz.model.{otr => _, _}
import com.waz.service.ContactsServiceImpl
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, FlatMapSignal, Signal, Subscription}
import com.waz.utils.{CachedStorageImpl, Cleanup, Managed, returning}
import libcore.net.MimeUtils
import org.robolectric.shadows.ShadowContentResolver2
import org.scalactic.Equality
import org.scalatest.enablers.{Emptiness, Length}

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.Random

package object testutils {

  private implicit lazy val ec = Threading.Background

  object Implicits {

    implicit lazy val CursorEmptiness: Emptiness[Cursor] = new Emptiness[Cursor] {
      override def isEmpty(thing: Cursor): Boolean = thing.getCount == 0
    }

    implicit lazy val UiSignalEmptiness: Emptiness[api.UiSignal[_]] = new Emptiness[api.UiSignal[_]] {
      override def isEmpty(thing: api.UiSignal[_]): Boolean = thing.isEmpty
    }

    implicit lazy val CoreListLength = new Length[CoreList[_]] {
      override def lengthOf(obj: CoreList[_]): Long = obj.size()
    }

    implicit lazy val CoreListEmptiness: Emptiness[CoreList[_]] = new Emptiness[CoreList[_]] {
      override def isEmpty(thing: CoreList[_]): Boolean = thing.size() == 0
    }

    implicit class MessagesCursorSeq(list: MsgCursor) extends Seq[MessageAndLikes] {
      override def length: Int = list.size
      override def apply(idx: Int): MessageAndLikes = list(idx)
      override def iterator: Iterator[MessageAndLikes] = new Iterator[MessageAndLikes]() {
        var idx = 0
        override def hasNext: Boolean = idx < list.size
        override def next(): MessageAndLikes = {
          idx += 1
          list(idx - 1)
        }
      }
    }

    implicit class EnrichedInt(val a: Int) extends AnyVal {
      def times(f: => Unit): Unit = (1 to a) foreach (_ => f)
    }

    implicit class CoreListAsScala[A](list: CoreList[A]) {
      def asScala: Vector[A] = (0 until list.size).map(list.get)(breakOut)
    }

    implicit class RichCoreListSeq[T](list: CoreList[T]) extends Seq[T] {
      override def length: Int = list.size
      override def apply(idx: Int): T = list.get(idx)
      override def iterator: Iterator[T] = new Iterator[T]() {
        var idx = 0
        override def hasNext: Boolean = idx < list.size
        override def next(): T = {
          idx += 1
          list.get(idx - 1)
        }
      }
    }

    implicit class UiSignalIsSignal[A](val s: api.UiSignal[A]) extends AnyVal {
      def signal : Signal[A] = new Signal[A] with api.Subscriber[A] {
        var sub = Option.empty[api.Subscription]

        override def next(value: A): Unit = set(Some(value), Some(Threading.Ui))

        override def onWire(): Unit = sub = Some(s.subscribe(this))

        override def onUnwire(): Unit = {
          sub.foreach(_.cancel())
          sub = None
        }
      }
    }

    implicit class UiObservableCanBeSignal[A <: api.UiObservable](val a: A) extends AnyVal {
      def signal[B](f: A => B): Signal[B] = new Signal[B] with api.UpdateListener {

        override def updated(): Unit = set(Some(f(a)), Some(Threading.Ui))

        override def onWire(): Unit = {
          a.addUpdateListener(this)
          updated()
        }

        override def onUnwire(): Unit = a.removeUpdateListener(this)
      }

      def flatSignal[B](f: A => Signal[B]): Signal[B] = new FlatMapSignal(signal(identity), f)
    }

    implicit class SignalToSink[A](val signal: Signal[A]) extends AnyVal {
      def sink: SignalSink[A] = returning(new SignalSink[A])(_.subscribe(signal)(EventContext.Global))
    }

    class SignalSink[A] {
      @volatile private var sub = Option.empty[Subscription]
      def subscribe(s: Signal[A])(implicit ctx: EventContext): Unit = sub = Some(s(v => value = Some(v)))
      def unsubscribe: Unit = sub.foreach { s =>
        s.destroy()
        sub = None
      }
      @volatile private[testutils] var value = Option.empty[A]
      def current: Option[A] = value
    }

    implicit object GenericMessageEquality extends Equality[GenericMessage] {
      override def areEqual(a: GenericMessage, b: Any): Boolean = {
        b match {
          case m: MessageNano => MessageNano.toByteArray(m).toSeq == MessageNano.toByteArray(a).toSeq
          case _ => false
        }
      }
    }

    implicit object MessageDataEquality extends Equality[MessageData] {
      override def areEqual(a: MessageData, b: Any): Boolean = {
        b match {
          case m: MessageData =>
            if (m.copy(protos = Nil) != a.copy(protos = Nil)) println(s"message content differ: \n$a\n$m\n")
            m.copy(protos = Nil) == a.copy(protos = Nil) && m.protos.size == a.protos.size && m.protos.zip(a.protos).forall { case (p1, p2) => GenericMessageEquality.areEqual(p1, p2) }
          case _ => false
        }
      }
    }
  }

  def withUpdate[T](signal: Signal[_])(body: => T)(implicit timeout: FiniteDuration = 5.seconds): T = {
    import com.waz.utils.events.EventContext.Implicits.global
    @volatile var updateCount = 0
    signal { _ => updateCount += 1 }
    RobolectricUtils.awaitUi(updateCount > 0)
    updateCount = 0

    val result = body

    RobolectricUtils.awaitUi(updateCount > 0, "Signal was not updated")

    result
  }

  lazy val knownMimeTypes = returning(classOf[MimeUtils].getDeclaredField("mimeTypeToExtensionMap"))(_.setAccessible(true)).get(null).asInstanceOf[java.util.Map[String, String]].asScala.keys.toVector.map(Mime(_))

  import ContactsServiceImpl.Col

  def prepareAddressBookEntries(emailAddresses: Seq[(String, String)], phoneNumbers: Seq[(String, String)])(implicit context: Context): Unit = {
    ShadowContentResolver2.reset()
    ShadowContentResolver2.registerProvider(ContactsContract.AUTHORITY, new TestContentProvider {
      override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor = uri match {
        case ContactsServiceImpl.Emails => cursor(Vector(Col.ContactId, Col.EmailAddress), emailAddresses map { case (a, b) => Seq(a, b) })
        case ContactsServiceImpl.Phones => cursor(Vector(Col.ContactId, Col.PhoneNumber), phoneNumbers map { case (a, b) => Seq(a, b) })
        case _ => null
      }
    })
    context.getContentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)
  }

  def prepareContacts(contacts: Contact*)(implicit context: Context): Unit = {
    ShadowContentResolver2.reset()
    val emailAddresses = contacts flatMap (c => c.emailAddresses map (e => Seq(c.id.str, e.str)))
    val phoneNumbers = contacts flatMap (c => c.phoneNumbers map (p => Seq(c.id.str, p.str)))

    ShadowContentResolver2.registerProvider(ContactsContract.AUTHORITY, new TestContentProvider {
      override def query(uri: Uri, projection: Array[String], selection: String, selectionArgs: Array[String], sortOrder: String): Cursor = {
        def checking(cols: String*) = returning(cols.toVector)(_ => require(projection.toVector == cols, s"projections should match; expected ${cols.toVector.mkString(", ")} but received ${projection.toVector.mkString(", ")}"))
        uri match {
          case ContactsServiceImpl.Emails => cursor(checking(Col.ContactId, Col.EmailAddress), emailAddresses)
          case ContactsServiceImpl.Phones => cursor(checking(Col.ContactId, Col.PhoneNumber), phoneNumbers)
          case ContactsServiceImpl.Contacts =>
            cursor(checking(Col.RowId, Col.Name, Col.NameSource, Col.SortKeyPrimary), contacts.sortBy(_.sortKey).map(c => Seq(c.id.str, c.name, src(c), c.sortKey)))
        }
      }
    })

    context.getContentResolver.notifyChange(ContactsContract.Contacts.CONTENT_URI, null)
  }

  private def src(c: Contact) = (c.nameSource match {
    case NameSource.StructuredName => DisplayNameSources.STRUCTURED_NAME
    case NameSource.Nickname => DisplayNameSources.NICKNAME
    case _ => DisplayNameSources.UNDEFINED
  }).toString

  def withParcel[A](f: Parcel => A): A = Managed(Parcel.obtain).acquire(f)
  implicit lazy val ParcelCleanup: Cleanup[Parcel] = new Cleanup[Parcel] { def apply(a: Parcel): Unit = a.recycle() }

  implicit object CacheEntryEquality extends Equality[CacheEntryData] {
    override def areEqual(a: CacheEntryData, b: Any): Boolean = {
      b match {
        case CacheEntryData(k, d, lu, t, p, e, f, m, i, l) =>
          k == a.key && lu == a.lastUsed && t == a.timeout && p == a.path && i == a.fileId && d.map(_.toSeq) == a.data.map(_.toSeq) && m == a.mimeType && f == a.fileName && l == a.length
        case _ => false
      }
    }
  }

  implicit object CacheEntryOptionEquality extends Equality[Option[CacheEntryData]] {
    override def areEqual(a: Option[CacheEntryData], b: Any): Boolean = (a, b) match {
      case (None, None) => true
      case (Some(entry), Some(b)) => CacheEntryEquality.areEqual(entry, b)
      case _ => false
    }
  }


  implicit class RichStorage[K, V](storage: CachedStorageImpl[K, V]) {
    def deleteAll() = storage.list() flatMap { vs =>
      storage.removeAll(vs.map(storage.dao.idExtractor))
    }
  }

  def randomPhoneNumber = PhoneNumber("+0" + (Random.nextInt(9) + 1).toString + Array.fill(13)(Random.nextInt(10)).mkString)
}

object JCE {

  def removeCryptographyRestrictions() =
    try {
      /*
       * JceSecurity.isRestricted = false;
       * JceSecurity.defaultPolicy.perms.clear();
       * JceSecurity.defaultPolicy.add(CryptoAllPermission.INSTANCE);
       */
      val jceSecurity = Class.forName("javax.crypto.JceSecurity")
      val cryptoPermissions = Class.forName("javax.crypto.CryptoPermissions")
      val cryptoAllPermission = Class.forName("javax.crypto.CryptoAllPermission")

      val isRestrictedField = jceSecurity.getDeclaredField("isRestricted")
      if (java.lang.reflect.Modifier.isFinal(isRestrictedField.getModifiers)) {
        val modifiers = Class.forName("java.lang.reflect.Field").getDeclaredField("modifiers")
        modifiers.setAccessible(true)
        modifiers.setInt(isRestrictedField, isRestrictedField.getModifiers & ~java.lang.reflect.Modifier.FINAL)
      }
      isRestrictedField.setAccessible(true)
      isRestrictedField.set(null, false)

      val defaultPolicyField = jceSecurity.getDeclaredField("defaultPolicy")
      defaultPolicyField.setAccessible(true)
      val defaultPolicy = defaultPolicyField.get(null).asInstanceOf[PermissionCollection]

      val perms = cryptoPermissions.getDeclaredField("perms")
      perms.setAccessible(true)
      perms.get(defaultPolicy).asInstanceOf[java.util.Map[_,_]].clear()

      val instance = cryptoAllPermission.getDeclaredField("INSTANCE")
      instance.setAccessible(true)
      defaultPolicy.add(instance.get(null).asInstanceOf[Permission])
    } catch {
      case e: ClassNotFoundException =>
        println(s"Unable to enable unlimited-strength crypto: $e")
        e.printStackTrace()
      case e: Exception =>
        println("Failed to remove cryptography restrictions")
        e.printStackTrace()
    }
}
