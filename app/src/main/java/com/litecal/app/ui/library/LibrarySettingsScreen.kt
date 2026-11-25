package com.litecal.app.ui.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.litecal.app.data.local.FoodItemEntity
import com.litecal.app.data.local.MealComboWithItems
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
    allFoods: List<FoodItemEntity>,
    combos: List<MealComboWithItems>,
    onSaveAiConfig: (AiConfig) -> Unit,
    onTestAiConfig: (AiConfig) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onCreateCustomFood: (String, String, Float, Float, Float, Float, Float) -> Unit,
    onDeleteCustomFood: (FoodItemEntity) -> Unit,
    onDeleteCombo: (Long) -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf("AI 识餐接口", "常吃与套餐", "目标与提醒")

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

            1 -> CustomLibrarySection(
                customFoods = allFoods.filter { !it.isBuiltIn || it.isFavorite },
                combos = combos,
                onCreateCustomFood = onCreateCustomFood,
                onDeleteCustomFood = onDeleteCustomFood,
                onDeleteCombo = onDeleteCombo
            )

            2 -> GoalAndReminderSection(
                profile = profile,
                onSaveProfile = onUpdateProfile
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            Text(
                text = "直连多模态大模型（零第三方后端，API Key 加密保存在手机本地）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text("快捷预设填充：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.OPENAI_COMPATIBLE
                        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        modelName = "qwen-vl-max"
                    },
                    label = { Text("通义千问 Qwen-VL (免代理)") }
                )
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.OPENAI_COMPATIBLE
                        baseUrl = "https://api.siliconflow.cn/v1"
                        modelName = "Qwen/Qwen2.5-VL-72B-Instruct"
                    },
                    label = { Text("硅基流动 (免代理)") }
                )
                AssistChip(
                    onClick = {
                        protocol = AiProviderProtocol.GEMINI_NATIVE
                        baseUrl = "https://generativelanguage.googleapis.com"
                        modelName = "gemini-2.5-flash"
                    },
                    label = { Text("Google Gemini") }
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AiProviderProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = { protocol = p },
                        label = { Text(p.label) }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("接口 Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("模型名称 (Model ID)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (本地 AES-256 加密存储)") },
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("测试连通性")
                }
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}

@Composable
private fun CustomLibrarySection(
    customFoods: List<FoodItemEntity>,
    combos: List<MealComboWithItems>,
    onCreateCustomFood: (String, String, Float, Float, Float, Float, Float) -> Unit,
    onDeleteCustomFood: (FoodItemEntity) -> Unit,
    onDeleteCombo: (Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" 新增自定义常吃单品")
            }
        }

        item {
            Text(
                text = "已保存的套餐组合 (${combos.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(combos, key = { "mgr_combo_${it.combo.id}" }) { combo ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(combo.combo.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${combo.totalKcal.roundToInt()} kcal · 蛋白 ${combo.totalProteinG.roundToInt()}g · ${combo.items.size}项食材",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onDeleteCombo(combo.combo.id) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除套餐")
                    }
                }
            }
        }

        item {
            Text(
                text = "收藏与自定义食物 (${customFoods.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(customFoods, key = { "mgr_food_${it.id}" }) { food ->
            val unitKcal = (food.kcalPer100g * food.defaultUnitGrams / 100f).roundToInt()
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(food.name, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${food.defaultUnitName} (${food.defaultUnitGrams.roundToInt()}g) · $unitKcal kcal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!food.isBuiltIn) {
                        IconButton(onClick = { onDeleteCustomFood(food) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除自定义食物", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        AddCustomFoodDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, unit, grams, kcal, prot, carbs, fat ->
                onCreateCustomFood(name, unit, grams, kcal, prot, carbs, fat)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                    label = { Text("目标体重 (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customProteinText,
                    onValueChange = { customProteinText = it.filter(Char::isDigit).take(3) },
                    label = { Text("自定义蛋白质g (留空自动)") },
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
                label = { Text("每日饮水目标 (ml)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("日常活动强度：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityLevel.entries.forEach { lv ->
                    FilterChip(
                        selected = activityLevel == lv,
                        onClick = { activityLevel = lv },
                        label = { Text(lv.title) }
                    )
                }
            }
        }

        item {
            Column {
                Text(
                    text = "期望每周减重速率：${String.format("%.2f", weeklyLossRate)} kg/周",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = weeklyLossRate,
                    onValueChange = { weeklyLossRate = (it * 20).roundToInt() / 20f },
                    valueRange = 0.2f..0.85f
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("周热量银行 · 周末聚餐预留", fontWeight = FontWeight.SemiBold)
                        Switch(checked = weekendReserve, onCheckedChange = { weekendReserve = it })
                    }
                    if (weekendReserve) {
                        Text(
                            text = "工作日每天预留 ${weekendSaveKcal.roundToInt()} kcal → 周六/周日各多吃 +${((weekendSaveKcal * 5f) / 2f).roundToInt()} kcal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = weekendSaveKcal,
                            onValueChange = { weekendSaveKcal = (it / 10f).roundToInt() * 10f },
                            valueRange = 60f..200f
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("智能温和防漏记提醒（已记自动静默不打扰）", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("早上 08:00 未称重轻提醒", style = MaterialTheme.typography.bodyMedium)
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
                        Text("晚上 20:30 疑似漏记晚餐提醒", style = MaterialTheme.typography.bodyMedium)
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
                            targetWeightKg = targetWeightText.toFloatOrNull() ?: profile.targetWeightKg,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存减脂目标与提醒设置")
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AddCustomFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Float, Float, Float, Float, Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf("一份") }
    var gramsText by remember { mutableStateOf("100") }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义常吃食物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("食物名称，如：公司楼下鸡腿面") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = { Text("单位名(如:一碗)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("该单位克重(g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = kcalText,
                        onValueChange = { kcalText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("该份热量(kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("该份蛋白(g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kcal = kcalText.toFloatOrNull() ?: 0f
                    if (name.isNotBlank() && kcal > 0f) {
                        onConfirm(
                            name,
                            unitName,
                            gramsText.toFloatOrNull() ?: 100f,
                            kcal,
                            proteinText.toFloatOrNull() ?: 0f,
                            0f,
                            0f
                        )
                    }
                }
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
