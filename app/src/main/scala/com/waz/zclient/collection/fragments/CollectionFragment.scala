/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.collection.fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.widget.RecyclerView.State
import android.support.v7.widget.{LinearLayoutManager, RecyclerView, Toolbar}
import android.text.{Editable, TextWatcher}
import android.view.View.{OnClickListener, OnFocusChangeListener, OnLayoutChangeListener}
import android.view._
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.{EditText, TextView}
import com.waz.ZLog._
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.adapters.CollectionAdapter.AdapterState
import com.waz.zclient.collection.adapters.{CollectionAdapter, SearchAdapter}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.controllers.CollectionController.AllContent
import com.waz.zclient.collection.views.CollectionRecyclerView
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.theme.ThemeUtils
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.{RichView, ViewUtils}
import com.waz.zclient.{FragmentHelper, R}
import org.threeten.bp.{LocalDateTime, ZoneId}

class CollectionFragment extends BaseFragment[CollectionFragment.Container] with FragmentHelper {

  private implicit lazy val context: Context = getContext

  private implicit val tag: LogTag = logTagFor[CollectionFragment]

  lazy val controller = inject[CollectionController]
  lazy val messageActionsController = inject[MessageActionsController]
  lazy val accentColorController = inject[AccentColorController]
  var collectionAdapter: CollectionAdapter = null
  var searchAdapter: SearchAdapter = null

  override def onDestroy(): Unit = {
    if (collectionAdapter != null) collectionAdapter.closeCursors()
    super.onDestroy()
  }

  private def showSingleImage() = {
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    getChildFragmentManager.findFragmentByTag(SingleImageCollectionFragment.TAG) match {
      case null => getChildFragmentManager.beginTransaction.add(R.id.fl__collection_content, SingleImageCollectionFragment.newInstance(), SingleImageCollectionFragment.TAG).addToBackStack(SingleImageCollectionFragment.TAG).commit
      case _ =>
    }
  }

