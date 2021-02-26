package com.waz.zclient.`export`.fragments

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.Nullable
import com.waz.utils.returning
import com.waz.zclient.log.LogUI.verbose
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{ManagerFragment, R}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExportFragment extends ManagerFragment {
  import ExportFragment._
  override def contentId: Int = R.id.fl__export__container

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_export, container, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    verbose(l"onViewCreated.")
    withChildFragmentOpt(R.id.fl__export__container) {
      case Some(_) => //no action to take, view was already set
      case _ =>
        (getStringArg(PageToOpenArg) match {
          case Some(ExportConfigurationFragment.Tag) =>
            Future.successful((new ExportConfigurationFragment, ExportConfigurationFragment.Tag))
          case _ =>
            Future.successful((new ExportConfigurationFragment, ExportConfigurationFragment.Tag))
        }).map {
          case (f, tag) =>
            getChildFragmentManager.beginTransaction
              .replace(R.id.fl__export__container, f, tag)
              .addToBackStack(tag)
              .commit
        }
    }
  }

  override def onBackPressed(): Boolean = {
    getChildFragmentManager.popBackStack()
    getFragmentManager.popBackStack()
    true
  }

}

object ExportFragment {
  val TAG: String = classOf[ExportFragment].getName
  private val PageToOpenArg = "ARG__FIRST__PAGE"

  def newInstance(page: Option[String]): ExportFragment =
    returning(new ExportFragment) { f =>
      page.foreach { p =>
        f.setArguments(returning(new Bundle)(_.putString(PageToOpenArg, p)))
      }
    }
}
