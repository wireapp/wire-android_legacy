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
package com.waz.zclient.common.fragments

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.jakewharton.processphoenix.ProcessPhoenix
import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.service.ZMessaging
import com.waz.threading.Threading._
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.pages.main.connectivity.ConnectivityIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Quart
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.duration._
import com.waz.threading.Threading._
import com.waz.zclient.views.LoadingIndicatorView.InfiniteLoadingBar
import com.wire.signals.{CancellableFuture, Signal}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ConnectivityFragment extends Fragment with FragmentHelper with ConnectivityIndicatorView.OnExpandListener {

  import ConnectivityFragment._

  private lazy val network = Option(ZMessaging.currentGlobal).map(_.network.networkMode).getOrElse(Signal.const(NetworkMode.UNKNOWN))
  private lazy val accentColor = inject[AccentColorController].accentColor
  private lazy val longProcess = inject[Signal[ZMessaging]].flatMap(_.push.processing).flatMap {
    case true => Signal.from(CancellableFuture.delay(LongProcessingDelay)).map(_ => true).orElse(Signal.const(false))
    case _ => Signal.const(false)
  }
  private lazy val tooLongProcess = for {
    push <- inject[Signal[ZMessaging]].map(_.push)
    isTooLong <- inject[Signal[ZMessaging]].flatMap(_.eventScheduler.currentEventTag).flatMap { _ =>
      Signal.from(CancellableFuture.delay(TooLongProcessingDelay)).map {_ =>
        val isProcessing = push.processing.currentValue.getOrElse(false)
        isProcessing
      }
    }
  } yield { isTooLong }

  private lazy val tooLongObservingContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1))

  private var root: View = _

  private lazy val noInternetIndicator = returning(findById[ConnectivityIndicatorView](root, R.id.civ__connectivity_indicator)) { v =>
    v.onClick {
      v.show()
      loadingIndicatorView.hide()
    }
    v.setOnExpandListener(this)
  }

  private lazy val connectivityIndicatorViewForeground = findById[ImageView](root, R.id.iv__connectivity_foreground)
  private lazy val loadingIndicatorView = findById[LoadingIndicatorView](root, R.id.liv__server_connecting_indicator)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    root = inflater.inflate(R.layout.fragment_connectivity, container, false)

    //initialise lazy
    loadingIndicatorView
    noInternetIndicator

    accentColor.map(_.color).onUi(loadingIndicatorView.setColor)

    network.map(_ == NetworkMode.OFFLINE).onUi {
      case true  => noInternetIndicator.show()
      case false => noInternetIndicator.hide()
    }

    (for {
      mode       <- network
      processing <- longProcess
    } yield (mode, processing)).onUi {
      case (NetworkMode.OFFLINE | NetworkMode.UNKNOWN, _) => loadingIndicatorView.hide()
      case (_, true)                                      => loadingIndicatorView.show(InfiniteLoadingBar)
      case _                                              => loadingIndicatorView.hide()
    }

    val tooLongObserverTag = LogTag("TooLongObserver")
    tooLongProcess.on(tooLongObservingContext) {
      case true => {
        error(l"App doing work for too long. Restarting the application!")(tooLongObserverTag)
        restartTheApp()
      }
      case _ => {
        verbose(l"App not working for so long. Keeping it alive")(tooLongObserverTag)
      }
    }

    root
  }

  private def restartTheApp(): Unit = {
    ProcessPhoenix.triggerRebirth(currentAndroidContext)
  }

  override def onExpandBegin(animationDuration: Long) = {
    connectivityIndicatorViewForeground.animate
      .alpha(0)
      .setDuration(animationDuration)
      .setInterpolator(new Quart.EaseIn)
      .start()
  }

  override def onCollapseBegin(animationDuration: Long) = {
    connectivityIndicatorViewForeground.animate
      .alpha(1)
      .setDuration(animationDuration)
      .setInterpolator(new Quart.EaseOut)
      .start()
  }

  override def onHideBegin(animationDuration: Long) = {
    connectivityIndicatorViewForeground.animate
      .alpha(0)
      .setDuration(animationDuration)
      .setInterpolator(new Quart.EaseIn)
      .start()
  }
}

object ConnectivityFragment {
  val LongProcessingDelay = 2.seconds
  val TooLongProcessingDelay = 5.seconds
  val FragmentTag = ConnectivityFragment.getClass.getSimpleName

  def apply(): ConnectivityFragment = new ConnectivityFragment()
}
