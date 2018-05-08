/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import android.app.{Dialog, Service}
import android.content.res.Resources
import android.content.{Context, ContextWrapper, DialogInterface}
import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v4.app.{Fragment, FragmentActivity, FragmentManager}
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.Preference
import android.view.View.OnClickListener
import android.view.animation.{AlphaAnimation, Animation, AnimationUtils}
import android.view.{LayoutInflater, View, ViewGroup, ViewStub}
import android.widget.TextView
import com.waz.ZLog._
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper.getNextAnimationDuration
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.utils.{ContextUtils, RichView}

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

trait ViewEventContext extends View with EventContext {

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    onContextStart()
  }

  override def onDetachedFromWindow(): Unit = {
    onContextStop()
    super.onDetachedFromWindow()
  }
}

trait ViewHelper extends View with ViewFinder with Injectable with ViewEventContext {
  lazy implicit val wContext = WireContext(getContext)
  lazy implicit val injector = wContext.injector

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int): V = findViewById(id).asInstanceOf[V]

  def inflate(layoutResId: Int, group: ViewGroup = ViewHelper.viewGroup(this), addToParent: Boolean = true)(implicit tag: LogTag = "ViewHelper") =
    ViewHelper.inflate[View](layoutResId, group, addToParent)
}

object ViewHelper {

  def findById[V <: View](parent: View)(id: Int): V =
    parent.findViewById[V](id)

  @SuppressLint(Array("LogNotTimber"))
  def inflate[T <: View](layoutResId: Int, group: ViewGroup, addToParent: Boolean)(implicit logTag: LogTag) =
    try LayoutInflater.from(group.getContext).inflate(layoutResId, group, addToParent).asInstanceOf[T]
    catch {
      case e: Throwable =>
        var cause = e
        while (cause.getCause != null) cause = cause.getCause
        error("inflate failed with root cause:", cause)
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



trait FragmentHelper extends Fragment with OnBackPressedListener with ViewFinder with Injectable with EventContext {

  implicit def currentAndroidContext: Context = getContext
  lazy implicit val injector: Injector = getActivity.asInstanceOf[WireContext].injector
  override implicit def eventContext: EventContext = this

  private var views: List[ViewHolder[_]] = Nil

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = {
    val res = getView.findViewById[V](id)
    if (res != null) res
    else getActivity.findViewById(id).asInstanceOf[V]
  }


  /*
   * This part (the methods onCreateAnimation and the accompanying util method, getNextAnimationDuration) of the Wire
   * software are based heavily off of code posted in this Stack Overflow answer.
   * https://stackoverflow.com/a/23276145/1751834
   *
   * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
   * (http://creativecommons.org/licenses/by-sa/2.5)
   *
   * Contributors on StackOverflow:
   *  - kcoppock (https://stackoverflow.com/users/321697/kcoppock)
   *
   * This is a workaround for the bug where child fragments disappear when the parent is removed (as all children are
   * first removed from the parent) See https://code.google.com/p/android/issues/detail?id=55228. Apply the workaround
   * only if this is a child fragment, and the parent is being removed.
   */
  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    Option(getParentFragment) match {
      case Some(parent: Fragment) if !enter && parent.isRemoving =>
        returning(new AlphaAnimation(1, 1))(_.setDuration(getNextAnimationDuration(parent)))
      case _ if !FragmentHelper.allowAnimations =>
        returning(new Animation() {})(_.setDuration(0))
      case _ =>
        super.onCreateAnimation(transit, enter, nextAnim)
    }

  def withBackstackHead[A](f: Option[Fragment] => A): A = {
    import scala.collection.JavaConverters._
    f(getChildFragmentManager.getFragments.asScala.toList.flatMap(Option(_)).lastOption)
  }

  def withParentFragmentOpt[A](f: Option[Fragment] => A): A =
    f(Option(getParentFragment))

  def withFragmentOpt[A](tag: String)(f: Option[Fragment] => A): A =
    f(Option(getChildFragmentManager.findFragmentByTag(tag)))

  def findFragment(@IdRes id: Int): Option[Fragment] =
    Option(getChildFragmentManager.findFragmentById(id))

  def withFragmentOpt[A](@IdRes id: Int)(f: Option[Fragment] => A): A =
    f(findFragment(id))

  def withFragment(@IdRes id: Int)(f: Fragment => Unit): Unit =
    findFragment(id).foreach(f)

  def findById[V <: View](parent: View, id: Int): V =
    parent.findViewById(id).asInstanceOf[V]

  def view[V <: View](id: Int) = {
    val h = new ViewHolder[V](id, this)
    views ::= h
    h
  }

  def getStringArg(key: String): Option[String] =
    Option(getArguments).flatMap(a => Option(a.getString(key)))

  def getBooleanArg(key: String, default: Boolean = false): Boolean =
    Option(getArguments).map(_.getBoolean(key, default)).getOrElse(default)

  override def onBackPressed(): Boolean = {
    verbose(s"onBackPressed")(getClass.getSimpleName)
    false
  }

  override def onResume() = {
    super.onResume()
    views.foreach(_.onResume())
  }

  override def onPause() = {
    views.foreach(_.onPause())
    super.onPause()
  }

  override def onDestroyView() = {
    views foreach(_.clear())
    super.onDestroyView()
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

object FragmentHelper {

  var allowAnimations = true

  private val DefaultAnimationDuration = 350L //TODO is this value correct?

  def getNextAnimationDuration(fragment: Fragment) =
    try { // Attempt to get the resource ID of the next animation that
      // will be applied to the given fragment.
      val nextAnimField = classOf[Fragment].getDeclaredField("mNextAnim")
      nextAnimField.setAccessible(true)
      val nextAnimResource = nextAnimField.getInt(fragment)
      val nextAnim = AnimationUtils.loadAnimation(fragment.getActivity, nextAnimResource)
      // ...and if it can be loaded, return that animation's duration
      if (nextAnim == null) DefaultAnimationDuration
      else nextAnim.getDuration
    } catch {
      case (_: NoSuchFieldException | _: IllegalAccessException | _: Resources.NotFoundException) =>
        DefaultAnimationDuration
    }
}

trait ManagerFragment extends FragmentHelper {
  def contentId: Int

  import ManagerFragment._

  val currentContent = Signal(Option.empty[Page])

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    getChildFragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener {
      override def onBackStackChanged(): Unit =
        currentContent ! withFragmentOpt(contentId)(_.map(_.getTag)).map(Page(_, getChildFragmentManager.getBackStackEntryCount <= 1))
    })
  }

  def getContentFragment: Option[Fragment] = withContentFragment(identity)

  def withContentFragment[A](f: Option[Fragment] => A): A = withFragmentOpt(contentId)(f)
}

object ManagerFragment {
  case class Page(tag: String, firstPage: Boolean)
}

trait DialogHelper extends Dialog with Injectable with EventContext {
  val context: Context
  lazy implicit val injector = context.asInstanceOf[WireContext].injector
  override implicit def eventContext: EventContext = this

  private var dismissListener = Option.empty[DialogInterface.OnDismissListener]

  super.setOnDismissListener(new DialogInterface.OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = {
      dismissListener.foreach(_.onDismiss(dialogInterface))
      onContextDestroy()
    }
  })

  override def onStart(): Unit = {
    onContextStart()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    onContextStop()
  }

  override def setOnDismissListener(listener: DialogInterface.OnDismissListener): Unit = {
    dismissListener = Some(listener)
  }
}

trait ActivityHelper extends AppCompatActivity with ViewFinder with Injectable with WireContext with EventContext {

