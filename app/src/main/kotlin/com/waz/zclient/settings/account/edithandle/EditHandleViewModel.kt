package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleSuccess
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleParams
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleParams
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsParams
import com.waz.zclient.user.domain.usecase.handle.HandleIsAvailable
import com.waz.zclient.user.domain.usecase.handle.HandleSameAsCurrent
import com.waz.zclient.user.domain.usecase.handle.UnknownError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.Locale

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class EditHandleViewModel(
    private val checkHandleExistsUseCase: CheckHandleExistsUseCase,
    private val changeHandleUseCase: ChangeHandleUseCase,
    private val getHandleUseCase: GetHandleUseCase,
    private val validateHandleUseCase: ValidateHandleUseCase
) : ViewModel() {

    private var mutableHandle = MutableLiveData<String>()
    private var mutableError = MutableLiveData<ValidateHandleError>()
    private var mutableOkEnabled = MutableLiveData<Boolean>()
    private var mutableDismiss = MutableLiveData<Unit>()
    private var mutableSuccess = MutableLiveData<ValidateHandleSuccess>()

    val handle: LiveData<String> = mutableHandle
    val success: LiveData<ValidateHandleSuccess> = mutableSuccess
    val error: LiveData<ValidateHandleError> = mutableError
    val okEnabled: LiveData<Boolean> = mutableOkEnabled
    val dismiss: LiveData<Unit> = mutableDismiss

    fun afterHandleTextChanged(newHandle: String) {
        val lowercaseHandle = newHandle.toLowerCase(Locale.getDefault())
        if (!newHandle.equals(lowercaseHandle, ignoreCase = false)) {
            mutableHandle.value = lowercaseHandle
        } else {
            validateHandleUseCase(viewModelScope, ValidateHandleParams(newHandle)) {
                it.fold(::handleFailure, ::afterTextChangedValidationSuccess)
            }
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
                handleFailure(HandleSameAsCurrent)
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
            it.fold({ handleFailure(UnknownError) }, { mutableDismiss.postValue(Unit) })
        }
    }

    private fun handleFailure(failure: Failure) {
        mutableOkEnabled.postValue(false)
        if (failure is ValidateHandleError) {
            mutableError.postValue(failure)
        }
    }
}
