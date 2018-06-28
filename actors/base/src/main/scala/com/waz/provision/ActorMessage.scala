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
package com.waz.provision

import akka.actor.ActorRef
import com.waz.api.Message
import com.waz.api.impl.AccentColor
import com.waz.model._
import com.waz.threading.QueueReport
import org.threeten.bp.Instant

import scala.concurrent.duration.FiniteDuration

trait ActorMessage

trait LogisticalMessage extends ActorMessage

trait ResponseMessage extends LogisticalMessage


object ActorMessage {

  //////////////////////////////////////////////////////////////////////////////
  // LOGISTICAL MESSAGES - for setting up and communicating between actors
  //////////////////////////////////////////////////////////////////////////////
  /**
   * A message used to release all remote process actors. Pass this message to the [[CoordinatorActor]], and it
   * will pass it on to any connected remotes, which may subsequently shut down their host processes (up to remote actor).
   */
  case object ReleaseRemotes extends LogisticalMessage

  /**
    * Sent from the [[CoordinatorActor]] to each registered [[RemoteProcessActor]] upon receiving a [[ReleaseRemotes]]
    * [[RemoteProcessActor]] which was created with specific coordinator will stop and [[RemoteProcess]] will die.
    * If remote actor was started without specific coordinator it will switch to waiting for next coordinator.
   */
  case object ReleaseProcess extends LogisticalMessage

  /**
   * A response message from the CoordinatorActor whenever a command is sent, but there are no registered
   * remote processes.
   */
  case object NoRemotes extends LogisticalMessage

  /**
   * Sent automatically to the [[CoordinatorActor]] by a [[RemoteProcessActor]] as soon as the remote process
   * is established and the actor is ready. With this message, the [[CoordinatorActor]] gets a reference to the remote.
   * @param processName The name that the remote process wishes to be aliased as - makes debugging easier
   */
  case class RegisterProcess(processName: String) extends LogisticalMessage

  /**
   * A message to be sent to any [[RemoteProcessActor]] asking it to create a new "Device", at which point a
   * new instance of SE will be created on the host process. The [[RemoteProcessActor]] will then forward the
   * [[ActorRef]] back to whatever sender asked for the device to be spawned
   *
   * @param processName This message can also be passed to a [[CoordinatorActor]], at which point the [[processName]]
   *                    will be used to look up the reference to the [[RemoteProcessActor]]
   * @param deviceName The alias that we want to give to the spawned 'device'. This is just to help debugging
   */
  case class SpawnRemoteDevice(processName: String, deviceName: String) extends LogisticalMessage

  /**
   * To be sent to a [[CoordinatorActor]] to wait on and fetch the [[ActorRef]] of a newly created process.
   * The usual flow is that the main (testing) process creates an [[akka.actor.ActorSystem]] with a
   * [[CoordinatorActor]], and then starts a new [[RemoteProcess]], specifying the [[processName]]. Then the testing
   * process needs to ask the [[CoordinatorActor]] with a [[WaitUntilRegistered()]] message, which should eventually
   * return our [[RemoteProcessActor]]s [[ActorRef]].
   * @param processName the name of the process that was just spawned
   */
  case class WaitUntilRegistered(processName: String) extends LogisticalMessage

  /**
   * Sent to the [[CoordinatorActor]] to return a list of registered remote processes
   */
  case object ListRemotes extends LogisticalMessage

  /**
   * The response message for [[ListRemotes]]
   * @param remotes a list of the [[RemoteProcessActor]]s [[ActorRef]]s and their process names
   */
  case class ListRemotes(remotes: Map[ActorRef, String]) extends LogisticalMessage

  /**
   * A message to have the [[CoordinatorActor]] pass on any [[ActorMessage]] to the desired remote process by its
   * alias. Useful if we lose the reference to the [[RemoteProcessActor]] for any reason
   * @param targetProcessName the remote process alias, to who we want to send a message
   * @param msg the message
   */
  case class Command(targetProcessName: String, msg: ActorMessage) extends LogisticalMessage

