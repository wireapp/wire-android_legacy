package com.waz.zclient.settings.account.edithandle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleParams
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsParams
import com.waz.zclient.user.domain.usecase.handle.CheckHandleExistsUseCase
import com.waz.zclient.user.domain.usecase.handle.GetHandleUseCase
import com.waz.zclient.user.domain.usecase.handle.HandleIsAvailable
import com.waz.zclient.user.domain.usecase.handle.HandleSameAsCurrent
import com.waz.zclient.user.domain.usecase.handle.UnknownError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleError
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleParams
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleSuccess
import com.waz.zclient.user.domain.usecase.handle.ValidateHandleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import java.util.Locale

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class SettingsAccountEditHandleViewModel(
    private val checkHandleExistsUseCase: CheckHandleExistsUseCase,
    private val changeHandleUseCase: ChangeHandleUseCase,
    private val getHandleUseCase: GetHandleUseCase,
    private val validateHandleUseCase: ValidateHandleUseCase
) : ViewModel() {

    private var _handleLiveData = MutableLiveData<String>()
    private var _errorLiveData = MutableLiveData<ValidateHandleError>()
    private var _okEnabledLiveData = MutableLiveData<Boolean>()
    private var _dismissLiveData = MutableLiveData<Unit>()
    private var _successLiveData = MutableLiveData<ValidateHandleSuccess>()

    val handleLiveData: LiveData<String> = _handleLiveData
    val successLiveData: LiveData<ValidateHandleSuccess> = _successLiveData
    val errorLiveData: LiveData<ValidateHandleError> = _errorLiveData
    val okEnabledLiveData: LiveData<Boolean> = _okEnabledLiveData
    val dismissLiveData: LiveData<Unit> = _dismissLiveData

    fun afterHandleTextChanged(newHandle: String) {
        val lowercaseHandle = newHandle.toLowerCase(Locale.getDefault())
        if (!newHandle.equals(lowercaseHandle, ignoreCase = false)) {
            _handleLiveData.value = lowercaseHandle
        } else {
            validateHandleUseCase(viewModelScope, ValidateHandleParams(newHandle), Dispatchers.Default) {
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
        validateHandleUseCase(viewModelScope, ValidateHandleParams(handleInput), Dispatchers.Default) {
            it.fold(::handleFailure, ::updateHandle)
        }
    }

    fun onBackButtonClicked(suggestedHandle: String?) {
        if (!suggestedHandle.isNullOrEmpty()) {
            updateHandle(suggestedHandle)
        }
        _dismissLiveData.postValue(Unit)
    }

    private fun handleIsAvailableSuccess() {
        _okEnabledLiveData.postValue(true)
        _successLiveData.postValue(HandleIsAvailable)
    }

    private fun updateHandle(handle: String) {
        changeHandleUseCase(viewModelScope, ChangeHandleParams(handle)) {
            it.fold({ handleFailure(UnknownError) }, { _dismissLiveData.postValue(Unit) })
        }
    }

    private fun handleFailure(failure: Failure) {
        _okEnabledLiveData.postValue(false)
        if (failure is ValidateHandleError) {
            _errorLiveData.postValue(failure)
        }
    }
}
