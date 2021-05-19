package com.waz.zclient.legalhold

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.model.{ConvId, UserId}
import com.waz.utils.returning
import com.waz.zclient.messages.UsersController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{FragmentHelper, R}
import com.wire.signals.Signal

class LegalHoldInfoFragment extends FragmentHelper {

  import LegalHoldInfoFragment._

  private lazy val infoMessageTextView = view[TypefaceTextView](R.id.legal_hold_info_message_text_view)
  private lazy val subjectsRecyclerView = view[RecyclerView](R.id.legal_hold_info_subjects_recycler_view)

  private lazy val legalHoldController    = inject[LegalHoldController]
  private lazy val usersController        = inject[UsersController]

  private lazy val adapter = returning(new LegalHoldUsersAdapter(users.map(_.toSet), Some(MAX_PARTICIPANTS))) { adapter =>
    adapter.onClick.pipeTo(legalHoldController.onLegalHoldSubjectClick)
    adapter.onShowAllParticipantsClick.pipeTo(legalHoldController.onAllLegalHoldSubjectsClick)
  }

  private lazy val users: Signal[Seq[UserId]] =
    getStringArg(ARG_CONV_ID).map(ConvId(_)) match {
      case Some(convId) => legalHoldController.legalHoldUsers(convId)
      case None         => usersController.selfUser.map(user => Seq(user.id))
    }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_legal_hold_info, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    setMessage()
    setUpRecyclerView()
  }

  override def onAttach(context: Context): Unit = {
    super.onAttach(context)
    legalHoldController.showingLegalHoldInfo ! true
  }

  override def onDetach(): Unit = {
    legalHoldController.showingLegalHoldInfo ! false
    super.onDetach()
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

  val Tag = "LegalHoldInfoFragment"
  private val MAX_PARTICIPANTS = 4
  val ARG_MESSAGE_RES_ID = "legalHoldInfo_messageResId_Arg"
  val ARG_CONV_ID = "legalHoldInfo_convId_Arg"

  def newInstance(messageResId: Int, convId: Option[ConvId]): LegalHoldInfoFragment =
    returning(new LegalHoldInfoFragment()) { frag =>
      val args = returning(new Bundle()) { b =>
        b.putInt(ARG_MESSAGE_RES_ID, messageResId)
        convId.foreach(id => b.putString(ARG_CONV_ID, id.str))
      }
      frag.setArguments(args)
    }
}
