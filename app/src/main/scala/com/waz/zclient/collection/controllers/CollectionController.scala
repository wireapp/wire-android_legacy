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
package com.waz.zclient.collection.controllers

import java.lang.Math.min

import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import com.waz.api.{ContentSearchQuery, IConversation, Message, TypeFilter}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events.{EventStream, Signal, SourceSignal}
import com.waz.zclient.collection.controllers.CollectionController.CollectionInfo
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{Injectable, Injector}

class CollectionController(implicit injector: Injector)
  extends Injectable with DerivedLogTag {

  private implicit val dispatcher = new SerialDispatchQueue(name = "CollectionController")

  val zms = inject[Signal[ZMessaging]]

  lazy val convController = inject[ConversationController]

  val msgStorage = zms.map(_.messagesStorage)

  val assetStorage = zms.map(_.assetsStorage)

  private var observers = Set.empty[CollectionsObserver]

  //val conversation = zms.zip(currentConv) flatMap { case (zms, convId) => zms.convsStorage.signal(convId) }

  lazy val conversationName = convController.currentConv map (data => if (data.convType == IConversation.Type.GROUP) data.name.filter(!_.isEmpty).getOrElse(data.generatedName) else data.generatedName)

  val focusedItem: SourceSignal[Option[MessageData]] = Signal(None)

  val openedCollection = Signal[Option[CollectionInfo]]()

  val openContextMenuForMessage = EventStream[MessageData]()

  val clickedMessage = EventStream[MessageData]()

  val contentSearchQuery = Signal[ContentSearchQuery](ContentSearchQuery.empty)

  lazy val matchingTextSearchMessages = for {
    z <- zms
    convId <- convController.currentConvId
    query <- contentSearchQuery
    res <- if (query.isEmpty) Signal.const(Set.empty[MessageId])
    else Signal future z.messagesIndexStorage.matchingMessages(query, Some(convId))
  } yield res

  def openCollection() = observers foreach { _.openCollection() }

  def closeCollection() = { observers foreach { _.closeCollection() }; openedCollection ! None }

  def addObserver(collectionsObserver: CollectionsObserver): Unit = observers += collectionsObserver

  def removeObserver(collectionsObserver: CollectionsObserver): Unit = observers -= collectionsObserver

  def clearSearch() = {
    focusedItem ! None
    contentSearchQuery ! ContentSearchQuery.empty
  }
}

object CollectionController {

  val GridColumns = 4
  def injectedCollectionController(injectable: Injectable)(implicit injector: Injector): CollectionController =  {
    injectable.inject[CollectionController]
  }

  case class CollectionInfo(conversation: ConversationData, empty: Boolean)

  trait ContentType {
    val msgTypes: Seq[Message.Type]
    lazy val typeFilter: Seq[TypeFilter] = msgTypes.map(TypeFilter(_, None))
  }

  case object Links extends ContentType {
    override val msgTypes = Seq(Message.Type.RICH_MEDIA)
  }

  case object Images extends ContentType {
    override val msgTypes = Seq(Message.Type.IMAGE_ASSET)
  }

  //Now we can add more types to this sequence for the "others" category
  case object Files extends ContentType {
    override val msgTypes = Seq(Message.Type.ANY_ASSET, Message.Type.AUDIO_ASSET, Message.Type.VIDEO_ASSET)
  }

  case object AllContent extends ContentType {
    //feels a little bit messy... maybe think of a neater way to represent the types
    override val msgTypes = Images.msgTypes ++ Files.msgTypes ++ Links.msgTypes
    override lazy val typeFilter: Seq[TypeFilter] = Seq(
      TypeFilter(Message.Type.IMAGE_ASSET, Some(8)),
      TypeFilter(Message.Type.RICH_MEDIA, Some(3)),
      TypeFilter(Message.Type.ANY_ASSET, Some(3)),
      TypeFilter(Message.Type.AUDIO_ASSET, Some(3)),
      TypeFilter(Message.Type.VIDEO_ASSET, Some(3))
    )
  }
}

object CollectionUtils {
  def getHighlightedSpannableString(originalMessage: String, normalizedMessage: String, queries: Set[String], color: Int, beginThreshold: Int = -1): (SpannableString, Int) = {

    def getQueryPosition(normalizedMessage: String, query: String, fromIndex: Int = 0, acc: Seq[(Int, Int)] = Seq()): Seq[(Int, Int)] = {
      val beginIndex = normalizedMessage.indexOf(query, fromIndex)
      if (beginIndex < 0) acc
      else {
        val endIndex = min(beginIndex + query.length, normalizedMessage.length)
        getQueryPosition(normalizedMessage, query, endIndex, acc ++ (if (beginIndex > 0 && normalizedMessage.charAt(beginIndex - 1).isLetterOrDigit) Seq.empty else Seq((beginIndex, endIndex))))
      }
    }

    val matches = queries.map(getQueryPosition(normalizedMessage, _))
    if (matches.exists(_.isEmpty)) (new SpannableString(originalMessage), 0)
    else {
      val flatMatches = matches.flatten.filter(_._1 >= 0)
      if (flatMatches.isEmpty) {
        (new SpannableString(originalMessage), 0)
      } else {
        val minPos = if (beginThreshold == -1) 0 else Math.max(flatMatches.map(_._1).min - beginThreshold, 0)
        val ellipsis = if (minPos > 0) "..." else ""
        val spannableString = new SpannableString(ellipsis + originalMessage.substring(minPos))
        val offset = minPos - ellipsis.length
        flatMatches.foreach(pos => spannableString.setSpan(new BackgroundColorSpan(color), pos._1 - offset, pos._2 - offset, 0))
        (spannableString, flatMatches.size)
      }
    }
  }
}
