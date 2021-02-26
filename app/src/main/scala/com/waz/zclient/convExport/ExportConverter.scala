package com.waz.zclient.convExport

import java.io.{File, FileNotFoundException}
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

import android.webkit.MimeTypeMap
import android.widget.Toast
import com.waz.api.{KindOfMedia, MediaProvider}
import com.waz.model.{AssetId, ConvId, MessageData, Mime, PictureNotUploaded, PictureUploaded, RemoteInstant, UserData, UserId}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent.ClientAction
import com.waz.model.messages.media.{MediaAssetData, TrackData}
import com.waz.model.nano.Messages
import com.waz.model.nano.Messages.Asset.Original
import com.waz.model.nano.Messages.{Availability, Confirmation, Ephemeral}
import com.waz.service.assets.AssetInput
import com.waz.service.{SearchKey, ZMessaging}
import com.waz.zclient.WireApplication
import com.waz.zclient.log.LogUI.{verbose, _}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.{OutputKeys, TransformerFactory}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, ExecutionContext, Future, blocking}
import com.waz.zclient.R
import com.waz.znet2.http.HttpClient.ProgressCallback

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.Duration

class ExportConverter(exportController: ExportController) extends DerivedLogTag{

  private val dataPath="data"+File.separator
  private val mediaPath=dataPath+"media"+File.separator
  private val profilePath=dataPath+"profileData"+File.separator

  private val userList=ListBuffer[UserId]()
  private var zmsg: ZMessaging= _
  private val documentFactory = DocumentBuilderFactory.newInstance
  private val documentBuilder = documentFactory.newDocumentBuilder
  private val doc = documentBuilder.newDocument
  private var zip: ExportZip= _
  private val debug=true
  private val waitTime=30000000000L // 30 seconds (in nanoseconds)
  private val exportProgress=new ExportProgress(this)

  def getExportProgress: ExportProgress = { exportProgress }

