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
package com.waz.service.assets

import java.io.{File, FileNotFoundException}

import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.media.AudioManager
import android.telephony.TelephonyManager
import com.waz.api
import com.waz.api.Asset.LoadCallback
import com.waz.api.ErrorType._
import com.waz.api.impl.PlaybackControls
import com.waz.api.{AudioEffect, ErrorType}
import com.waz.audioeffect.{AudioEffect => AVSEffect}
import com.waz.cache.{CacheService, Expiration}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.AccountsService.InForeground
import com.waz.service.assets.AudioLevels.peakLoudness
import com.waz.service.{AccountsService, ErrorsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals._
import com.waz.utils.wrappers.URI
import org.threeten.bp
import org.threeten.bp.Instant

import scala.concurrent.Future.{failed, successful}
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.{NoStackTrace, NonFatal}
import scala.util.{Failure, Success}

class RecordAndPlayService(userId:        UserId,
                           globalService: GlobalRecordAndPlayService,
                           errors:        ErrorsService,
                           accounts:      AccountsService) {
  import EventContext.Implicits.global
  import Threading.Implicits.Background

  globalService.onError { err =>
    err.tpe.foreach { tpe => errors.addErrorWhenActive(ErrorData(Uid(), tpe, responseMessage = err.message)) }
  }

  accounts.accountState(userId).map(_ == InForeground).onChanged.on(Background) {
    case false => globalService.AudioFocusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    case true =>
  }
}

// invariant: only do side effects and/or access player/recorder during a transition
class GlobalRecordAndPlayService(cache: CacheService, context: Context, fileCache: GeneralFileCache) extends DerivedLogTag {
  import GlobalRecordAndPlayService._
  import Threading.Implicits.Background

  private lazy val stateSource = returning(Signal[State](Idle))(_.disableAutowiring())
  private implicit def implicitContext: Context = context

  lazy val state: Signal[State] = stateSource
  lazy val audioManager = context.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]

  val onError = EventStream[Error]()

  context.registerReceiver(interruptionBroadcastReceiver, interruptionIntentFilter)

  def play(key: MediaKey, content: GlobalRecordAndPlayService.Content): Future[State] = transitionF {
    case Idle =>
      withAudioFocus()(playOrResumeTransition(key, Left(content)))
    case Playing(player, `key`) =>
      successful(KeepCurrent())
    case Playing(player, ongoing) =>
      player.release().recoverWithLog().flatMap(_ => withAudioFocus()(playOrResumeTransition(key, Left(content)))).recover {
        case NonFatal(cause) => Next(Idle, Some(Error(s"cannot start playback $key ($content) after releasing playing $ongoing", Some(cause), Some(PLAYBACK_FAILURE))))
      }
    case Paused(player, `key`, playhead, _) =>
      withAudioFocus()(playOrResumeTransition(key, Right(player))).recoverWith {  // TODO if this fails, try reset, seek to playhead and then start
        case NonFatal(cause) =>
          player.release().recoverWithLog().map(_ => Next(Idle, Some(Error(s"cannot resume playback $key ($content)", Some(cause), Some(PLAYBACK_FAILURE)))))
      }
    case Paused(player, ongoing, _, _) =>
      player.release().recoverWithLog().flatMap(_ => withAudioFocus()(playOrResumeTransition(key, Left(content)))).recover {
        case NonFatal(cause) => Next(Idle, Some(Error(s"cannot start playback $key ($content) after releasing paused $ongoing", Some(cause), Some(PLAYBACK_FAILURE))))
      }
    case rec @ Recording(_, ongoing, _, _, _, _) =>
      cancelOngoingRecording(rec).flatMap { _ =>
        withAudioFocus()(playOrResumeTransition(key, Left(content))).recover {
          case NonFatal(cause) => Next(Idle, Some(Error(s"cannot start playback $key ($content) after canceling recording $ongoing", Some(cause), Some(PLAYBACK_FAILURE))))
        }
      }
  } (s"error while starting playback $key ($content)", Some(PLAYBACK_FAILURE))

  private def playOrResumeTransition(key: MediaKey, contentOrPlayer: Either[GlobalRecordAndPlayService.Content, Player]): Future[Transition] =
    Future(contentOrPlayer).flatMap(_.fold(content => Player(content, Observe(key)), successful)).flatMap { p =>
      contentOrPlayer.fold(_ => p.start(), _ => p.resume()).map(_ => Next(Playing(p, key))).andThenFuture {
        case Failure(cause) => p.release()
      }
    }.andThen { case Failure(cause) => abandonAudioFocus() }

  case class Observe(key: MediaKey) extends Player.Observer with DerivedLogTag {
    override def onCompletion(): Unit = transitionF {
      case Playing(player, `key`) =>
        player.release().recoverWithLog().map(_ => Next(Idle))
      case Paused(player, `key`, _, _) =>
        player.release().recoverWithLog().map(_ => Next(Idle))
      case other =>
        successful(KeepCurrent(Some(Error(s"Player signaled end of playback $key but state = $other"))))
    } (s"error while ending playback $key")

    override def onError(msg: String): Unit = transitionF {
      case Playing(player, `key`) =>
        player.release().recoverWithLog().map(_ => Next(Idle, Some(Error(s"error during playback $key: $msg", None, Some(PLAYBACK_FAILURE)))))
      case other =>
        warn(l"Received playback error signal (key = $key, msg: ${Error(msg)}) but state = $other")
        successful(KeepCurrent())
    } (s"error during playback $key", Some(PLAYBACK_FAILURE))
  }

  def pause(key: MediaKey): Future[State] = transitionF {
    case Idle =>
      successful(KeepCurrent())
    case Playing(player, `key`) =>
      pauseTransition(key, player, false)
    case Paused(_, `key`, _, _) =>
      successful(KeepCurrent())
    case other =>
      failed(new IllegalStateException(s"state = $other"))
  } (s"error while pausing playback $key", Some(PLAYBACK_FAILURE))

  private def pauseTransition(key: MediaKey, player: Player, transient: Boolean): Future[Transition] =
    player.pause() map { media =>
      abandonAudioFocus()
      Next(Paused(player, key, media, transient))
    } recoverWith {
      case NonFatal(cause) => player.release().map { _ =>
        abandonAudioFocus()
        Next(Idle)
      }
    }

  def setPlayhead(key: MediaKey, content: GlobalRecordAndPlayService.Content, playhead: bp.Duration): Future[State] = {
    def seek(maybePlayer: Option[Player] = None) =
      maybePlayer.fold2(Player(content, Observe(key)), successful).flatMap { player =>
        player.repositionPlayhead(playhead).map(_ => returning(player)(_ => verbose(l"repositioned playhead: $playhead"))).andThenFuture {
          case Failure(cause) => if (maybePlayer.isEmpty) player.release() else successful(())
       }
      }

    def releaseOngoingAndSeek(res: Either[Recording, Player], ongoing: MediaKey) = {
      verbose(l"releasing $ongoing to seek $key to $playhead")
      res.fold(rec => cancelOngoingRecording(rec), _.release())
    }.flatMap(_ => seek().map(p => Next(Paused(p, key, MediaPointer(content, playhead))))).recover {
      case NonFatal(cause) => Next(Idle, Some(Error(s"cannot seek $key to $playhead after stopping $ongoing", Some(cause))))
    }.andThen {
      case _ => abandonAudioFocus()
    }

    transitionF {
      case Idle                                 => seek().map(p => Next(Paused(p, key, MediaPointer(content, playhead))))
      case Playing(player, `key`)               => seek(Some(player)).map(_ => KeepCurrent())
      case Paused(player, `key`, _, _)          => seek(Some(player)).map(p => Next(Paused(p, key, MediaPointer(content, playhead))))
      case Playing(player, ongoing)             => releaseOngoingAndSeek(Right(player), ongoing)
      case Paused(player, ongoing, _, _)        => releaseOngoingAndSeek(Right(player), ongoing)
      case rec @ Recording(_, ongoing, _, _, _, _) => releaseOngoingAndSeek(Left(rec), ongoing)
    }(s"error while setting playhead")
  }

  def playhead(key: MediaKey): Signal[bp.Duration] = stateSource flatMap {
    case Playing(_, `key`) =>
      ClockSignal(tickInterval).flatMap { i =>
        Signal.from(duringIdentityTransition { case Playing(player, `key`) => player.playhead })
      }
    case Paused(player, `key`, media, _) =>
      Signal.const(media.playhead)
    case other =>
      Signal.const(bp.Duration.ZERO)
  }

  def isPlaying(key: MediaKey): Signal[Boolean] = stateSource.map {
    case Playing(_, `key`) => true
    case other => false
  }

  def recordingLevel(key: AssetMediaKey): EventStream[Float] =
    stateSource.flatMap {
      case Recording(_, `key`, _, _, _, _) =>
        ClockSignal(tickInterval).flatMap { i =>
          Signal.from(duringIdentityTransition { case Recording(recorder, `key`, _, _, _, _) => successful((peakLoudness(recorder.maxAmplitudeSinceLastCall), i)) })
        }
      case other => Signal.empty[(Float, Instant)]
    }.onChanged.map { case (level, _) => level }

  def record(key: AssetMediaKey, maxAllowedDuration: FiniteDuration): Future[(Instant, Future[RecordingResult])] = {
    def record(): Future[Next] = withAudioFocus() {
      fileCache. createEmptyFile(CacheKey.fromAssetId(key.id).str) map { file =>
        verbose(l"started recording in file: ${showString(file.getAbsolutePath)}")
        val promisedResult = Promise[RecordingResult]
        withCleanupOnFailure {
          val start = Instant.now
          val recorder = startRecording(file, maxAllowedDuration)

          recorder.onLengthLimitReached {
            verbose(l"recording $key reached the file size limit")
            stopRecording(key)
          }

          recorder.onError { cause =>
            transition {
              case Recording(recorder, `key`, _, mime, _, promisedResult) =>
                error(l"recording $key failed", cause)
                promisedResult.tryFailure(cause)
                abandonAudioFocus()
                Next(Idle)
              case other =>
                KeepCurrent()
            } ("onError failed")
          }

          Next(Recording(recorder, key, start, Mime.Audio.PCM, file, promisedResult))
        } { cause =>
          file.delete()
        }
      }
    } andThen {
      case Failure(cause) => abandonAudioFocus()
    }

    def releaseOngoingAndRecord(res: Either[Recording, Player], ongoing: MediaKey) = {
      verbose(l"releasing $ongoing to start recording $key")
      res.fold(rec => cancelOngoingRecording(rec), _.release())
    }.flatMap(_ => record()).recover {
      case NonFatal(cause) => Next(Idle, Some(Error(s"cannot start recording $key after stopping $ongoing", Some(cause), Some(RECORDING_FAILURE))))
    }

    transitionF {
      case Idle                                            => record()
      case Playing(player, ongoing)                        => releaseOngoingAndRecord(Right(player), ongoing)
      case Paused(player, ongoing, _, _)                   => releaseOngoingAndRecord(Right(player), ongoing)
      case rec @ Recording(recorder, ongoing, _, _, _, _)  => releaseOngoingAndRecord(Left(rec), ongoing)
    }(s"error while starting audio recording $key", Some(RECORDING_FAILURE)).map {
      case Recording(_, `key`, start, _, _, result)     => (start, result.future)
      case other                                        => throw new IllegalStateException(s"recording not started; state = $other")
    }
  }

  protected def startRecording(destination: File, lengthLimit: FiniteDuration): PCMRecorder = PCMRecorder.startRecording(destination, lengthLimit)

  def stopRecording(key: AssetMediaKey): Future[State] = transitionF {
    case rec @ Recording(recorder, `key`, start, mime, file, promisedAsset) =>
      val newState = for {
        cause <- recorder.stopRecording()
        _ = if (file.exists()) {
          verbose(l"stop recording: passing asset with data entry: ???, is it encrypted?: no bytes at $key")
          val audio = Audio(key, file, PCM.durationFromByteCount(file.length), mime, applyAudioEffect)
          val successState = RecordingSuccessful(audio, cause == PCMRecorder.LengthLimitReached)
          promisedAsset.trySuccess(successState)
        } else promisedAsset.tryFailure(new FileNotFoundException(s"audio file does not exist after recording: $file"))
      } yield Next(Idle)
      newState.andThen { case _ => abandonAudioFocus() }
    case other =>
      failed(new IllegalStateException(s"state = $other"))
  } (s"error while stopping audio recording $key", Some(RECORDING_FAILURE))

  def cancelRecording(key: AssetMediaKey): Future[State] = transitionF {
    case rec @ Recording(_, `key`, _, _, _, _) =>
      cancelOngoingRecording(rec).map { _ =>
        abandonAudioFocus()
        Next(Idle)
      }
    case other =>
      successful(KeepCurrent())
  } (s"error while cancelling audio recording $key", Some(RECORDING_FAILURE))

  private def cancelOngoingRecording(rec: Recording): Future[Unit] =
    for {
      _ <- rec.recorder.cancelRecording().recoverWithLog()
      _ = rec.file.delete()
    } yield rec.result.trySuccess(RecordingCancelled)

  def releaseAnyOngoing(keys: Set[MediaKey]): Future[State] = transitionF {
    case Playing(player, key) if keys(key) =>
      player.release().recoverWithLog().map(_ => Next(Idle)).andThen { case _ => abandonAudioFocus() }
    case Paused(player, key, _, _) if keys(key) =>
      player.release().recoverWithLog().map(_ => Next(Idle))
    case rec @ Recording(_, key, _, _, _, _) if keys(key) =>
      cancelOngoingRecording(rec).map { _ =>
        abandonAudioFocus()
        Next(Idle)
      }
    case other => successful(KeepCurrent())
  }(s"error cancelling any ongoing $keys")

  def applyAudioEffect(effect: AudioEffect, pcmFile: File): Future[Audio] = {
    val id = AssetId()
    fileCache.createEmptyFile(CacheKey.fromAssetId(id).str).map { file =>
      withCleanupOnFailure {
        val fx = new AVSEffect
        try {
          val result = fx.applyEffectPCM(pcmFile.getAbsolutePath, file.getAbsolutePath, PCM.sampleRate, effect.avsOrdinal, true)
          if (result < 0) throw new RuntimeException(s"applyEffectPCM returned error code: $result")
          Audio(AssetMediaKey(id), file, PCM.durationFromByteCount(file.length()), Mime.Audio.PCM, applyAudioEffect)
        } finally fx.destroy
      }(_ => file.delete)
    }(Threading.IO)
  }

  private def abandonAudioFocus(): Unit = audioManager.abandonAudioFocus(AudioFocusListener)

  private def withAudioFocus[A](transient: Boolean = false)(f: => A): A =
    audioManager.requestAudioFocus(AudioFocusListener, AudioManager.STREAM_MUSIC, if (transient) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN) match {
      case AudioManager.AUDIOFOCUS_REQUEST_GRANTED => f
      case _ => throw new RuntimeException("audio focus request denied")
    }

  private def interruptionBroadcastReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      val isIgnoredPhoneStateTransition =
        intent.getAction == TelephonyManager.ACTION_PHONE_STATE_CHANGED &&
        intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING

      if (! isIgnoredPhoneStateTransition) {
        verbose(l"interruption broadcast: ${showString(intent.getAction)}")
        AudioFocusListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
      }
    }
  }

  private def interruptionIntentFilter = returning(new IntentFilter) { filter =>
    filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
    filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL)
  }

  object AudioFocusListener extends AudioManager.OnAudioFocusChangeListener {
    override def onAudioFocusChange(focusChange: Int): Unit = focusChange match {
      case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT =>
        verbose(l"audio focus lost (transient)")
        transitionF {
          case Playing(player, key)             => pauseTransition(key, player, true)
          case rec @ Recording(_, key, _, _, _, _) => cancelOngoingRecording(rec).map(_ => Next(Idle))
          case other                            => successful(KeepCurrent())
        }(s"error while handling transient audio focus loss")
      case AudioManager.AUDIOFOCUS_GAIN =>
        verbose(l"audio focus gained")
        transitionF {
          case Paused(player, key, _, true) => playOrResumeTransition(key, Right(player))
          case other                        => successful(KeepCurrent())
        }(s"error while handling audio focus gain")
      case AudioManager.AUDIOFOCUS_LOSS =>
        verbose(l"audio focus lost")
        abandonAudioFocus()
        transitionF {
          case Playing(player, key)             => pauseTransition(key, player, false)
          case rec @ Recording(_, key, _, _, _, _) => cancelOngoingRecording(rec).map(_ => Next(Idle))
          case other                            => successful(KeepCurrent())
        }(s"error while handling transient audio focus loss")
      case other =>
        warn(l"unknown audio focus change: $other")
    }
    override val toString: String = s"AudioFocusListener-${Uid().str}"
  }

  private def transition(f: State => Transition)(errorMessage: String, errorType: Option[ErrorType] = None): Future[State] =
    transitionF(s => Future(f(s)))(errorMessage, errorType)

  private def transitionF(f: State => Future[Transition])(errorMessage: String, errorType: Option[ErrorType] = None): Future[State] =
    Serialized.future("GlobalRecordAndPlayService")(keepStateOnFailure(stateSource.head.flatMap(f))(errorMessage, errorType).map(applyState))

  private def duringIdentityTransition[A](pf: PartialFunction[State, Future[A]]): Future[A] = {
    val p = Promise[A]

    transitionF { state =>
      if (pf.isDefinedAt(state))
        try pf(state).map { a =>
          p.success(a)
          KeepCurrent()
        } catch {
          case cause: Throwable =>
            p.failure(cause)
            failed(cause)
      } else {
        p.failure(new NoSuchElementException(s"partial function not defined at $state") with NoStackTrace)
        successful(KeepCurrent())
      }
    } ("identity transition failed")

    p.future
  }

  private def applyState: Transition => State = { t =>
    t.changedState.foreach { next =>
      stateSource.mutate { current =>
        verbose(l"transition: $current -> $next")
        next
      }
    }
    t.error.foreach { err =>
      err.cause.fold(error(l"$err"))(c => error(l"$err", c))
      onError ! err
    }
    t.changedState.orElse(stateSource.currentValue).getOrElse(Idle)
  }

  private def keepStateOnFailure(f: Future[Transition])(errorMessage: String, errorType: Option[ErrorType]): Future[Transition] = f recover {
    case NonFatal(cause) => KeepCurrent(Some(Error(errorMessage, Some(cause), errorType)))
  }
}

