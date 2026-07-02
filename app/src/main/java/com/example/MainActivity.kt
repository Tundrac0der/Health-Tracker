package com.example

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HealthMeasurement
import com.example.data.HealthViewModel
import com.example.data.MedicationReminder
import com.example.data.PdfExporter
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load initial locale before setting content
        val sharedPrefs = getSharedPreferences("health_tracker_prefs", Context.MODE_PRIVATE)
        val savedLang = sharedPrefs.getString("language", "en") ?: "en"
        setAppLocale(this, savedLang)

        enableEdgeToEdge()
        setContent {
            val viewModel: HealthViewModel = viewModel()
            val language by viewModel.currentLanguage.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            // Resolve layout direction based on active language
            val layoutDirection = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            // Determine if dark theme should be applied
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MyApplicationTheme(darkTheme = isDarkTheme) {
                    AppContent(
                        viewModel = viewModel,
                        activity = this@MainActivity,
                        language = language
                    )
                }
            }
        }
    }

    fun setAppLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
        } else {
            configuration.locale = locale
        }
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    viewModel: HealthViewModel,
    activity: MainActivity,
    language: String
) {
    val context = LocalContext.current
    var showLoading by remember { mutableStateOf(true) }

    // Splash Timer
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showLoading = false
    }

    if (showLoading) {
        LoadingScreen(language = language)
    } else {
        // Main Application Scaffolding
        var selectedTab by remember { mutableStateOf(0) }
        var showAddMeasurementDialog by remember { mutableStateOf(false) }
        var showAddReminderDialog by remember { mutableStateOf(false) }

        val username by viewModel.username.collectAsStateWithLifecycle()
        var showNameDialog by remember { mutableStateOf(false) }

        val sharedPrefs = remember { context.getSharedPreferences("health_tracker_prefs", Context.MODE_PRIVATE) }
        var showFirstTimeNamePrompt by remember {
            mutableStateOf(sharedPrefs.getBoolean("first_time_name_prompt", true))
        }

        if (showFirstTimeNamePrompt && username == "Marc") {
            LaunchedEffect(Unit) {
                showNameDialog = true
            }
        }

        // Ask for Notifications Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var hasNotificationPermission by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasNotificationPermission = isGranted
            }
            LaunchedEffect(Unit) {
                if (!hasNotificationPermission) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Scaffold(
            topBar = {
                val categoryText = when (selectedTab) {
                    0 -> stringResource(id = R.string.dashboard).uppercase()
                    1 -> stringResource(id = R.string.history).uppercase()
                    2 -> stringResource(id = R.string.stats).uppercase()
                    3 -> stringResource(id = R.string.reminders).uppercase()
                    else -> stringResource(id = R.string.settings).uppercase()
                }
                val user = username.ifEmpty { "Marc" }
                val greetingText = when (language) {
                    "fr" -> "Bonjour, $user"
                    "ar" -> "مرحباً، $user"
                    else -> "Hello, $user"
                }
                val titleText = when (selectedTab) {
                    0 -> greetingText
                    1 -> stringResource(id = R.string.history)
                    2 -> stringResource(id = R.string.stats)
                    3 -> stringResource(id = R.string.medication_reminders)
                    else -> stringResource(id = R.string.settings)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = categoryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { showNameDialog = true }
                                .testTag("edit_profile_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Edit User Name",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text(text = stringResource(id = R.string.dashboard).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.testTag("tab_dashboard")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Menu, contentDescription = "History") },
                        label = { Text(text = stringResource(id = R.string.history).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.testTag("tab_history")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Stats") },
                        label = { Text(text = stringResource(id = R.string.stats).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.testTag("tab_stats")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Reminders") },
                        label = { Text(text = stringResource(id = R.string.reminders).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.testTag("tab_reminders")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text(text = stringResource(id = R.string.settings).uppercase(), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0 || selectedTab == 1) {
                    FloatingActionButton(
                        onClick = { showAddMeasurementDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_add_measurement")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add measurement")
                    }
                } else if (selectedTab == 3) {
                    FloatingActionButton(
                        onClick = { showAddReminderDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("fab_add_reminder")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add reminder")
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        viewModel = viewModel,
                        onAddShortcut = { showAddMeasurementDialog = true }
                    )
                    1 -> HistoryScreen(viewModel = viewModel)
                    2 -> StatsScreen(viewModel = viewModel)
                    3 -> RemindersScreen(viewModel = viewModel)
                    4 -> SettingsScreen(viewModel = viewModel, activity = activity)
                }
            }
        }

        // Dialog Modals
        if (showAddMeasurementDialog) {
            AddMeasurementDialog(
                onDismiss = { showAddMeasurementDialog = false },
                onSave = { type, val1, val2, notes, ts ->
                    viewModel.insertMeasurement(type, val1, val2, notes, ts)
                    showAddMeasurementDialog = false
                    Toast.makeText(context, context.getString(R.string.healthy_heart), Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showAddReminderDialog) {
            AddReminderDialog(
                onDismiss = { showAddReminderDialog = false },
                onSave = { name, dosage, sched, hour, minute, rDate ->
                    viewModel.insertReminder(name, dosage, sched, hour, minute, rDate)
                    showAddReminderDialog = false
                    Toast.makeText(context, context.getString(R.string.reminders), Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showNameDialog) {
            UserNameDialog(
                currentName = username,
                onDismiss = {
                    showNameDialog = false
                    sharedPrefs.edit().putBoolean("first_time_name_prompt", false).apply()
                    showFirstTimeNamePrompt = false
                },
                onSave = { newName ->
                    viewModel.setUsername(newName)
                    showNameDialog = false
                    sharedPrefs.edit().putBoolean("first_time_name_prompt", false).apply()
                    showFirstTimeNamePrompt = false
                }
            )
        }
    }
}

// ---------------- SPLASH / LOADING SCREEN ----------------
@Composable
fun LoadingScreen(language: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1978E5), Color(0xFF0C4E9C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Heart Beat Pulse Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Pulse Heart",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = stringResource(id = R.string.app_name),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(id = R.string.healthy_heart),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
            
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ---------------- DASHBOARD TAB ----------------
@Composable
fun DashboardScreen(
    viewModel: HealthViewModel,
    onAddShortcut: () -> Unit
) {
    val measurements by viewModel.allMeasurements.collectAsStateWithLifecycle(initialValue = emptyList())
    val reminders by viewModel.allReminders.collectAsStateWithLifecycle(initialValue = emptyList())

    val latestGlucose = measurements.firstOrNull { it.type == "GLUCOSE" }
    val latestPressure = measurements.firstOrNull { it.type == "PRESSURE" }
    val latestHeartRate = measurements.firstOrNull { it.type == "HEART_RATE" }

    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Banner - Typographical
        item {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.healthy_heart),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Hero Metric Card: Blood Glucose
        item {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddShortcut() }
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    val glucoseVal = latestGlucose?.value1 ?: 1.2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.blood_glucose),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = if (latestGlucose != null) String.format(Locale.US, "%.2f", latestGlucose.value1) else "1.20",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "g/L",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                            }
                        }

                        // Status Badge Row
                        val (statusTextRes, statusColor) = when {
                            glucoseVal in 0.8..2.0 -> Pair(R.string.classify_normal, Color(0xFF4CAF50))
                            glucoseVal in 2.1..2.6 -> Pair(R.string.classify_caution, Color(0xFFFBC02D))
                            glucoseVal in 2.7..3.5 -> Pair(R.string.classify_high, Color(0xFFFF9800))
                            glucoseVal > 3.5 -> Pair(R.string.classify_very_high, Color(0xFFE53935))
                            else -> Pair(R.string.classify_normal, Color(0xFF4CAF50))
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Text(
                                text = stringResource(id = statusTextRes).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Progress slider line matching design HTML
                    Spacer(modifier = Modifier.height(16.dp))
                    val fraction = if (latestGlucose != null) {
                        (latestGlucose.value1 / 4.0).toFloat().coerceIn(0.15f, 1.0f)
                    } else {
                        0.4f
                    }
                    val progressColor = when {
                        glucoseVal in 0.8..2.0 -> Color(0xFF4CAF50)
                        glucoseVal in 2.1..2.6 -> Color(0xFFFBC02D)
                        glucoseVal in 2.7..3.5 -> Color(0xFFFF9800)
                        else -> Color(0xFFE53935)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .background(progressColor, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (latestGlucose != null) {
                            "${stringResource(id = R.string.latest_measurements)}: ${simpleDateFormat.format(Date(latestGlucose.timestamp))}"
                        } else {
                            "Dernière mesure: Aujourd\'hui, 08:30"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Two-column Grid for BP and Heart Rate
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pressure Card
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAddShortcut() }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.blood_pressure).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (latestPressure != null) "${latestPressure.value1.toInt()}/${latestPressure.value2?.toInt() ?: 0}" else "120/80",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "mmHg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Heart Rate Card
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAddShortcut() }
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE53935).copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(id = R.string.heart_rate).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (latestHeartRate != null) "${latestHeartRate.value1.toInt()}" else "72",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Medication Reminders
        item {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.medication_reminders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                val activeReminders = reminders.filter { it.isActive }
                if (activeReminders.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = stringResource(id = R.string.no_upcoming_reminders),
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeReminders.take(3).forEachIndexed { index, reminder ->
                            // The top active reminder matches the primary solid blue design card from the HTML
                            val usePrimaryCard = index == 0
                            DashboardReminderCard(
                                reminder = reminder,
                                usePrimaryTheme = usePrimaryCard,
                                onToggle = { viewModel.toggleReminder(reminder) },
                                onDelete = { viewModel.deleteReminder(reminder) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardReminderCard(
    reminder: MedicationReminder,
    usePrimaryTheme: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (usePrimaryTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (usePrimaryTheme) Color.White else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (usePrimaryTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconBg = if (usePrimaryTheme) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val iconColor = if (usePrimaryTheme) Color.White else MaterialTheme.colorScheme.primary
    val borderStroke = if (usePrimaryTheme) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = borderStroke,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.medicationName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = reminder.dosage,
                    fontSize = 12.sp,
                    color = subTextColor
                )
                val scheduleLabel = if (reminder.scheduleType == "ONCE" && reminder.reminderDate != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.format(java.util.Date(reminder.reminderDate))
                } else {
                    stringResource(id = when (reminder.scheduleType) {
                        "DAILY" -> R.string.schedule_daily
                        "WEEKLY" -> R.string.schedule_weekly
                        "MONTHLY" -> R.string.schedule_monthly
                        else -> R.string.schedule_once
                    })
                }
                Text(
                    text = "$scheduleLabel • ${String.format(Locale.US, "%02d:%02d", reminder.hour, reminder.minute)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (usePrimaryTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = if (usePrimaryTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ---------------- HISTORY TAB ----------------
@Composable
fun HistoryScreen(viewModel: HealthViewModel) {
    val measurements by viewModel.filteredMeasurements.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.selectedFilterType.collectAsStateWithLifecycle()
    val startDate by viewModel.startDateFilter.collectAsStateWithLifecycle()
    val endDate by viewModel.endDateFilter.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filter Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text(text = stringResource(id = R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("search_field"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Quick Selector for type
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val types = listOf(
                "ALL" to R.string.filter_all,
                "GLUCOSE" to R.string.blood_glucose,
                "PRESSURE" to R.string.blood_pressure,
                "HEART_RATE" to R.string.heart_rate
            )
            items(types) { (typeKey, stringRes) ->
                val isSelected = filterType == typeKey
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectedFilterType.value = typeKey },
                    label = { Text(text = stringResource(id = stringRes)) },
                    modifier = Modifier.testTag("filter_chip_$typeKey")
                )
            }
        }

        // Date range selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, yr, mo, dy ->
                            val cal = Calendar.getInstance().apply { set(yr, mo, dy, 0, 0, 0) }
                            viewModel.startDateFilter.value = cal.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = if (startDate != null) simpleDateFormat.format(Date(startDate!!)) else stringResource(id = R.string.date_start),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Button(
                onClick = {
                    val calendar = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, yr, mo, dy ->
                            val cal = Calendar.getInstance().apply { set(yr, mo, dy, 23, 59, 59) }
                            viewModel.endDateFilter.value = cal.timeInMillis
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text(
                    text = if (endDate != null) simpleDateFormat.format(Date(endDate!!)) else stringResource(id = R.string.date_end),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            if (startDate != null || endDate != null) {
                IconButton(
                    onClick = {
                        viewModel.startDateFilter.value = null
                        viewModel.endDateFilter.value = null
                    }
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Dates")
                }
            }
        }

        // Export PDF Banner
        Button(
            onClick = {
                PdfExporter.exportToPdf(context, measurements, filterType)
                Toast.makeText(context, context.getString(R.string.report_generated_success), Toast.LENGTH_LONG).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("export_pdf_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Share, contentDescription = "PDF Report")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.generate_report))
        }

        // List of entries
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(measurements) { item ->
                MeasurementRowItem(item = item, onDelete = { viewModel.deleteMeasurement(item) })
            }
        }
    }
}

// ---------------- STATISTICS TAB ----------------
@Composable
fun StatsScreen(viewModel: HealthViewModel) {
    val measurements by viewModel.allMeasurements.collectAsStateWithLifecycle(initialValue = emptyList())

    val glucoseData = measurements.filter { it.type == "GLUCOSE" }
    val bpData = measurements.filter { it.type == "PRESSURE" }
    val hrData = measurements.filter { it.type == "HEART_RATE" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Blood Glucose Statistics
        item {
            StatsCard(
                title = stringResource(id = R.string.blood_glucose),
                avgStr = if (glucoseData.isEmpty()) "-" else String.format(Locale.US, "%.2f g/L", glucoseData.map { it.value1 }.average()),
                minStr = if (glucoseData.isEmpty()) "-" else String.format(Locale.US, "%.2f g/L", glucoseData.map { it.value1 }.minOrNull()),
                maxStr = if (glucoseData.isEmpty()) "-" else String.format(Locale.US, "%.2f g/L", glucoseData.map { it.value1 }.maxOrNull()),
                trend = getGlucoseTrend(glucoseData)
            )
        }

        // Blood Pressure Statistics
        item {
            StatsCard(
                title = stringResource(id = R.string.blood_pressure),
                avgStr = if (bpData.isEmpty()) "-" else "${bpData.map { it.value1 }.average().toInt()}/${bpData.map { it.value2 ?: 0.0 }.average().toInt()} mmHg",
                minStr = if (bpData.isEmpty()) "-" else "${bpData.map { it.value1 }.minOrNull()?.toInt()}/${bpData.map { it.value2 ?: 0.0 }.minOrNull()?.toInt()} mmHg",
                maxStr = if (bpData.isEmpty()) "-" else "${bpData.map { it.value1 }.maxOrNull()?.toInt()}/${bpData.map { it.value2 ?: 0.0 }.maxOrNull()?.toInt()} mmHg",
                trend = getBpTrend(bpData)
            )
        }

        // Heart Rate Statistics
        item {
            StatsCard(
                title = stringResource(id = R.string.heart_rate),
                avgStr = if (hrData.isEmpty()) "-" else "${hrData.map { it.value1 }.average().toInt()} bpm",
                minStr = if (hrData.isEmpty()) "-" else "${hrData.map { it.value1 }.minOrNull()?.toInt()} bpm",
                maxStr = if (hrData.isEmpty()) "-" else "${hrData.map { it.value1 }.maxOrNull()?.toInt()} bpm",
                trend = getHrTrend(hrData)
            )
        }
    }
}

private fun getGlucoseTrend(list: List<HealthMeasurement>): String {
    if (list.size < 2) return "Stable"
    val last = list.first().value1
    val prev = list[1].value1
    return when {
        last > prev + 0.1 -> "Rising ↗"
        last < prev - 0.1 -> "Declining ↘"
        else -> "Stable →"
    }
}

private fun getBpTrend(list: List<HealthMeasurement>): String {
    if (list.size < 2) return "Stable"
    val last = list.first().value1 // Systolic
    val prev = list[1].value1
    return when {
        last > prev + 5 -> "Rising ↗"
        last < prev - 5 -> "Declining ↘"
        else -> "Stable →"
    }
}

private fun getHrTrend(list: List<HealthMeasurement>): String {
    if (list.size < 2) return "Stable"
    val last = list.first().value1
    val prev = list[1].value1
    return when {
        last > prev + 4 -> "Rising ↗"
        last < prev - 4 -> "Declining ↘"
        else -> "Stable →"
    }
}

// Stats Card Container Helper
@Composable
fun StatsCard(
    title: String,
    avgStr: String,
    minStr: String,
    maxStr: String,
    trend: String
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = trend,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.average).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = avgStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.min).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = minStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.max).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = maxStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ---------------- REMINDERS TAB ----------------
@Composable
fun RemindersScreen(viewModel: HealthViewModel) {
    val reminders by viewModel.allReminders.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.no_upcoming_reminders),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderRowItem(
                        reminder = reminder,
                        onToggle = { viewModel.toggleReminder(reminder) },
                        onDelete = { viewModel.deleteReminder(reminder) }
                    )
                }
            }
        }
    }
}

// ---------------- SETTINGS TAB ----------------
@Composable
fun SettingsScreen(
    viewModel: HealthViewModel,
    activity: MainActivity
) {
    val language by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // User Name Card
        val usernameState = remember { mutableStateOf(username) }
        LaunchedEffect(username) {
            usernameState.value = username
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(id = R.string.user_name),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = usernameState.value,
                    onValueChange = { newValue ->
                        usernameState.value = newValue
                        viewModel.setUsername(newValue)
                    },
                    placeholder = { Text(text = stringResource(id = R.string.enter_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Language Select Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(id = R.string.language), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val langs = listOf(
                        "en" to "English",
                        "fr" to "Français",
                        "ar" to "العربية"
                    )
                    langs.forEach { (code, label) ->
                        val isSelected = language == code
                        Button(
                            onClick = {
                                viewModel.setLanguage(code)
                                activity.setAppLocale(activity, code)
                                activity.recreate() // Recreate triggers dynamic resource reload immediately!
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(text = label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Theme Mode Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(id = R.string.theme), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val themes = listOf(
                        "light" to R.string.light_mode,
                        "dark" to R.string.dark_mode,
                        "system" to R.string.system_mode
                    )
                    themes.forEach { (mode, strRes) ->
                        val isSelected = themeMode == mode
                        Button(
                            onClick = { viewModel.setTheme(mode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(text = stringResource(id = strRes), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- DIALOGS ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, val1: Double, val2: Double?, notes: String?, timestamp: Long) -> Unit
) {
    var type by remember { mutableStateOf("GLUCOSE") } // GLUCOSE, PRESSURE, HEART_RATE
    var glucoseStr by remember { mutableStateOf("") }
    var systolicStr by remember { mutableStateOf("") }
    var diastolicStr by remember { mutableStateOf("") }
    var pulseStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_add_measurement")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.add_measurement),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Type selector Segment
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val list = listOf(
                        "GLUCOSE" to R.string.blood_glucose,
                        "PRESSURE" to R.string.blood_pressure,
                        "HEART_RATE" to R.string.heart_rate
                    )
                    list.forEach { (key, strRes) ->
                        val isSel = type == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { type = key }
                                .testTag("type_radio_$key"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = strRes),
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Inputs depending on Type
                when (type) {
                    "GLUCOSE" -> {
                        OutlinedTextField(
                            value = glucoseStr,
                            onValueChange = { glucoseStr = it },
                            label = { Text(text = stringResource(id = R.string.glucose_value)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("input_glucose"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    "PRESSURE" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = systolicStr,
                                onValueChange = { systolicStr = it },
                                label = { Text(text = stringResource(id = R.string.systolic_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_systolic"),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = diastolicStr,
                                onValueChange = { diastolicStr = it },
                                label = { Text(text = stringResource(id = R.string.diastolic_value)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).testTag("input_diastolic"),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    "HEART_RATE" -> {
                        OutlinedTextField(
                            value = pulseStr,
                            onValueChange = { pulseStr = it },
                            label = { Text(text = stringResource(id = R.string.heart_rate_value)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("input_heart_rate"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(text = stringResource(id = R.string.notes)) },
                    placeholder = { Text(text = stringResource(id = R.string.notes_placeholder)) },
                    modifier = Modifier.fillMaxWidth().height(80.dp).testTag("input_notes"),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_cancel")) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val val1: Double
                            var val2: Double? = null
                            when (type) {
                                "GLUCOSE" -> {
                                    val1 = glucoseStr.toDoubleOrNull() ?: 0.0
                                }
                                "PRESSURE" -> {
                                    val1 = systolicStr.toDoubleOrNull() ?: 120.0
                                    val2 = diastolicStr.toDoubleOrNull() ?: 80.0
                                }
                                "HEART_RATE" -> {
                                    val1 = pulseStr.toDoubleOrNull() ?: 70.0
                                }
                                else -> return@Button
                            }
                            onSave(type, val1, val2, notes.ifEmpty { null }, System.currentTimeMillis())
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_save")
                    ) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, dosage: String, sched: String, hour: Int, minute: Int, reminderDate: Long?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var sched by remember { mutableStateOf("DAILY") } // DAILY, WEEKLY, MONTHLY, ONCE
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var reminderDate by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current

    LaunchedEffect(sched) {
        if (sched == "ONCE" && reminderDate == null) {
            reminderDate = System.currentTimeMillis()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_add_reminder")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.add_reminder),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(id = R.string.medication_name)) },
                    modifier = Modifier.fillMaxWidth().testTag("input_reminder_name"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text(text = stringResource(id = R.string.dosage)) },
                    modifier = Modifier.fillMaxWidth().testTag("input_reminder_dosage"),
                    shape = RoundedCornerShape(10.dp)
                )

                // Schedule Type
                Column {
                    Text(text = stringResource(id = R.string.schedule_type), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val items = listOf(
                            "DAILY" to R.string.schedule_daily,
                            "WEEKLY" to R.string.schedule_weekly,
                            "MONTHLY" to R.string.schedule_monthly,
                            "ONCE" to R.string.schedule_once
                        )
                        items.forEach { (key, res) ->
                            val isSel = sched == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { sched = key }
                                    .testTag("sched_radio_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = res),
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Specific Date Selector (only shown if ONCE is selected)
                if (sched == "ONCE") {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateString = reminderDate?.let { sdf.format(java.util.Date(it)) } ?: stringResource(id = R.string.select_date)
                    Button(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val selectedCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    reminderDate = selectedCal.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("button_reminder_date"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "${stringResource(id = R.string.select_date)}: $dateString", fontSize = 12.sp)
                    }
                }

                // Time picker button
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hr, min ->
                                hour = hr
                                minute = min
                            },
                            hour,
                            minute,
                            true
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("button_reminder_time"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text(text = "${stringResource(id = R.string.time_of_reminder)}: ${String.format(Locale.US, "%02d:%02d", hour, minute)}")
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_reminder_cancel")) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotEmpty()) {
                                onSave(name, dosage, sched, hour, minute, reminderDate)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_reminder_save")
                    ) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
}


// ---------------- ITEM ROW COMPOSABLES ----------------

@Composable
fun MeasurementRowItem(
    item: HealthMeasurement,
    onDelete: () -> Unit
) {
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Status Icon Accent
            val iconTint: Color
            val labelRes: Int
            val valueText: String

            when (item.type) {
                "GLUCOSE" -> {
                    labelRes = R.string.blood_glucose
                    valueText = String.format(Locale.US, "%.2f g/L", item.value1)
                    
                    // Specific dynamic classifications color
                    iconTint = when {
                        item.value1 in 0.8..2.0 -> Color(0xFF4CAF50) // Green
                        item.value1 in 2.1..2.6 -> Color(0xFFFBC02D) // Yellow
                        item.value1 in 2.7..3.5 -> Color(0xFFFF9800) // Orange
                        item.value1 > 3.5 -> Color(0xFFE53935)       // Red
                        else -> MaterialTheme.colorScheme.primary
                    }
                }
                "PRESSURE" -> {
                    labelRes = R.string.blood_pressure
                    valueText = "${item.value1.toInt()}/${item.value2?.toInt() ?: 0} mmHg"
                    iconTint = MaterialTheme.colorScheme.primary
                }
                "HEART_RATE" -> {
                    labelRes = R.string.heart_rate
                    valueText = "${item.value1.toInt()} bpm"
                    iconTint = Color(0xFFE53935) // Heart Red
                }
                else -> {
                    labelRes = R.string.app_name
                    valueText = item.value1.toString()
                    iconTint = MaterialTheme.colorScheme.secondary
                }
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.type) {
                        "GLUCOSE" -> Icons.Default.Info
                        "PRESSURE" -> Icons.Default.Menu
                        else -> Icons.Default.Favorite
                    },
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = labelRes).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = valueText,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!item.notes.isNullOrEmpty()) {
                    Text(
                        text = item.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = simpleDateFormat.format(Date(item.timestamp)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Quick delete handle
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_measurement_btn")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ReminderRowItem(
    reminder: MedicationReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.medicationName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = reminder.dosage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val scheduleLabel = if (reminder.scheduleType == "ONCE" && reminder.reminderDate != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdf.format(java.util.Date(reminder.reminderDate))
                } else {
                    stringResource(id = when (reminder.scheduleType) {
                        "DAILY" -> R.string.schedule_daily
                        "WEEKLY" -> R.string.schedule_weekly
                        "MONTHLY" -> R.string.schedule_monthly
                        else -> R.string.schedule_once
                    })
                }
                Text(
                    text = "$scheduleLabel • ${String.format(Locale.US, "%02d:%02d", reminder.hour, reminder.minute)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // On/Off Switch Toggle
            Switch(
                checked = reminder.isActive,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("reminder_switch_${reminder.id}")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_reminder_btn")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Reminder",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun UserNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.edit_user_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text(text = stringResource(id = R.string.enter_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nameInput.isNotBlank()) {
                                onSave(nameInput)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(id = R.string.save))
                    }
                }
            }
        }
    }
}