  def export(convIds: Seq[ConvId]): Unit = {
    var sleepTimeAtTheEnd=5000
    exportProgress.currentState=ExportProgressState.INIT
    if(exportController.exportFile.getValue.isEmpty){
      Toast.makeText(WireApplication.APP_INSTANCE.getApplicationContext,
        WireApplication.APP_INSTANCE.getApplicationContext.getString(R.string.export_file_not_set),Toast.LENGTH_LONG)
      verbose(l"############## EXPORT FAILED ##############")
      verbose(l"############ NO FILE SPECIFIED ############")
      return
    }
    val fut=exportController.zms.future.map(z=>zmsg=z)
    Await.ready(fut, Duration.fromNanos(waitTime)).onFailure({case t =>
      verbose(l"######## INTERNAL EXPORT ERROR 01 ########")
      t.printStackTrace()
    })

    verbose(l"------------ START EXPORT ------------")
    exportProgress.currentState=ExportProgressState.STARTED

    if(debug) verbose(l"FILE: ${showString(exportController.exportFile.getValue.get.toString)}")
    try{
      zip=new ExportZip(WireApplication.APP_INSTANCE.getContentResolver.openFileDescriptor(exportController.exportFile.getValue.get, "rwt"), exportController)
    }catch{
      case e: FileNotFoundException =>
        verbose(l"Error: Export file does not exist. Cancel export.")
        exportController.exportFile.onNext(None)
        exportController.cancelExport=true
        sleepTimeAtTheEnd=0
    }

    val root = doc.createElement("chatexport")
    doc.appendChild(root)
    val conversations=addElement(root,"conversations")
    val users=addElement(root,"users")

    if(!exportController.cancelExport){
      if(debug) verbose(l"############ Export Conversations ############")
      exportProgress.currentState=ExportProgressState.CONVERSATIONS
      exportProgress.conversationsTotal=convIds.size
      // ADD CONVERSATIONS
      try{
        for (cid <- convIds) {
          if(exportController.cancelExport) throw new Exception
          conversations.appendChild(createConversationElement(cid))
        }
      }catch{case _: Exception =>}
    }
    if(!exportController.cancelExport){
      if(debug) verbose(l"############ Export Users ############")
      exportProgress.currentState=ExportProgressState.USERS
      var selfId: Option[UserId]=None
      zmsg.users.selfUser.future.withTimeout(Duration.fromNanos(waitTime)).recover({
        case t =>
          verbose(l"######## EXPORT ERROR 02 - SELF ########")
          t.printStackTrace()
          return
      }).onSuccess{case u=>selfId=Some(u.id)}
      // ADD USERS
      val userListDist=userList.toList.distinct
      if(debug) verbose(l"Export: Export user amount: ${showString(userListDist.size.toString)}")
      exportProgress.usersTotal=userListDist.size
      val userAmount=new AtomicInteger(userListDist.size)
      userListDist.takeWhile(_ => !exportController.cancelExport).foreach(u=>exportController.usersController.user(u).future
          .withTimeout(Duration.fromNanos(waitTime))
          .recover({
            case _ =>
              verbose(l"##### EXPORT ERROR 04 - USER NOT FOUND (using empty userdata) #####")
              new UserData(u, name="???", searchKey = SearchKey.Empty)
          }).foreach(ud=>{
            userListDist.synchronized{
              if(!exportController.cancelExport){
                if(debug) verbose(l"Export: User >> ID - ${showString(ud.id.toString)}")
                val user=addElement(users, "user")
                if(selfId.nonEmpty && selfId.get.equals(ud.id)) user.setAttribute("isSelf","true")
                addElement(user,"userid",ud.id.str)
                addElement(user,"name",ud.name.str)
                addElement(user,"accent_color",ud.accent.toString)
                if(debug) verbose(l"Export: User >> ID - ${showString(ud.id.toString)} >> required fields exported")
                ud.handle.foreach(h=>addElement(user,"username",h.string))
                ud.teamId.foreach(tid=>addElement(user,"teamid",tid.str))
                ud.email.foreach(em=>addElement(user,"email",em.str))
                ud.phone.foreach(p=>addElement(user,"phone",p.str))
                ud.trackingId.foreach(tid=>addElement(user,"trackingid",tid.str))
                if(exportController.exportProfilePictures){
                  ud.picture.foreach(p=>{
                    (p match {
                      case p: PictureUploaded => saveAssetIdAndGetFilename(p.id, profilePath, None, None, true).orElse(Some(p.id.str))
                      case p: PictureNotUploaded => saveAssetIdAndGetFilename(AssetId.apply(p.id.str), profilePath, None, None, true).orElse(Some(p.id.str))
                      case _ => None
                    }).foreach(path=>{
                      val pic=addElement(user,"picture", path)
                      addAttribute(pic,"uploaded",p.isInstanceOf[PictureUploaded].toString)
                    })
                  })
                }
                if(ud.fields.nonEmpty) addElement(user,"userfields",ud.fields.toString)
                if(ud.permissions._1!=0 || ud.permissions._2!=0) addElement(user,"permission",ud.permissions._1+" "+ud.permissions._2)
                if(debug) verbose(l"Export: User done >> ID - ${showString(ud.id.toString)}")
              }
              userAmount.synchronized{
                userAmount.decrementAndGet()
                userAmount.notifyAll()
              }
            }
          }))
      userAmount.synchronized{
        blocking{
          while(userAmount.get()>0 && !exportController.cancelExport){
            exportProgress.usersDone=exportProgress.usersTotal-userAmount.get()
            if(debug) verbose(l"Export: Wait for users to be exported: ${showString(userAmount.get().toString)}")
            userAmount.wait()
          }
        }
        if(!exportController.cancelExport) exportProgress.usersDone=exportProgress.usersTotal
      }
    }
    if(!exportController.cancelExport){
      if(debug) verbose(l"############ Export XML to ZIP ############")
      exportProgress.currentState=ExportProgressState.XML
      val transformerFactory = TransformerFactory.newInstance
      val transformer = transformerFactory.newTransformer
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
      transformer.setOutputProperty(OutputKeys.ENCODING,"UTF-16")
      val domSource = new DOMSource(doc)
      zip.writeFile(dataPath+"chats.xml", o=>{
        val streamResult = new StreamResult(o)
        transformer.transform(domSource, streamResult)
      })
      if(exportController.includeHtml && !exportController.cancelExport){
        if(debug) verbose(l"############ Export HTML-VIEWER ############")
        exportProgress.currentState=ExportProgressState.HTML
        zip.addHtmlViewerFiles()
      }
    }
    if(zip!=null) zip.close()
    verbose(l"------------- END EXPORT -------------")
    if(exportController.cancelExport) {
      try{
        if(exportController.exportFile.getValue.nonEmpty)
          WireApplication.APP_INSTANCE.getContentResolver.delete(exportController.exportFile.getValue.get, null)
      }catch{case e: NoSuchMethodError =>}
      exportProgress.currentState=ExportProgressState.CANCELED
    } else
      exportProgress.currentState=ExportProgressState.DONE
    Thread.sleep(sleepTimeAtTheEnd)
  }

