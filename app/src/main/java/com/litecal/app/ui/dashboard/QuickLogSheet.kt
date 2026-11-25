package com.litecal.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.litecal.app.data.local.FoodItemEntity
import com.litecal.app.data.local.MealComboWithItems
import com.litecal.app.domain.engine.MetabolismEngine
import com.litecal.app.domain.model.AiMealEstimateResponse
import com.litecal.app.domain.model.MealType
import com.litecal.app.ui.AiUiState
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(
    initialMealType: MealType,
    initialTab: Int,
    frequentFoods: List<FoodItemEntity>,
    allFoods: List<FoodItemEntity>,
    mealCombos: List<MealComboWithItems>,
    aiUiState: AiUiState,
    onDismiss: () -> Unit,
    onLogFood: (MealType, FoodItemEntity, Float, String) -> Unit,
    onLogCombo: (MealType, MealComboWithItems) -> Unit,
    onToggleFavorite: (FoodItemEntity) -> Unit,
    onRunAiEstimate: (String, Uri?) -> Unit,
    onResetAiEstimate: () -> Unit,
    onConfirmAiEstimate: (MealType, AiMealEstimateResponse, Float, Boolean, String?) -> Unit,
    onLogQuickEntry: (MealType, String, String, Float, Float, Float, Float, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMealType by remember { mutableStateOf(initialMealType) }
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 480.dp, max = 720.dp)
                .padding(horizontal = 16.dp)
        ) {
            // 餐次快速选择条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("记入：", style = MaterialTheme.typography.labelMedium)
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text("${type.emoji}${type.label}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val tabs = listOf("常吃/套餐", "AI 估算", "中式词库", "数字快记")
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (selectedTab) {
                0 -> FrequentAndComboTab(
                    mealCombos = mealCombos,
                    frequentFoods = frequentFoods,
                    onSelectCombo = { onLogCombo(selectedMealType, it) },
                    onSelectFood = { food, grams, desc ->
                        onLogFood(selectedMealType, food, grams, desc)
                    },
                    onToggleFavorite = onToggleFavorite
                )

                1 -> AiEstimateTab(
                    aiUiState = aiUiState,
                    onRunEstimate = onRunAiEstimate,
                    onReset = onResetAiEstimate,
                    onConfirm = { resp, mult, saveTemplates, comboName ->
                        onConfirmAiEstimate(selectedMealType, resp, mult, saveTemplates, comboName)
                    }
                )

                2 -> BuiltinFoodLibraryTab(
                    allFoods = allFoods,
                    onSelectFood = { food, grams, desc ->
                        onLogFood(selectedMealType, food, grams, desc)
                    },
                    onToggleFavorite = onToggleFavorite
                )

                3 -> FastNumberAndKjTab(
                    onSubmit = { title, portion, kcal, prot, carbs, fat, saveTemplate ->
                        onLogQuickEntry(selectedMealType, title, portion, kcal, prot, carbs, fat, saveTemplate)
                    }
                )
            }
        }
    }
}

