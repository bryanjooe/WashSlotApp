package com.washslot.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.model.LaundryRequest
import com.washslot.domain.model.RequestStatus
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.LaundryRepository
import com.washslot.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val laundryRepository: LaundryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val notificationScheduler = NotificationScheduler(context)
    
    private val _reservations = MutableStateFlow<List<ReservationItem>>(emptyList())
    val reservations: StateFlow<List<ReservationItem>> = _reservations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.US)
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.US)

    init {
        loadReservations()
    }

    fun loadReservations() {
        viewModelScope.launch {
            _isLoading.value = true
            val user = authRepository.currentUser.value
            if (user != null) {
                laundryRepository.getRequests(user.id).collect { requests ->
                    val now = LocalDateTime.now()

                    val items = requests.filter { request ->
                        request.status == RequestStatus.PENDING || request.status == RequestStatus.ALLOCATED
                    }.map { request ->
                        try {
                            val requestDate = LocalDate.parse(request.preferredDate, dateFormatter)
                            val startTime = LocalTime.parse(request.preferredStartTime, timeFormatter)
                            val endTime = LocalTime.parse(request.preferredEndTime, timeFormatter)
                            
                            val startDateTime = LocalDateTime.of(requestDate, startTime)
                            var endDateTime = java.time.LocalDateTime.of(requestDate, endTime)
                            
                            // Adjust for slots crossing midnight
                            if (endDateTime.isBefore(startDateTime)) {
                                endDateTime = endDateTime.plusDays(1)
                            }

                            // Use the same robust comparison logic
                            val isOngoing = (now.isAfter(startDateTime.minusMinutes(5)) || now.isEqual(startDateTime)) && now.isBefore(endDateTime)
                            val isUpcoming = now.isBefore(startDateTime.minusMinutes(5))

                            if (isUpcoming) {
                                notificationScheduler.scheduleReminder(request)
                            }

                            ReservationItem(
                                request = request,
                                isOngoing = isOngoing,
                                isUpcoming = isUpcoming,
                                startDateTime = startDateTime
                            )
                        } catch (e: Exception) {
                            // Fallback if parsing fails
                            ReservationItem(request = request, isOngoing = false, isUpcoming = true, startDateTime = LocalDateTime.MAX)
                        }
                    }.filter { it.isOngoing || it.isUpcoming }
                    .sortedBy { it.startDateTime }
                    
                    _reservations.value = items
                    _isLoading.value = false
                }
            } else {
                _isLoading.value = false
            }
        }
    }

    data class ReservationItem(
        val request: LaundryRequest,
        val isOngoing: Boolean,
        val isUpcoming: Boolean,
        val startDateTime: LocalDateTime
    )
}
