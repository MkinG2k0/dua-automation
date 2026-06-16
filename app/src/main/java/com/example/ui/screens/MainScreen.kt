package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.AppSettingsEntity
import com.example.data.database.DuaaEntity
import com.example.data.database.DuaaTriggerLogEntity
import com.example.ui.viewmodel.DuaaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DuaaViewModel) {
    val context = LocalContext.current
    val duaaList by viewModel.duaaList.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val logs by viewModel.triggerLogs.collectAsStateWithLifecycle()

    val currentSettings = settings ?: AppSettingsEntity()

    var selectedTab by remember { mutableIntStateOf(0) }

    // State for permission dialogs and configurations inputs
    var showWifiDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showAlarmFilterDialog by remember { mutableStateOf(false) }

    // Read states of permissions
    var hasNotificationListenerPermission by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Refresh listener and notification permission states when returning to app
    val mainLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(mainLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationListenerPermission = isNotificationListenerEnabled(context)
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            }
        }
        mainLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            mainLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!currentSettings.isOnboardingCompleted) {
        OnboardingScreen(
            viewModel = viewModel,
            settings = currentSettings,
            onFinished = {
                viewModel.setOnboardingCompleted(true)
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = if (selectedTab == 0) "Дуа Автоматизация" else "Триггеры и Настройки",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                letterSpacing = (-0.5).sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (currentSettings.isGlobalAutomationEnabled) Color(0xFF4CAF50) else Color.Gray)
                                )
                                Text(
                                    text = if (currentSettings.isGlobalAutomationEnabled) "Служба активна в фоновом режиме" else "Служба остановлена",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Дуа") },
                        label = { Text("Дуа") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                        label = { Text("Настройки") }
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (selectedTab == 0) {
                    // Permission alerts and requirements
                    if (!hasNotificationListenerPermission || !hasNotificationPermission) {
                        item {
                            PermissionRequestCard(
                                hasListener = hasNotificationListenerPermission,
                                hasNotifications = hasNotificationPermission,
                                hasLocation = true, // Hide GPS location condition from UI warning card
                                onGrantListener = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                onGrantNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                onGrantLocation = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    // Global Master Activation Switch
                    item {
                        GlobalToggleCard(
                            isEnabled = currentSettings.isGlobalAutomationEnabled,
                            onToggle = { viewModel.toggleGlobalAutomation(it) }
                        )
                    }

                    // Supplication List Header
                    item {
                        Text(
                            text = "Священные Дуа",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 5 Fixed Supplication Cards
                    items(duaaList) { duaa ->
                        DuaaItemCard(
                            duaa = duaa,
                            isGlobalEnabled = currentSettings.isGlobalAutomationEnabled,
                            onToggleEnabled = { isChecked ->
                                viewModel.toggleDuaa(duaa, isChecked)
                            },
                            onTestPlay = {
                                viewModel.testPlayDuaa(context, duaa)
                            }
                        )
                    }
                } else {
                    // Configuration Options Panel
                    item {
                        Text(
                            text = "Настройки триггеров",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        SettingsPanelCard(
                            settings = currentSettings,
                            onEditWifi = { showWifiDialog = true },
                            onEditBluetooth = { showBluetoothDialog = true },
                            onEditLocation = { showLocationDialog = true },
                            onEditAlarmFilter = { showAlarmFilterDialog = true },
                            onResetOnboarding = { viewModel.setOnboardingCompleted(false) },
                            onVolumeChange = { viewModel.saveVolume(it) }
                        )
                    }

                    // History Logs Panel
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "История срабатываний",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (logs.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllLogs() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Очистить")
                                }
                            }
                        }
                    }

                    if (logs.isEmpty()) {
                        item {
                            EmptyLogsCard()
                        }
                    } else {
                        items(logs) { log ->
                            LogItemRow(log = log)
                        }
                    }
                }
            }
        }

        // Config Dialogs
        if (showWifiDialog) {
            WifiConfigDialog(
                currentSsid = currentSettings.homeSsid,
                onDismiss = { showWifiDialog = false },
                onSave = {
                    viewModel.saveWifiSsid(it)
                    showWifiDialog = false
                }
            )
        }

        if (showBluetoothDialog) {
            BluetoothConfigDialog(
                currentDevice = currentSettings.carBluetoothName,
                onDismiss = { showBluetoothDialog = false },
                onSave = {
                    viewModel.saveBluetoothFilter(it)
                    showBluetoothDialog = false
                }
            )
        }

        if (showLocationDialog) {
            LocationConfigDialog(
                currentLat = currentSettings.homeLatitude,
                currentLng = currentSettings.homeLongitude,
                currentRadius = currentSettings.geofenceRadius,
                onDismiss = { showLocationDialog = false },
                onSave = { lat, lng, radius ->
                    viewModel.saveGeofenceSettings(lat, lng, radius)
                    showLocationDialog = false
                }
            )
        }

        if (showAlarmFilterDialog) {
            AlarmFilterConfigDialog(
                currentFilter = currentSettings.alarmKeywordFilter,
                detectedAlarmsList = currentSettings.alarmKeywordFilterList,
                onDismiss = { showAlarmFilterDialog = false },
                onSave = {
                    viewModel.saveAlarmKeywordFilter(it)
                    showAlarmFilterDialog = false
                }
            )
        }
    }
}

@Composable
fun PermissionRequestCard(
    hasListener: Boolean,
    hasNotifications: Boolean,
    hasLocation: Boolean,
    onGrantListener: () -> Unit,
    onGrantNotifications: () -> Unit,
    onGrantLocation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Требуются системные разрешения",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 16.sp
                )
            }

            Text(
                text = "Некоторым функциям автозапуска требуются специальные системные разрешения для фоновой работы в автономном режиме.",
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                fontSize = 13.sp
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))

            if (!hasListener) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Триггер отключения будильника", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Требуется доступ к мониторингу уведомлений", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = onGrantListener,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Разрешить", fontSize = 12.sp)
                    }
                }
            }

            if (!hasNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Запуск аудио на переднем плане", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Требуется разрешение на показ уведомлений", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = onGrantNotifications,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Разрешить", fontSize = 12.sp)
                    }
                }
            }

            if (!hasLocation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Фоновые геозоны (GPS)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Требуется доступ к геолокации GPS", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = onGrantLocation,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Разрешить", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalToggleCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                            .padding(end = 8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Автоматический запуск",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isEnabled) {
                        "Все фоновые события отслеживаются и будут автоматически воспроизводить дуа."
                    } else {
                        "Вся автоматизация полностью остановлена. События не будут запускать воспроизведение."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("global_automation_toggle"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun DuaaItemCard(
    duaa: DuaaEntity,
    isGlobalEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onTestPlay: () -> Unit
) {
    val cardAlpha = if (isGlobalEnabled && duaa.isEnabled) 1.0f else 0.5f
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(4.dp)
                ) {
                    val icon = when (duaa.triggerType) {
                        "ALARM_DISMISSED" -> Icons.Default.AccessAlarms
                        "ENTER_HOME" -> Icons.Default.MeetingRoom
                        "LEAVE_HOME" -> Icons.Default.SensorDoor
                        "CAR_CONNECT" -> Icons.Default.DirectionsCar
                        "CAR_DISCONNECT" -> Icons.Default.ExitToApp
                        else -> Icons.Default.VolumeUp
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = duaa.name,
                            tint = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.primary else Color(0xFF21005D)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = duaa.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Скрыть транскрипцию" else "Показать транскрипцию",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Событие: " + when (duaa.triggerType) {
                                "ALARM_DISMISSED" -> "Будильник отключен"
                                "ENTER_HOME" -> "Вход в дом (Wi-Fi)"
                                "LEAVE_HOME" -> "Выход из дома (Wi-Fi)"
                                "CAR_CONNECT" -> "Вход в машину (Bluetooth)"
                                "CAR_DISCONNECT" -> "Выход из машины (Bluetooth)"
                                else -> "Системное событие"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = cardAlpha)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onTestPlay,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Switch(
                        checked = duaa.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = duaa.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = cardAlpha),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsPanelCard(
    settings: AppSettingsEntity,
    onEditWifi: () -> Unit,
    onEditBluetooth: () -> Unit,
    onEditLocation: () -> Unit,
    onEditAlarmFilter: () -> Unit,
    onResetOnboarding: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Wifi detail row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditWifi() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = "Wifi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Сети Wi-Fi (SSID)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (settings.homeSsid.isBlank()) "Не настроено" else settings.homeSsid, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = Color.Gray)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Bluetooth detail row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditBluetooth() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = "Bluetooth",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Устройства Bluetooth (фильтр)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(if (settings.carBluetoothName.isBlank()) "Не настроено" else settings.carBluetoothName, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = Color.Gray)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Alarm filter detail row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditAlarmFilter() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Alarm,
                        contentDescription = "Alarm Filter",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Фильтры будильников", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (settings.alarmKeywordFilter.isBlank()) "Любой будильник" else "Фильтры: \"${settings.alarmKeywordFilter}\"",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = Color.Gray)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            /*
            // Location detail row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditLocation() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Координаты домашней геозоны", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Широта: ${settings.homeLatitude}, Долгота: ${settings.homeLongitude} (Радиус: ${settings.geofenceRadius.toInt()}м)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = Color.Gray)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            */

            // Volume control row
            var localVolume by remember(settings.volume) { mutableFloatStateOf(settings.volume) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Громкость",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Громкость воспроизведения", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Slider(
                            value = localVolume,
                            onValueChange = { localVolume = it },
                            onValueChangeFinished = { onVolumeChange(localVolume) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(localVolume * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Reset Onboarding row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResetOnboarding() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset Onboarding",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Сбросить обучение (анбординг)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                        Text("Повторно запустить интерактивную настройку", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Icon(Icons.Default.ArrowForward, contentDescription = "Reset", modifier = Modifier.size(16.dp), tint = Color.Gray)
            }
        }
    }
}



@Composable
fun EmptyLogsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.HistoryToggleOff,
                contentDescription = "Empty",
                modifier = Modifier.size(36.dp),
                tint = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "История срабатываний пока пуста.",
                fontSize = 13.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun LogItemRow(log: DuaaTriggerLogEntity) {
    val dateString = remember(log.timestamp) {
        val sdf = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.5f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = log.duaaName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Событие: ${log.triggerSource}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = dateString,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(0.5f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun WifiConfigDialog(
    currentSsid: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf(currentSsid) }
    
    // Scan-related states
    var scannedNetworks by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeSsid by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var isGpsEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    
    fun performScan() {
        if (!hasLocationPermission) return
        isScanning = true
        activeSsid = getActiveWifiSsid(context)
        scannedNetworks = scanWifiNetworks(context)
        isScanning = false
    }
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = isLocationEnabled(context)
                if (isGpsEnabled && hasLocationPermission) {
                    performScan()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(Unit) {
        performScan()
    }
    
    LaunchedEffect(activeSsid) {
        val ssid = activeSsid
        if (!ssid.isNullOrBlank()) {
            val trimmed = ssid.removeSurrounding("\"")
            if (trimmed.isNotBlank()) {
                val updated = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (!updated.contains(trimmed)) {
                    updated.add(trimmed)
                    textState = updated.joinToString(", ")
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Wifi, contentDescription = "WiFi", tint = MaterialTheme.colorScheme.primary)
                Text("Select Home WI-FI", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Выберите домашнюю сеть из списка доступных сетей ниже.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                
                // Nearby Wi-Fi Header & Scan Reload Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Доступные сети рядом (выберите несколько)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        onClick = {
                            isGpsEnabled = isLocationEnabled(context)
                            performScan()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh scan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Scanned Wi-Fi SSIDs List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    if (isScanning) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (!hasLocationPermission) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("Необходимы гео-разрешения для сканирования сетей.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else if (!isGpsEnabled) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Служба геопозиции выключена. Для сканирования Wi-Fi необходимо её включить.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { openLocationSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Enable GPS", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Включить геопозицию", fontSize = 11.sp)
                            }
                        }
                    } else if (scannedNetworks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("Сети не найдены. Убедитесь, что Wi-Fi и геопозиция включены.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        // Scrollable list
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(scannedNetworks) { ssid ->
                                val isSelected = currentTokens.contains(ssid)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            val updated = currentTokens.toMutableList()
                                            if (updated.contains(ssid)) {
                                                updated.remove(ssid)
                                            } else {
                                                updated.add(ssid)
                                            }
                                            textState = updated.joinToString(", ")
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Wifi,
                                        contentDescription = "WiFi icon",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = ssid,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Выбранные сети Wi-Fi:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (textState.isNotBlank()) textState else "Ничего не выбрано (выберите из списков выше)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (textState.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(textState) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun BluetoothConfigDialog(
    currentDevice: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf(currentDevice) }
    var pairedDevices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isScanningOrLoading by remember { mutableStateOf(false) }

    val hasBluetoothPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true

    fun loadDevices() {
        if (!hasBluetoothPermission) return
        isScanningOrLoading = true
        pairedDevices = getPairedBluetoothDevices(context)
        isScanningOrLoading = false
    }

    LaunchedEffect(Unit) {
        loadDevices()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BluetoothConnected, contentDescription = "Bluetooth", tint = MaterialTheme.colorScheme.primary)
                Text("Выбрать Bluetooth автомобиля", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Выберите сопряженное устройство Bluetooth автомобиля ниже или введите имя/фильтр вручную.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Bluetooth permission check warning
                if (!hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Недостаточно разрешений Bluetooth Connect. Разрешите доступ к Bluetooth в настройках устройства для отображения списка.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Paired devices header with Refresh Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ранее сопряженные устройства",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { loadDevices() },
                        modifier = Modifier.size(24.dp),
                        enabled = hasBluetoothPermission
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh list",
                            tint = if (hasBluetoothPermission) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Paired Bluetooth Devices List Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                ) {
                    if (isScanningOrLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (!hasBluetoothPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Нет доступа к списку устройств Bluetooth. Введите имя вручную ниже.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (pairedDevices.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "Нет сопряженных устройств. Убедитесь, что Bluetooth включен и устройства сопряжены в системе.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Scrollable list of bonded devices
                        val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(pairedDevices) { device ->
                                val deviceName = device.first
                                val isSelected = currentTokens.contains(deviceName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            val updated = currentTokens.toMutableList()
                                            if (updated.contains(deviceName)) {
                                                updated.remove(deviceName)
                                            } else {
                                                updated.add(deviceName)
                                            }
                                            textState = updated.joinToString(", ")
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.BluetoothConnected,
                                        contentDescription = "Bluetooth device",
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = deviceName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = device.second,
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Выбранные устройства Bluetooth:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (textState.isNotBlank()) textState else "Ничего не выбрано (выберите из списка выше)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (textState.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(textState) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun LocationConfigDialog(
    currentLat: Double,
    currentLng: Double,
    currentRadius: Float,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Float) -> Unit
) {
    val context = LocalContext.current
    var latState by remember { mutableStateOf(currentLat.toString()) }
    var lngState by remember { mutableStateOf(currentLng.toString()) }
    var radiusState by remember { mutableStateOf(currentRadius.toString()) }

    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Location", tint = MaterialTheme.colorScheme.primary)
                Text("Координаты домашней геозоны")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Укажите географические координаты вашего дома. Встроенный модуль GPS-геозон отслеживает прибытие или выход из этой зоны.", fontSize = 11.sp, color = Color.Gray)

                // Choose Current Location Button
                Button(
                    onClick = {
                        if (!hasLocationPermission) {
                            locationError = "Необходимы разрешения геопозиции. Разрешите их на главном экране."
                            return@Button
                        }
                        isLocating = true
                        locationError = null
                        requestSingleLocationUpdate(context) { location ->
                            isLocating = false
                            if (location != null) {
                                latState = location.latitude.toString()
                                lngState = location.longitude.toString()
                            } else {
                                locationError = "Не удалось определить координаты. Включите геолокацию/GPS."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("select_current_location_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Определение геопозиции...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = "Current location", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать текущее местоположение как дом", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                locationError?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Широта: ${latState.toDoubleOrNull()?.let { "%.5f".format(it) } ?: latState}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Долгота: ${lngState.toDoubleOrNull()?.let { "%.5f".format(it) } ?: lngState}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                OutlinedTextField(
                    value = radiusState,
                    onValueChange = { radiusState = it },
                    label = { Text("Радиус (в метрах)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("radius_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latState.toDoubleOrNull() ?: currentLat
                    val lng = lngState.toDoubleOrNull() ?: currentLng
                    val rad = radiusState.toFloatOrNull() ?: currentRadius
                    onSave(lat, lng, rad)
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AlarmFilterConfigDialog(
    currentFilter: String,
    detectedAlarmsList: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textState by remember { mutableStateOf(currentFilter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Alarm, contentDescription = "Alarm", tint = MaterialTheme.colorScheme.primary)
                Text("Фильтр будильников")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Введите кодовое слово или выберите будильник из списка ниже. Сработает только тогда, когда имя или описание будильника содержит это слово/время.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                TextField(
                    value = textState,
                    onValueChange = { textState = it },
                    singleLine = true,
                    placeholder = { Text("Введите кодовое слово (например, Работа)") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("alarm_filter_input")
                )

                if (detectedAlarmsList.isNotEmpty()) {
                    Text(
                        text = "Выберите будильник с телефона:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(detectedAlarmsList) { alarm ->
                            val isSelected = if (alarm == "Любой будильник") {
                                textState.isBlank()
                            } else {
                                currentTokens.contains(alarm)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        if (alarm == "Любой будильник") {
                                            textState = ""
                                        } else {
                                            val updated = currentTokens.toMutableList()
                                            if (updated.contains(alarm)) {
                                                updated.remove(alarm)
                                            } else {
                                                updated.add(alarm)
                                            }
                                            textState = updated.joinToString(", ")
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = alarm,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(textState) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@SuppressLint("MissingPermission")
fun getPairedBluetoothDevices(context: Context): List<Pair<String, String>> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
    val bluetoothAdapter = bluetoothManager.adapter ?: return emptyList()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasConnect) return emptyList()
    }
    
    return try {
        val bondedDevices = bluetoothAdapter.bondedDevices
        bondedDevices?.map { Pair(it.name ?: "Unknown Device", it.address ?: "") }
            ?.sortedBy { it.first.lowercase() }
            ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

@SuppressLint("MissingPermission")
fun scanWifiNetworks(context: Context): List<String> {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return emptyList()
    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasLocation) return emptyList()

    return try {
        try {
            wifiManager.startScan()
        } catch (e: Exception) {}
        val results = wifiManager.scanResults
        results?.map { it.SSID }
            ?.filter { !it.isNullOrBlank() && !it.contains("0x") }
            ?.distinct()
            ?.sorted() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun getActiveWifiSsid(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager ?: return null
    return try {
        val connectionInfo = wifiManager.connectionInfo
        if (connectionInfo != null && connectionInfo.networkId != -1) {
            val ssid = connectionInfo.ssid
            if (ssid != "<unknown ssid>") {
                ssid.removeSurrounding("\"")
            } else null
        } else null
    } catch (e: Exception) {
        null
    }
}

@SuppressLint("MissingPermission")
fun requestSingleLocationUpdate(context: Context, onLocationResult: (android.location.Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    if (lm == null) {
        onLocationResult(null)
        return
    }

    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted && !coarseGranted) {
        onLocationResult(null)
        return
    }

    try {
        val gpsLoc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        val netLoc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
        val lastKnown = if (gpsLoc != null && netLoc != null) {
            if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
        } else {
            gpsLoc ?: netLoc
        }

        if (lastKnown != null) {
            onLocationResult(lastKnown)
            return
        }

        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                onLocationResult(location)
                lm.removeUpdates(this)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            lm.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, listener, context.mainLooper)
        } else if (lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, listener, context.mainLooper)
        } else {
            onLocationResult(null)
        }
    } catch (e: Exception) {
        onLocationResult(null)
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
    return packageNames.contains(context.packageName)
}

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
    return locationManager?.let {
        it.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
        it.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    } ?: false
}

fun openLocationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ex: Exception) {}
    }
}
