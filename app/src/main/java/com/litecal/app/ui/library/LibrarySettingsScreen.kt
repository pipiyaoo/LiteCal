package com.litecal.app.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litecal.app.domain.model.ActivityLevel
import com.litecal.app.domain.model.AiConfig
import com.litecal.app.domain.model.AiProviderProtocol
import com.litecal.app.domain.model.UserProfile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsScreen(
    profile: UserProfile,
    aiConfig: AiConfig,
    aiTestMessage: String?,
    onSaveAiConfig: (AiConfig) -> Unit,
    onTestAiConfig: (AiConfig) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf("AI 模型", "目标设置")

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedSection) {
            sections.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedSection == idx,
                    onClick = { selectedSection = idx },
                    text = { Text(title) }
                )
            }
        }

        when (selectedSection) {
            0 -> AiConfigSection(
                initialConfig = aiConfig,
                testMessage = aiTestMessage,
                onSave = onSaveAiConfig,
                onTest = onTestAiConfig
            )

            1 -> GoalAndReminderSection(
                profile = profile,
                onSaveProfile = onUpdateProfile
            )
        }
    }
}

@Composable
private fun AiConfigSection(
    initialConfig: AiConfig,
    testMessage: String?,
    onSave: (AiConfig) -> Unit,
    onTest: (AiConfig) -> Unit
) {
    var protocol by remember(initialConfig) { mutableStateOf(initialConfig.protocol) }
    var baseUrl by remember(initialConfig) { mutableStateOf(initialConfig.baseUrl) }
    var apiKey by remember(initialConfig) { mutableStateOf(initialConfig.apiKey) }
    var modelName by remember(initialConfig) { mutableStateOf(initialConfig.modelName) }

    val draft = AiConfig(
        protocol = protocol,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.OPENAI_COMPATIBLE
                        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        modelName = "qwen-vl-max"
                    },
                    label = { Text("通义千问", maxLines = 1) }
                )
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.OPENAI_COMPATIBLE
                        baseUrl = "https://api.siliconflow.cn/v1"
                        modelName = "Qwen/Qwen2.5-VL-72B-Instruct"
                    },
                    label = { Text("硅基流动", maxLines = 1) }
                )
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.GEMINI_NATIVE
                        baseUrl = "https://generativelanguage.googleapis.com"
                        modelName = "gemini-2.5-flash"
                    },
                    label = { Text("Gemini", maxLines = 1) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiProviderProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = { protocol = p },
                        modifier = Modifier.weight(1f),
                        label = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!testMessage.isNullOrBlank()) {
            item {
                Text(
                    text = testMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (testMessage.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onTest(draft) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("测试")
                }
                Button(
                    onClick = { onSave(draft) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun GoalAndReminderSection(
    profile: UserProfile,
    onSaveProfile: (UserProfile) -> Unit
) {
    var targetWeightText by remember(profile) { mutableStateOf(profile.targetWeightKg.toString()) }
    var weeklyLossRate by remember(profile) { mutableFloatStateOf(profile.weeklyLossRateKg) }
    var activityLevel by remember(profile) { mutableStateOf(profile.activityLevel) }
    var customProteinText by remember(profile) { mutableStateOf(profile.customProteinTargetG?.toString().orEmpty()) }
    var waterTargetText by remember(profile) { mutableStateOf(profile.waterTargetMl.toString()) }
    var weekendReserve by remember(profile) { mutableStateOf(profile.weekendReserveEnabled) }
    var weekendSaveKcal by remember(profile) { mutableFloatStateOf(profile.weekendDailySaveKcal.toFloat()) }
    var morningReminder by remember(profile) { mutableStateOf(profile.morningReminderEnabled) }
    var eveningReminder by remember(profile) { mutableStateOf(profile.eveningReminderEnabled) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = targetWeightText,
                    onValueChange = { targetWeightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("目标体重 kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customProteinText,
                    onValueChange = { customProteinText = it.filter(Char::isDigit).take(3) },
                    label = { Text("蛋白质 g") },
                    placeholder = { Text("自动") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = waterTargetText,
                onValueChange = { waterTargetText = it.filter(Char::isDigit).take(4) },
                label = { Text("饮水 ml") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("活动强度", style = MaterialTheme.typography.titleMedium)
                ActivityLevel.entries.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { lv ->
                            FilterChip(
                                selected = activityLevel == lv,
                                onClick = { activityLevel = lv },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(lv.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = "每周减重 ${String.format("%.2f", weeklyLossRate)} kg",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = weeklyLossRate,
                    onValueChange = { weeklyLossRate = (it * 20).roundToInt() / 20f },
                    valueRange = 0.2f..0.85f,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("周末聚餐预留", fontWeight = FontWeight.SemiBold)
                        Switch(checked = weekendReserve, onCheckedChange = { weekendReserve = it })
                    }
                    if (weekendReserve) {
                        Text(
                            text = "工作日 -${weekendSaveKcal.roundToInt()} · 周末 +${((weekendSaveKcal * 5f) / 2f).roundToInt()} kcal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = weekendSaveKcal,
                            onValueChange = { weekendSaveKcal = (it / 10f).roundToInt() * 10f },
                            valueRange = 60f..200f,
                            colors = SliderDefaults.colors(
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("定时提醒", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("08:00 晨重", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = morningReminder,
                            onCheckedChange = {
                                morningReminder = it
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("20:30 晚餐", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = eveningReminder,
                            onCheckedChange = {
                                eveningReminder = it
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSaveProfile(
                        profile.copy(
                            targetWeightKg = targetWeightText.toFloatOrNull()?.coerceIn(35f, 200f) ?: profile.targetWeightKg,
                            weeklyLossRateKg = weeklyLossRate,
                            activityLevel = activityLevel,
                            customProteinTargetG = customProteinText.toIntOrNull()?.takeIf { it > 0 },
                            waterTargetMl = waterTargetText.toIntOrNull()?.coerceIn(500, 6000) ?: 2000,
                            weekendReserveEnabled = weekendReserve,
                            weekendDailySaveKcal = weekendSaveKcal.roundToInt(),
                            morningReminderEnabled = morningReminder,
                            eveningReminderEnabled = eveningReminder
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("保存")
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
