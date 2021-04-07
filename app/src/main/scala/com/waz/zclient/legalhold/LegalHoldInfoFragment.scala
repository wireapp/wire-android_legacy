package com.waz.zclient.legalhold

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{FragmentHelper, R}
import com.wire.signals.Signal

class LegalHoldInfoFragment extends BaseFragment[LegalHoldInfoFragment.Container]()
  with FragmentHelper {

  import LegalHoldInfoFragment._

  private lazy val infoMessageTextView = view[TypefaceTextView](R.id.legal_hold_info_message_text_view)
  private lazy val subjectsRecyclerView = view[RecyclerView](R.id.legal_hold_info_subjects_recycler_view)

  private lazy val adapter = new LegalHoldUsersAdapter(users, MAX_PARTICIPANTS)

  private lazy val users = getContainer.legalHoldUsers.map(_.toSet)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_legal_hold_info, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    setMessage()
    setUpRecyclerView()
  }

  private def setMessage(): Unit =
    infoMessageTextView.foreach { textView =>
      getIntArg(ARG_MESSAGE_RES_ID).foreach(textView.setText)
    }

  private def setUpRecyclerView(): Unit =
    subjectsRecyclerView.foreach { recyclerView =>
      recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
      recyclerView.setAdapter(adapter)
    }
}

object LegalHoldInfoFragment {

  trait Container {
    val legalHoldUsers: Signal[Seq[UserId]]
  }

  val TAG = "LegalHoldInfoFragment"
  private val MAX_PARTICIPANTS = 7
  val ARG_MESSAGE_RES_ID = "messageResId_Arg"

  def newInstance(messageResId: Int): LegalHoldInfoFragment =
    returning(new LegalHoldInfoFragment()) { frag =>
      val args = returning(new Bundle()) {
        _.putInt(ARG_MESSAGE_RES_ID, messageResId)
      }
      frag.setArguments(args)
    }
}
