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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _hostel = MutableStateFlow("")
    val hostel = _hostel.asStateFlow()

    private val _roomNumber = MutableStateFlow("")
    val roomNumber = _roomNumber.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _registerEvent = MutableSharedFlow<RegisterEvent>()
    val registerEvent = _registerEvent.asSharedFlow()

    fun onNameChange(name: String) { _name.value = name }
    fun onEmailChange(email: String) { _email.value = email }
    fun onPasswordChange(password: String) { _password.value = password }
    fun onHostelChange(hostel: String) { _hostel.value = hostel }
    fun onRoomNumberChange(roomNumber: String) { _roomNumber.value = roomNumber }

    fun register() {
        if (_name.value.isBlank() || _email.value.isBlank() || _hostel.value.isBlank() || _roomNumber.value.isBlank()) {
            viewModelScope.launch { _registerEvent.emit(RegisterEvent.Error("All fields are required")) }
            return
        }
        if (_password.value.length < 6) {
            viewModelScope.launch { _registerEvent.emit(RegisterEvent.Error("Password needs to be at least 6 characters")) }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepository.register(
                _email.value,
                _password.value,
                _name.value,
                _hostel.value,
                _roomNumber.value
            )
            _isLoading.value = false
            if (result.isSuccess) {
                _registerEvent.emit(RegisterEvent.Success)
            } else {
                _registerEvent.emit(RegisterEvent.Error(result.exceptionOrNull()?.message ?: "Registration failed"))
            }
        }
    }

    sealed class RegisterEvent {
        object Success : RegisterEvent()
        data class Error(val message: String) : RegisterEvent()
    }
}
