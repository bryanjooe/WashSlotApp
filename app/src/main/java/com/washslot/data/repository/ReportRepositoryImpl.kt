package com.washslot.data.repository

import com.washslot.domain.model.Report
import com.washslot.domain.repository.ReportRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ReportRepository {

    override suspend fun submitReport(report: Report): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            supabaseClient.postgrest["reports"].insert(report)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyReports(userId: String): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            val reports = supabaseClient.postgrest["reports"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<Report>()
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
