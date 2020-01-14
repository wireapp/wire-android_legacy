package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.usecase.handle.HandleInvalidError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleParams
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragmentViewModel(private val validateHandleUseCase: ValidateHandleUseCase) : ViewModel() {

    private var mutableHandle = MutableLiveData<String>()
    private var mutableError = MutableLiveData<String>()
    private var previousInput: String = String.empty()

    private val handle: LiveData<String>
        get() = mutableHandle

    private val error: LiveData<String>
        get() = mutableError

    fun afterHandleTextChanged(newHandle: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(newHandle)) {
            it.fold(::handleValidationFailure, ::handleValidationSuccess)
        }
    }

    fun beforeHandleTextChanged(oldHandle: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(oldHandle)) {
            it.fold({ failure -> if (failure != HandleInvalidError) previousInput = oldHandle }) {}
        }
    }

    private fun handleValidationSuccess(validatedHandle: String) {

    }

    private fun handleValidationFailure(failure: Failure) {

    }

    fun onOkButtonClicked(handleInput: String) {

    }

    fun onBackButtonClicked() {

    }
}
