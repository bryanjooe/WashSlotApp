package com.washslot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.washslot.domain.model.LaundryRequest
import com.washslot.domain.model.RequestStatus
import com.washslot.domain.repository.AuthRepository
import com.washslot.domain.repository.LaundryRepository
import com.washslot.notification.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val PAGE_SIZE = 15
private const val MAX_MACHINES = 4
// Editing not allowed if the slot starts within 60 minutes
private const val EDIT_CUTOFF_MINUTES = 60L

// ── UI State Types ────────────────────────────────────────────────────────────

sealed class ActiveRequestUiState {
    object Loading : ActiveRequestUiState()
    data class HasRequest(val request: LaundryRequest) : ActiveRequestUiState()
    object NoRequest : ActiveRequestUiState()
    data class Error(val message: String) : ActiveRequestUiState()
}

data class HistoryUiState(
    val items: List<LaundryRequest> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val initialError: String? = null,
    val pageError: String? = null
)

sealed class EditSheetUiState {
    object Hidden : EditSheetUiState()
    object LoadingSlots : EditSheetUiState()
    data class SlotsLoaded(
        val date: String,
        val slots: List<SlotOption>,
        val isConfirming: Boolean = false
    ) : EditSheetUiState()
    data class SlotError(val message: String) : EditSheetUiState()
}

data class SlotOption(
    val time: String,
    val takenCount: Int,
    val maxMachines: Int,
    val isTooSoon: Boolean
) {
    val isFull: Boolean get() = takenCount >= maxMachines
    val isSelectable: Boolean get() = !isFull && !isTooSoon
}

sealed class MyRequestsEvent {
    data class ShowSnackbar(val message: String) : MyRequestsEvent()
}

