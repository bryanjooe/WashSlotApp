package com.washslot.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.model.LaundryRequest
import com.washslot.domain.model.RequestStatus
import com.washslot.domain.model.User
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.LaundryRepository
import com.washslot.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val laundryRepository: LaundryRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val notificationScheduler = NotificationScheduler(context)

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    val activeRequest: StateFlow<LaundryRequest?> = refreshTrigger
        .flatMapLatest { timestamp ->
            currentUser.flatMapLatest { user ->
                if (user != null) {
                    laundryRepository.getRequests(user.id)
                } else {
                    flowOf(emptyList())
                }
            }
        }
        .onEach { requests ->
            requests.filter { it.status == RequestStatus.PENDING || it.status == RequestStatus.ALLOCATED }
                .forEach { notificationScheduler.scheduleReminder(it) }
        }
        .map { requests ->
            requests.firstOrNull { it.status == RequestStatus.PENDING || it.status == RequestStatus.ALLOCATED }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormatter))
    val selectedDate = _selectedDate.asStateFlow()

    private val _slotAvailability = MutableStateFlow<Map<String, Int>>(emptyMap())
    val slotAvailability = _slotAvailability.asStateFlow()

    private val _isCancelling = MutableStateFlow(false)
    val isCancelling = _isCancelling.asStateFlow()

    private val _cancelError = MutableStateFlow<String?>(null)
    val cancelError = _cancelError.asStateFlow()

    private val _statusUpdateLoading = MutableStateFlow(false)
    val statusUpdateLoading = _statusUpdateLoading.asStateFlow()

    private val hostelMachineCounts = mapOf(
        "Ashwatha" to 5,
        "Ashoka" to 4,
        "Jasmine" to 6,
        "Jasmine Annexure" to 4
    )

    val maxSlotsPerTime: StateFlow<Int> = currentUser.map { user ->
        hostelMachineCounts[user?.hostel] ?: 4
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 4)

    val availableTimes = listOf(
        "12:00 AM", "01:00 AM", "02:00 AM", "03:00 AM", "04:00 AM", "05:00 AM",
        "06:00 AM", "07:00 AM", "08:00 AM", "09:00 AM", "10:00 AM",
        // No slots between 11:00 AM and 04:00 PM (Maintenance/No Water)
        "04:00 PM", "05:00 PM", "06:00 PM", "07:00 PM", "08:00 PM", "09:00 PM", "10:00 PM", "11:00 PM"
    )

    init {
        loadAvailability()
    }

    private fun loadAvailability() {
        viewModelScope.launch {
            try {
                val user = authRepository.currentUser.first()
                val occupancyResult = laundryRepository.getSlotOccupancy(_selectedDate.value, user?.hostel)
                if (occupancyResult.isSuccess) {
                    _slotAvailability.value = occupancyResult.getOrThrow()
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadAvailability failed", e)
                _slotAvailability.value = emptyMap()
            }
        }
    }

    fun onDateChange(date: String) {
        _selectedDate.value = date
        loadAvailability()
    }

    fun cancelActiveRequest(requestId: String) {
        if (_isCancelling.value) return
        viewModelScope.launch {
            _isCancelling.value = true
            try {
                val result = laundryRepository.cancelRequest(requestId)
                if (result.isSuccess) {
                    notificationScheduler.cancelReminder(requestId)
                    refreshTrigger.value = System.currentTimeMillis()
                    loadAvailability()
                }
            } finally {
                _isCancelling.value = false
            }
        }
    }

    fun clearCancelError() {
        _cancelError.value = null
    }

    fun updateStatus(requestId: String, newStatus: RequestStatus) {
        viewModelScope.launch {
            _statusUpdateLoading.value = true
            try {
                val result = laundryRepository.updateRequestStatus(requestId, newStatus)
                if (result.isSuccess) {
                    refreshTrigger.value = System.currentTimeMillis()
                }
            } finally {
                _statusUpdateLoading.value = false
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
