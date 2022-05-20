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
import android.content.{ActivityNotFoundException, Context, ContextWrapper, DialogInterface, Intent}
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.animation.{AlphaAnimation, Animation, AnimationUtils}
import android.view.{LayoutInflater, View, ViewGroup, ViewStub}
import android.widget.{TextView, Toast}
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.{Fragment, FragmentActivity, FragmentManager}
import androidx.preference.Preference
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.wire.signals._
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper.getNextAnimationDuration
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.{ContextUtils, RichView}
import com.waz.threading.Threading._

import scala.language.implicitConversions

object WireContext extends DerivedLogTag {

  implicit def apply(context: Context): WireContext = context match {
    case ctx: WireContext => ctx
    case wrapper: ContextWrapper => apply(wrapper.getBaseContext)
    case _ => throw new IllegalArgumentException("Expecting WireContext, got: " + context)
  }
}

trait WireContext extends Context {

  def eventContext: EventContext

  private lazy val _injector =
    WireApplication.APP_INSTANCE.contextModule(this) :: getApplicationContext.asInstanceOf[WireApplication].module

  implicit def injector: Injector = _injector
}

trait ViewFinder {
  def findById[V <: View](id: Int) : V
  def stub[V <: View](id: Int) : V = findById[ViewStub](id).inflate().asInstanceOf[V]
}

trait ViewEventContext extends View {
  implicit lazy val eventContext: EventContext = EventContext()

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    eventContext.start()
  }

  override def onDetachedFromWindow(): Unit = {
    eventContext.stop()
    super.onDetachedFromWindow()
  }
}

trait ViewHelper extends View with ViewFinder with Injectable with ViewEventContext {
  lazy implicit val wContext = WireContext(getContext)
  lazy implicit val injector = wContext.injector

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int): V = findViewById(id).asInstanceOf[V]

  def inflate(layoutResId: Int, group: ViewGroup = ViewHelper.viewGroup(this), addToParent: Boolean = true)(implicit tag: LogTag = LogTag("ViewHelper")) =
    ViewHelper.inflate[View](layoutResId, group, addToParent)
}

object ViewHelper {

  def findById[V <: View](parent: View)(id: Int): V =
    parent.findViewById[V](id)

  def inflate[T <: View](layoutResId: Int, group: ViewGroup, addToParent: Boolean)(implicit logTag: LogTag) =
    try LayoutInflater.from(group.getContext).inflate(layoutResId, group, addToParent).asInstanceOf[T]
    catch {
      case e: Throwable =>
        var cause = e
        while (cause.getCause != null) cause = cause.getCause
        error(l"inflate failed with root cause:", cause)
        throw e
    }

  def viewGroup(view: View) = view match {
    case vg: ViewGroup => vg
    case _ => view.getParent.asInstanceOf[ViewGroup]
  }
}

trait ServiceHelper extends Service with Injectable with WireContext {

  override implicit lazy val eventContext: EventContext = EventContext()

  override def onCreate(): Unit = {
    eventContext.start()
    super.onCreate()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    eventContext.stop()
    eventContext.destroy()
  }
}


trait FragmentHelper
  extends Fragment
    with OnBackPressedListener
    with ViewFinder
    with Injectable
    with DerivedLogTag {

  implicit def currentAndroidContext: Context = getContext
  lazy implicit val injector: Injector = getActivity.asInstanceOf[WireContext].injector
  implicit lazy val eventContext: EventContext = EventContext()

  private var views: List[ViewHolder[_]] = Nil

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = {
    val res = getView.findViewById[V](id)
    if (res != null) res
    else getActivity.findViewById(id).asInstanceOf[V]
  }

  def safeStartActivityForResult(intent: Intent, requestCode: Int): Boolean =
    try {
      startActivityForResult(intent, requestCode)
      true
    } catch {
      case ex: ActivityNotFoundException =>
        error(l"No application found to handle this request", ex)
        Toast.makeText(getContext, s"No application found to handle this request", Toast.LENGTH_SHORT).show()
        false
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

  @inline
  private def findFragment[T <: Fragment](tag: String, manager: FragmentManager): Option[T] =
    Option(manager.findFragmentByTag(tag)).map(_.asInstanceOf[T])

  @inline
  private def findFragment[T <: Fragment](@IdRes id: Int, manager: FragmentManager): Option[T] =
    Option(manager.findFragmentById(id)).map(_.asInstanceOf[T])

  @inline
  def findFragment[T <: Fragment](tag: String): Option[T] =
    findFragment(tag, getFragmentManager)

  @inline
  def findChildFragment[T <: Fragment](tag: String): Option[T] =
    findFragment(tag, getChildFragmentManager)

  @inline
  def findChildFragment[T <: Fragment](@IdRes id: Int): Option[T] =
    findFragment(id, getChildFragmentManager)

  def withChildFragmentOpt[A](@IdRes id: Int)(f: Option[Fragment] => A): A =
    f(findChildFragment(id))

  def withChildFragment(@IdRes id: Int)(f: Fragment => Unit): Unit =
    findChildFragment(id).foreach(f)

  def findById[V <: View](parent: View, id: Int): V =
    parent.findViewById(id).asInstanceOf[V]

  def view[V <: View](id: Int) = {
    val h = new ViewHolder[V](id, this)
    views ::= h
    h
  }

  def slideFragmentInFromRight(f: Fragment, tag: String): Unit =
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.fl__participant__container, f, tag)
      .addToBackStack(tag)
      .commit

  def getStringArg(key: String): Option[String] =
    Option(getArguments).flatMap(a => Option(a.getString(key)))

  def getBooleanArg(key: String, default: Boolean = false): Boolean =
    Option(getArguments).map(_.getBoolean(key, default)).getOrElse(default)

  def getIntArg(key: String): Option[Int] =
    Option(getArguments).flatMap(a => Option(a.getInt(key)))

  override def onBackPressed(): Boolean = {
    verbose(l"onBackPressed")(LogTag(getClass.getSimpleName))
    false
  }

  override def onResume(): Unit = {
    super.onResume()
    views.foreach(_.onResume())
  }

  override def onPause(): Unit = {
    views.foreach(_.onPause())
    super.onPause()
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    views.foreach(_.clear())
  }

  override def onDestroyView(): Unit = {
    views foreach(_.clear())
    super.onDestroyView()
  }

  override def onStart(): Unit = {
    eventContext.start()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    eventContext.stop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    eventContext.destroy()
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
        currentContent ! withChildFragmentOpt(contentId)(_.map(_.getTag)).map(Page(_, getChildFragmentManager.getBackStackEntryCount <= 1))
    })
  }

  def getContentFragment: Option[Fragment] = withContentFragment(identity)

  def withContentFragment[A](f: Option[Fragment] => A): A = withChildFragmentOpt(contentId)(f)
}

