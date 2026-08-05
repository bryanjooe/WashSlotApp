package com.washslot.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://aavbammvfcblxgkxolqx.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFhdmJhbW12ZmNibHhna3hvbHF4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODU4MTcyMDIsImV4cCI6MjEwMTM5MzIwMn0.0zCkWsQa5iPVQTC_VCbDR6ATaw139mzHpSU0IN5vjaI"
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
