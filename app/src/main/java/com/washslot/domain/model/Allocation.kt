package com.washslot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Allocation(
    val id: String? = null,
    @SerialName("request_id")
    val requestId: String,
    @SerialName("machine_id")
    val machineId: String,
    @SerialName("allocated_start")
    val allocatedStart: String,
    @SerialName("allocated_end")
    val allocatedEnd: String
)
