package com.washslot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LaundryRequest(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("preferred_date")
    val preferredDate: String,
    @SerialName("preferred_start_time")
    val preferredStartTime: String,
    @SerialName("preferred_end_time")
    val preferredEndTime: String,
    val duration: Int,
    val flexibility: String,
    val notes: String?,
    val status: RequestStatus,
    val hostel: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
enum class RequestStatus {
    PENDING, ALLOCATED, COMPLETED, CANCELLED
}
