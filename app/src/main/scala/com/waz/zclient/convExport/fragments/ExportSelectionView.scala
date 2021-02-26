package com.waz.zclient.convExport.fragments

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{BaseAdapter, Button, CheckBox, CompoundButton, LinearLayout, ListView, TextView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData
import com.waz.zclient.convExport.ExportController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{R, ViewHelper}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class ExportSelectionView(context: Context, onNext: () => Unit, attrs: AttributeSet = null, style: Int = 0)
  extends LinearLayout(context, attrs, style) with ViewHelper with DerivedLogTag with ExportFragmentChild{

  inflate(R.layout.fragment_export_selection)

  private lazy val exportController = inject[ExportController]
  private lazy val exportSelectionListView=findById[ListView](R.id.export_selection)
  private lazy val exportConfirmSelection=findById[Button](R.id.export_confirm_selection)
  private lazy val convController = inject[ConversationController]

  val conversationList: immutable.IndexedSeq[Doublet[ConversationData, Boolean]] =
    exportController.convStorage.contents.currentValue.getOrElse(Map.empty).values.toIndexedSeq.map(c=>Doublet(c, false))

  exportController.exportConvIds.foreach(
    seq=>seq.foreach(id=>conversationList.filter(d=>d._1.id.equals(id)).foreach(d=>d._2=true))
  )

  exportConfirmSelection.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      exportController.exportConvIds=Some(conversationList.filter(d=>d._2).map(d=>d._1.id))
      onNext.apply()
    }
  })

  exportSelectionListView.setAdapter(new BaseAdapter {
    override def getCount: Int = {
      conversationList.size
    }

    override def getItem(position: Int): ConversationData = {
      conversationList(position)._1
    }

    override def getItemId(position: Int): Long = {
      position
    }

    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      var view=convertView
      if(view==null){
        val root = new LinearLayout(getContext)
        root.setOrientation(LinearLayout.HORIZONTAL)
        val tvName=new TextView(getContext)
        tvName.setTextSize(20f)
        tvName.setId(1)
        val tvCheck=new CheckBox(getContext)
        tvCheck.setId(2)
        tvCheck.setTextSize(20f)
        root.setPadding(5,5,5,5)
        root.addView(tvCheck)
        root.addView(tvName)
        view=root
      }
      if(view!=null){
        val item=conversationList(position)
        val checkBox=view.findViewById[CheckBox](2)
        checkBox.setChecked(item._2)
        checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener {
          override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
            item._2=isChecked
          }
        })
        view.findViewById[TextView](1).setText(getItem(position).name.map(n=>n.str).getOrElse(getItem(position).id.str))
        val fut=exportController.convController.conversationName(item._1.id).future
        fut.onSuccess{
          case mn=>
            getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
              override def run(): Unit = {
                view.findViewById[TextView](1).setText(mn.str)
            }})
        }(ExecutionContext.global)
      }
      view
    }
  })

  case class Doublet[ConversationData, Boolean](var _1: ConversationData, var _2: Boolean){}

}

object ExportSelectionView {
  val Tag: String = getClass.getSimpleName
}
