package com.waz.zclient.legalhold

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.usersearch.views.{PickerSpannableEditText, SearchEditText}

class AllLegalHoldSubjectsFragment extends BaseFragment[LegalHoldSubjectsContainer]()
  with FragmentHelper {

  private lazy val legalHoldController = inject[LegalHoldController]

  private lazy val users = getContainer.legalHoldUsers.map(_.toSet)

  private lazy val adapter = returning(new LegalHoldUsersAdapter(users)) {
    _.onClick(legalHoldController.onLegalHoldSubjectClick ! _)
  }

  private lazy val searchBox = view[SearchEditText](R.id.search_box)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) =
    inflater.inflate(R.layout.all_participants_fragment, container, false)


  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    val recyclerView = findById[RecyclerView](R.id.recycler_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext))
    recyclerView.setAdapter(adapter)
    searchBox.foreach { sb =>
      sb.applyDarkTheme(inject[ThemeController].isDarkTheme)
      sb.setCallback(new PickerSpannableEditText.Callback {
        override def onRemovedTokenSpan(element: PickableElement): Unit = {}

        override def afterTextChanged(s: String): Unit = {
          adapter.filter ! s
        }
      })
    }
  }
}

object AllLegalHoldSubjectsFragment {

  val Tag = "AllLegalHoldSubjectsFragment"

  def newInstance() = new AllLegalHoldSubjectsFragment
}
