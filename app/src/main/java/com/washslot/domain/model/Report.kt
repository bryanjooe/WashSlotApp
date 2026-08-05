package com.washslot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Report(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("machine_number")
    val machineNumber: String,
    val problem: String,
    val hostel: String,
    @SerialName("created_at")
    val createdAt: String? = null
)
