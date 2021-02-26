package com.waz.zclient.convExport.fragments;

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import android.app.{Activity, DatePickerDialog, TimePickerDialog}
import android.os.Environment
import android.content.{Context, Intent}
import android.provider.DocumentsContract
import android.view.View.OnClickListener
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{Button, CompoundButton, DatePicker, LinearLayout, ProgressBar, RelativeLayout, Switch, TextView, TimePicker}
import com.google.android.material.textfield.TextInputLayout
import com.waz.model.RemoteInstant
import com.waz.utils.returningF
import com.waz.zclient.convExport.{ExportController, ExportProgress, ExportProgressState}
import com.waz.zclient.{R, ViewHelper}
import io.reactivex.functions.Consumer
import android.icu.util.Calendar
import android.net.Uri
import android.util.AttributeSet
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI.{showString, verbose}
import com.waz.zclient.log.LogUI._
import io.reactivex.disposables.Disposable

class ExportConfigurationFragment (context: Context, intentStarter: (Intent, Int) => Unit, attrs: AttributeSet = null, style: Int = 0)
  extends LinearLayout(context, attrs, style) with ViewHelper with DerivedLogTag with ExportFragmentChild {
  private lazy val exportController = inject[ExportController]

  private lazy val exportButton=findById[Button](R.id.b__export_start)
  private lazy val exportCancelButton=findById[Button](R.id.export_cancel)
  private lazy val exportIncludeMediaSwitch=findById[Switch](R.id.export_include_media)
  private lazy val exportIncludeProfilePicturesSwitch=findById[Switch](R.id.export_include_profile_pictures)
  private lazy val exportIncludeHtmlSwitch=findById[Switch](R.id.export_include_html)
  private lazy val exportLimitFromSwitch=findById[Switch](R.id.export_limit_from)
  private lazy val exportLimitToSwitch=findById[Switch](R.id.export_limit_to)
  private lazy val filePathInput=findById[TextInputLayout](R.id.b__export_file_input)
  private lazy val dateFromInput=findById[Button](R.id.export_date_from)
  private lazy val dateToInput=findById[Button](R.id.export_date_to)

  private lazy val exportLoadingIndicator=findById[LinearLayout](R.id.export_loading_indicator)
  private lazy val exportProgressRunning=findById[ProgressBar](R.id.export_progress_running)
  private lazy val exportProgressState=findById[TextView](R.id.export_progress_state)

  private lazy val exportProgressConversations=findById[ProgressBar](R.id.export_progress_conversations)
  private lazy val exportProgressConversationsCurrent=findById[TextView](R.id.export_progress_conversations_current)
  private lazy val exportProgressConversationsTotal=findById[TextView](R.id.export_progress_conversations_total)

  private lazy val exportProgressUsers=findById[ProgressBar](R.id.export_progress_users)
  private lazy val exportProgressUsersCurrent=findById[TextView](R.id.export_progress_users_current)
  private lazy val exportProgressUsersTotal=findById[TextView](R.id.export_progress_users_total)

  private lazy val exportProgressMessagesContainer=findById[RelativeLayout](R.id.export_progress_messages_container)
  private lazy val exportProgressMessages=findById[ProgressBar](R.id.export_progress_messages)
  private lazy val exportProgressMessagesCurrent=findById[TextView](R.id.export_progress_messages_current)
  private lazy val exportProgressMessagesTotal=findById[TextView](R.id.export_progress_messages_total)

  private lazy val exportProgressAssets=findById[ProgressBar](R.id.export_progress_asset)

  private var dateFrom: Option[RemoteInstant] = None
  private var dateTo: Option[RemoteInstant] = None
  private var currentExportSubscriber: Disposable = _
  private var currentExportFileSubscriber: Disposable = _
  private var currentProgressService: Option[ScheduledExecutorService] = None

  inflate(R.layout.fragment_export_configuration)

  currentExportFileSubscriber=exportController.exportFile.subscribe(new Consumer[Option[Uri]] {
    override def accept(fileOption: Option[Uri]): Unit = {
      getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
        override def run(): Unit = {
          if(fileOption.nonEmpty){
            filePathInput.getEditText.setText(fileOption.get.toString)
            exportButton.setEnabled(true)
          }else{
            filePathInput.getEditText.setText("")
            exportButton.setEnabled(false)
          }
        }})
    }})

  exportProgressRunning.setMax(100)
  exportProgressRunning.setProgress(100)

  currentExportSubscriber=exportController.currentExport.subscribe(new Consumer[Option[ExportProgress]] {
    override def accept(progressOption: Option[ExportProgress]): Unit = {
      getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
        override def run(): Unit = {
          if(progressOption.nonEmpty){
            if(exportController.exportFile.getValue.nonEmpty) filePathInput.getEditText.setText(exportController.exportFile.getValue.get.toString)
            switchToRunningExport(true)
            if(currentProgressService.nonEmpty) currentProgressService.get.shutdown()
            currentProgressService=Some(Executors.newSingleThreadScheduledExecutor())
            val exportProgress=progressOption.get
            currentProgressService.get.scheduleAtFixedRate(new Runnable {
              override def run(): Unit = {
                getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
                  override def run(): Unit = {
                    exportProgressState.setText(exportProgress.currentState.getDescription)

                    exportProgressConversations.setProgress(((exportProgress.conversationsDone.toFloat / exportProgress.conversationsTotal) * 100).toInt)
                    exportProgressConversationsTotal.setText(exportProgress.conversationsTotal.toString)
                    exportProgressConversationsCurrent.setText(exportProgress.conversationsDone.toString)

                    exportProgressUsers.setProgress(((exportProgress.usersDone.toFloat / exportProgress.usersTotal) * 100).toInt)
                    exportProgressUsersTotal.setText(exportProgress.usersTotal.toString)
                    exportProgressUsersCurrent.setText(exportProgress.usersDone.toString)

                    if (exportProgress.messagesCurrentConversationTotal >= 0) {
                      exportProgressMessagesContainer.setVisibility(View.VISIBLE)
                      exportProgressMessages.setProgress(((exportProgress.messagesCurrentConversationDone.toFloat / exportProgress.messagesCurrentConversationTotal) * 100).toInt)
                      exportProgressMessagesTotal.setText(exportProgress.messagesCurrentConversationTotal.toString)
                      exportProgressMessagesCurrent.setText(exportProgress.messagesCurrentConversationDone.toString)
                    } else {
                      exportProgressMessagesContainer.setVisibility(View.INVISIBLE)
                    }

                    if (exportProgress.assetDownloadTotal >= 0 || exportProgress.assetDownloadDone >= 0) {
                      exportProgressAssets.setVisibility(View.VISIBLE)
                      if(exportProgress.assetDownloadTotal>=0) {
                        exportProgressAssets.setIndeterminate(false)
                        exportProgressAssets.setProgress(((exportProgress.assetDownloadDone.toFloat / exportProgress.assetDownloadTotal) * 100).toInt)
                      }else{
                        exportProgressAssets.setIndeterminate(true)
                      }
                    } else {
                      exportProgressAssets.setVisibility(View.INVISIBLE)
                    }
                    if(exportProgress.currentState.equals(ExportProgressState.DONE))
                      exportProgressRunning.setVisibility(View.GONE)
                    else
                      exportProgressRunning.setVisibility(View.VISIBLE)
                  }})
              }
            },0, 200, TimeUnit.MILLISECONDS)
          }else{
            switchToRunningExport(false)
            currentProgressService.foreach(s=>s.shutdown())
            currentProgressService=None
            exportCancelButton.setEnabled(true)
          }
        }
      })
    }
  }, new Consumer[Throwable] {
    override def accept(t: Throwable): Unit = {
      verbose(l"Export: Error - ${showString(t.toString)}")
      t.printStackTrace()
    }
  })

  exportButton.setEnabled(false)
  exportButton.setOnClickListener(new View.OnClickListener {
    override def onClick(v: View): Unit = {
      exportController.`export`()
    }
  })

  exportCancelButton.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      exportController.cancelExport = true
      exportCancelButton.setEnabled(false)
      verbose(l"Export: CANCEL EXPORT - ${showString(exportController.cancelExport.toString)}")
    }
  })

  exportIncludeMediaSwitch.setChecked(exportController.exportFiles)
  exportIncludeMediaSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {exportController.exportFiles=isChecked}
  })

  exportIncludeProfilePicturesSwitch.setChecked(exportController.exportProfilePictures)
  exportIncludeProfilePicturesSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {exportController.exportProfilePictures=isChecked}
  })

  exportIncludeHtmlSwitch.setChecked(exportController.includeHtml)
  exportIncludeHtmlSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {exportController.includeHtml=isChecked}
    })

  dateFrom=exportController.timeFrom
  exportLimitFromSwitch.setChecked(dateFrom.nonEmpty)
  exportLimitFromSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      if(isChecked)
        exportController.timeFrom=dateFrom
      else
        exportController.timeFrom=None
    }
  })
  dateFromInput.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      showDateTimePicker(dateFrom,r=>{
        dateFrom=Some(r)
        val date = new Date()
        date.setTime(r.toEpochMilli)
        val format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        dateFromInput.setText(format.format(date))
        if(exportLimitFromSwitch.isChecked)
          exportController.timeFrom=dateFrom;
      })
    }
  })

  dateTo=exportController.timeTo
  exportLimitToSwitch.setChecked(dateTo.nonEmpty)
  exportLimitToSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
    def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      if(isChecked)
        exportController.timeTo=dateTo
      else
        exportController.timeTo=None
    }
  })
  dateToInput.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      showDateTimePicker(dateTo,r=>{
        dateTo=Some(r)
        val date = new Date()
        date.setTime(r.toEpochMilli)
        val format=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
        dateToInput.setText(format.format(date))
        if(exportLimitToSwitch.isChecked)
          exportController.timeTo=dateTo
      })
    }
  })

  filePathInput.getEditText.setEnabled(false)
  returningF(findById(R.id.b__export_file_select)){ b: Button =>
    b.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        selectFile()
      }
    })
  }

  def switchToRunningExport(running: Boolean): Unit = {
    if (running) {
      exportLoadingIndicator.setVisibility(View.VISIBLE)
    } else {
      exportLoadingIndicator.setVisibility(View.GONE)
    }
  }

  def showDateTimePicker(time: Option[RemoteInstant], callback: RemoteInstant => Unit): Unit = {
    val currentDate = Calendar.getInstance
    val date = Calendar.getInstance
    if(time.nonEmpty)
      date.setTimeInMillis(time.get.toEpochMilli)
    new DatePickerDialog(getContext, new DatePickerDialog.OnDateSetListener() {
      override def onDateSet(view: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int): Unit = {
        date.set(year, monthOfYear, dayOfMonth)
        new TimePickerDialog(getContext, new TimePickerDialog.OnTimeSetListener() {
          def onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int): Unit = {
            date.set(Calendar.HOUR_OF_DAY, hourOfDay)
            date.set(Calendar.MINUTE, minute)
            callback(RemoteInstant.ofEpochMilli(date.getTimeInMillis))
          }
        }, currentDate.get(Calendar.HOUR_OF_DAY), currentDate.get(Calendar.MINUTE), false).show()
      }
    }, currentDate.get(Calendar.YEAR), currentDate.get(Calendar.MONTH), currentDate.get(Calendar.DATE)).show()
  }

  def selectFile(): Unit = {
    val intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("application/zip")
    intent.putExtra(Intent.EXTRA_TITLE, "chatexport.zip")
    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.DIRECTORY_DOCUMENTS)
    intentStarter(intent, ExportConfigurationFragment.SELECT_FILE_REQUEST)
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent): Unit = {
    if (requestCode == ExportConfigurationFragment.SELECT_FILE_REQUEST && resultCode == Activity.RESULT_OK && resultData!=null)
      exportController.exportFile.onNext(Some(resultData.getData))
  }

  override def onDestroyView(): Unit = {
    if(currentExportSubscriber!=null) currentExportSubscriber.dispose()
    currentProgressService.foreach(s=>s.shutdown())
  }
}

object ExportConfigurationFragment {
    val Tag: String = getClass.getSimpleName
    val SELECT_FILE_REQUEST: Int = 1337
}
