package com.waz.zclient.convExport.fragments

import android.content.{Context, Intent}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.convExport.ExportController
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator}

class ExportView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private lazy val exportController = inject[ExportController]
  private lazy val nextSelectionPage=() => loadNextView(ExportConfigurationFragment.Tag)
  private val activityStarter=(intent, resId) => {startActivityForResult(intent, resId)}

  def loadView(page: String): Unit = {
    page match {
      case ExportConfigurationFragment.Tag =>
        load(new ExportConfigurationFragment(context, activityStarter))
      case ExportSelectionView.Tag if exportController.currentExport.getValue.isEmpty =>
        load(new ExportSelectionView(context, nextSelectionPage))
      case ExportSelectionView.Tag if exportController.currentExport.getValue.nonEmpty=>
        load(new ExportConfigurationFragment(context, activityStarter))
      case _ =>
        load(new ExportSelectionView(context, nextSelectionPage))
    }
  }
  private def load(node: LinearLayout) = {
    this.removeAllViews()
    this.addView(node)
  }
  private def loadNextView(page: String): Unit ={
    val navigator = inject[BackStackNavigator]
    navigator.goTo(ExportKey(returning(new Bundle)(_.putString(ExportFragment.PageToOpenArg, page))))
  }
  private def startActivityForResult(intent: Intent, resultId: Int): Unit ={
    context.asInstanceOf[PreferencesActivity].startActivityForResult(intent, resultId)
  }
}

case class ExportKey(args: Bundle = new Bundle()) extends BackStackKey(args){
  override def nameId: Int = R.string.pref_account_export_title

  override def layoutId: Int = R.layout.preferences_conversation_export

  override def onViewAttached(v: View): Unit = {
    v.asInstanceOf[ExportView].loadView(args.getString(ExportFragment.PageToOpenArg))
  }

  override def onViewDetached(): Unit = {}
}