  private def createConversationElement(convId: ConvId): Element = {
    if(debug) verbose(l"Export: Conversation >> ID - ${showString(convId.toString)}")
    val conversation=doc.createElement("conversation")
    // ADD CONVERSATION INFORMATION
    if(debug) verbose(l"Export: ConversationData >> ID - ${showString(convId.toString)}")
    exportController.convController.conversationData(convId).future.foreach(o=>o.foreach(cd=>{
      addElement(conversation, "id",cd.id.str)
      addElement(conversation, "remoteid",cd.remoteId.str)
      addElement(conversation,"creator",cd.creator.str)
      addElement(conversation,"convType",cd.convType.toString)
      addElement(conversation,"verified",cd.verified.toString)

      cd.name.foreach(n=>{
        val elem=addElement(conversation,"name",n.str)
        val fut=exportController.convController.conversationName(convId).future
        fut.onSuccess{
          case mn=>elem.setTextContent(mn.str)
        }(ExecutionContext.global)
        Await.ready(fut,Duration.fromNanos(waitTime))
      })

      cd.team.foreach(tid=>addElement(conversation,"teamid",tid.str))
      if(cd.access.nonEmpty){
        val access=addElement(conversation,"access")
        cd.access.foreach(a=>addElement(access,"accesstype",a.toString))
      }
      cd.accessRole.foreach(ar=>addElement(conversation,"accessrole",ar.toString))
      cd.link.foreach(l=>addElement(conversation,"link",l.url))
    }))
    // ADD USERS OF CONVERSATION AND THEIR ROLES
    if(!exportController.cancelExport) {
      if (debug) verbose(l"Export: Users and roles")
      val userroles = addElement(conversation, "userroles")
      val userAddFut = exportController.convController.convMembers(convId).future.map(m => m.foreach({ case (uid, cr) =>
        if (debug) verbose(l"Export: Add users and roles >> User-ID - ${showString(uid.toString)}")
        val userrole = addElement(userroles, "userrole")
        addAttribute(userrole, "userid", uid.str)
        addAttribute(userrole, "role", cr.label)
        userList += uid
      }))
      Await.ready(userAddFut, Duration.fromNanos(waitTime)).onFailure({ case t =>
        verbose(l"##### EXPORT ERROR 05 - USERROLE #####")
        t.printStackTrace()
      })
    }
    // ADD MESSAGES
    if(debug) verbose(l"Export: Messages >> ID - ${showString(convId.toString)}")
    val messagesElem=addElement(conversation, "messages")
    Try(Await.ready(
        if (exportController.timeFrom.isEmpty && exportController.timeTo.isEmpty)
          zmsg.messagesStorage.findMessagesFrom(convId, RemoteInstant(org.threeten.bp.Instant.EPOCH))
        else if (exportController.timeFrom.nonEmpty && exportController.timeTo.isEmpty)
          zmsg.messagesStorage.findMessagesFrom(convId, exportController.timeFrom.get)
        else if (exportController.timeFrom.isEmpty && exportController.timeTo.nonEmpty)
          zmsg.messagesStorage.findMessagesBetween(convId, RemoteInstant(org.threeten.bp.Instant.EPOCH), exportController.timeTo.get)
        else
          zmsg.messagesStorage.findMessagesBetween(convId, exportController.timeFrom.get, exportController.timeTo.get)
      ,Duration.fromNanos(waitTime))) match {
        case Success(f) => f.value.get match {
          case Success(messages) =>
            exportProgress.messagesCurrentConversationTotal=messages.size
            exportProgress.messagesCurrentConversationDone=0
            try{
              for (msg <- messages.sortBy(md => md.time)) {
                if(exportController.cancelExport) throw new Exception
                messagesElem.appendChild(createMessageElement(msg))
                exportProgress.messagesCurrentConversationDone+=1
              }
            }catch{case _: Exception =>}
          case Failure(e) => verbose(l"FAILURE LOADING CONVERSATION MESSAGES : ${showString(e.toString)}")
        }
        case Failure(e) => verbose(l"FAILURE LOADING CONVERSATION MESSAGES : ${showString(e.toString)}")
    }
    exportProgress.conversationsDone+=1
    exportProgress.messagesCurrentConversationTotal = -1
    conversation
  }

