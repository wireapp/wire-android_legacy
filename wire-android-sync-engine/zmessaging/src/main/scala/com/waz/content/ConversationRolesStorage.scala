package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.ConversationRoleAction.ConversationRoleActionDao
import com.waz.model.{ConvId, ConversationAction, ConversationRole, ConversationRoleAction}
import com.waz.threading.Threading
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.DB
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.Future

trait ConversationRolesStorage extends CachedStorage[(String, String, Option[ConvId]), ConversationRoleAction] {
  def getRoleByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole]
  def getRoleByConvId(convId: ConvId): Future[Set[ConversationRole]]
  def defaultRoles: Future[Set[ConversationRole]]

  def createOrUpdate(convId: ConvId, roles: Set[ConversationRole]): Future[Unit]
  def setDefault(roles: Set[ConversationRole]): Future[Unit]
}

class ConversationRolesStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[(String, String, Option[ConvId]), ConversationRoleAction](
    new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationRoleActionDao, LogTag("ConversationRolesStorage_Cached")
  ) with ConversationRolesStorage {

  override def getRoleByLabel(label: String, convId: Option[ConvId] = None): Future[ConversationRole] =
    find({ cra => cra.label == label && cra.convId == convId }, ConversationRoleActionDao.findForRoleAndConv(label, convId)(_), identity).map { res =>
      val actionNames = res.map(_.action).toSet
      ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
    }(Threading.Background)

  override def getRoleByConvId(convId: ConvId): Future[Set[ConversationRole]] = roleByConvId(Some(convId))

  override def defaultRoles: Future[Set[ConversationRole]] = roleByConvId(None)

  override def createOrUpdate(convId: ConvId, roles: Set[ConversationRole]): Future[Unit] = ???

  override def setDefault(roles: Set[ConversationRole]): Future[Unit] = ???
  
  private def roleByConvId(convId: Option[ConvId]) =
    find({ _.convId == convId }, ConversationRoleActionDao.findForConv(convId)(_), identity).map {
      _.groupBy(_.label).map { case (label, actions) =>
        val actionNames = actions.map(_.action).toSet
        ConversationRole(label, ConversationAction.allActions.filter(ca => actionNames.contains(ca.name)))
      }.toSet
    }(Threading.Background)
}
