package com.litecal.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litecal.app.domain.model.MealType
import com.litecal.app.ui.DashboardUiState
import com.litecal.app.ui.LiteCalViewModel
import com.litecal.app.ui.dashboard.DashboardScreen
import com.litecal.app.ui.dashboard.QuickLogSheet
import com.litecal.app.ui.library.LibrarySettingsScreen
import com.litecal.app.ui.onboarding.OnboardingScreen
import com.litecal.app.ui.theme.LiteCalTheme
import com.litecal.app.ui.trends.TrendsScreen
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private val viewModel: LiteCalViewModel by viewModels {
        val app = application as LiteCalApplication
        LiteCalViewModel.factory(app.repository, app.reminderScheduler)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetIntent(intent)

        setContent {
            LiteCalTheme {
                LiteCalRootApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val defaultMeal = MealType.defaultForHour(LocalTime.now().hour)
        when (intent?.action) {
            ACTION_QUICK_ADD -> {
                viewModel.selectDate(LocalDate.now())
                viewModel.openQuickSheet(mealType = defaultMeal, initialTab = 0)
            }
            ACTION_AI_SCAN -> {
                viewModel.selectDate(LocalDate.now())
                viewModel.openQuickSheet(mealType = defaultMeal, initialTab = 1)
            }
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.litecal.app.ACTION_QUICK_ADD"
        const val ACTION_AI_SCAN = "com.litecal.app.ACTION_AI_SCAN"
    }
}

@Composable
fun LiteCalRootApp(viewModel: LiteCalViewModel) {
    val uiState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val quickSheetReq by viewModel.quickSheetState.collectAsStateWithLifecycle()
    val frequentFoods by viewModel.frequentFoodsFlow.collectAsStateWithLifecycle()
    val allFoods by viewModel.allFoodsFlow.collectAsStateWithLifecycle()
    val mealCombos by viewModel.mealCombosFlow.collectAsStateWithLifecycle()
    val aiConfig by viewModel.aiConfigFlow.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val aiTestMessage by viewModel.aiTestMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.profile.onboardingCompleted) {
        OnboardingScreen(
            initialProfile = uiState.profile,
            onComplete = viewModel::completeOnboarding
        )
        return
    }

    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("今日") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    label = { Text("趋势") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                    label = { Text("词库与设置") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> DashboardScreen(
                    state = uiState,
                    onPrevDay = { viewModel.shiftDate(-1) },
                    onNextDay = { viewModel.shiftDate(1) },
                    onBackToToday = { viewModel.selectDate(LocalDate.now()) },
                    onOpenQuickAdd = viewModel::openQuickSheet,
                    onCopyYesterdayMeal = viewModel::copyYesterdayMeal,
                    onSaveMealAsCombo = viewModel::saveMealCardAsCombo,
                    onDeleteMealLog = viewModel::deleteMealLog,
                    onRecordWeight = viewModel::recordWeight,
                    onAddWater = viewModel::addWater,
                    onToggleIncompleteDay = viewModel::toggleIncompleteDay
                )

                1 -> TrendsScreen(
                    state = uiState,
                    onRecordBodyShape = viewModel::recordBodyShape
                )

                2 -> LibrarySettingsScreen(
                    profile = uiState.profile,
                    aiConfig = aiConfig,
                    aiTestMessage = aiTestMessage,
                    allFoods = allFoods,
                    combos = mealCombos,
                    onSaveAiConfig = viewModel::saveAiConfig,
                    onTestAiConfig = viewModel::testAiConnection,
                    onUpdateProfile = viewModel::updateUserProfile,
                    onCreateCustomFood = viewModel::createCustomFood,
                    onDeleteCustomFood = viewModel::deleteCustomFood,
                    onDeleteCombo = viewModel::deleteCombo
                )
            }
        }
    }

    quickSheetReq?.let { req ->
        QuickLogSheet(
            initialMealType = req.mealType,
            initialTab = req.initialTab,
            frequentFoods = frequentFoods,
            allFoods = allFoods,
            mealCombos = mealCombos,
            aiUiState = aiUiState,
            onDismiss = viewModel::dismissQuickSheet,
            onLogFood = viewModel::logLibraryFood,
            onLogCombo = viewModel::logCombo,
            onToggleFavorite = viewModel::toggleFavorite,
            onRunAiEstimate = viewModel::runAiEstimation,
            onResetAiEstimate = viewModel::resetAiEstimation,
            onConfirmAiEstimate = viewModel::confirmAiMeal,
            onLogQuickEntry = viewModel::logQuickEntry
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    LiteCalTheme {
        DashboardScreen(
            state = DashboardUiState(
                isLoading = false,
                consumedKcal = 1120,
                dynamicTargetKcal = 1750,
                remainingKcal = 630,
                proteinConsumedG = 78,
                proteinTargetG = 110,
                carbsConsumedG = 120,
                fatConsumedG = 32,
                waterMl = 1250,
                selectedDateWeightKg = 71.4f,
                selectedDateEmaKg = 71.6f
            ),
            onPrevDay = {},
            onNextDay = {},
            onBackToToday = {},
            onOpenQuickAdd = { _, _ -> },
            onCopyYesterdayMeal = {},
            onSaveMealAsCombo = { _, _ -> },
            onDeleteMealLog = {},
            onRecordWeight = {},
            onAddWater = {},
            onToggleIncompleteDay = {}
        )
    }
}
