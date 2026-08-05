package com.washslot.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val hostel: String,
    @SerialName("room_number")
    val roomNumber: String,
    @SerialName("created_at")
    val createdAt: String? = null
)
