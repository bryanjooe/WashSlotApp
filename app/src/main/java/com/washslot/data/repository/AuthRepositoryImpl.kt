package com.washslot.data.repository

import android.util.Log
import com.washslot.domain.model.User
import com.washslot.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        Log.d("AuthRepo", "Initializing AuthRepositoryImpl")
        CoroutineScope(Dispatchers.IO).launch {
            refreshUser()
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            Log.d("AuthRepo", "Attempting login for: $email")
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            refreshUser()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        name: String,
        hostel: String,
        roomNumber: String
    ): Result<Unit> {
        return try {
            Log.d("AuthRepo", "Attempting registration for: $email")
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            var authUser = supabaseClient.auth.currentUserOrNull()
            if (authUser == null) {
                try {
                    supabaseClient.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }
                    authUser = supabaseClient.auth.currentUserOrNull()
                } catch (ignored: Exception) {
                    Log.d("AuthRepo", "Post-signup signin fallback ignored: ${ignored.message}")
                }
            }

            Log.d("AuthRepo", "Auth user after signup: ${authUser?.id}")

            if (authUser != null) {
                val profile = User(
                    id = authUser.id,
                    name = name,
                    email = email,
                    hostel = hostel,
                    roomNumber = roomNumber
                )
                
                Log.d("AuthRepo", "Inserting profile to database: $profile")
                try {
                    supabaseClient.postgrest["profiles"].insert(profile)
                } catch (e: Exception) {
                    Log.e("AuthRepo", "Error inserting profile on registration: ${e.message}")
                }
                _currentUser.value = profile
                Result.success(Unit)
            } else {
                Result.failure(Exception("Registration completed. Please log in with your credentials."))
            }
        } catch (e: Exception) {
            Log.e("AuthRepo", "Registration failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun refreshUser() {
        try {
            val session = supabaseClient.auth.currentSessionOrNull()
            Log.d("AuthRepo", "Refreshing user. Session found: ${session != null}")
            if (session != null) {
                val userId = session.user?.id ?: return
                val email = session.user?.email ?: ""
                Log.d("AuthRepo", "Fetching profile for userId: $userId")
                var profile: User? = null
                try {
                    profile = supabaseClient.postgrest["profiles"]
                        .select {
                            filter {
                                eq("id", userId)
                            }
                        }
                        .decodeSingle<User>()
                } catch (e: Exception) {
                    Log.e("AuthRepo", "Error decoding profile from DB: ${e.message}")
                }

                if (profile == null) {
                    val fallbackName = email.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.ifEmpty { "Student" }
                    profile = User(
                        id = userId,
                        name = fallbackName,
                        email = email,
                        hostel = "Hostel A",
                        roomNumber = "101"
                    )
                    try {
                        supabaseClient.postgrest["profiles"].insert(profile)
                    } catch (e: Exception) {
                        Log.e("AuthRepo", "Error inserting fallback profile: ${e.message}")
                    }
                }
                
                Log.d("AuthRepo", "Profile fetched successfully: ${profile.name}")
                _currentUser.value = profile
            } else {
                _currentUser.value = null
            }
        } catch (e: Exception) {
            Log.e("AuthRepo", "Error refreshing user: ${e.message}")
            _currentUser.value = null
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            Log.d("AuthRepo", "Logging out")
            supabaseClient.auth.signOut()
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