  override implicit def eventContext: EventContext = this

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = findViewById(id).asInstanceOf[V]

  def findFragment[T](id: Int) : T = {
    this.asInstanceOf[FragmentActivity].getSupportFragmentManager.findFragmentById(id).asInstanceOf[T]
  }

  def withFragmentOpt[A](tag: String)(f: Option[Fragment] => A): A =
    f(Option(this.asInstanceOf[FragmentActivity].getSupportFragmentManager.findFragmentByTag(tag)))

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

trait CallingBannerActivity extends ActivityHelper {
  lazy val callController = inject[CallController]

  lazy val callBanner = returning(findById[View](R.id.call_banner)) { v =>
    v.onClick(CallingActivity.startIfCallIsActive(this))
  }
  lazy val callBannerStatus   = findById[TextView](R.id.call_banner_status)
  lazy val callBannerDuration = findById[TextView](R.id.call_banner_duration)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    callController.isCallActiveDelay.onUi { est =>
      getWindow.setStatusBarColor(ContextUtils.getColor(if (est) R.color.accent_green else android.R.color.transparent)(this))
      callBanner.setVisibility(if (est) View.VISIBLE else View.GONE)
    }

    callController.callBannerText.onUi(callBannerStatus.setText)
    callController.duration.onUi(callBannerDuration.setText)
  }
}

class ViewHolder[T <: View](id: Int, finder: ViewFinder) {
  private var view = Option.empty[T]
  private var onClickListener = Option.empty[OnClickListener]

  def get: T = view.getOrElse { returning(finder.findById[T](id)) { t => view = Some(t) } }

  @inline
  def opt: Option[T] = Option(get)

  def clear() =
    view = Option.empty

  def fold[B](ifEmpty: => B)(f: T => B): B = opt.fold(ifEmpty)(f)

  def foreach(f: T => Unit): Unit = opt.foreach(f)

  def map[A](f: T => A): Option[A] = opt.map(f)

  def flatMap[A](f: T => Option[A]): Option[A] = opt.flatMap(f)

  def filter(p: T => Boolean): Option[T] = opt.filter(p)

  def onResume() = onClickListener.foreach(l => foreach(_.setOnClickListener(l)))

  def onPause() = onClickListener.foreach(_ => foreach(_.setOnClickListener(null)))

  def onClick(f: T => Unit): Unit = {
    onClickListener = Some(returning(new OnClickListener {
      override def onClick(v: View) = f(v.asInstanceOf[T])
    })(l => view.foreach(_.setOnClickListener(l))))
  }
}

trait PreferenceHelper extends Preference with Injectable with EventContext {
  lazy implicit val wContext = WireContext(getContext)
  lazy implicit val injector = wContext.injector

  override def onAttached(): Unit = {
    super.onAttached()
    onContextStart()
  }

  override def onDetached(): Unit = {
    onContextStop()
    super.onDetached()
  }
}