  /**
   * A general utility message signifying that the command was executed successfully. Useful if we HAVE to wait
   * on the command being completed before exectution can continue
   */
  case object Successful extends ResponseMessage

  /**
   * Same as [[Successful]] but with a value that results as part of the completion, such as a [[UserId]], for example.
   * @param response successful completion value
   */
  case class Successful(response: String) extends ResponseMessage

  /**
   * A general utility message signifying that something went wrong while an Actor was carrying out a command
   * @param reason A description of the reason for failure
   */
  case class Failed(reason: String) extends ResponseMessage

  /**
   * A message used to basically ping an actor. All actors should simply return this [[ActorMessage]] to the sender
   * with the same [[msg]] as was sent to it, so that the sender knows the actor is still alive
   * @param msg Some message to be echoed by the target actor
   * @param processName The target Actor would normally respond with its own alias, just for extra certainty
   */
  case class Echo(msg: String, processName: String = "") extends ResponseMessage


  //////////////////////////////////////////////////////////////////////////////
  // COMMAND MESSAGES - the things that we actually want our "Devices" to perform.
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Perform a login to the target device
   * @param emailLogin
   * @param password
   */
  case class Login(emailLogin: String, password: String) extends ActorMessage

  case class RegisterPhone(phone: String, confirmationCode: String, name: String = "", accentColor: Int = 1) extends ActorMessage

  /**
   * A message to ask the device to return the [[UserId]] of the currently logged in user. Will return with a
   * [[Successful(userId)]] message
   */
  case object GetUser extends ActorMessage

  case object GetUserName extends ActorMessage

  /**
   * Fetch [[com.waz.model.RConvId]] of a conversation. Will return a [[Successful(convId: String)]] to the sender
   * @param name the name of the conversation to find the id for
   */
  case class GetConv(name: String) extends ActorMessage

  case class DeleteMessage(convId: RConvId, id: MessageId) extends ActorMessage

  case class RecallMessage(convId: RConvId, id: MessageId) extends ActorMessage

  /**
   * Send a Wire text message to a conversation
 *
   * @param remoteId The (remote) conversation id.
   *                 Note on [[RConvId]]: These are just objects that wrap a string of the conversation Id that the
   *                 backend maintains. The tricky part is that conversations might be referenced differently
   *                 by a device before they are synced by the backend. Hence there may a difference between
   *                 [[RConvId]] and [[com.waz.model.ConvId]]. By default, local Ids are just the [[UserId]] for
   *                 any 1:1 conversation, but for group conversations it might be worth sending a [[GetConv]] message
   *                 first to get the correct ID.
   * @param msg the message to send
   */
  case class SendText(remoteId: RConvId, msg: String) extends ActorMessage

  case class UpdateText(msgId: MessageId, msg: String) extends ActorMessage

  /**
   * Have a device send an image to a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   * @param path the file of an image on the remote processes host
   */
  case class SendImage(remoteId: RConvId, path: String) extends ActorMessage

  case class SendImageData(remoteId: RConvId, image: Array[Byte]) extends ActorMessage

  /**
    * Send generic asset to a conversation. Will return a [[Successful(messageId: String)]] to the sender.
    */
  case class SendAsset(remoteId: RConvId, data: Array[Byte], mime: String, fileName: String, delayPost: Boolean = false) extends ActorMessage


  case class SendGiphy(remoteId: RConvId, searchQuery: String) extends ActorMessage

  case class SendFile(remoteId: RConvId, filePath: String, mime: String) extends ActorMessage

  case class SendLocation(remoteId: RConvId, lon: Float, lat: Float, name: String, zoom: Int) extends ActorMessage

