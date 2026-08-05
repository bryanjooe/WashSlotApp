package com.washslot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.model.Report
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val currentUser = authRepository.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _machineNumber = MutableStateFlow("")
    val machineNumber = _machineNumber.asStateFlow()

    private val _problemType = MutableStateFlow("")
    val problemType = _problemType.asStateFlow()

    private val _problemDescription = MutableStateFlow("")
    val problemDescription = _problemDescription.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _reportEvent = MutableSharedFlow<ReportEvent>()
    val reportEvent = _reportEvent.asSharedFlow()

    private val _myReports = MutableStateFlow<List<Report>>(emptyList())
    val myReports = _myReports.asStateFlow()

    init {
        loadMyReports()
    }

    fun onMachineNumberChange(number: String) { _machineNumber.value = number }
    fun onProblemTypeChange(type: String) { _problemType.value = type }
    fun onProblemDescriptionChange(description: String) { _problemDescription.value = description }

    fun loadMyReports() {
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            if (user != null) {
                val result = reportRepository.getMyReports(user.id)
                if (result.isSuccess) {
                    _myReports.value = result.getOrThrow()
                }
            }
        }
    }

    fun submitReport() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            if (_machineNumber.value.isBlank() || _problemType.value.isBlank()) {
                _reportEvent.emit(ReportEvent.Error("Machine number and problem type are required"))
                return@launch
            }

            if (_problemType.value == "Others" && _problemDescription.value.isBlank()) {
                _reportEvent.emit(ReportEvent.Error("Please describe the problem"))
                return@launch
            }

            _isLoading.value = true
            val fullProblem = if (_problemType.value == "Others") {
                _problemDescription.value
            } else if (_problemDescription.value.isNotBlank()) {
                "${_problemType.value}: ${_problemDescription.value}"
            } else {
                _problemType.value
            }

            val report = Report(
                userId = user.id,
                machineNumber = _machineNumber.value,
                problem = fullProblem,
                hostel = user.hostel
            )
            val result = reportRepository.submitReport(report)
            _isLoading.value = false

            if (result.isSuccess) {
                _reportEvent.emit(ReportEvent.Success)
                _machineNumber.value = ""
                _problemType.value = ""
                _problemDescription.value = ""
                loadMyReports()
            } else {
                _reportEvent.emit(ReportEvent.Error(result.exceptionOrNull()?.message ?: "Failed to submit report"))
            }
        }
    }

    sealed class ReportEvent {
        object Success : ReportEvent()
        data class Error(val message: String) : ReportEvent()
    }
}
