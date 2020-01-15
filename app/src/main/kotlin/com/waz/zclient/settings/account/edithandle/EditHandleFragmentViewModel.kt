package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.empty
import com.waz.zclient.user.domain.usecase.handle.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleFragmentViewModel(
    private val changeHandleUseCase: ChangeHandleUseCase,
    private val retrieveHandleUseCase: RetrieveHandleUseCase,
    private val validateHandleUseCase: ValidateHandleUseCase) : ViewModel() {

    private var mutableHandle = MutableLiveData<String>()
    private var mutableError = MutableLiveData<ValidateHandleError>()
    private var mutableOkEnabled = MutableLiveData<Boolean>()
    private var mutableDismiss = MutableLiveData<Unit>()

    private var previousInput: String = String.empty()

    val handle: LiveData<String>
        get() = mutableHandle

    val error: LiveData<ValidateHandleError>
        get() = mutableError

    val okEnabled: LiveData<Boolean>
        get() = mutableOkEnabled

    val dismiss: LiveData<Unit>
        get() = mutableDismiss

    fun beforeHandleTextChanged(oldHandle: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(oldHandle)) {
            it.fold({ failure -> if (failure != HandleInvalidError) previousInput = oldHandle }) {}
        }
    }

    fun afterHandleTextChanged(newHandle: String) {
        retrieveHandleUseCase(viewModelScope, Unit) {
            it.fold(::handleFailure) { currentHandle -> handleRetrievalSuccess(currentHandle, newHandle) }
        }
    }

    private fun handleRetrievalSuccess(currentHandle: String, newHandle: String) {
        if (!currentHandle.equals(newHandle, ignoreCase = true)) {
            validateHandle(newHandle)
        }
    }

    private fun validateHandle(newHandle: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(newHandle)) {
            it.fold(::handleFailure, ::handleValidationSuccess)
        }
    }

    private fun handleValidationSuccess(validatedHandle: String) {
        mutableOkEnabled.postValue(true)
        mutableHandle.postValue(validatedHandle)
    }

    fun onOkButtonClicked(handleInput: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(handleInput)) {
            it.fold(::handleFailure, ::updateHandle)
        }
    }

    fun onBackButtonClicked(suggestedHandle: String?, isDialogCancelable: Boolean) {
        if (!isDialogCancelable && !suggestedHandle.isNullOrEmpty()) {
            updateHandle(suggestedHandle)
        }
        mutableDismiss.postValue(Unit)
    }

    private fun updateHandle(handle: String) {
        changeHandleUseCase(viewModelScope, ChangeHandleParams(handle)) {
            it.fold({ handleFailure(HandleUnknownError) }, { mutableDismiss.postValue(Unit) })
        }
    }

    private fun handleFailure(failure: Failure) {
        mutableOkEnabled.postValue(false)
        when (failure) {
            is HandleInvalidError, is HandleTooLongError -> {
                mutableHandle.postValue(previousInput)
            }
            is HandleUnknownError, is HandleExistsAlreadyError -> {
                mutableError.postValue(failure as ValidateHandleError)
            }
        }
    }


}