  /**
   * Send a connection request to another user
   * @param userId The user ID of the target user. Note on UserIds: They are just a simple class wrapping the
   *               string representation of the user ID that can be fetched from the backend.
   */
  case class SendRequest(userId: UserId) extends ActorMessage

  /**
   * Accept a connection request from another user
   * @param userId The user ID of the target user. Note on UserIds: @see SendRequest
   */
  case class AcceptConnection(userId: UserId) extends ActorMessage

  /**
   * Create a new group converation
   * @param users a sequence of the [[UserId]]s of any users to be in the group conversation
   */
  case class CreateGroupConversation(users: UserId*) extends ActorMessage

  /**
   * Add members to a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   * @param users a sequence of [[UserId]]s of any users to add to the converation
   */
  case class AddMembers(remoteId: RConvId, users: UserId*) extends ActorMessage


  /**
   * Clear (and archive) a conversation.
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class ClearConversation(remoteId: RConvId) extends ActorMessage

  /**
   * Send a Wire ping (Knock) to a target conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class Knock(remoteId: RConvId) extends ActorMessage

  /**
   * Start 'typing' into the virtual devices text input, to check isTyping works
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class Typing(remoteId: RConvId) extends ActorMessage

  case class SetEphemeral(remoteId: RConvId, ephemeral: Option[FiniteDuration]) extends ActorMessage

  case class MarkEphemeralRead(convId: RConvId, msgId: MessageId) extends ActorMessage

  /**
   * Stop any typing going on in a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class ClearTyping(remoteId: RConvId) extends ActorMessage

  /**
   * Archive a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class ArchiveConv(remoteId: RConvId) extends ActorMessage

  /**
   * Unarchive a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class UnarchiveConv(remoteId: RConvId) extends ActorMessage

  /**
   * Mute a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class MuteConv(remoteId: RConvId) extends ActorMessage

  /**
   * Unmute a conversation
   * @param remoteId The (remote) conversation id. Note on [[RConvId]]: @see SendText
   */
  case class UnmuteConv(remoteId: RConvId) extends ActorMessage

  case class SetMessageReaction(remoteId: RConvId, messageId: MessageId, action: Liking.Action) extends ActorMessage

  /**
   * Change the profile picture of the user logged into the remote device
   * @param path the path to the image file on the classpath (as seen from the DeviceActor class)
   */
  case class UpdateProfileImage(path: String) extends ActorMessage

  /**
   * Change the user name of the user logged into the remote device
   * @param name
   */
  case class UpdateProfileName(name: String) extends ActorMessage

  /**
    * Change the unique user name
    * @param userName
    */
  case class UpdateProfileUserName(userName : String) extends ActorMessage

  /**
   * Change the accent color of the user logged into the remote device
   * @param color an [[AccentColor]] to change the accent color to
   */
  case class UpdateProfileColor(color: AccentColor) extends ActorMessage

  /**
   * Change the email address of the user logged into the remote device
   * @param email the email address as a string
   */
  case class UpdateProfileEmail(email: String) extends ActorMessage

  case object AwaitSyncCompleted extends ActorMessage

  case object ResetQueueStats extends ActorMessage

  case object GetQueueStats extends ActorMessage

  case class QueueStats(reports: Array[QueueReport]) extends ResponseMessage

  case class SetDeviceLabel(label: String) extends ActorMessage

  case class DeleteDevice(clientId: String, password: String) extends ActorMessage

  case class DeleteAllOtherDevices(password: String) extends ActorMessage

  case class GetDeviceId() extends ActorMessage

  case class GetDeviceFingerPrint() extends ActorMessage

  case class MessageInfo(id: MessageId, tpe: Message.Type, time: Instant)

  case class ConvMessages(msgs: Array[MessageInfo]) extends ResponseMessage

  case class GetMessages(remoteId: RConvId) extends ActorMessage

  case object ForceAddressBookUpload extends ActorMessage

  case class SetStatus(status: String) extends ActorMessage
}
