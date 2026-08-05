package com.washslot.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.washslot.ui.components.CustomTextField
import com.washslot.ui.theme.BlueGradient
import com.washslot.viewmodel.CreateRequestViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateRequestScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSubmitted: () -> Unit = onNavigateBack,
    viewModel: CreateRequestViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedTime by viewModel.selectedStartTime.collectAsState()
    val selectedDuration by viewModel.selectedDuration.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val slotAvailability by viewModel.slotAvailability.collectAsState()
    val maxSlotsPerTime by viewModel.maxSlotsPerTime.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a", Locale.US) }
    val now = remember { LocalTime.now() }
    val today = remember { LocalDate.now().toString() }
    
    var isGridView by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.requestEvent.collect { event ->
            when (event) {
                is CreateRequestViewModel.RequestEvent.Success -> {
                    Toast.makeText(context, "Slot Reserved Successfully!", Toast.LENGTH_SHORT).show()
                    onNavigateToSubmitted()
                }
                is CreateRequestViewModel.RequestEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Book a Slot", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Select Date", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.dateOptions.forEach { option ->
                    val isSelected = selectedDate == option.date
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.onDateChange(option.date) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else Color.Gray
                            )
                            Text(
                                text = option.display,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Pick Available Time", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Availability shown as taken/total", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = { isGridView = !isGridView },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                        contentDescription = "Toggle View"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isGridView) {
                val rows = viewModel.availableTimes.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { time ->
                                val slotTime = try {
                                    LocalTime.parse(time, timeFormatter)
                                } catch (_: Exception) {
                                    LocalTime.MIN
                                }
                                val isPast = selectedDate == today && now.isAfter(slotTime)
                                
                                TimeSlotChip(
                                    modifier = Modifier.weight(1f),
                                    time = time,
                                    takenSlots = slotAvailability[time] ?: 0,
                                    maxSlots = maxSlotsPerTime,
                                    isSelected = selectedTime == time,
                                    isEnabled = !isPast,
                                    onSelect = { viewModel.onStartTimeChange(time) }
                                )
                            }
                            // Fill remaining space if row is not full
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    viewModel.availableTimes.forEach { time ->
                        val slotTime = try {
                            LocalTime.parse(time, timeFormatter)
                        } catch (_: Exception) {
                            LocalTime.MIN
                        }
                        val isPast = selectedDate == today && now.isAfter(slotTime)

                        TimeSlotRow(
                            time = time,
                            takenSlots = slotAvailability[time] ?: 0,
                            maxSlots = maxSlotsPerTime,
                            isSelected = selectedTime == time,
                            isEnabled = !isPast,
                            onSelect = { viewModel.onStartTimeChange(time) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Wash Type / Duration", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val durations = listOf(
                    Triple("Quick", 45, "45 min"),
                    Triple("Normal", 60, "1 hr"),
                    Triple("Heavy", 75, "75 min")
                )
                
                durations.forEach { (label, minutes, display) ->
                    val isSelected = selectedDuration == minutes
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.onDurationChange(minutes) },
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else Color.Gray
                            )
                            Text(
                                text = display,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Additional Notes", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            CustomTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = "e.g. Type of clothes",
                icon = Icons.Default.Info
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = viewModel::submitRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        brush = Brush.horizontalGradient(BlueGradient),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm Reservation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TimeSlotChip(
    modifier: Modifier = Modifier,
    time: String,
    takenSlots: Int,
    maxSlots: Int,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onSelect: () -> Unit
) {
    val isFull = takenSlots >= maxSlots
    val canSelect = isEnabled && !isFull
    
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        !isEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    val contentColor = when {
        isSelected -> Color.White
        !isEnabled -> Color.Gray.copy(alpha = 0.5f)
        isFull -> Color.Gray
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)),
        onClick = { if (canSelect) onSelect() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(time, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                if (!isEnabled) "Passed" else "$takenSlots/$maxSlots",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    isSelected -> Color.White.copy(alpha = 0.8f)
                    !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                    isFull -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
fun TimeSlotRow(
    time: String,
    takenSlots: Int,
    maxSlots: Int,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onSelect: () -> Unit
) {
    val isFull = takenSlots >= maxSlots
    val canSelect = isEnabled && !isFull
    
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        !isEnabled -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (isSelected) null else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)),
        onClick = { if (canSelect) onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = when {
                        isSelected -> Color.White
                        !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSelected -> Color.White
                        !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                        else -> Color.White
                    }
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        !isEnabled -> "Unavailable"
                        isFull -> "Full"
                        else -> "${maxSlots - takenSlots} slots left"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isSelected -> Color.White.copy(alpha = 0.8f)
                        !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                        isFull -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (!isEnabled) "--/--" else "$takenSlots/$maxSlots",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        isSelected -> Color.White
                        !isEnabled -> Color.Gray.copy(alpha = 0.5f)
                        isFull -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}
