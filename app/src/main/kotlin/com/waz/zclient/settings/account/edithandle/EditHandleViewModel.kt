package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.handle.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleViewModel(
    private val checkHandleExistsUseCase: CheckHandleExistsUseCase,
    private val changeHandleUseCase: ChangeHandleUseCase,
    private val getHandleUseCase: GetHandleUseCase,
    private val validateHandleUseCase: ValidateHandleUseCase) : ViewModel() {

    private var mutableError = MutableLiveData<ValidateHandleError>()
    private var mutableOkEnabled = MutableLiveData<Boolean>()
    private var mutableDismiss = MutableLiveData<Unit>()
    private var mutableSuccess = MutableLiveData<ValidateHandleSuccess>()

    val success: LiveData<ValidateHandleSuccess> = mutableSuccess

    val error: LiveData<ValidateHandleError> = mutableError

    val okEnabled: LiveData<Boolean> = mutableOkEnabled

    val dismiss: LiveData<Unit> = mutableDismiss

    fun afterHandleTextChanged(newHandle: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(newHandle)) {
            it.fold(::handleFailure, ::afterTextChangedValidationSuccess)
        }
    }

    private fun afterTextChangedValidationSuccess(validatedHandle: String) {
        getHandleUseCase(viewModelScope, Unit) {
            it.fold(::handleFailure) { handle -> handleRetrievalSuccess(handle, validatedHandle) }
        }
    }

    private fun handleRetrievalSuccess(currentHandle: String?, newHandle: String) {
        currentHandle?.let { handle ->
            if (!handle.equals(newHandle, ignoreCase = true)) {
                checkHandleExistsUseCase(viewModelScope, CheckHandleExistsParams(newHandle)) {
                    it.fold(::handleFailure) { handleIsAvailableSuccess() }
                }
            } else {
                handleFailure(HandleSameAsCurrentError)
            }
        }
    }

    fun onOkButtonClicked(handleInput: String) {
        validateHandleUseCase(viewModelScope, ValidateHandleParams(handleInput)) {
            it.fold(::handleFailure, ::updateHandle)
        }
    }

    fun onBackButtonClicked(suggestedHandle: String?) {
        if (!suggestedHandle.isNullOrEmpty()) {
            updateHandle(suggestedHandle)
        }
        mutableDismiss.postValue(Unit)
    }

    private fun handleIsAvailableSuccess() {
        mutableOkEnabled.postValue(true)
        mutableSuccess.postValue(HandleIsAvailable)
    }

    private fun updateHandle(handle: String) {
        changeHandleUseCase(viewModelScope, ChangeHandleParams(handle)) {
            it.fold({ handleFailure(HandleUnknownError) }, { mutableDismiss.postValue(Unit) })
        }
    }

    private fun handleFailure(failure: Failure) {
        mutableOkEnabled.postValue(false)
        if (failure is ValidateHandleError) {
            mutableError.postValue(failure)
        }
    }
}
