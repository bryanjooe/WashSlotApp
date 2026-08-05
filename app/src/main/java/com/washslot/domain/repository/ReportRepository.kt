package com.washslot.domain.repository

import com.washslot.domain.model.Report

interface ReportRepository {
    suspend fun submitReport(report: Report): Result<Unit>
    suspend fun getMyReports(userId: String): Result<List<Report>>
}
