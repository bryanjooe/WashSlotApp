package com.washslot.domain.repository

import com.washslot.domain.model.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<User?>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(email: String, password: String, name: String, hostel: String, roomNumber: String): Result<Unit>
    suspend fun logout(): Result<Unit>
}