  private def closeSingleImage() = {
    getChildFragmentManager.findFragmentByTag(SingleImageCollectionFragment.TAG) match {
      case null =>
      case _ => getChildFragmentManager.popBackStackImmediate(SingleImageCollectionFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_collection, container, false)
    val name: TextView  = ViewUtils.getView(view, R.id.tv__collection_toolbar__name)
    val timestamp: TextView  = ViewUtils.getView(view, R.id.tv__collection_toolbar__timestamp)
    val collectionRecyclerView: CollectionRecyclerView = ViewUtils.getView(view, R.id.collection_list)
    val searchRecyclerView: RecyclerView = ViewUtils.getView(view, R.id.search_results_list)
    val emptyView: View = ViewUtils.getView(view, R.id.ll__collection__empty)
    val toolbar: Toolbar = ViewUtils.getView(view, R.id.t_toolbar)
    val searchBoxView: TypefaceEditText = ViewUtils.getView(view, R.id.search_box)
    val searchBoxClose: GlyphTextView = ViewUtils.getView(view, R.id.search_close)
    val searchBoxHint: TypefaceTextView = ViewUtils.getView(view, R.id.search_hint)
    val noSearchResultsText: TypefaceTextView = ViewUtils.getView(view, R.id.no_search_results)

    def setNavigationIconVisibility(visible: Boolean) = {
      if (visible) {
        if (ThemeUtils.isDarkTheme(getContext)) {
          toolbar.setNavigationIcon(R.drawable.action_back_light)
        } else {
          toolbar.setNavigationIcon(R.drawable.action_back_dark)
        }
      } else {
        toolbar.setNavigationIcon(null)
      }
    }

    emptyView.setVisibility(View.GONE)
    timestamp.setVisibility(View.GONE)
    setNavigationIconVisibility(false)
    controller.focusedItem ! None

    messageActionsController.onMessageAction.on(Threading.Ui){
      case (MessageAction.Reveal, _) =>
        KeyboardUtils.closeKeyboardIfShown(getActivity)
        controller.closeCollection
        controller.focusedItem.mutate {
          case Some(m) if m.msgType == Message.Type.ASSET => None
          case m => m
        }
      case _ =>
    }

    controller.focusedItem.on(Threading.Ui) {
      case Some(md) if md.msgType == Message.Type.ASSET => showSingleImage()
      case _ => closeSingleImage()
    }

    accentColorController.accentColor.on(Threading.Ui){ color =>
      searchBoxView.setAccentColor(color.getColor())
    }

    collectionAdapter = new CollectionAdapter(collectionRecyclerView.viewDim)
    collectionRecyclerView.init(collectionAdapter)

    searchAdapter = new SearchAdapter()

    searchRecyclerView.addOnLayoutChangeListener(new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit = {
        searchAdapter.notifyDataSetChanged()
      }
    })

    searchRecyclerView.setLayoutManager(new LinearLayoutManager(getContext){
      override def supportsPredictiveItemAnimations(): Boolean = true

      override def onScrollStateChanged(state: Int): Unit = {
        super.onScrollStateChanged(state)
        if (state == RecyclerView.SCROLL_STATE_DRAGGING){
          KeyboardUtils.closeKeyboardIfShown(getActivity)
        }
      }

      override def onLayoutChildren(recycler: RecyclerView#Recycler, state: State): Unit = {
        try{
          super.onLayoutChildren(recycler, state)
        } catch {
          case ioob: IndexOutOfBoundsException => error("IOOB caught") //XXX: I don't think this is needed anymore
        }

      }
    })
    searchRecyclerView.setAdapter(searchAdapter)

    controller.contentSearchQuery.currentValue.foreach{q =>
      if (q.originalString.nonEmpty) {
        searchBoxView.setText(q.originalString)
        searchBoxHint.setVisibility(View.GONE)
        searchBoxClose.setVisibility(View.VISIBLE)
      }
    }
    searchBoxView.addTextChangedListener(new TextWatcher {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {
        if (s.toString.trim.length() <= 1) {
          controller.contentSearchQuery ! ContentSearchQuery.empty
        } else {
          controller.contentSearchQuery ! ContentSearchQuery(s.toString)
        }
        searchBoxClose.setVisible(s.toString.nonEmpty)
      }

      override def afterTextChanged(s: Editable): Unit = {}
    })
    searchBoxView.asInstanceOf[EditText].setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          KeyboardUtils.closeKeyboardIfShown(getActivity)
          searchBoxView.clearFocus()
        }
        true
      }
    })
    searchBoxView.setOnKeyPreImeListener(new View.OnKeyListener(){
      override def onKey(v: View, keyCode: Int, event: KeyEvent): Boolean = {
        if (event.getAction == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
          v.clearFocus()
        }
        false
      }
    })
    searchBoxView.setOnFocusChangeListener(new OnFocusChangeListener {
      override def onFocusChange(v: View, hasFocus: Boolean): Unit = {
        searchBoxHint.setVisible(!hasFocus && searchBoxView.getText.length() == 0)
      }
    })

    searchBoxClose.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        searchBoxView.setText("")
        searchBoxView.clearFocus()
        searchBoxHint.setVisibility(View.VISIBLE)
        KeyboardUtils.closeKeyboardIfShown(getActivity)
      }
    })

    controller.conversationName.on(Threading.Ui){ name.setText }

    Signal(collectionAdapter.adapterState, controller.focusedItem, controller.contentSearchQuery).on(Threading.Ui) {
      case (AdapterState(_, _, _), Some(messageData), _) if messageData.msgType == Message.Type.ASSET =>
        setNavigationIconVisibility(true)
        timestamp.setVisibility(View.VISIBLE)
        timestamp.setText(LocalDateTime.ofInstant(messageData.time, ZoneId.systemDefault()).toLocalDate.toString)
      case (_, _, query) if query.originalString.nonEmpty =>
        collectionRecyclerView.setVisibility(View.GONE)
        searchRecyclerView.setVisibility(View.VISIBLE)
        timestamp.setVisibility(View.GONE)
        emptyView.setVisibility(View.GONE)
      case (AdapterState(AllContent, 0, false), None, _) =>
        emptyView.setVisibility(View.VISIBLE)
        collectionRecyclerView.setVisibility(View.GONE)
        searchRecyclerView.setVisibility(View.GONE)
        setNavigationIconVisibility(false)
        timestamp.setVisibility(View.GONE)
        noSearchResultsText.setVisibility(View.GONE)
      case (AdapterState(contentMode, _, _), None, _) =>
        emptyView.setVisibility(View.GONE)
        collectionRecyclerView.setVisibility(View.VISIBLE)
        searchRecyclerView.setVisibility(View.GONE)
        setNavigationIconVisibility(contentMode != AllContent)
        timestamp.setVisibility(View.GONE)
        noSearchResultsText.setVisibility(View.GONE)
      case _ =>
    }

    Signal(searchAdapter.cursor.flatMap(_.countSignal).orElse(Signal(-1)), controller.contentSearchQuery).on(Threading.Ui) {
      case (0, query) if query.originalString.nonEmpty =>
        noSearchResultsText.setVisibility(View.VISIBLE)
      case _ =>
        noSearchResultsText.setVisibility(View.GONE)
    }

    collectionAdapter.contentMode.on(Threading.Ui){ _ =>
      collectionRecyclerView.scrollToPosition(0)
    }

    toolbar.inflateMenu(R.menu.toolbar_collection)

    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        onBackPressed()
      }
    })

    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener {
      override def onMenuItemClick(item: MenuItem): Boolean = {
        item.getItemId match {
          case R.id.close =>
            controller.focusedItem ! None
            controller.contentSearchQuery ! ContentSearchQuery.empty
            controller.closeCollection()
            true
          case _ => false
        }
      }
    })
    view
  }

  override def onBackPressed(): Boolean = {
    Option(findById[CollectionRecyclerView](R.id.collection_list)).foreach { rv =>
      rv.stopScroll()
      rv.getSpanSizeLookup().clearCache()
    }

    withFragmentOpt(SingleImageCollectionFragment.TAG) {
      case Some(_: SingleImageCollectionFragment) =>
        controller.focusedItem ! None
        true
      case _ =>
        if (!collectionAdapter.onBackPressed){
          controller.contentSearchQuery ! ContentSearchQuery.empty
          controller.closeCollection()
        }
        true
    }
  }
}

object CollectionFragment {

  val TAG = CollectionFragment.getClass.getSimpleName

  val MAX_DELTA_TOUCH = 30

  def newInstance() = new CollectionFragment

  trait Container

}
