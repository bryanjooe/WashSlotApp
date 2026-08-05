package com.washslot.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object CreateRequest : Screen("create_request")
    object RequestSubmitted : Screen("request_submitted")
    object MyRequests : Screen("my_requests")
    object AllocationDetails : Screen("allocation_details/{allocationId}") {
        fun createRoute(allocationId: String) = "allocation_details/$allocationId"
    }
    object MySchedule : Screen("my_schedule")
    object Reports : Screen("reports")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    
    // Kept for compatibility if needed, but using Reports now
    val Notifications = Reports
}