// ── Available time slots (same set as CreateRequestScreen) ────────────────────
val ALL_TIME_SLOTS = listOf(
    "08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM",
    "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM",
    "04:00 PM", "05:00 PM", "06:00 PM", "07:00 PM", "08:00 PM"
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class MyRequestsViewModel @Inject constructor(
    private val laundryRepository: LaundryRepository,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val notificationScheduler = NotificationScheduler(context)

    private val _activeState = MutableStateFlow<ActiveRequestUiState>(ActiveRequestUiState.Loading)
    val activeState: StateFlow<ActiveRequestUiState> = _activeState.asStateFlow()

    private val _historyState = MutableStateFlow(HistoryUiState())
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    private val _editState = MutableStateFlow<EditSheetUiState>(EditSheetUiState.Hidden)
    val editState: StateFlow<EditSheetUiState> = _editState.asStateFlow()

    private val _events = MutableSharedFlow<MyRequestsEvent>()
    val events: SharedFlow<MyRequestsEvent> = _events.asSharedFlow()

    // Track history pagination offset
    private var historyOffset = 0
    private var isFetchingHistory = false

    init {
        load()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun load() {
        viewModelScope.launch {
            loadActiveRequest()
            resetAndLoadHistory()
        }
    }

    fun refresh() {
        load()
    }

    fun loadMoreHistory() {
        if (isFetchingHistory) return
        val current = _historyState.value
        if (!current.hasMore || current.isInitialLoading) return

        viewModelScope.launch { fetchHistoryPage() }
    }

    fun cancelRequest(requestId: String) {
        viewModelScope.launch {
            val result = laundryRepository.cancelRequest(requestId)
            if (result.isSuccess) {
                notificationScheduler.cancelReminder(requestId)
                _activeState.value = ActiveRequestUiState.NoRequest
                // Restart history (cancelled item will now appear there)
                resetAndLoadHistory()
                _events.emit(MyRequestsEvent.ShowSnackbar("Request cancelled."))
            } else {
                _events.emit(
                    MyRequestsEvent.ShowSnackbar(
                        result.exceptionOrNull()?.message ?: "Failed to cancel. Please try again."
                    )
                )
            }
        }
    }

    fun openEditSheet() {
        val active = (_activeState.value as? ActiveRequestUiState.HasRequest)?.request ?: return
        _editState.value = EditSheetUiState.LoadingSlots
        viewModelScope.launch {
            loadSlotOptions(date = active.preferredDate, excludeRequestId = active.id)
        }
    }

    fun dismissEditSheet() {
        _editState.value = EditSheetUiState.Hidden
    }

    fun confirmEdit(newDate: String, newTime: String) {
        val activeRequest = (_activeState.value as? ActiveRequestUiState.HasRequest)?.request ?: return
        val currentSheet = _editState.value as? EditSheetUiState.SlotsLoaded ?: return

        _editState.value = currentSheet.copy(isConfirming = true)

        viewModelScope.launch {
            val result = laundryRepository.updateRequestTimeSlot(
                requestId = activeRequest.id!!,
                newDate = newDate,
                newTime = newTime,
                maxMachines = MAX_MACHINES,
                excludeRequestId = activeRequest.id
            )
            if (result.isSuccess) {
                val updatedRequest = result.getOrThrow()
                notificationScheduler.scheduleReminder(updatedRequest)
                _activeState.value = ActiveRequestUiState.HasRequest(updatedRequest)
                _editState.value = EditSheetUiState.Hidden
                _events.emit(MyRequestsEvent.ShowSnackbar("Slot updated successfully!"))
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Update failed. Please try again."
                // If it's a slot-full race error, stay in sheet with error state
                _editState.value = EditSheetUiState.SlotError(errorMsg)
            }
        }
    }

    /** Called when the user wants to retry loading slots after a SlotError */
    fun retryOpenEditSheet() {
        val active = (_activeState.value as? ActiveRequestUiState.HasRequest)?.request ?: return
        _editState.value = EditSheetUiState.LoadingSlots
        viewModelScope.launch {
            loadSlotOptions(date = active.preferredDate, excludeRequestId = active.id)
        }
    }

    fun retryLoadMoreHistory() {
        viewModelScope.launch { fetchHistoryPage() }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadActiveRequest() {
        val userId = currentUserId() ?: return
        _activeState.value = ActiveRequestUiState.Loading
        val result = laundryRepository.getActiveRequest(userId)
        _activeState.value = when {
            result.isFailure -> ActiveRequestUiState.Error(
                result.exceptionOrNull()?.message ?: "Could not load your active request."
            )
            result.getOrNull() != null -> ActiveRequestUiState.HasRequest(result.getOrThrow()!!)
            else -> ActiveRequestUiState.NoRequest
        }
    }

    private suspend fun resetAndLoadHistory() {
        historyOffset = 0
        isFetchingHistory = false
        _historyState.value = HistoryUiState(isInitialLoading = true)
        fetchHistoryPage()
    }

    private suspend fun fetchHistoryPage() {
        if (isFetchingHistory) return
        isFetchingHistory = true

        val userId = currentUserId() ?: run {
            isFetchingHistory = false
            return
        }

        val isFirstPage = historyOffset == 0

        if (isFirstPage) {
            _historyState.value = _historyState.value.copy(
                isInitialLoading = true,
                initialError = null,
                pageError = null
            )
        } else {
            _historyState.value = _historyState.value.copy(
                isLoadingMore = true,
                pageError = null
            )
        }

        val result = laundryRepository.getPastRequestsPage(userId, historyOffset, PAGE_SIZE)

        if (result.isSuccess) {
            val page = result.getOrThrow()
            val current = _historyState.value
            _historyState.value = current.copy(
                items = if (isFirstPage) page else current.items + page,
                isInitialLoading = false,
                isLoadingMore = false,
                hasMore = page.size >= PAGE_SIZE,
                initialError = null,
                pageError = null
            )
            historyOffset += page.size
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Failed to load history."
            val current = _historyState.value
            _historyState.value = current.copy(
                isInitialLoading = false,
                isLoadingMore = false,
                initialError = if (isFirstPage) errorMsg else null,
                pageError = if (!isFirstPage) errorMsg else null
            )
        }

        isFetchingHistory = false
    }

    private suspend fun loadSlotOptions(date: String, excludeRequestId: String?) {
        val result = laundryRepository.getSlotOccupancy(date, excludeRequestId)
        if (result.isFailure) {
            _editState.value = EditSheetUiState.SlotError(
                result.exceptionOrNull()?.message ?: "Could not load slot availability."
            )
            return
        }

        val occupancy = result.getOrThrow()
        val now = LocalDate.now()
        val slotDate = try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            now
        }

        val slots = ALL_TIME_SLOTS.map { timeStr ->
            val taken = occupancy[timeStr] ?: 0
            val isTooSoon = isSlotTooSoon(slotDate, timeStr)
            SlotOption(
                time = timeStr,
                takenCount = taken,
                maxMachines = MAX_MACHINES,
                isTooSoon = isTooSoon
            )
        }

        _editState.value = EditSheetUiState.SlotsLoaded(date = date, slots = slots)
    }

    private fun isSlotTooSoon(date: LocalDate, timeStr: String): Boolean {
        return try {
            val today = LocalDate.now()
            if (date.isBefore(today)) return true
            if (date.isAfter(today)) return false
            // same day — check 1-hour cutoff
            val slotTime = parseSlotTime(timeStr)
            val now = LocalTime.now()
            slotTime.isBefore(now.plusMinutes(EDIT_CUTOFF_MINUTES))
        } catch (e: Exception) {
            false
        }
    }

    private fun parseSlotTime(timeStr: String): LocalTime {
        return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("hh:mm a"))
    }

    private fun currentUserId(): String? {
        return authRepository.currentUser.value?.id
    }
}
