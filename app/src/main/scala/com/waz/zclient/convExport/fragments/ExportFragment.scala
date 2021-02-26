package com.waz.zclient.convExport.fragments

import android.content.Intent
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout}
import androidx.annotation.Nullable
import com.waz.utils.returning
import com.waz.zclient.convExport.ExportController
import com.waz.zclient.log.LogUI.verbose
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{ManagerFragment, R}

class ExportFragment extends ManagerFragment {
  import ExportFragment._
  override def contentId: Int = R.id.fl__export__container
  private lazy val exportController = inject[ExportController]
  private var childView: Option[LinearLayout with ExportFragmentChild] = None
  private val activityStarter: (Intent, Int) => Unit=(intent, resId) => {startActivityForResult(intent, resId)}

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_export, container, false)

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    verbose(l"onViewCreated.")
    childView=Some(getStringArg(PageToOpenArg) match {
      case Some(ExportConfigurationFragment.Tag) =>
          new ExportConfigurationFragment(getContext, activityStarter)
      case Some(ExportSelectionView.Tag) if exportController.currentExport.getValue.isEmpty =>
          new ExportSelectionView(getContext, () => { exportController.onShowExport ! Some(ExportConfigurationFragment.Tag) })
      case Some(ExportSelectionView.Tag) if exportController.currentExport.getValue.nonEmpty => // currently running export
        new ExportConfigurationFragment(getContext, activityStarter)
      case _ =>
        new ExportSelectionView(getContext, () => { exportController.onShowExport ! Some(ExportConfigurationFragment.Tag) })
    })
    childView.foreach(v=>getView.findViewById(R.id.fl__export__container).asInstanceOf[FrameLayout].addView(v))
  }

  override def onBackPressed(): Boolean = {
    getFragmentManager.popBackStack()
    true
  }

  override def onDestroyView(): Unit = {
    childView.foreach(v=>v.onDestroyView())
    super.onDestroyView()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent): Unit = {
    childView.foreach(v=>v.onActivityResult(requestCode, resultCode, resultData))
    super.onActivityResult(requestCode, resultCode, resultData)
  }
}

object ExportFragment {
  val TAG: String = classOf[ExportFragment].getName
  val PageToOpenArg = "ARG__FIRST__PAGE"

  def newInstance(page: Option[String]): ExportFragment =
    returning(new ExportFragment) { f =>
      page.foreach { p =>
        f.setArguments(returning(new Bundle)(_.putString(PageToOpenArg, p)))
      }
    }
}

trait ExportFragmentChild{
  def onDestroyView(): Unit = {}
  def onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent): Unit = {}
}
