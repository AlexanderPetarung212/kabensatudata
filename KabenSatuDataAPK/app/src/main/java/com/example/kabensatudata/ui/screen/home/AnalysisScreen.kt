package com.example.kabensatudata.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.AppTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(title = "Analisis Data", onBack = onBack)
        },
        containerColor = AppColors.Background
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            analysisSection(
                state = uiState.analysisState,
                onToggleGroup = viewModel::toggleAnalysisGroup,
                onFilterChanged = viewModel::updateAnalysisFilter,
                onVillageSelected = viewModel::selectAnalysisVillage,
                onAnalyze = viewModel::runAnalysis,
                onClear = viewModel::clearAnalysis,
                onToggleCrossTab = viewModel::toggleCrossTab,
                onCrossTabVar1Changed = viewModel::setCrossTabVar1,
                onCrossTabVar2Changed = viewModel::setCrossTabVar2,
                onRunCrossTab = viewModel::runCrossTab,
                onPageChanged = viewModel::setAnalysisPage,
                onDismissSuccessNotif = viewModel::dismissSuccessNotif,
                onExport = { viewModel.exportAnalysis(context) }
            )
        }
    }
}