  private def createMessageElement(message: MessageData): Element = {
    if(debug) verbose(l"Export: Create Message element >> ID - ${showString(message.id.toString)}")
    val msg=doc.createElement("message")
    addElement(msg, "id", message.id.str)
    addElement(msg, "msg_type", message.msgType.toString)
    addElement(msg, "userid", message.userId.str)
    addElement(msg, "state", message.state.toString)
    addElement(msg, "time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(message.time.toEpochMilli)))
    if(message.localTime.instant.compareTo(RemoteInstant.Epoch.instant)!=0)
      addElement(msg, "local_time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(message.localTime.toEpochMilli)))
    message.error.foreach(e=>{
      val err=addElement(msg, "error")
      addElement(err, "client_id",e.clientId.str)
      addElement(err, "errorcode",e.code.toString)
    })
    if(message.firstMessage) addElement(msg, "first_message", "true")
    if(message.members.nonEmpty){
      val members=addElement(msg, "members")
      message.members.foreach(m=>addElement(members, "uuid", m.str))
    }
    message.recipient.foreach(r=>addElement(msg, "recipient", r.str))
    if(message.editTime.compareTo(RemoteInstant.Epoch)!=0) addElement(msg, "edit_time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(message.editTime.toEpochMilli)))
    message.ephemeral.foreach(d=>{
      val members=addElement(msg, "ephemeral")
      addElement(members, "length", d.length.toString)
      addElement(members, "timeunit", d.unit.toString)
    })
    message.expiryTime.foreach(t=>addElement(msg, "expiry_time", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(t.toEpochMilli))))
    message.duration.foreach(d=>{
      val members=addElement(msg, "duration")
      addElement(members, "length", d.length.toString)
      addElement(members, "timeunit", d.unit.toString)
    })
    message.assetId.foreach(aid=>addElement(msg, "asset_id", aid.str))
    message.quote.foreach(q=>{
      val quote=addElement(msg, "quote", q.message.str)
      quote.setAttribute("validity",q.validity.toString)
    })
    if(debug) verbose(l"Export: Add Message content >> ID - ${showString(message.id.toString)}")
    if(message.content.nonEmpty){
      val contents=addElement(msg, "contents")
      message.content.foreach(mc=>{
        val msgc=addElement(contents, "content")
        addElement(msgc, "type", mc.tpe.toString)
        addElement(msgc, "content", mc.content)
        mc.asset.foreach(aid=>addElement(msgc, "asset_id", aid.str))
        if(mc.width>0) addElement(msgc, "width", mc.width.toString)
        if(mc.height>0) addElement(msgc, "height", mc.height.toString)
        if(mc.mentions.nonEmpty){
          val mentions=addElement(msgc, "mentions")
          mc.mentions.foreach(m=>{
            val mention=addElement(mentions, "mention")
            addElement(mention, "start", m.start.toString)
            addElement(mention, "length", m.length.toString)
            m.userId.foreach(uid=>addElement(mention, "userid", uid.str))
          })
        }
        mc.richMedia.foreach(mda=>msgc.appendChild(createMediaAssetData(mda)))
        mc.openGraph.foreach(og=>{
          val openGraph=addElement(msgc, "open_graph", og.toString)
          addElement(openGraph,"title",og.title)
          addElement(openGraph,"description",og.description)
          addElement(openGraph,"tpe",og.tpe)
          og.permanentUrl.foreach(url=>addElement(openGraph,"permanenturl",url.toString))
          og.image.foreach(ogi=>addElement(openGraph,"image",ogi.url.toString))
        })
      })
    }
    if(message.protos.nonEmpty) {
      val protos = addElement(msg, "protos")
      message.protos.foreach(gm => {
        val pr = addElement(protos, "proto")
        import Messages.{GenericMessage => GM}
        gm.getContentCase match {
          case GM.ASSET_FIELD_NUMBER => pr.appendChild(createAssetElement(gm.getAsset))
          case GM.CALLING_FIELD_NUMBER => addElement(pr,"calling",gm.getCalling.content)
          case GM.CLEARED_FIELD_NUMBER =>
            val cleared=addElement(pr,"cleared")
            addAttribute(cleared,"timestamp",gm.getCleared.clearedTimestamp.toString)
          case GM.CLIENTACTION_FIELD_NUMBER =>
            addElement(pr,"clientaction", ClientAction(gm.getClientAction).value match {
              case Messages.RESET_SESSION => "RESET_SESSION"
              case Messages.DISABLED => "DISABLED"
              case Messages.ENABLED => "ENABLED"
              case _ => "UNKNOWN"
            })
          case GM.DELETED_FIELD_NUMBER => addElement(pr,"deleted",gm.getDeleted.messageId)
          case GM.EDITED_FIELD_NUMBER =>
            val edited=addElement(pr, "edited")
            addElement(edited,"replacingMessageId",gm.getEdited.replacingMessageId)
            if(gm.getEdited.hasComposite) edited.appendChild(createCompositeElement(gm.getEdited.getComposite))
            if(gm.getEdited.hasText) edited.appendChild(createTextElement(gm.getEdited.getText))
          case GM.EXTERNAL_FIELD_NUMBER =>
            val external=addElement(pr,"external")
            addElement(external, "encryption", gm.getExternal.encryption.toString)
            addElement(external, "otrKey", byteArrayToString(gm.getExternal.otrKey))
            addElement(external, "sha256", byteArrayToString(gm.getExternal.sha256))
          case GM.HIDDEN_FIELD_NUMBER => addElement(pr,"hidden",gm.getHidden.messageId)
          case GM.IMAGE_FIELD_NUMBER => pr.appendChild(createImageElement(gm.getImage))
          case GM.KNOCK_FIELD_NUMBER => pr.appendChild(createKnockElement(gm.getKnock))
          case GM.LASTREAD_FIELD_NUMBER => addElement(pr,"lastread",gm.getLastRead.lastReadTimestamp.toString)
          case GM.REACTION_FIELD_NUMBER =>
            val reaction=addElement(pr,"reaction")
            addElement(reaction,"emoji",gm.getReaction.emoji)
            addElement(reaction,"messageid",gm.getReaction.messageId)
          case GM.TEXT_FIELD_NUMBER => pr.appendChild(createTextElement(gm.getText))
          case GM.LOCATION_FIELD_NUMBER => pr.appendChild(createLocationElement(gm.getLocation))
          case GM.CONFIRMATION_FIELD_NUMBER =>
            val confirmation=addElement(pr,"confirmation")
            addElement(confirmation,"type",
              gm.getConfirmation.`type` match {
                case Confirmation.DELIVERED => "DELIVERED"
                case Confirmation.READ => "READ"
                case _ => "UNKNOWN"
              })
            val messages=addElement(confirmation,"messageids")
            (gm.getConfirmation.moreMessageIds:+gm.getConfirmation.firstMessageId).foreach(mid=>addElement(messages,"uuid",mid))
          case GM.EPHEMERAL_FIELD_NUMBER =>
            gm.getEphemeral.getContentCase match {
              case Ephemeral.ASSET_FIELD_NUMBER =>
                pr.appendChild(createAssetElement(gm.getEphemeral.getAsset,gm.getEphemeral.expireAfterMillis))
              case Ephemeral.IMAGE_FIELD_NUMBER =>
                pr.appendChild(createImageElement(gm.getEphemeral.getImage,gm.getEphemeral.expireAfterMillis))
              case Ephemeral.KNOCK_FIELD_NUMBER =>
                pr.appendChild(createKnockElement(gm.getEphemeral.getKnock,gm.getEphemeral.expireAfterMillis))
              case Ephemeral.LOCATION_FIELD_NUMBER =>
                pr.appendChild(createLocationElement(gm.getEphemeral.getLocation,gm.getEphemeral.expireAfterMillis))
              case Ephemeral.TEXT_FIELD_NUMBER =>
                pr.appendChild(createTextElement(gm.getEphemeral.getText,gm.getEphemeral.expireAfterMillis))
              case _ => // unknown/unsupported type
                val debugElem=doc.createElement("debug_data")
                debugElem.setTextContent(gm.getEphemeral.toString)
                debugElem
            }
          case GM.AVAILABILITY_FIELD_NUMBER =>
            addElement(pr,"availability",gm.getAvailability.`type` match {
              case Availability.AVAILABLE => "AVAILABLE"
              case Availability.AWAY => "AWAY"
              case Availability.BUSY => "BUSY"
              case Availability.NONE => "NONE"
              case _ => "UNKNOWN"
            })
          case GM.COMPOSITE_FIELD_NUMBER => pr.appendChild(createCompositeElement(gm.getComposite))
          case GM.BUTTONACTION_FIELD_NUMBER =>
            val btnAct=addElement(pr,"buttonaction")
            addElement(btnAct, "buttonid", gm.getButtonAction.buttonId)
            addElement(btnAct, "refmessageid", gm.getButtonAction.referenceMessageId)
          case GM.BUTTONACTIONCONFIRMATION_FIELD_NUMBER =>
            val btnAct=addElement(pr,"buttonactionconfirmation")
            addElement(btnAct, "buttonid", gm.getButtonActionConfirmation.buttonId)
            addElement(btnAct, "refmessageid", gm.getButtonActionConfirmation.referenceMessageId)
          case GM.DATATRANSFER_FIELD_NUMBER => addElement(pr, "datatransfer",gm.getDataTransfer.trackingIdentifier.identifier)
          case _ => addElement(pr, "debug_data", gm.toString)
        }
      })
    }
    msg
  }

  private def createCompositeElement(composite: Messages.Composite): Element = {
    if(debug) verbose(l"Export: Create Composite element")
    val comp=doc.createElement("composite")
    composite.items.foreach(item=>{
      if(item.hasText) comp.appendChild(createTextElement(item.getText))
      if(item.hasButton){
        val button=item.getButton
        val buttonElem=doc.createElement("button")
        addElement(buttonElem,"id",button.id)
        addElement(buttonElem,"text",button.text)
      }
    })
    comp
  }

  private def createMediaAssetData(mda: MediaAssetData, tagname: String = "rich_media"): Element = {
    if(debug) verbose(l"Export: Create MediaAsset element")
    val richMedia=doc.createElement(tagname)
    addElement(richMedia,"kind",mda.kind match {
      case KindOfMedia.PLAYLIST => "PLAYLIST"
      case KindOfMedia.TRACK => "TRACK"
      case _ => "UNKNOWN"
    })
    addElement(richMedia,"provider",mda.provider match {
      case MediaProvider.YOUTUBE => "YOUTUBE"
      case MediaProvider.SOUNDCLOUD => "SOUNDCLOUD"
      case MediaProvider.SPOTIFY => "SPOTIFY"
      case _ => "UNKNOWN"
    })
    addElement(richMedia,"title",mda.title)
    addElement(richMedia,"linkurl",mda.linkUrl)
    mda.artist.foreach(ad=>{
      val artist=addElement(richMedia,"artist")
      addElement(artist,"name",ad.name)
      if(exportController.exportFiles) ad.avatar.foreach(a => saveAssetIdAndGetFilename(a, mediaPath).orElse(Some(a.str)).foreach(path=>addElement(artist, "avatar", path)))
    })
    mda.duration.foreach(d=>addElement(richMedia,"duration",d.toMillis.toString))
    if(exportController.exportFiles) mda.artwork.foreach(a=>saveAssetIdAndGetFilename(AssetId.apply(a.str), mediaPath).orElse(Some(a.str)).foreach(path=>addElement(richMedia,"artwork",path)))
    if(mda.expires.compareTo(org.threeten.bp.Instant.EPOCH)!=0) addElement(richMedia,"expires",mda.expires.toEpochMilli.toString)
    if(mda.tracks.nonEmpty){
      val tracks=addElement(richMedia,"tracks")
      mda.tracks.filter(p=>p!=mda).foreach(td=>tracks.appendChild(createMediaAssetData(td,"track")))
    }
    mda match {
      case TrackData(_, _, _, _, _, _, streamable, streamUrl, previewUrl, _) =>
        addElement(richMedia,"streamable",streamable.toString)
        streamUrl.foreach(u=>addElement(richMedia,"streamurl",u))
        previewUrl.foreach(u=>addElement(richMedia,"previewurl",u))
      //case PlaylistData(provider, title, artist, linkUrl, artwork, duration, tracks, expires) => // No difference -> ignore
      case _ =>
    }
    richMedia
  }

  private def createImageElement(m_image: Messages.ImageAsset, expireMillis: Long = -1): Element = {
    if(debug) verbose(l"Export: Create Image element")
    val image=doc.createElement("image")
    if(expireMillis>=0)
      image.setAttribute("expiremillis",expireMillis.toString)
    addElement(image,"mimeType",m_image.mimeType)
    addElement(image,"tag",m_image.tag)
    addElement(image,"width",m_image.width.toString)
    addElement(image,"height",m_image.height.toString)
    addElement(image,"mac",byteArrayToString(m_image.mac))
    addElement(image,"macKey",byteArrayToString(m_image.macKey))
    addElement(image,"originalWidth",m_image.originalWidth.toString)
    addElement(image,"originalHeight",m_image.originalHeight.toString)
    addElement(image,"otrKey",byteArrayToString(m_image.otrKey))
    addElement(image,"sha256",byteArrayToString(m_image.sha256))
    addElement(image,"size",m_image.size.toString)
    image
  }

  private def createKnockElement(m_knock: Messages.Knock, expireMillis: Long = -1): Element = {
    if(debug) verbose(l"Export: Create Knock element")
    val knock=doc.createElement("knock")
    if(expireMillis>=0)
      knock.setAttribute("expiremillis",expireMillis.toString)
    knock.setAttribute("hotknock",m_knock.hotKnock.toString)
    knock
  }

  private def createLocationElement(m_location: Messages.Location, expireMillis: Long = -1): Element = {
    if(debug) verbose(l"Export: Create Location element")
    val location=doc.createElement("location")
    if(expireMillis>=0)
      location.setAttribute("expiremillis",expireMillis.toString)
    addElement(location,"latitude",m_location.latitude.toString)
    addElement(location,"longitude",m_location.longitude.toString)
    addElement(location,"name",m_location.name)
    addElement(location,"zoom",m_location.zoom.toString)
    location
  }

  private def createTextElement(m_text: Messages.Text, expireMillis: Long = -1): Element = {
    if(debug) verbose(l"Export: Create Text element")
    val text=doc.createElement("text")
    if(expireMillis>=0)
      text.setAttribute("expiremillis",expireMillis.toString)
    addElement(text,"content",m_text.content)
    if(m_text.linkPreview.nonEmpty){
      val linkPreviews=addElement(text,"linkpreviews")
      m_text.linkPreview.foreach(pv=>{
        val linkPreview=addElement(linkPreviews,"linkpreview")
        if(pv.image!=null) linkPreview.appendChild(createAssetElement(pv.image, tagname="image"))
        addElement(linkPreview,"permanenturl",pv.permanentUrl)
        addElement(linkPreview,"url",pv.url)
        addElement(linkPreview,"title",pv.title)
        addElement(linkPreview,"summary",pv.summary)
        addElement(linkPreview,"urloffset",pv.urlOffset.toString)
        if(pv.hasTweet){
          val tweet=addElement(linkPreview,"tweet")
          addElement(tweet,"author", pv.getTweet.author)
          addElement(tweet,"username", pv.getTweet.username)
        }
      })
    }
    if(m_text.mentions.nonEmpty){
      val mentions=addElement(text, "mentions")
      m_text.mentions.foreach(m=>{
        val mention=addElement(mentions, "mention")
        addElement(mention, "start", m.start.toString)
        addElement(mention, "length", m.length.toString)
        if(m.hasUserId) addElement(mention,"userid",m.getUserId)
      })
    }
    if(m_text.quote!=null) addElement(text,"quote",m_text.quote.quotedMessageId)
    text
  }

  private def createAssetElement(m_asset: Messages.Asset, expireMillis: Long = -1, tagname: String = "asset"): Element = {
    if(debug) verbose(l"Export: Create Asset element")
    if(m_asset==null){
      val error=doc.createElement("debug_data")
      error.setTextContent("ERROR: GenericMessage of type ASSET_FIELD_NUMBER has no Asset (getAsset==null)")
      return error
    }
    val asset=doc.createElement(tagname)
    if(expireMillis>=0)
      asset.setAttribute("expiremillis",expireMillis.toString)
    if(m_asset.original!=null){
      addElement(asset,"mime_type",m_asset.original.mimeType)
      addElement(asset,"name",m_asset.original.name)
      addElement(asset,"size",m_asset.original.size.toString)
      val metadata=addElement(asset,"metadata")
      m_asset.original.getMetaDataCase match {
        case Original.AUDIO_FIELD_NUMBER =>
          val audio=addElement(metadata,"audio")
          addElement(audio,"duration_in_millis",m_asset.original.getAudio.durationInMillis.toString)
          addElement(audio,"normalized_loudness",byteArrayToString(m_asset.original.getAudio.normalizedLoudness))
          addAttribute(metadata,"type","AUDIO")
        case Original.IMAGE_FIELD_NUMBER =>
          val image=addElement(metadata,"image")
          addElement(image,"width",m_asset.original.getImage.width.toString)
          addElement(image,"height",m_asset.original.getImage.height.toString)
          addElement(image,"tag",m_asset.original.getImage.tag)
          addAttribute(metadata,"type","IMAGE")
        case Original.VIDEO_FIELD_NUMBER =>
          val video=addElement(metadata,"video")
          addElement(video,"duration_in_millis",m_asset.original.getVideo.durationInMillis.toString)
          addElement(video,"width",m_asset.original.getVideo.width.toString)
          addElement(video,"height",m_asset.original.getVideo.height.toString)
          addAttribute(metadata,"type","VIDEO")
        case _=>
          addElement(metadata,"unknown",m_asset.original.toString)
          addAttribute(metadata,"type","UNKNOWN")
      }
      if(exportController.exportFiles) {
        if (m_asset.getUploaded != null) {
          val assetId=AssetId.apply(m_asset.getUploaded.assetId)
          var extension=assetIdGetFileExtension(assetId)
          if (extension.isEmpty) extension = Some(MimeTypeMap.getSingleton.getExtensionFromMimeType(m_asset.original.mimeType))
          var foundExtension:Option[String]=None
          val split=m_asset.original.name.split("\\.")
          if(split.length>1 && (Mime.fromExtension(split.last).ne(Mime.Unknown) || MimeTypeMap.getSingleton.hasExtension(split.last)))
            foundExtension=Some(split.last)
          val path=saveAssetIdAndGetFilename(assetId, mediaPath, Some(m_asset.original.name + (if (foundExtension.isEmpty) "."+extension.getOrElse("") else "")))

          path.foreach(p=>addElement(asset, "filepath",p)) // add filepath if no error occoured
        }else{
          verbose(l"NOT UPLOADED: ${showString(m_asset.toString)}")
        }
      }
    }
    if(m_asset.preview!=null){
      if(m_asset.original==null)
        verbose(l"ONLY PREVIEWDATA IS THERE!")
      else
        verbose(l"PREVIEWDATA IS THERE!")
    }
    asset
  }

  private def saveAssetIdAndGetFilename(assetId: AssetId, folder: String, filename: Option[String] = None, convId: Option[ConvId] = None, loadPublicOnly: Boolean = false): Option[String] = {
    if(debug) verbose(l"Export: Save Asset (${showString(assetId.str)})")
    var file=filename
    if(file.getOrElse("").equals("")) file=None
    var path: Option[String]=None
    val callback=new ProgressCallback {
      override def updated(progress: Long, total: Option[Long]): Unit = {
        exportProgress.assetDownloadTotal=total.getOrElse(-1)
        exportProgress.assetDownloadDone=progress
        if(total.nonEmpty && total.get.equals(progress)) exportProgress.assetDownloadDone = -1
        if(debug) verbose(l"Export: Asset download progress: ${showString(progress.toString)}/${showString(total.map(l=>l.toString).getOrElse("?"))}")
      }
    }
    val assetReceiveFuture: Future[AssetInput] = {
      if(loadPublicOnly){
        if(debug) verbose(l"Export: Try public Asset download (${showString(assetId.str)})")
        zmsg.assetService.loadPublicContentById(assetId,convId, Some(callback))
      }
      else{
        if(debug) verbose(l"Export: Try Asset download (${showString(assetId.str)})")
        zmsg.assetService.loadContentById(assetId, Some(callback)).fallbackTo({
          if(debug) verbose(l"Export: Asset not found locally, try public content (${showString(assetId.str)})")
          zmsg.assetService.loadPublicContentById(assetId,convId, Some(callback))
        })
      }
    }
    Try(Await.ready(assetReceiveFuture, Duration.fromNanos(waitTime))) match {
      case Success(f) =>
        f.value.get match {
          case Success(ai) =>
            ai.toInputStream.foreach(is => {
              path=Some(folder + file.getOrElse(assetId.str+assetIdGetFileExtension(assetId).map(a=>"."+a).getOrElse("")))
              if(debug) verbose(l"Export: Save Asset (${showString(assetId.str)}) to ${showString(path.toString)}")
              zip.doSynchronizedToZip(()=>{
                if(zip.fileExists(path.get)){
                  path=Some(folder + assetId.str+assetIdGetFileExtension(assetId).map(a=>"."+a).getOrElse(""))
                  if(debug) verbose(l"Export: (File already exists ==> ) Save Asset (${showString(assetId.str)}) to ${showString(path.toString)}")
                }
                if(!zip.fileExists(path.get)) zip.writeFile(path.get,is)
              })
              if(debug) verbose(l"Export: Asset (${showString(assetId.str)}) saved to ${showString(path.toString)}")
            })
          case Failure(exception) => verbose(l"FAILURE LOADING ASSET : ${showString(exception.toString)}")
        }
      case Failure(exception) => verbose(l"FAILURE LOADING ASSET : ${showString(exception.toString)}")
    }
    exportProgress.assetDownloadDone = -1
    exportProgress.assetDownloadTotal = -1
    path
  }

  private def assetIdGetFileExtension(assetId: AssetId): Option[String] = {
    if(debug) verbose(l"Export: AssetID get extension: (${showString(assetId.str)})")
    var extension: Option[String]=None
    zmsg.assetService.getAsset(assetId).withTimeout(Duration.fromNanos(2000000000L)).map(a=>extension=Some(a.mime.extension))
    extension
  }

  private def byteArrayToString(data: Array[Byte]): String ={
    data.toVector.mkString(",")
  }

  private def addAttribute(parent: Element, name: String, content: String): Unit = {
    this.synchronized {
      parent.setAttribute(name, content)
    }
  }

  private def addElement(parent: Element, tagname: String, content: String): Element={
    val elem=addElement(parent,tagname)
    elem.setTextContent(content)
    elem
  }

  private def addElement(parent: Element, tagname: String): Element={
    var elem: Element=null
    this.synchronized{
      elem = doc.createElement(tagname)
      parent.appendChild(elem)
    }
    elem
  }
}
