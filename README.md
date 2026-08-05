# WashSlot - Laundry Management App

WashSlot is a modern Android application designed to streamline laundry slot bookings for students in hostels. It allows users to book washing machine slots, track their request history, and report machine issues.

Video Link - https://drive.google.com/file/d/12cTfV8Kth9bcm5sdYvBNwpGLWUIOaUwo/view?usp=sharing
Website Link - https://github.com/mshezan/washslot


## Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Modern declarative UI)
- **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **Dependency Injection:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Backend-as-a-Service:** [Supabase](https://supabase.com/)
    - **Authentication:** Supabase Auth (Email/Password)
    - **Database:** PostgreSQL (via Postgrest)
    - **Realtime:** For live updates.
- **Networking:** [Ktor](https://ktor.io/) (Client for Supabase communication)
- **Asynchronous Programming:** Kotlin Coroutines & Flow
- **Image Loading:** [Coil 3](https://coil-kt.github.io/coil/)
- **Serialization:** Kotlinx Serialization

## Features

- **Persistent Login:** Stay logged in across app restarts.
- **Intuitive Booking:** 
    - Quick-select chips for Today, Tomorrow, and Day After.
    - Grid/List view toggle for time slots.
    - Automatic disabling of past time slots for the current day.
- **Active Session Dashboard:** 
    - Real-time status tracking.
    - "Arrived" button enabled only at the scheduled time.
    - Single-line session details.
- **Request History:** Detailed log of all past and pending requests with status chips.
- **Issue Reporting:** Submit machine problems directly to the system.
- **Hostel-Specific Availability:** Machine counts and availability based on the user's hostel.

## Database Setup (Supabase)

To run this project, you need the following tables in your Supabase database:

### 1. `profiles` Table
Stores user information after registration.
```sql
CREATE TABLE profiles (
    id UUID REFERENCES auth.users PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    hostel TEXT NOT NULL,
    room_number TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 2. `laundry_requests` Table
Stores all booking information.
```sql
CREATE TABLE laundry_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    preferred_date DATE NOT NULL,
    preferred_start_time TEXT NOT NULL,
    preferred_end_time TEXT NOT NULL,
    duration INTEGER NOT NULL,
    notes TEXT,
    status TEXT DEFAULT 'PENDING',
    hostel TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 3. `reports` Table
Stores machine issue reports.
```sql
CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    machine_number TEXT NOT NULL,
    problem TEXT NOT NULL,
    hostel TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

## Theme & UI
The app features a custom **Dark Theme** with a signature **Blue Gradient** for primary actions, providing a modern and clean aesthetic for night-time hostel use.
