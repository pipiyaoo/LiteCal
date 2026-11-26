package com.litecal.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    aiUiState: AiUiState,
    onDismiss: () -> Unit,
    onRunAiEstimate: (String, Uri?) -> Unit,
    onResetAiEstimate: () -> Unit,
    onConfirmAiEstimate: (MealType, AiMealEstimateResponse, Float) -> Unit,
    onLogManualEntry: (MealType, String, Float, Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMealType by remember(initialMealType) { mutableStateOf(initialMealType) }
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        modifier = Modifier.weight(1f),
                        label = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = type.label, maxLines = 1)
                            }
                        }
                    )
                }
            }

            val tabs = listOf("拍照测算", "手动填写")
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.bodyMedium) }
                    )
                }
            }

            when (selectedTab) {
                0 -> PhotoAiTab(
                    aiUiState = aiUiState,
                    onRunEstimate = onRunAiEstimate,
                    onReset = onResetAiEstimate,
                    onConfirm = { resp, mult ->
                        onConfirmAiEstimate(selectedMealType, resp, mult)
                    }
                )

                1 -> ManualEntryTab(
                    onSubmit = { title, kcal, prot ->
                        onLogManualEntry(selectedMealType, title, kcal, prot)
                    }
                )
            }
        }
    }
}

@Composable
private fun PhotoAiTab(
    aiUiState: AiUiState,
    onRunEstimate: (String, Uri?) -> Unit,
    onReset: () -> Unit,
    onConfirm: (AiMealEstimateResponse, Float) -> Unit
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

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("拍照")
            }

            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("相册")
            }
        }

        if (selectedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("已选照片", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = "移除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { selectedImageUri = null }
                )
            }
        }

        OutlinedTextField(
            value = descriptionText,
            onValueChange = { descriptionText = it },
            label = { Text("补充说明（选填）") },
            placeholder = { Text("如：少油、吃了一半") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (aiUiState) {
            is AiUiState.Idle, is AiUiState.Error -> {
                if (aiUiState is AiUiState.Error) {
                    Text(
                        text = aiUiState.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = { onRunEstimate(descriptionText, selectedImageUri) },
                    enabled = selectedImageUri != null || descriptionText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("开始测算")
                }
            }

            is AiUiState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("测算中...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            is AiUiState.Success -> {
                AiResultConfirmCard(
                    response = aiUiState.response,
                    onReEstimate = onReset,
                    onConfirm = onConfirm
                )
            }
        }
    }
}

@Composable
private fun AiResultConfirmCard(
    response: AiMealEstimateResponse,
    onReEstimate: () -> Unit,
    onConfirm: (AiMealEstimateResponse, Float) -> Unit
) {
    var portionMultiplier by remember(response) { mutableFloatStateOf(1.0f) }

    val totalKcal = (response.items.sumOf { it.kcal.toDouble() } * portionMultiplier).roundToInt()
    val totalProt = (response.items.sumOf { it.proteinG.toDouble() } * portionMultiplier).roundToInt()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalKcal kcal · 蛋白 ${totalProt}g",
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

            response.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(item.kcal * portionMultiplier).roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "份量 ${String.format("%.1f", portionMultiplier)}x",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = portionMultiplier,
                    onValueChange = { portionMultiplier = (it * 10).roundToInt() / 10f },
                    valueRange = 0.4f..2.0f,
                    colors = SliderDefaults.colors(
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = { onConfirm(response, portionMultiplier) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text("记入 ($totalKcal kcal)")
            }
        }
    }
}

@Composable
private fun ManualEntryTab(
    onSubmit: (String, Float, Float) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var showKjConverter by remember { mutableStateOf(false) }

    var kjPer100gText by remember { mutableStateOf("") }
    var eatenGramsText by remember { mutableStateOf("100") }

    val kcalVal = kcalText.toFloatOrNull() ?: 0f

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = kcalText,
                onValueChange = { kcalText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = { Text("热量 (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = proteinText,
                onValueChange = { proteinText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("蛋白质 (g)") },
                placeholder = { Text("选填") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("食物名称（选填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (kcalVal > 0f) {
                    val prot = proteinText.toFloatOrNull() ?: 0f
                    onSubmit(title, kcalVal, prot)
                }
            },
            enabled = kcalVal > 0f,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("记入")
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = { showKjConverter = !showKjConverter }) {
                Text(
                    text = if (showKjConverter) "收起千焦换算" else "千焦(kJ) 换算 ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = showKjConverter) {
            val kj = kjPer100gText.toFloatOrNull() ?: 0f
            val grams = eatenGramsText.toFloatOrNull() ?: 0f
            val convertedKcal = MetabolismEngine.nutritionTableToKcal(kj, isKj = true, weightGrams = grams).roundToInt()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = kjPer100gText,
                        onValueChange = { kjPer100gText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("每100g kJ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eatenGramsText,
                        onValueChange = { eatenGramsText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                        label = { Text("克重 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (convertedKcal > 0) {
                            kcalText = convertedKcal.toString()
                        }
                    },
                    enabled = convertedKcal > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("填入 $convertedKcal kcal")
                }
            }
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