object GlobalRecordAndPlayService extends DerivedLogTag {

  case class Audio(key: MediaKey,
                   file: File,
                   duration: bp.Duration,
                   mime: Mime,
                   private val fx: (AudioEffect, File) => Future[Audio]) {

    def getPlaybackControls: PlaybackControls =
      new PlaybackControls(key, PCMContent(file), _ => Signal.const(duration))(ZMessaging.currentUi)

    def applyEffect(effect: api.AudioEffect, callback: LoadCallback[Audio]): Unit = {
      verbose(l"applyEffect($effect) $this")
      fx(effect, file).onComplete {
        case Success(asset) =>
          callback.onLoaded(asset)
        case Failure(cause) =>
          error(l"effect application failed", cause)
          callback.onLoadFailed()
      }(Threading.Ui)
    }

  }

  sealed trait State
  case object Idle extends State
  case class Playing(player: Player, key: MediaKey) extends State
  case class Paused(player: Player, key: MediaKey, playhead: MediaPointer, transient: Boolean = false) extends State
  case class Recording(recorder: PCMRecorder, key: MediaKey, start: Instant, mime: Mime, file: File, result: Promise[RecordingResult]) extends State

  sealed trait RecordingResult
  case object RecordingCancelled extends RecordingResult
  case class RecordingSuccessful(audio: Audio, lengthLimitReached: Boolean) extends RecordingResult

  sealed abstract class Transition(val changedState: Option[State], val error: Option[Error])
  case class KeepCurrent(override val error: Option[Error] = None) extends Transition(None, error)
  case class Next(state: State, override val error: Option[Error] = None) extends Transition(Some(state), error)
  case class Error(message: String, cause: Option[Throwable] = None, tpe: Option[ErrorType] = None)

  sealed trait MediaKey
  case class AssetMediaKey(id: AssetId) extends MediaKey
  case class UriMediaKey(uri: URI) extends MediaKey

  sealed trait Content
  case class UnauthenticatedContent(uri: URI) extends Content
  case class PCMContent(file: File) extends Content

  case class MediaPointer(content: Content, playhead: bp.Duration)

  val tickInterval = 30.millis
  val recordingExpiration = Expiration in 7.days
}
