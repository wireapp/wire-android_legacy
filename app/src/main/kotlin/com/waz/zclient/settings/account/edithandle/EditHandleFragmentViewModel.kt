package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.ViewModel
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.domain.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragmentViewModel(
    private val getHandleUseCase: GetHandleUseCase,
    private val checkHandleExistsUseCase: CheckHandleExistsUseCase,
    private val validateHandleUseCase: ValidateHandleUseCase) : ViewModel() {

    fun afterHandleTextChanged(newHandle: String) {

    }

    fun beforeHandleTextChanged(oldHandle: String) {

    }

    fun onOkButtonClicked(handleInput: String) {

    }

    fun onBackButtonClicked() {

    }
}
