package com.waz.zclient.legalhold

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.StringRes
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.FragmentHelper
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.R
import com.waz.zclient.ui.text.TypefaceTextView
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

  private def setMessage(): Unit = {
    getIntArg(ARG_MESSAGE_RES).foreach { messageResId =>
      infoMessageTextView.foreach(_.setText(messageResId))
    }
  }

  private def setUpRecyclerView(): Unit = {
    subjectsRecyclerView.foreach { recyclerView =>
      recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
      recyclerView.setAdapter(adapter)
    }
  }
}

object LegalHoldInfoFragment {

  trait Container {
    val legalHoldUsers: Signal[Seq[UserId]]
  }

  private val MAX_PARTICIPANTS = 7
  private val ARG_MESSAGE_RES = "LegalHoldInfoFragment_messageResId"

  def newInstance(@StringRes messageResId: Int): LegalHoldInfoFragment =
    returning(new LegalHoldInfoFragment()) { frag =>
      val bundle = new Bundle()
      bundle.putInt(ARG_MESSAGE_RES, messageResId)
      frag.setArguments(bundle)
    }
}