object ManagerFragment {
  case class Page(tag: String, firstPage: Boolean)
}

trait DialogHelper extends Dialog with Injectable {
  val context: Context
  lazy implicit val injector = context.asInstanceOf[WireContext].injector
  implicit lazy val eventContext: EventContext = EventContext()

  private var dismissListener = Option.empty[DialogInterface.OnDismissListener]

  super.setOnDismissListener(new DialogInterface.OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = {
      dismissListener.foreach(_.onDismiss(dialogInterface))
      eventContext.destroy()
    }
  })

  override def onStart(): Unit = {
    eventContext.start()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    eventContext.stop()
  }

  override def setOnDismissListener(listener: DialogInterface.OnDismissListener): Unit = {
    dismissListener = Some(listener)
  }
}

trait ActivityHelper extends AppCompatActivity with ViewFinder with Injectable with WireContext {

  implicit lazy val eventContext: EventContext = EventContext()

  @SuppressLint(Array("com.waz.ViewUtils"))
  def findById[V <: View](id: Int) = findViewById(id).asInstanceOf[V]

  def findFragment[T](id: Int) : T = {
    this.asInstanceOf[FragmentActivity].getSupportFragmentManager.findFragmentById(id).asInstanceOf[T]
  }

  def withFragmentOpt[A](tag: String)(f: Option[Fragment] => A): A =
    f(Option(this.asInstanceOf[FragmentActivity].getSupportFragmentManager.findFragmentByTag(tag)))

  override def onStart(): Unit = {
    eventContext.start()
    super.onStart()
  }

  override def onStop(): Unit = {
    super.onStop()
    eventContext.stop()
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    eventContext.destroy()
  }
}

trait CallingBannerActivity extends ActivityHelper {
  lazy val callController = inject[CallController]

  lazy val callBanner = returning(findById[View](R.id.call_banner)) { v =>
    v.onClick(CallingActivity.startIfCallIsActive(this))
  }
  lazy val callBannerStatus = findById[TextView](R.id.call_banner_status)
  lazy val mutedGlyph       = findById[GlyphTextView](R.id.muted_glyph)
  lazy val spacerGlyph      = findById[GlyphTextView](R.id.spacer_glyph)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)

    callController.isCallActiveDelay.onUi { est =>
      getWindow.setStatusBarColor(ContextUtils.getColor(if (est) R.color.accent_green else R.color.black)(this))
      callBanner.setVisibility(if (est) View.VISIBLE else View.GONE)
    }

    Signal.zip(callController.isMuted, callController.isCallEstablished).onUi {
      case (true, true) =>
        mutedGlyph.setVisibility(View.VISIBLE)
        spacerGlyph.setVisibility(View.INVISIBLE)
      case _ =>
        mutedGlyph.setVisibility(View.GONE)
        spacerGlyph.setVisibility(View.GONE)
    }

    callController.callBannerText.onUi(callBannerStatus.setText)
  }
}

//TODO add a withFilter method for for comprehensions
class ViewHolder[T <: View](id: Int, finder: ViewFinder) {
  private var view = Option.empty[T]
  private var onClickListener = Option.empty[OnClickListener]

  def get: T = view.getOrElse { returning(finder.findById[T](id)) { t => view = Some(t) } }

  @inline
  def opt: Option[T] =
    if(finder != null) Option(get)
    else None

  def clear() =
    view = Option.empty

  def fold[B](ifEmpty: => B)(f: T => B): B = opt.fold(ifEmpty)(f)

  def foreach(f: T => Unit): Unit = opt.foreach(f)

  def map[A](f: T => A): Option[A] = opt.map(f)

  def flatMap[A](f: T => Option[A]): Option[A] = opt.flatMap(f)

  def filter(p: T => Boolean): Option[T] = opt.filter(p)

  def exists(p: T => Boolean): Boolean = opt.exists(p(_))

  def onResume() = onClickListener.foreach(l => foreach(_.setOnClickListener(l)))

  def onPause() = onClickListener.foreach(_ => foreach(_.setOnClickListener(null)))

  def onClick(f: T => Unit): Unit = {
    onClickListener = Some(returning(new OnClickListener {
      override def onClick(v: View) = f(v.asInstanceOf[T])
    })(l => view.foreach(_.setOnClickListener(l))))
  }
}

trait PreferenceHelper extends Preference with Injectable {
  lazy implicit val wContext = WireContext(getContext)
  lazy implicit val injector = wContext.injector
  lazy implicit val eventContext: EventContext = EventContext()

  override def onAttached(): Unit = {
    super.onAttached()
    eventContext.start()
  }

  override def onDetached(): Unit = {
    eventContext.stop()
    super.onDetached()
  }
}
