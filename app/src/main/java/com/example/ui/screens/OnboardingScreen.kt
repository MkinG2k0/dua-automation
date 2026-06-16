package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.database.AppSettingsEntity
import com.example.ui.viewmodel.DuaaViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: DuaaViewModel,
    settings: AppSettingsEntity,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 3 // Changed from 4 to hide GPS trigger setup, can be restored later

    // Inside step state variables holding configuration temporary values
    var alarmKeyword by remember { mutableStateOf(settings.alarmKeywordFilter) }
    var wifiSsid by remember { mutableStateOf(settings.homeSsid) }
    var btDeviceName by remember { mutableStateOf(settings.carBluetoothName) }
    var locationLat by remember { mutableDoubleStateOf(settings.homeLatitude) }
    var locationLng by remember { mutableDoubleStateOf(settings.homeLongitude) }
    var locationRadius by remember { mutableFloatStateOf(settings.geofenceRadius) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Первоначальная настройка",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep-- }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { onFinished() }) {
                        Text("Пропустить всё", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step Progress Bar Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (step in 1..totalSteps) {
                    val isActive = step == currentStep
                    val isCompleted = step < currentStep
                    val barColor = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else if (isCompleted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }

            // Central Animating Steps Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() with
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() with
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "step_transition"
                ) { step ->
                    when (step) {
                        1 -> AlarmStepContent(
                            initialValue = alarmKeyword,
                            detectedAlarms = settings.alarmKeywordFilterList,
                            onValueChange = { alarmKeyword = it }
                        )
                        2 -> WifiStepContent(
                            initialValue = wifiSsid,
                            onValueChange = { wifiSsid = it }
                        )
                        3 -> BluetoothStepContent(
                            initialValue = btDeviceName,
                            onValueChange = { btDeviceName = it }
                        )
                        4 -> LocationStepContent(
                            lat = locationLat,
                            lng = locationLng,
                            radius = locationRadius,
                            onLocationChanged = { lat, lng ->
                                locationLat = lat
                                locationLng = lng
                            },
                            onRadiusChanged = { locationRadius = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row (Skip, Next or Finished)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary action: "Skip" this prompt
                OutlinedButton(
                    onClick = {
                        // Keep current default value or clear, go next step
                        when (currentStep) {
                            1 -> alarmKeyword = ""
                            2 -> wifiSsid = ""
                            3 -> btDeviceName = ""
                        }
                        if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            // Finish onboarding and save nothing for last step, or save skipped states
                            viewModel.saveAlarmKeywordFilter(alarmKeyword)
                            viewModel.saveWifiSsid(wifiSsid)
                            viewModel.saveBluetoothFilter(btDeviceName)
                            viewModel.saveGeofenceSettings(locationLat, locationLng, locationRadius)
                            onFinished()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Пропустить")
                }

                // Primary action: "Save & Next" or "Finish"
                Button(
                    onClick = {
                        when (currentStep) {
                            1 -> {
                                viewModel.saveAlarmKeywordFilter(alarmKeyword)
                                currentStep++
                            }
                            2 -> {
                                viewModel.saveWifiSsid(wifiSsid)
                                currentStep++
                            }
                            3 -> {
                                viewModel.saveBluetoothFilter(btDeviceName)
                                if (totalSteps == 3) {
                                    onFinished()
                                } else {
                                    currentStep++
                                }
                            }
                            4 -> {
                                viewModel.saveGeofenceSettings(locationLat, locationLng, locationRadius)
                                onFinished()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (currentStep == totalSteps) "Завершить" else "Далее")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (currentStep == totalSteps) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Action icon",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmStepContent(
    initialValue: String,
    detectedAlarms: List<String>,
    onValueChange: (String) -> Unit
) {
    var textState by remember { mutableStateOf(initialValue) }
    val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "Alarm trigger",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Шаг 1: Будильники и Таймеры",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Дуа при пробуждении будет автоматически запускаться при отключении будильника. Вы можете выбрать конкретные будильники или указать кодовые фильтры.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        item {
            TextField(
                value = textState,
                onValueChange = {
                    textState = it
                    onValueChange(it)
                },
                label = { Text("Ключевые слова фильтра (через запятую)") },
                placeholder = { Text("Например: Работа, Утро, Семья") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        }

        if (detectedAlarms.isNotEmpty()) {
            item {
                Text(
                    text = "Выберите из обнаруженных будильников:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(detectedAlarms) { alarm ->
                val isSelected = if (alarm == "Любой будильник") {
                    textState.isBlank()
                } else {
                    currentTokens.contains(alarm)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(8.dp)
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
                            onValueChange(textState)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Alarm,
                        contentDescription = "Select alarm",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = alarm,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Нет обнаруженных системных будильников. Будет использован любой сработавший будильник.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WifiStepContent(
    initialValue: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf(initialValue) }
    var scannedNetworks by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeSsid by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    var locationGrantedState by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isGpsEnabled by remember { mutableStateOf(isLocationEnabled(context)) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val isGranted = fineGranted || coarseGranted
        locationGrantedState = isGranted
        if (isGranted) {
            isGpsEnabled = isLocationEnabled(context)
            isScanning = true
            activeSsid = getActiveWifiSsid(context)
            scannedNetworks = scanWifiNetworks(context)
            isScanning = false
        }
    }

    fun performScan() {
        isGpsEnabled = isLocationEnabled(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationGrantedState = true
            isScanning = true
            activeSsid = getActiveWifiSsid(context)
            scannedNetworks = scanWifiNetworks(context)
            isScanning = false
        } else {
            locationGrantedState = false
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isGpsEnabled = isLocationEnabled(context)
                if (isGpsEnabled && locationGrantedState) {
                    performScan()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(locationGrantedState) {
        if (locationGrantedState) {
            isGpsEnabled = isLocationEnabled(context)
            isScanning = true
            activeSsid = getActiveWifiSsid(context)
            scannedNetworks = scanWifiNetworks(context)
            isScanning = false
        }
    }

    LaunchedEffect(activeSsid) {
        val ssid = activeSsid
        if (!ssid.isNullOrBlank()) {
            val trimmed = ssid.removeSurrounding("\"")
            if (trimmed.isNotBlank()) {
                val updated = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                if (!updated.contains(trimmed)) {
                    updated.add(trimmed)
                    val newValue = updated.joinToString(", ")
                    textState = newValue
                    onValueChange(newValue)
                }
            }
        }
    }

    val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wifi trigger",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Шаг 2: Беспроводные сети Wi-Fi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Приложение отслеживает подключение и отключение Wi-Fi. Дуа входа в дом автоматически воспроизведется при подключении к домашней сети Wi-Fi, а Дуа выхода — при отключении.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        // Scanned WiFi Networks List OR Permission Request
        if (locationGrantedState) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Доступные сети рядом (выберите нужные):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { performScan() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (isScanning) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (!isGpsEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Служба геопозиции выключена!\nДля автоматического сканирования Wi-Fi сетей её необходимо активировать.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { openLocationSettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = "Включить геопозицию")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Включить геопозицию")
                            }
                        }
                    }
                }
            } else if (scannedNetworks.isEmpty()) {
                item {
                    Text("Рядом не найдено активных Wi-Fi сетей. Нажмите иконку обновления вверху справа для повторного поиска.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                items(scannedNetworks) { ssid ->
                    val isSelected = currentTokens.contains(ssid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val updated = currentTokens.toMutableList()
                                if (updated.contains(ssid)) {
                                    updated.remove(ssid)
                                } else {
                                    updated.add(ssid)
                                }
                                textState = updated.joinToString(", ")
                                onValueChange(textState)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Wifi,
                            contentDescription = "Scanned network",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = ssid,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Требуется доступ к геолокации", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Чтобы сканировать доступные Wi-Fi сети вокруг, операционной системе Android требуется разрешение на геолокацию. Вы можете предоставить его кнопкой ниже или ввести имя сети вручную.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { performScan() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Enable Location")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сканировать Wi-Fi (Разрешить доступ)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BluetoothStepContent(
    initialValue: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf(initialValue) }
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

    val currentTokens = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.BluetoothConnected,
                    contentDescription = "Bluetooth trigger",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Шаг 3: Автомобильный Bluetooth",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Автоматическое воспроизведение Дуа при входе в автомобиль и выходе из него. Приложение отслеживает сопряжение с аудиосистемой.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        item {
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (textState.isNotBlank()) textState else "Ничего не выбрано (выберите устройства сопряжения ниже)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (textState.isNotBlank()) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }
            }
        }

        if (hasBluetoothPermission) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Связанные Bluetooth устройства:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { loadDevices() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (isScanningOrLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (pairedDevices.isEmpty()) {
                item {
                    Text("В системе нет сопряженных Bluetooth устройств.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                items(pairedDevices) { device ->
                    val deviceName = device.first
                    val isSelected = currentTokens.contains(deviceName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                val updated = currentTokens.toMutableList()
                                if (updated.contains(deviceName)) {
                                    updated.remove(deviceName)
                                } else {
                                    updated.add(deviceName)
                                }
                                textState = updated.joinToString(", ")
                                onValueChange(textState)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.BluetoothConnected,
                            contentDescription = "Bluetooth device logo",
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = deviceName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "Адрес: ${device.second}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Доступ к Bluetooth требуется", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Для автоматического получения списка сопряженных устройств предоставьте разрешение Bluetooth Connect или введите фильтр вручную.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationStepContent(
    lat: Double,
    lng: Double,
    radius: Float,
    onLocationChanged: (Double, Double) -> Unit,
    onRadiusChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    var isLocating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun fetchCurrentLocation() {
        if (!hasLocationPermission) {
            locationError = "Нет разрешения на геолокацию в системе!"
            return
        }
        isLocating = true
        locationError = null
        requestSingleLocationUpdate(context) { location ->
            isLocating = false
            if (location != null) {
                onLocationChanged(location.latitude, location.longitude)
            } else {
                locationError = "Не удалось автоматически определить координаты. Убедитесь, что GPS на телефоне включен."
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Location trigger",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Шаг 4: Геозона (Геопозиция дома)",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Координаты дома служат резервным триггером. Как только вы окажетесь на расстоянии ${radius.toInt()}м от домашней координаты, сработает Дуа входа. При выходе — Дуа дорожного путешествия.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        if (!hasLocationPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Геолокация отключена", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Предоставьте приложению разрешение на точное местоположение для точного расчета геозоны.", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Текущие координаты центра:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Button(
                        onClick = { fetchCurrentLocation() },
                        enabled = !isLocating
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = "Active Loc", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Определить GPS", fontSize = 12.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Широта: ${"%.5f".format(lat)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Долгота: ${"%.5f".format(lng)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                locationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Радиус геозоны: ${radius.toInt()} метров",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Slider(
                    value = radius,
                    onValueChange = { onRadiusChanged(it) },
                    valueRange = 50f..1000f,
                    steps = 19
                )
            }
        }
    }
}
