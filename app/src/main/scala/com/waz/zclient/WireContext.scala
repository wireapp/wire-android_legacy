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
package com.waz.zclient

import android.annotation.SuppressLint
import android.app.{Activity, Service}
import android.content.{Context, ContextWrapper}
import android.support.v4.app.{Fragment, FragmentActivity}
import android.view.{LayoutInflater, View, ViewGroup, ViewStub}
import com.waz.ZLog
import com.waz.ZLog._
import com.waz.threading.CancellableFuture
import com.waz.utils.events._
import com.waz.utils.returning

import scala.language.implicitConversions

object WireContext {
  private implicit val tag: LogTag = logTagFor[WireContext]

  implicit def apply(context: Context): WireContext = context match {
    case ctx: WireContext => ctx
    case wrapper: ContextWrapper => apply(wrapper.getBaseContext)
    case _ => throw new IllegalArgumentException("Expecting WireContext, got: " + context)
  }
}

trait WireContext extends Context {

  def eventContext: EventContext

  implicit lazy val injector: Injector = {
    WireApplication.APP_INSTANCE.contextModule(this) :: getApplicationContext.asInstanceOf[WireApplication].module
  }
}

trait ViewFinder {
  def findById[V <: View](id: Int) : V
  def stub[V <: View](id: Int) : V = findById[ViewStub](id).inflate().asInstanceOf[V]
}

trait DelayedViewEventContext extends View with EventContext {
  import com.waz.ZLog.ImplicitTag._
  import com.waz.threading.Threading.Implicits.Ui

  import scala.concurrent.duration._

  private var attached = false
  private var stopFuture = CancellableFuture.cancelled[Any]()

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()

    attached = true
    stopFuture.cancel()
    onContextStart()
  }

  override def onDetachedFromWindow(): Unit = {
    super.onDetachedFromWindow()

    attached = false
    stopFuture = CancellableFuture.delayed(1.second) {
      if (!attached) onContextStop()
    }
  }
}

trait ViewHelper extends View with ViewFinder with Injectable with DelayedViewEventContext {
  lazy implicit val wContext = WireContext(getContext)
  lazy implicit val injector = wContext.injector

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int): V = findViewById(id).asInstanceOf[V]

  def inflate(layoutResId: Int, group: ViewGroup = ViewHelper.viewGroup(this), addToParent: Boolean = true)(implicit tag: LogTag = "ViewHelper") =
    ViewHelper.inflate[View](layoutResId, group, addToParent)
}

object ViewHelper {

  @SuppressLint(Array("LogNotTimber"))
  def inflate[T <: View](layoutResId: Int, group: ViewGroup, addToParent: Boolean)(implicit logTag: ZLog.LogTag) =
    try LayoutInflater.from(group.getContext).inflate(layoutResId, group, addToParent).asInstanceOf[T]
    catch {
      case e: Throwable =>
        var cause = e
        while (cause.getCause != null) cause = cause.getCause
        ZLog.error("inflate failed with root cause:", cause)
        throw e
    }

  def viewGroup(view: View) = view match {
    case vg: ViewGroup => vg
    case _ => view.getParent.asInstanceOf[ViewGroup]
  }
}

trait ServiceHelper extends Service with Injectable with WireContext with EventContext {

  override implicit def eventContext: EventContext = this

  override def onCreate(): Unit = {
    onContextStart()
    super.onCreate()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    onContextStop()
    onContextDestroy()
  }
}

trait FragmentHelper extends Fragment with ViewFinder with Injectable with EventContext {

  lazy implicit val injector = getActivity.asInstanceOf[WireContext].injector
  override implicit def eventContext: EventContext = this

  implicit def holder_to_view[T <: View](h: ViewHolder[T]): T = h.get
  private var views: List[ViewHolder[_]] = Nil

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = {
    val res = getView.findViewById(id)
    if (res != null) res.asInstanceOf[V]
    else getActivity.findViewById(id).asInstanceOf[V]
  }
  def view[V <: View](id: Int) = {
    val h = new ViewHolder[V](id, this)
    views ::= h
    h
  }

  override def onDestroyView() = {
    super.onDestroyView()
    views foreach(_.clear())
  }

  override def onStart(): Unit = {
    onContextStart()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    onContextStop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    onContextDestroy()
  }
}

trait ActivityHelper extends Activity with ViewFinder with Injectable with WireContext with EventContext {

  override implicit def eventContext: EventContext = this

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = findViewById(id).asInstanceOf[V]

  def findFragment[T](id: Int) : T = {
    this.asInstanceOf[FragmentActivity].getSupportFragmentManager.findFragmentById(id).asInstanceOf[T]
  }

  override def onStart(): Unit = {
    onContextStart()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    onContextStop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    onContextDestroy()
  }
}

class ViewHolder[T <: View](id: Int, finder: ViewFinder) {
  var view = Option.empty[T]

  def get: T = view.getOrElse { returning(finder.findById(id)) { t => view = Some(t) } }

  def clear() = view = Option.empty
}
