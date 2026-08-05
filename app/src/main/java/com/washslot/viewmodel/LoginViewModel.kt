package com.washslot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginEvent = MutableSharedFlow<LoginEvent>()
    val loginEvent = _loginEvent.asSharedFlow()

    fun onEmailChange(email: String) {
        _email.value = email
    }

    fun onPasswordChange(password: String) {
        _password.value = password
    }

    fun login() {
        if (_email.value.isBlank()) {
            viewModelScope.launch { _loginEvent.emit(LoginEvent.Error("Email cannot be empty")) }
            return
        }
        if (_password.value.length < 6) {
            viewModelScope.launch { _loginEvent.emit(LoginEvent.Error("Password needs to be at least 6 characters")) }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepository.login(_email.value, _password.value)
            _isLoading.value = false
            if (result.isSuccess) {
                _loginEvent.emit(LoginEvent.Success)
            } else {
                _loginEvent.emit(LoginEvent.Error(result.exceptionOrNull()?.message ?: "Login failed. Please check your credentials."))
            }
        }
    }

    sealed class LoginEvent {
        object Success : LoginEvent()
        data class Error(val message: String) : LoginEvent()
    }
}
