package com.washslot.domain.repository

import com.washslot.domain.model.Allocation
import com.washslot.domain.model.LaundryRequest
import kotlinx.coroutines.flow.Flow

interface LaundryRepository {
    fun getRequests(userId: String): Flow<List<LaundryRequest>>
    fun getAllocations(userId: String): Flow<List<Allocation>>
    suspend fun createRequest(request: LaundryRequest): Result<Unit>
    suspend fun getRequestById(id: String): LaundryRequest?
    suspend fun getAllocationById(id: String): Allocation?
    suspend fun cancelRequest(requestId: String): Result<Unit>

    /** Returns the single active (PENDING or ALLOCATED) request for the user, or null. */
    suspend fun getActiveRequest(userId: String): Result<LaundryRequest?>

    /**
     * Returns a page of past (COMPLETED or CANCELLED) requests sorted newest-first.
     * [offset] is zero-based.
     */
    suspend fun getPastRequestsPage(userId: String, offset: Int, limit: Int): Result<List<LaundryRequest>>

    /**
     * Returns a map of preferredStartTime → count of ACTIVE requests for [date] in [hostel],
     * optionally excluding [excludeRequestId].
     */
    suspend fun getSlotOccupancy(date: String, hostel: String?, excludeRequestId: String? = null): Result<Map<String, Int>>

    /**
     * Atomically updates the time-slot of an existing request.
     * Performs a re-check of slot occupancy before writing.
     * Returns the updated [LaundryRequest] on success, or a failure describing the reason.
     */
    suspend fun updateRequestTimeSlot(
        requestId: String,
        newDate: String,
        newTime: String,
        maxMachines: Int,
        excludeRequestId: String?
    ): Result<LaundryRequest>

    /** Updates only the status of a request */
    suspend fun updateRequestStatus(requestId: String, newStatus: com.washslot.domain.model.RequestStatus): Result<Unit>
}
