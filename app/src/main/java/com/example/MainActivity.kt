package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TransactionEntity
import com.example.network.ParsedTransactionResult
import com.example.ui.components.AppLockScreen
import com.example.ui.components.ReceiptViewerModal
import com.example.ui.screens.ApprovalScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.TransactionEntryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.UiEvent
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inisialisasi Notification Channel
        com.example.util.AppNotificationHelper.createNotificationChannel(this)

        // Minta Izin Notifikasi untuk Android 13+ (Tiramisu)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            MyApplicationTheme {
                BukuKasPintarApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BukuKasPintarApp(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val schoolProfile by viewModel.schoolProfile.collectAsStateWithLifecycle()
    val userSession by viewModel.userSession.collectAsStateWithLifecycle()
    val transactions by viewModel.visibleTransactions.collectAsStateWithLifecycle()
    val pagedTransactions by viewModel.pagedTransactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFundFilter by viewModel.selectedFundFilter.collectAsStateWithLifecycle()
    val pendingTransactions by viewModel.pendingApprovalTransactions.collectAsStateWithLifecycle()
    val fundBalances by viewModel.fundBalances.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val googleSheetsUrl by viewModel.googleSheetsUrl.collectAsStateWithLifecycle()
    val spreadsheetDocUrl by viewModel.spreadsheetDocUrl.collectAsStateWithLifecycle()
    val customFundSources by viewModel.customFundSources.collectAsStateWithLifecycle()
    val visibleFundSources by viewModel.visibleFundSources.collectAsStateWithLifecycle()
    val deviceRoleLock by viewModel.deviceRoleLock.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val bkuReportState by viewModel.bkuReportState.collectAsStateWithLifecycle()
    val schoolPairingKey by viewModel.schoolPairingKey.collectAsStateWithLifecycle()
    val cloudMailboxRelayUrl by viewModel.cloudMailboxRelayUrl.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Dashboard, 1: Entry, 2: Approval, 3: Report
    var showAccountDialog by remember { mutableStateOf(false) }
    var showQrPairingDialog by remember { mutableStateOf(false) }
    var selectedReceiptTx by remember { mutableStateOf<TransactionEntity?>(null) }

    // Saldo Tidak Cukup Warning Alert Dialog
    var showBalanceWarningDialog by remember { mutableStateOf(false) }
    var warningSource by remember { mutableStateOf("") }
    var warningAvailable by remember { mutableStateOf(0.0) }
    var warningRequested by remember { mutableStateOf(0.0) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
    }

    // Jika aplikasi belum di-unlock dengan PIN, tampilkan Layar Kunci Keamanan
    if (!isAppUnlocked) {
        AppLockScreen(
            schoolName = schoolProfile.schoolName,
            userSession = userSession,
            deviceRoleLock = deviceRoleLock,
            onUnlockSuccess = { role ->
                viewModel.unlockApp(role)
            }
        )
        return
    }

    // Auto-sync refresh when switching tabs or viewing screens
    LaunchedEffect(activeTab) {
        viewModel.syncNowSilently()
    }

    // Event listener for toasts, balance warnings, and voice parsing
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.BalanceWarning -> {
                    warningSource = event.fundSource
                    warningAvailable = event.currentBalance
                    warningRequested = event.requestedAmount
                    showBalanceWarningDialog = true
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Dashboard, contentDescription = "Beranda") },
                    label = { Text("Beranda") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Pencatatan") },
                    label = { Text("Catat Kas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = {
                        if (pendingTransactions.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${pendingTransactions.size}") } }) {
                                Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = "Verifikasi")
                            }
                        } else {
                            Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = "Verifikasi")
                        }
                    },
                    label = { Text("Verifikasi") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Assessment, contentDescription = "BKU / SPJ") },
                    label = { Text("BKU Kas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeTab) {
                0 -> DashboardScreen(
                    currentRole = currentRole,
                    schoolProfile = schoolProfile,
                    userSession = userSession,
                    fundBalances = fundBalances,
                    fundSources = visibleFundSources,
                    transactions = pagedTransactions,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query -> viewModel.setSearchQuery(query) },
                    selectedFundFilter = selectedFundFilter,
                    onSelectFundFilter = { fund -> viewModel.setSelectedFundFilter(fund) },
                    onLoadNextPage = { viewModel.nextPage() },
                    pendingTransactionsCount = pendingTransactions.size,
                    onRoleSwitched = { role -> viewModel.switchRole(role) },
                    onOpenAccountDialog = { showAccountDialog = true },
                    onOpenQrPairing = { showQrPairingDialog = true },
                    onLockApp = { viewModel.lockApp() },
                    onNavigateToEntry = { activeTab = 1 },
                    onNavigateToApproval = { activeTab = 2 },
                    onViewReceipt = { tx -> selectedReceiptTx = tx },
                    onDeleteTransaction = { tx -> viewModel.deleteTransaction(tx) }
                )

                1 -> TransactionEntryScreen(
                    initialParsedResult = null,
                    userSession = userSession,
                    currentRole = currentRole,
                    fundSources = visibleFundSources,
                    onBack = { activeTab = 0 },
                    onSubmitTransaction = { title, amount, type, fundSource, category, volume, unitName, unitPrice, notes, receiptUri, date ->
                        viewModel.submitTransaction(title, amount, type, fundSource, category, volume, unitName, unitPrice, notes, receiptUri, date)
                        activeTab = 0
                    }
                )

                2 -> ApprovalScreen(
                    pendingTransactions = pendingTransactions,
                    currentRole = currentRole,
                    schoolProfile = schoolProfile,
                    onOpenAccountDialog = { showAccountDialog = true },
                    onApprove = { id -> viewModel.approveTransaction(id) },
                    onReject = { id -> viewModel.rejectTransaction(id) },
                    onViewReceipt = { tx -> selectedReceiptTx = tx }
                )

                3 -> ReportScreen(
                    transactions = transactions,
                    schoolProfile = schoolProfile,
                    fundSources = visibleFundSources,
                    currentRole = currentRole,
                    bkuReportState = bkuReportState,
                    onSelectFundSource = { fund -> viewModel.setReportFundSource(fund) },
                    syncState = syncState,
                    googleSheetsUrl = googleSheetsUrl,
                    spreadsheetDocUrl = spreadsheetDocUrl,
                    onSaveGoogleSheetsUrl = { scriptUrl, docUrl -> viewModel.saveGoogleSheetsUrl(scriptUrl, docUrl) },
                    onExportCsv = { ctx, fundName -> viewModel.exportToCsv(ctx, fundName) },
                    onSyncAllToGoogleSheets = { viewModel.syncAllToGoogleSheets() },
                    onFetchFromGoogleSheets = { viewModel.fetchFromGoogleSheets() },
                    onDeleteAllData = { viewModel.deleteAllData() },
                    onSyncRkasToSheets = { items -> viewModel.syncRkasToGoogleSheets(items) },
                    onFetchRkasFromSheets = { callback -> viewModel.fetchRkasFromGoogleSheets(callback) }
                )
            }
        }

        // Account & School Profile Dialog Modal
        if (showAccountDialog) {
            com.example.ui.components.SchoolAccountDialog(
                schoolProfile = schoolProfile,
                userSession = userSession,
                deviceRoleLock = deviceRoleLock,
                schoolPairingKey = schoolPairingKey,
                cloudMailboxRelayUrl = cloudMailboxRelayUrl,
                googleSheetsUrl = googleSheetsUrl,
                spreadsheetDocUrl = spreadsheetDocUrl,
                customFundSources = customFundSources,
                onDismiss = { showAccountDialog = false },
                onLoginAccount = { role, name, pin -> viewModel.loginUserAccount(role, name, pin) },
                onSetDeviceRoleLock = { lockMode -> viewModel.setDeviceRoleLock(lockMode) },
                onUpdateProfile = { profile -> viewModel.updateSchoolProfile(profile) },
                onOpenQrPairing = { showQrPairingDialog = true },
                onSavePairingKey = { key, relay -> viewModel.setSchoolPairingKey(key, relay) },
                onSendToCloudMailbox = { viewModel.sendToCloudMailbox() },
                onFetchFromCloudMailbox = { viewModel.fetchFromCloudMailbox() },
                onSyncCloudMailbox = { viewModel.syncCloudMailbox() },
                onSaveGoogleSheetsUrl = { scriptUrl, docUrl -> viewModel.saveGoogleSheetsUrl(scriptUrl, docUrl) },
                onUpdatePin = { role, newPin -> viewModel.updatePin(role, newPin) },
                onUpdateFundSources = { list -> viewModel.updateFundSources(list) },
                onRenameFundSource = { old, new -> viewModel.renameFundSource(old, new) },
                onSyncAllToGoogleSheets = { viewModel.syncAllToGoogleSheets() },
                onFetchFromGoogleSheets = { viewModel.fetchFromGoogleSheets() }
            )
        }

        // QR Code Pairing Dialog Modal (Cara 1: Scan QR Code Pairing WA-Web style)
        if (showQrPairingDialog) {
            com.example.ui.components.QrPairingDialog(
                schoolProfile = schoolProfile,
                pairingKey = schoolPairingKey,
                relayUrl = cloudMailboxRelayUrl,
                currentRole = currentRole,
                onPairingConfirmed = { npsn, name, key, relay ->
                    viewModel.applyQrPairingData(npsn, name, key, relay)
                },
                onDismiss = { showQrPairingDialog = false }
            )
        }

        // Receipt Preview Modal Component
        if (selectedReceiptTx != null) {
            ReceiptViewerModal(
                transaction = selectedReceiptTx!!,
                onDismiss = { selectedReceiptTx = null }
            )
        }

        // Saldo Tidak Cukup Warning Dialog
        if (showBalanceWarningDialog) {
            AlertDialog(
                onDismissRequest = { showBalanceWarningDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Peringatan Saldo",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        text = "Saldo Tidak Cukup!",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                },
                text = {
                    Text(
                        text = "Sumber dana $warningSource tidak mencukupi untuk pengeluaran ini.\n\n" +
                                "• Saldo Tersedia: ${currencyFormatter.format(warningAvailable)}\n" +
                                "• Nominal Usulan: ${currencyFormatter.format(warningRequested)}\n\n" +
                                "Silakan periksa kembali alokasi anggaran atau pilih sumber dana lain."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showBalanceWarningDialog = false }) {
                        Text("Mengerti", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// Local Context Helper Object for Toast in composable
object LocalContextHolder {
    var context: android.content.Context? = null
}