@Composable
private fun FrequentAndComboTab(
    mealCombos: List<MealComboWithItems>,
    frequentFoods: List<FoodItemEntity>,
    onSelectCombo: (MealComboWithItems) -> Unit,
    onSelectFood: (FoodItemEntity, Float, String) -> Unit,
    onToggleFavorite: (FoodItemEntity) -> Unit
) {
    var expandedFoodId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (mealCombos.isNotEmpty()) {
            item {
                Text(
                    text = "⚡ 一键套餐模板（点一下整餐入账）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            items(mealCombos, key = { "combo_${it.combo.id}" }) { combo ->
                Card(
                    onClick = { onSelectCombo(combo) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(combo.combo.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = combo.items.joinToString(" + ") { it.foodName },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${combo.totalKcal.roundToInt()} kcal", fontWeight = FontWeight.Bold)
                            Text(
                                "蛋白 ${combo.totalProteinG.roundToInt()}g",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "⭐ 个人高频与收藏常吃",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }

        items(frequentFoods, key = { "freq_${it.id}" }) { food ->
            FoodSelectableCard(
                food = food,
                isExpanded = expandedFoodId == food.id,
                onExpandToggle = {
                    expandedFoodId = if (expandedFoodId == food.id) null else food.id
                },
                onConfirmPortion = { grams, desc -> onSelectFood(food, grams, desc) },
                onToggleFavorite = { onToggleFavorite(food) }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun BuiltinFoodLibraryTab(
    allFoods: List<FoodItemEntity>,
    onSelectFood: (FoodItemEntity, Float, String) -> Unit,
    onToggleFavorite: (FoodItemEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("全部") }
    var expandedFoodId by remember { mutableStateOf<Long?>(null) }

    val categories = listOf("全部", "主食", "优质蛋白", "家常菜", "外卖快餐", "便利店与果饮", "常吃自定义", "AI沉淀常吃")
    val filtered = remember(allFoods, query, selectedCategory) {
        allFoods.filter { item ->
            val matchCategory = selectedCategory == "全部" || item.category == selectedCategory
            val matchQuery = query.isBlank() || item.name.contains(query.trim(), ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜食物/外卖，如：猪脚饭、鸡胸肉、拿铁...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) }
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filtered, key = { "lib_${it.id}" }) { food ->
                FoodSelectableCard(
                    food = food,
                    isExpanded = expandedFoodId == food.id,
                    onExpandToggle = {
                        expandedFoodId = if (expandedFoodId == food.id) null else food.id
                    },
                    onConfirmPortion = { grams, desc -> onSelectFood(food, grams, desc) },
                    onToggleFavorite = { onToggleFavorite(food) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodSelectableCard(
    food: FoodItemEntity,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onConfirmPortion: (Float, String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val defaultKcal = (food.kcalPer100g * food.defaultUnitGrams / 100f).roundToInt()
    val defaultProt = food.proteinPer100g * food.defaultUnitGrams / 100f
    var customGramsText by remember(food.id) { mutableStateOf(food.defaultUnitGrams.roundToInt().toString()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${food.defaultUnitName} (${food.defaultUnitGrams.roundToInt()}g) · $defaultKcal kcal · 蛋白 ${String.format("%.1f", defaultProt)}g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (food.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "收藏常吃",
                            tint = if (food.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = {
                            onConfirmPortion(
                                food.defaultUnitGrams,
                                "${food.defaultUnitName} (${food.defaultUnitGrams.roundToInt()}g)"
                            )
                        },
                        label = { Text("+1份") }
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("快捷选份量（免厨房秤估算）：", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    listOf(0.5f to "半份", 1.0f to "1份(${food.defaultUnitName})", 1.5f to "1.5份", 2.0f to "2份").forEach { (mult, label) ->
                        val g = food.defaultUnitGrams * mult
                        AssistChip(
                            onClick = {
                                onConfirmPortion(g, "$label (${g.roundToInt()}g)")
                            },
                            label = { Text("$label · ${(food.kcalPer100g * g / 100f).roundToInt()}kcal") }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = customGramsText,
                        onValueChange = { customGramsText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("自定义克重 (g/ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val g = customGramsText.toFloatOrNull() ?: food.defaultUnitGrams
                            onConfirmPortion(g, "${g.roundToInt()}g")
                        }
                    ) {
                        val previewKcal = ((customGramsText.toFloatOrNull() ?: 0f) * food.kcalPer100g / 100f).roundToInt()
                        Text("确认 ($previewKcal kcal)")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiEstimateTab(
    aiUiState: AiUiState,
    onRunEstimate: (String, Uri?) -> Unit,
    onReset: () -> Unit,
    onConfirm: (AiMealEstimateResponse, Float, Boolean, String?) -> Unit
) {
    val context = LocalContext.current
    var descriptionText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            selectedImageUri = pendingCameraUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val captureUri = createCameraUri(context)
            pendingCameraUri = captureUri
            cameraLauncher.launch(captureUri)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                text = "支持拍餐盘照片，或直接输入/语音转文字一句话描述（如：“半碗米饭 + 食堂青椒肉丝去油 + 1个卤蛋”）。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            val captureUri = createCameraUri(context)
                            pendingCameraUri = captureUri
                            cameraLauncher.launch(captureUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("拍照识餐")
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("相册选图")
                }
            }
        }

        if (selectedImageUri != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("已附加餐食照片（将自动压缩上传识别）", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = "移除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { selectedImageUri = null }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text("一句话描述饮食（可搭配照片补充“少油/吃了一半”）") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        when (aiUiState) {
            is AiUiState.Idle, is AiUiState.Error -> {
                if (aiUiState is AiUiState.Error) {
                    item {
                        Text(
                            text = aiUiState.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                item {
                    Button(
                        onClick = { onRunEstimate(descriptionText, selectedImageUri) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI 智能拆解热量与蛋白质")
                    }
                }
            }

            is AiUiState.Loading -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在拆解食材分量与烹饪油脂热量...")
                    }
                }
            }

            is AiUiState.Success -> {
                item {
                    AiResultConfirmCard(
                        response = aiUiState.response,
                        onReEstimate = onReset,
                        onConfirm = onConfirm
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AiResultConfirmCard(
    response: AiMealEstimateResponse,
    onReEstimate: () -> Unit,
    onConfirm: (AiMealEstimateResponse, Float, Boolean, String?) -> Unit
) {
    var portionMultiplier by remember { mutableFloatStateOf(1.0f) }
    var saveToTemplates by remember { mutableStateOf(true) }
    var saveAsCombo by remember { mutableStateOf(false) }
    var comboName by remember { mutableStateOf("") }

    val totalKcal = (response.items.sumOf { it.kcal.toDouble() } * portionMultiplier).roundToInt()
    val totalProt = (response.items.sumOf { it.proteinG.toDouble() } * portionMultiplier).roundToInt()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "估算合计：$totalKcal kcal · 蛋白 ${totalProt}g",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "重算",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onReEstimate() }
                )
            }

            if (response.summary.isNotBlank()) {
                Text(response.summary, style = MaterialTheme.typography.labelMedium)
            }

            response.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "• ${item.name} (${item.portionDesc})",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(item.kcal * portionMultiplier).roundToInt()} kcal / ${(item.proteinG * portionMultiplier).roundToInt()}g蛋",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (response.confidenceNote.isNotBlank()) {
                Text(
                    text = "💡 提示：${response.confidenceNote}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = "实际吃掉比例微调：${String.format("%.1f", portionMultiplier)}x",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = portionMultiplier,
                    onValueChange = { portionMultiplier = (it * 10).roundToInt() / 10f },
                    valueRange = 0.4f..2.0f
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveToTemplates, onCheckedChange = { saveToTemplates = it })
                Text("同时沉淀到「本地常吃模板」（下次免联网秒点）", style = MaterialTheme.typography.labelMedium)
            }

            if (response.items.size >= 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsCombo, onCheckedChange = { saveAsCombo = it })
                    Text("打包保存为一键套餐", style = MaterialTheme.typography.labelMedium)
                }
                if (saveAsCombo) {
                    OutlinedTextField(
                        value = comboName,
                        onValueChange = { comboName = it },
                        label = { Text("套餐名，如：常点猪脚饭套餐") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    onConfirm(
                        response,
                        portionMultiplier,
                        saveToTemplates,
                        comboName.takeIf { saveAsCombo && it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认入账 ($totalKcal kcal)")
            }
        }
    }
}

@Composable
private fun FastNumberAndKjTab(
    onSubmit: (String, String, Float, Float, Float, Float, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var saveAsTemplate by remember { mutableStateOf(false) }

    // 包装食品营养成分表 kJ -> kcal 换算辅助器
    var kjPer100gText by remember { mutableStateOf("") }
    var protPer100gText by remember { mutableStateOf("") }
    var eatenGramsText by remember { mutableStateOf("100") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Text(
                text = "极速记账（支持仅填大卡，蛋白质选填）",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text("总热量 (kcal 必填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = { proteinText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("蛋白质 (g 选填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("备注名称（选填，如：便利店三明治）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
                Text("存为我的常吃食物", style = MaterialTheme.typography.labelMedium)
            }
        }

        item {
            Button(
                onClick = {
                    val kcal = kcalText.toFloatOrNull() ?: 0f
                    if (kcal > 0f) {
                        val prot = proteinText.toFloatOrNull() ?: 0f
                        onSubmit(title, "1份", kcal, prot, 0f, 0f, saveAsTemplate)
                    }
                },
                enabled = (kcalText.toFloatOrNull() ?: 0f) > 0f,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("立即记入")
            }
        }

        // 包装袋营养成分表千焦换算小工具
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🧮 国内包装袋营养成分表换算（千焦 kJ → 大卡 kcal）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = kjPer100gText,
                            onValueChange = { kjPer100gText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                            label = { Text("每100g能量(kJ)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = protPer100gText,
                            onValueChange = { protPer100gText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            label = { Text("每100g蛋白(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = eatenGramsText,
                            onValueChange = { eatenGramsText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                            label = { Text("吃了多少(g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    val kj = kjPer100gText.toFloatOrNull() ?: 0f
                    val g = eatenGramsText.toFloatOrNull() ?: 0f
                    val p100 = protPer100gText.toFloatOrNull() ?: 0f
                    val convertedKcal = MetabolismEngine.nutritionTableToKcal(kj, isKj = true, weightGrams = g)
                    val convertedProt = p100 * (g / 100f)

                    OutlinedButton(
                        onClick = {
                            if (convertedKcal > 0f) {
                                kcalText = convertedKcal.roundToInt().toString()
                                if (convertedProt > 0f) {
                                    proteinText = String.format("%.1f", convertedProt)
                                }
                            }
                        },
                        enabled = convertedKcal > 0f,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("填入换算结果：${convertedKcal.roundToInt()} kcal · 蛋白 ${String.format("%.1f", convertedProt)} g")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

private fun createCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
