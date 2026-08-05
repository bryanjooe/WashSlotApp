package com.washslot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.model.LaundryRequest
import com.washslot.domain.model.RequestStatus
import com.washslot.domain.model.User
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.LaundryRepository
import com.washslot.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CreateRequestViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val notificationScheduler = NotificationScheduler(context)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormatter))
    val selectedDate = _selectedDate.asStateFlow()

    data class DateOption(val label: String, val date: String, val display: String)

    val dateOptions: List<DateOption> = listOf(
        DateOption("Today", LocalDate.now().format(dateFormatter), LocalDate.now().format(displayDateFormatter)),
        DateOption("Tomorrow", LocalDate.now().plusDays(1).format(dateFormatter), LocalDate.now().plusDays(1).format(displayDateFormatter)),
        DateOption("Day After", LocalDate.now().plusDays(2).format(dateFormatter), LocalDate.now().plusDays(2).format(displayDateFormatter))
    )

    private val _selectedStartTime = MutableStateFlow("")
    val selectedStartTime = _selectedStartTime.asStateFlow()

    private val _selectedDuration = MutableStateFlow(60)
    val selectedDuration = _selectedDuration.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _requestEvent = MutableSharedFlow<RequestEvent>()
    val requestEvent = _requestEvent.asSharedFlow()

    private val _slotAvailability = MutableStateFlow<Map<String, Int>>(emptyMap())
    val slotAvailability = _slotAvailability.asStateFlow()

    private val hostelMachineCounts = mapOf(
        "Ashwatha" to 5,
        "Ashoka" to 4,
        "Jasmine" to 6,
        "Jasmine Annexure" to 4
    )

    val currentUser: StateFlow<User?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
                _slotAvailability.value = emptyMap()
            }
        }
    }

    fun onDateChange(date: String) { 
        _selectedDate.value = date 
        loadAvailability()
    }

    fun onStartTimeChange(time: String) { _selectedStartTime.value = time }
    fun onDurationChange(duration: Int) { _selectedDuration.value = duration }
    fun onNotesChange(notes: String) { _notes.value = notes }

    fun submitRequest() {
        if (_selectedDate.value.isEmpty() || _selectedStartTime.value.isEmpty()) {
            viewModelScope.launch { _requestEvent.emit(RequestEvent.Error("Please select date and time")) }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = currentUser.value
                if (user != null) {
                    val existingRequests = laundryRepository.getRequests(user.id).firstOrNull() ?: emptyList()
                    val hasActiveRequest = existingRequests.any { it.status == RequestStatus.PENDING || it.status == RequestStatus.ALLOCATED }
                    if (hasActiveRequest) {
                        _requestEvent.emit(RequestEvent.Error("You already have an active request!"))
                        return@launch
                    }

                    val request = LaundryRequest(
                        userId = user.id,
                        preferredDate = _selectedDate.value,
                        preferredStartTime = _selectedStartTime.value,
                        preferredEndTime = _selectedStartTime.value,
                        duration = _selectedDuration.value,
                        flexibility = "None",
                        notes = _notes.value.ifBlank { null },
                        status = RequestStatus.PENDING,
                        hostel = user.hostel
                    )
                    val result = laundryRepository.createRequest(request)
                    if (result.isSuccess) {
                        notificationScheduler.scheduleReminder(request)
                        _requestEvent.emit(RequestEvent.Success)
                    } else {
                        _requestEvent.emit(RequestEvent.Error(result.exceptionOrNull()?.message ?: "Failed to submit request"))
                    }
                } else {
                    _requestEvent.emit(RequestEvent.Error("User session not found. Please log in again."))
                }
            } catch (e: Exception) {
                _requestEvent.emit(RequestEvent.Error("Error: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    sealed class RequestEvent {
        object Success : RequestEvent()
        data class Error(val message: String) : RequestEvent()
    }
}
