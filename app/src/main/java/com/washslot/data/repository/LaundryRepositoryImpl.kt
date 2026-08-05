package com.washslot.data.repository

import android.util.Log
import com.washslot.domain.model.Allocation
import com.washslot.domain.model.LaundryRequest
import com.washslot.domain.model.RequestStatus
import com.washslot.domain.repository.LaundryRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class LaundryRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : LaundryRepository {

    override fun getRequests(userId: String): Flow<List<LaundryRequest>> = flow {
        if (userId.isEmpty()) {
             emit(emptyList())
             return@flow
        }
        val requests = supabaseClient.postgrest["laundry_requests"]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList<LaundryRequest>()
        emit(requests)
    }.catch { e ->
        Log.e("LaundryRepo", "Error fetching requests: ${e.message}")
        emit(emptyList())
    }

    override fun getAllocations(userId: String): Flow<List<Allocation>> = flow {
        val allocations = supabaseClient.postgrest["allocations"]
            .select()
            .decodeList<Allocation>()
        emit(allocations)
    }.catch { e ->
        Log.e("LaundryRepo", "Error fetching allocations: ${e.message}")
        emit(emptyList())
    }

    override suspend fun createRequest(request: LaundryRequest): Result<Unit> {
        return try {
            supabaseClient.postgrest["laundry_requests"].insert(request)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LaundryRepo", "Insert failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getRequestById(id: String): LaundryRequest? {
        return try {
            supabaseClient.postgrest["laundry_requests"]
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingle<LaundryRequest>()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAllocationById(id: String): Allocation? {
        return try {
            supabaseClient.postgrest["allocations"]
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingle<Allocation>()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun cancelRequest(requestId: String): Result<Unit> {
        return try {
            supabaseClient.postgrest["allocations"].delete {
                filter {
                    eq("request_id", requestId)
                }
            }

            supabaseClient.postgrest["laundry_requests"].update(
                {
                    set("status", RequestStatus.CANCELLED.name)
                }
            ) {
                filter {
                    eq("id", requestId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LaundryRepo", "Cancellation failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getActiveRequest(userId: String): Result<LaundryRequest?> {
        return try {
            val rows = supabaseClient.postgrest["laundry_requests"]
                .select {
                    filter {
                        eq("user_id", userId)
                        or {
                            eq("status", RequestStatus.PENDING.name)
                            eq("status", RequestStatus.ALLOCATED.name)
                        }
                    }
                }
                .decodeList<LaundryRequest>()
            Result.success(rows.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPastRequestsPage(
        userId: String,
        offset: Int,
        limit: Int
    ): Result<List<LaundryRequest>> {
        return try {
            val rows = supabaseClient.postgrest["laundry_requests"]
                .select {
                    filter {
                        eq("user_id", userId)
                        or {
                            eq("status", RequestStatus.COMPLETED.name)
                            eq("status", RequestStatus.CANCELLED.name)
                        }
                    }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<LaundryRequest>()
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSlotOccupancy(
        date: String,
        hostel: String?,
        excludeRequestId: String?
    ): Result<Map<String, Int>> {
        return try {
            val requests = supabaseClient.postgrest["laundry_requests"]
                .select {
                    filter {
                        eq("preferred_date", date)
                        or {
                            eq("status", RequestStatus.PENDING.name)
                            eq("status", RequestStatus.ALLOCATED.name)
                        }
                    }
                }
                .decodeList<LaundryRequest>()

            val filtered = if (excludeRequestId != null) {
                requests.filter { it.id != excludeRequestId }
            } else {
                requests
            }

            val occupancy = filtered
                .groupBy { it.preferredStartTime }
                .mapValues { it.value.size }

            Result.success(occupancy)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRequestTimeSlot(
        requestId: String,
        newDate: String,
        newTime: String,
        maxMachines: Int,
        excludeRequestId: String?
    ): Result<LaundryRequest> {
        return try {
            // Passing null for hostel here might be an issue if we want strict hostel check, 
            // but for simplicity we'll assume the request id implies the hostel.
            // Better to fetch the request first.
            val currentRequest = getRequestById(requestId) ?: return Result.failure(Exception("Request not found"))
            
            val occupancyResult = getSlotOccupancy(newDate, currentRequest.hostel, excludeRequestId)
            if (occupancyResult.isFailure) {
                return Result.failure(occupancyResult.exceptionOrNull() ?: Exception("Verification failed"))
            }
            val occupancy = occupancyResult.getOrThrow()
            val taken = occupancy[newTime] ?: 0
            if (taken >= maxMachines) {
                return Result.failure(Exception("Slot is full"))
            }

            supabaseClient.postgrest["laundry_requests"].update(
                {
                    set("preferred_date", newDate)
                    set("preferred_start_time", newTime)
                    set("preferred_end_time", newTime)
                }
            ) {
                filter { eq("id", requestId) }
            }

            val updated = supabaseClient.postgrest["laundry_requests"]
                .select { filter { eq("id", requestId) } }
                .decodeSingle<LaundryRequest>()

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateRequestStatus(requestId: String, newStatus: RequestStatus): Result<Unit> {
        return try {
            supabaseClient.postgrest["laundry_requests"].update(
                {
                    set("status", newStatus.name)
                }
            ) {
                filter {
                    eq("id", requestId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
