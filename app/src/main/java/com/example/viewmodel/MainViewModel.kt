package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.FundSourceDefaults
import com.example.data.model.FundSourceModel
import com.example.data.model.TransactionEntity
import com.example.data.model.UserRole
import com.example.data.repository.BkuSyncRepository
import com.example.data.repository.GoogleSheetsSyncRepository
import com.example.data.repository.SyncState
import com.example.data.repository.TransactionRepository
import com.example.network.GeminiNlpService
import com.example.network.ParsedTransactionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class PagingQueryParam(
    val fund: String,
    val query: String,
    val limit: Int,
    val offset: Int,
    val secretFunds: Set<String>
)

data class FundSourceBalance(
    val fundSource: String,
    val initialBudget: Double,
    val totalIncome: Double,
    val totalExpense: Double,
    val currentBalance: Double,
    val absorptionRate: Float, // 0.0f to 1.0f
    val isSecret: Boolean = false
)

data class TransactionWithBalance(
    val transaction: TransactionEntity,
    val runningBalance: Double
)

data class BkuReportState(
    val selectedFundSource: String = "BOS Reguler",
    val itemsWithBalance: List<TransactionWithBalance> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0,
    val isLoading: Boolean = false
)

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class BalanceWarning(val fundSource: String, val currentBalance: Double, val requestedAmount: Double) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    private var bkuSyncRepository: GoogleSheetsSyncRepository? = null
    private val nlpService = GeminiNlpService()

    val currentRole = MutableStateFlow(UserRole.BENDAHARA)
    val schoolProfile = MutableStateFlow(com.example.data.model.SchoolProfile())
    val userSession = MutableStateFlow(com.example.data.model.UserAccountSession())


    val selectedFundFilter = MutableStateFlow("BOS Reguler")
    val selectedCategoryFilter = MutableStateFlow("")
    val googleSheetsUrl = MutableStateFlow("") // Apps Script Web App URL for Sync
    val spreadsheetDocUrl = MutableStateFlow("") // Direct Google Spreadsheet URL for opening/editing

    val customFundSources = MutableStateFlow<List<FundSourceModel>>(FundSourceDefaults.DEFAULT_FUND_MODELS)

    // Pagination State
    val currentPage = MutableStateFlow(0)
    val pageSize = MutableStateFlow(30)
    val searchQuery = MutableStateFlow("")

    // Selected Fund Source for BKU Report Running Balance calculation
    val reportSelectedFundSource = MutableStateFlow("BOS Reguler")

    // Kunci Peran Perangkat: "ALL" (Bebas/Dual), "BENDAHARA" (Khusus HP Bendahara), "KEPALA_SEKOLAH" (Khusus HP Kepala Sekolah)
    val deviceRoleLock = MutableStateFlow("ALL")

    val isAppUnlocked = MutableStateFlow(false)

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    val syncState: StateFlow<SyncState> get() = bkuSyncRepository?.syncState ?: MutableStateFlow<SyncState>(SyncState.Idle).asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        val dao = database.transactionDao()
        repository = TransactionRepository(dao)

        try {
            bkuSyncRepository = GoogleSheetsSyncRepository(dao)
            val prefs = application.getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
            googleSheetsUrl.value = prefs.getString("google_sheets_url", "") ?: ""
            spreadsheetDocUrl.value = prefs.getString("spreadsheet_doc_url", "") ?: ""

            // Load saved school profile
            val sName = prefs.getString("school_name", "SD Negeri 33/III Air Tenang") ?: "SD Negeri 33/III Air Tenang"
            val sNpsn = prefs.getString("school_npsn", "10103214") ?: "10103214"
            val sAddr = prefs.getString("school_address", "Kec. Air Hangat, Kab. Kerinci, Prov. Jambi") ?: "Kec. Air Hangat, Kab. Kerinci, Prov. Jambi"
            val sKepsek = prefs.getString("school_kepsek", "H. Ahmad S.Pd., M.Pd.") ?: "H. Ahmad S.Pd., M.Pd."
            val sKepsekNip = prefs.getString("school_kepsek_nip", "197203151998031002") ?: "197203151998031002"
            val sBendahara = prefs.getString("school_bendahara", "Siti Rahma S.Pd.") ?: "Siti Rahma S.Pd."
            val sBendaharaNip = prefs.getString("school_bendahara_nip", "198507202010012015") ?: "198507202010012015"

            // Pairing key per sekolah (default BKU-<NPSN>)

            schoolProfile.value = com.example.data.model.SchoolProfile(
                schoolName = sName,
                npsn = sNpsn,
                address = sAddr,
                kepalaSekolahName = sKepsek,
                kepalaSekolahNip = sKepsekNip,
                bendaharaName = sBendahara,
                bendaharaNip = sBendaharaNip
            )

            // Load saved user session
            val savedLock = prefs.getString("device_role_lock", "ALL") ?: "ALL"
            deviceRoleLock.value = savedLock

            val activeRoleStr = prefs.getString("active_role", UserRole.BENDAHARA.name) ?: UserRole.BENDAHARA.name
            val activeRole = if (savedLock == "BENDAHARA") {
                UserRole.BENDAHARA
            } else if (savedLock == "KEPALA_SEKOLAH") {
                UserRole.KEPALA_SEKOLAH
            } else {
                try { UserRole.valueOf(activeRoleStr) } catch (e: Exception) { UserRole.BENDAHARA }
            }
            val userName = prefs.getString("user_name", if (activeRole == UserRole.BENDAHARA) sBendahara else sKepsek) ?: sBendahara
            val bPin = prefs.getString("bendahara_pin", "123456") ?: "123456"
            val kPin = prefs.getString("kepsek_pin", "123456") ?: "123456"

            currentRole.value = activeRole
            userSession.value = com.example.data.model.UserAccountSession(
                userName = userName,
                role = activeRole,
                isLoggedIn = true,
                bendaharaPin = bPin,
                kepsekPin = kPin
            )

            // Load saved custom fund sources with isSecret metadata
            val savedSourcesStr = prefs.getString("custom_fund_sources", null)
            val sourcesList = if (!savedSourcesStr.isNullOrBlank()) {
                savedSourcesStr.split(";;;").mapNotNull { token ->
                    val trimmed = token.trim()
                    if (trimmed.isBlank()) null
                    else if (trimmed.contains(":::")) {
                        val parts = trimmed.split(":::")
                        val name = parts[0].trim()
                        val isSecret = parts.getOrNull(1)?.toBoolean() ?: false
                        FundSourceModel(name = name, isSecret = isSecret)
                    } else {
                        FundSourceModel(name = trimmed, isSecret = false)
                    }
                }
            } else {
                FundSourceDefaults.DEFAULT_FUND_MODELS
            }
            customFundSources.value = sourcesList.ifEmpty { FundSourceDefaults.DEFAULT_FUND_MODELS }

            // Silent background license check ke Server Master Google Sheets
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    com.example.util.LicenseManager.checkLicenseOnline(application, sNpsn)
                } catch (ignored: Exception) {}
            }

            // OTOMATIS PENUH: Background Auto-Sync loop tanpa perlu klik tombol manual
            viewModelScope.launch {
                // Fetch saat aplikasi pertama kali dibuka
                kotlinx.coroutines.delay(1000)
                try {
                } catch (ignored: Exception) {}

                // Cek data masuk setiap 6 detik secara senyap di latar belakang
                while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) {
                    kotlinx.coroutines.delay(6000)
                    try {
                        } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Could not initialize Google Sheets sync repository", e)
        }
        
    }

    fun updateSchoolProfile(profile: com.example.data.model.SchoolProfile) {
        schoolProfile.value = profile
        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("school_name", profile.schoolName)
            .putString("school_npsn", profile.npsn)
            .putString("school_address", profile.address)
            .putString("school_kepsek", profile.kepalaSekolahName)
            .putString("school_kepsek_nip", profile.kepalaSekolahNip)
            .putString("school_bendahara", profile.bendaharaName)
            .putString("school_bendahara_nip", profile.bendaharaNip)
            .apply()

        // Sync active user name if it was matching default
        if (currentRole.value == UserRole.BENDAHARA) {
            userSession.value = userSession.value.copy(userName = profile.bendaharaName)
        } else if (currentRole.value == UserRole.KEPALA_SEKOLAH) {
            userSession.value = userSession.value.copy(userName = profile.kepalaSekolahName)
        }

        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Profil Identitas ${profile.schoolName} Berhasil Diperbarui!"))
            if (profile.npsn.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        com.example.util.LicenseManager.checkLicenseOnline(
                            getApplication(),
                            profile.npsn
                        )
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    fun loginUserAccount(role: UserRole, name: String, pinInput: String): Boolean {
        val currentSession = userSession.value
        val expectedPin = if (role == UserRole.BENDAHARA) currentSession.bendaharaPin else currentSession.kepsekPin

        if (pinInput != expectedPin && expectedPin.isNotBlank()) {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("PIN Login Salah! Silakan coba lagi."))
            }
            return false
        }

        currentRole.value = role
        val newSession = currentSession.copy(
            userName = name,
            role = role,
            isLoggedIn = true
        )
        userSession.value = newSession

        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("active_role", role.name)
            .putString("user_name", name)
            .apply()

        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Berhasil Login sebagai ${role.label} (${name})"))
        }
        return true
    }

    fun unlockApp(role: UserRole? = null) {
        if (role != null) {
            currentRole.value = role
            val profile = schoolProfile.value
            val name = if (role == UserRole.BENDAHARA) profile.bendaharaName else profile.kepalaSekolahName
            userSession.value = userSession.value.copy(
                userName = name,
                role = role
            )
            val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
            prefs.edit().putString("active_role", role.name).apply()
        }
        isAppUnlocked.value = true
    }

    fun lockApp() {
        isAppUnlocked.value = false
    }

    fun setDeviceRoleLock(lockMode: String) {
        // "ALL", "BENDAHARA", "KEPALA_SEKOLAH"
        deviceRoleLock.value = lockMode
        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        prefs.edit().putString("device_role_lock", lockMode).apply()

        if (lockMode == "BENDAHARA") {
            currentRole.value = UserRole.BENDAHARA
            val name = schoolProfile.value.bendaharaName
            userSession.value = userSession.value.copy(role = UserRole.BENDAHARA, userName = name)
            prefs.edit().putString("active_role", UserRole.BENDAHARA.name).apply()
        } else if (lockMode == "KEPALA_SEKOLAH") {
            currentRole.value = UserRole.KEPALA_SEKOLAH
            val name = schoolProfile.value.kepalaSekolahName
            userSession.value = userSession.value.copy(role = UserRole.KEPALA_SEKOLAH, userName = name)
            prefs.edit().putString("active_role", UserRole.KEPALA_SEKOLAH.name).apply()
        }

        viewModelScope.launch {
            val label = when (lockMode) {
                "BENDAHARA" -> "Khusus HP Bendahara (Peran Terkunci)"
                "KEPALA_SEKOLAH" -> "Khusus HP Kepala Sekolah (Peran Terkunci)"
                else -> "Mode Terbuka (Dual Role)"
            }
            _eventFlow.emit(UiEvent.ShowToast("Pengaturan Perangkat: $label"))
        }
    }

    fun updatePin(role: UserRole, newPin: String) {
        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        if (role == UserRole.BENDAHARA) {
            userSession.value = userSession.value.copy(bendaharaPin = newPin)
            prefs.edit().putString("bendahara_pin", newPin).apply()
        } else {
            userSession.value = userSession.value.copy(kepsekPin = newPin)
            prefs.edit().putString("kepsek_pin", newPin).apply()
        }
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("PIN Login ${role.label} Berhasil Diubah!"))
        }
    }

    fun saveGoogleSheetsUrl(appsScriptUrl: String, spreadsheetUrl: String = "") {
        googleSheetsUrl.value = appsScriptUrl.trim()
        if (spreadsheetUrl.isNotBlank() || spreadsheetDocUrl.value.isBlank()) {
            spreadsheetDocUrl.value = spreadsheetUrl.trim()
        }
        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("google_sheets_url", appsScriptUrl.trim())
            .putString("spreadsheet_doc_url", spreadsheetDocUrl.value.trim())
            .apply()
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Pengaturan URL berhasil disimpan"))
            if (appsScriptUrl.trim().isNotBlank()) {
                val currentList = repository.allTransactions.first()
                if (currentList.isEmpty()) {
                    // Jika data lokal di HP ini masih kosong (misal HP Bendahara baru pasang),
                    // otomatis TARIK data dari Google Sheets agar tidak menimpa data yang sudah dibuat Kepala Sekolah!
                    fetchFromGoogleSheets()
                } else {
                    syncAllToGoogleSheets()
                }
            }
        }
    }

    fun manualSync() {
        val sheetsUrl = googleSheetsUrl.value.trim()
        if (sheetsUrl.isNotBlank() && !sheetsUrl.contains("docs.google.com/spreadsheets")) {
            syncAllToGoogleSheets()
        } else {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("Silakan atur URL Google Sheets terlebih dahulu di menu pengaturan."))
            }
        }
    }

    private fun triggerAutoSyncIfConfigured() {
        // 2. Google Sheets sync (jika URL dikonfigurasi)
        val url = googleSheetsUrl.value.trim()
        val useGoogleSheets = url.isNotBlank() && !url.contains("docs.google.com/spreadsheets")

        if (!useGoogleSheets) {
            viewModelScope.launch {
                try {
                            } catch (ignored: Exception) {}
            }
        }
        if (url.isNotBlank() && !url.contains("docs.google.com/spreadsheets")) {
            viewModelScope.launch {
                try {
                    val freshList = repository.allTransactions.first()
                    val secretSources = customFundSources.value.filter { it.isSecret }.map { it.name }.toSet()
                    val nonSecretList = freshList.filter { it.fundSource !in secretSources }
                    val nonSecretFundNames = customFundSources.value.filter { !it.isSecret }.map { it.name }
                    bkuSyncRepository?.syncToGoogleSheets(url, nonSecretList, nonSecretFundNames)
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Auto sync background error", e)
                }
            }
        }
    }

    private fun saveFundSourcesToPrefs(models: List<FundSourceModel>) {
        val prefs = getApplication<Application>().getSharedPreferences("bku_settings", Application.MODE_PRIVATE)
        val serialized = models.joinToString(";;;") { "${it.name}:::${it.isSecret}" }
        prefs.edit().putString("custom_fund_sources", serialized).apply()
    }

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pendingApprovalTransactions: StateFlow<List<TransactionEntity>> = repository.getPendingApprovalTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered fund sources according to current role (Bendahara cannot see secret funds)
    val visibleFundSources: StateFlow<List<String>> = kotlinx.coroutines.flow.combine(customFundSources, currentRole) { sources, role ->
        if (role == UserRole.KEPALA_SEKOLAH) {
            sources.map { it.name }
        } else {
            sources.filter { !it.isSecret }.map { it.name }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered transactions according to current role (Bendahara cannot see transactions from secret funds)
    val visibleTransactions: StateFlow<List<TransactionEntity>> = kotlinx.coroutines.flow.combine(allTransactions, customFundSources, currentRole) { txs, sources, role ->
        if (role == UserRole.KEPALA_SEKOLAH) {
            txs
        } else {
            val secretFundNames = sources.filter { it.isSecret }.map { it.name }.toSet()
            txs.filter { it.fundSource !in secretFundNames }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Paged Transactions Flow (Accumulates pages or loads with limit = (currentPage + 1) * pageSize)
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedTransactions: StateFlow<List<TransactionEntity>> = kotlinx.coroutines.flow.combine(
        selectedFundFilter,
        searchQuery,
        currentPage,
        pageSize
    ) { fund, query, page, size ->
        val totalLimit = (page + 1) * size
        val effectiveFund = fund.ifBlank { "BOS Reguler" }
        Triple(effectiveFund, query.trim(), totalLimit)
    }.flatMapLatest { (fund, query, limit) ->
        repository.getTransactionsPaged(
            fundSource = fund,
            searchQuery = query,
            limit = limit,
            offset = 0
        )
    }.combine(
        kotlinx.coroutines.flow.combine(currentRole, customFundSources) { role, sources ->
            if (role == UserRole.KEPALA_SEKOLAH) emptySet()
            else sources.filter { it.isSecret }.map { it.name }.toSet()
        }
    ) { list, secretFunds ->
        if (secretFunds.isEmpty()) list
        else list.filter { it.fundSource !in secretFunds }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Running Balance (Saldo Kas BKU) Calculated on Dispatchers.Default to prevent UI freeze on large datasets
    val bkuReportState: StateFlow<BkuReportState> = kotlinx.coroutines.flow.combine(
        allTransactions,
        reportSelectedFundSource,
        customFundSources,
        currentRole
    ) { txs, selectedFund, sources, role ->
        // Return raw data to process on Default dispatcher
        listOf(txs, selectedFund, sources, role)
    }.map { params ->
        withContext(Dispatchers.Default) {
            @Suppress("UNCHECKED_CAST")
            val txs = params[0] as List<TransactionEntity>
            val selectedFund = params[1] as String
            @Suppress("UNCHECKED_CAST")
            val sources = params[2] as List<FundSourceModel>
            val role = params[3] as UserRole

            val secretFundNames = sources.filter { it.isSecret }.map { it.name }.toSet()
            if (role != UserRole.KEPALA_SEKOLAH && selectedFund in secretFundNames) {
                return@withContext BkuReportState(selectedFundSource = selectedFund)
            }

            val filteredList = txs.filter { tx ->
                tx.fundSource == selectedFund && tx.approvalStatus != "DITOLAK"
            }.sortedBy { it.date }

            var runningBalance = 0.0
            val itemsWithBal = ArrayList<TransactionWithBalance>(filteredList.size)
            var totalInc = 0.0
            var totalExp = 0.0

            for (tx in filteredList) {
                if (tx.type == "PEMASUKAN") {
                    runningBalance += tx.amount
                    totalInc += tx.amount
                } else {
                    runningBalance -= tx.amount
                    totalExp += tx.amount
                }
                itemsWithBal.add(TransactionWithBalance(tx, runningBalance))
            }

            BkuReportState(
                selectedFundSource = selectedFund,
                itemsWithBalance = itemsWithBal,
                totalIncome = totalInc,
                totalExpense = totalExp,
                netBalance = totalInc - totalExp,
                isLoading = false
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BkuReportState()
    )

    fun setReportFundSource(fundSource: String) {
        reportSelectedFundSource.value = fundSource
    }

    fun setPage(page: Int) {
        if (page >= 0) {
            currentPage.value = page
        }
    }

    fun nextPage() {
        currentPage.value = currentPage.value + 1
    }

    fun previousPage() {
        if (currentPage.value > 0) {
            currentPage.value = currentPage.value - 1
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        currentPage.value = 0 // Reset to first page on search
    }

    fun setSelectedFundFilter(fund: String) {
        selectedFundFilter.value = fund
        currentPage.value = 0 // Reset to first page on fund change
    }

    val fundBalances: StateFlow<List<FundSourceBalance>> = kotlinx.coroutines.flow.combine(allTransactions, customFundSources, currentRole) { transactions, sources, role ->
        val relevantSources = if (role == UserRole.KEPALA_SEKOLAH) sources else sources.filter { !it.isSecret }
        relevantSources.map { sourceModel ->
            val source = sourceModel.name
            val sourceTxs = transactions.filter { it.fundSource == source && it.approvalStatus != "DITOLAK" }
            val initialAlloc = FundSourceDefaults.INITIAL_BUDGETS[source] ?: 0.0
            val totalIncome = sourceTxs.filter { it.type == "PEMASUKAN" }.sumOf { it.amount }
            val totalExpense = sourceTxs.filter { it.type == "PENGELUARAN" }.sumOf { it.amount }
            
            val totalAvailable = initialAlloc + totalIncome
            val balance = totalAvailable - totalExpense
            val rate = if (totalAvailable > 0) (totalExpense / totalAvailable).toFloat().coerceIn(0f, 1f) else 0f

            FundSourceBalance(
                fundSource = source,
                initialBudget = initialAlloc,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                currentBalance = balance,
                absorptionRate = rate,
                isSecret = sourceModel.isSecret
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateFundSources(newSources: List<FundSourceModel>) {
        val cleanSources = newSources.filter { it.name.isNotBlank() }
        if (cleanSources.isEmpty()) return
        customFundSources.value = cleanSources
        saveFundSourcesToPrefs(cleanSources)

        val nonSecretNames = cleanSources.filter { !it.isSecret }.map { it.name }
        val url = googleSheetsUrl.value
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Daftar Pos Sumber Dana berhasil disimpan"))
            if (url.isNotBlank() && nonSecretNames.isNotEmpty()) {
                val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
                repo?.syncFundSources(url, nonSecretNames)
            }
        }
    }

    fun mergeCustomFundSources(newSources: List<String>) {
        val current = customFundSources.value.toMutableList()
        var changed = false
        val existingNames = current.map { it.name }.toSet()
        newSources.forEach { src ->
            val clean = src.trim()
            if (clean.isNotBlank() && !existingNames.contains(clean)) {
                current.add(FundSourceModel(name = clean, isSecret = false))
                changed = true
            }
        }
        if (changed) {
            customFundSources.value = current
            saveFundSourcesToPrefs(current)
        }
    }

    fun renameFundSource(oldName: String, newName: String) {
        val cleanNew = newName.trim()
        if (cleanNew.isBlank() || oldName == cleanNew) return
        val current = customFundSources.value.toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index != -1) {
            val oldModel = current[index]
            current[index] = oldModel.copy(name = cleanNew)
            customFundSources.value = current
            saveFundSourcesToPrefs(current)

            val url = googleSheetsUrl.value
            viewModelScope.launch {
                val txsToUpdate = allTransactions.value.filter { it.fundSource == oldName }
                txsToUpdate.forEach { tx ->
                    repository.updateTransaction(tx.copy(fundSource = cleanNew))
                }
                _eventFlow.emit(UiEvent.ShowToast("Pos Dana '$oldName' diubah menjadi '$cleanNew' (${txsToUpdate.size} transaksi diperbarui)"))
                if (url.isNotBlank() && !oldModel.isSecret) {
                    val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
                    val nonSecretNames = current.filter { !it.isSecret }.map { it.name }
                    repo?.syncFundSources(url, nonSecretNames)
                }
            }
        }
    }

    fun switchRole(role: UserRole) {
        currentRole.value = role
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Berpindah peran sebagai ${role.label}"))
        }
    }

    fun submitTransaction(
        title: String,
        amount: Double,
        type: String,
        fundSource: String,
        category: String,
        volume: Double = 1.0,
        unitName: String = "buah",
        unitPrice: Double = 0.0,
        notes: String,
        receiptUri: String?,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            if (amount <= 0) {
                _eventFlow.emit(UiEvent.ShowToast("Nominal transaksi harus lebih besar dari 0"))
                return@launch
            }

            // Pengeluaran kas oleh Bendahara membutuhkan verifikasi & persetujuan Kepala Sekolah
            val requiresApproval = (type == "PENGELUARAN") && currentRole.value == UserRole.BENDAHARA
            val approvalStatus = if (requiresApproval) "PENDING_APPROVAL" else "VERIFIED"

            val activeUserName = userSession.value.userName.trim()
            val roleLabel = when (currentRole.value) {
                UserRole.BENDAHARA -> {
                    if (activeUserName.isNotBlank() && !activeUserName.equals("Bendahara", ignoreCase = true)) {
                        "Bendahara ($activeUserName)"
                    } else {
                        "Bendahara"
                    }
                }
                UserRole.KEPALA_SEKOLAH -> {
                    if (activeUserName.isNotBlank() && !activeUserName.equals("Kepala Sekolah", ignoreCase = true)) {
                        "Kepala Sekolah ($activeUserName)"
                    } else {
                        "Kepala Sekolah"
                    }
                }
            }

            val newTx = TransactionEntity(
                title = title.ifBlank { "Transaksi Kas" },
                amount = amount,
                type = type,
                fundSource = fundSource,
                category = category,
                volume = volume,
                unitName = unitName,
                unitPrice = unitPrice,
                date = date,
                notes = notes,
                receiptUri = receiptUri,
                approvalStatus = approvalStatus,
                recordedByRole = roleLabel
            )

            val insertedId = repository.insertTransaction(newTx)

            // Trigger system notification if fund source is NOT secret
            val isFundSecret = customFundSources.value.firstOrNull { it.name.equals(fundSource, ignoreCase = true) }?.isSecret ?: false
            if (!isFundSecret) {
                com.example.util.AppNotificationHelper.showTransactionNotification(
                    context = getApplication(),
                    type = type,
                    title = title.ifBlank { "Transaksi Kas" },
                    amount = amount,
                    fundSource = fundSource,
                    recordedBy = roleLabel
                )
            }

            val successMsg = if (requiresApproval) {
                "Pengeluaran kas dicatat! Menunggu verifikasi & persetujuan Kepala Sekolah."
            } else {
                "Transaksi kas berhasil dicatat & disinkronkan!"
            }
            _eventFlow.emit(UiEvent.ShowToast(successMsg))

            // Trigger background auto sync to Google Sheets
            triggerAutoSyncIfConfigured()
        }
    }

    fun approveTransaction(transactionId: Int) {
        if (currentRole.value != UserRole.KEPALA_SEKOLAH) {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("🔒 Akses Ditolak: Hanya Kepala Sekolah yang berwenang menyetujui transaksi!"))
            }
            return
        }
        viewModelScope.launch {
            repository.updateApprovalStatus(transactionId, "VERIFIED")
            _eventFlow.emit(UiEvent.ShowToast("Transaksi berhasil diverifikasi & disetujui oleh Kepala Sekolah!"))
            triggerAutoSyncIfConfigured()
        }
    }

    fun rejectTransaction(transactionId: Int) {
        if (currentRole.value != UserRole.KEPALA_SEKOLAH) {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("🔒 Akses Ditolak: Hanya Kepala Sekolah yang berwenang menolak transaksi!"))
            }
            return
        }
        viewModelScope.launch {
            repository.updateApprovalStatus(transactionId, "DITOLAK")
            _eventFlow.emit(UiEvent.ShowToast("Transaksi telah ditolak oleh Kepala Sekolah"))
            triggerAutoSyncIfConfigured()
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            _eventFlow.emit(UiEvent.ShowToast("Transaksi berhasil diperbarui"))
            triggerAutoSyncIfConfigured()
        }
    }

    fun syncNowSilently() {
        viewModelScope.launch {
            try {
                val url = googleSheetsUrl.value.trim()
                if (url.isNotBlank() && !url.contains("docs.google.com/spreadsheets")) {
                    val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
                    val fetchResult = repo?.fetchDetailedFromGoogleSheets(url)
                    val sources = fetchResult?.getOrNull()?.fundSources ?: emptyList()
                    if (sources.isNotEmpty()) {
                        mergeCustomFundSources(sources)
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            _eventFlow.emit(UiEvent.ShowToast("Transaksi berhasil dihapus"))
            triggerAutoSyncIfConfigured()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllTransactions()
            _eventFlow.emit(UiEvent.ShowToast("Semua data transaksi & saldo berhasil dibersihkan (Rp 0)!"))
            triggerAutoSyncIfConfigured()
        }
    }

    fun backupData(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Memulai pencadangan data..."))
            val backupData = com.example.data.model.AppBackupData(
                timestamp = System.currentTimeMillis(),
                schoolProfile = schoolProfile.value,
                transactions = allTransactions.value
            )
            val success = BackupRestoreHelper.backupDataToUri(context, uri, backupData)
            if (success) {
                _eventFlow.emit(UiEvent.ShowToast("Pencadangan Berhasil Disimpan!"))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Gagal melakukan pencadangan data."))
            }
        }
    }

    fun restoreData(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.ShowToast("Membaca file cadangan..."))
            val backupData = BackupRestoreHelper.restoreDataFromUri(context, uri)
            if (backupData != null) {
                // Update profile
                updateSchoolProfile(backupData.schoolProfile)
                
                // Clear existing transactions and insert new ones
                repository.deleteAllTransactions()
                backupData.transactions.forEach { t ->
                    // Reset ID to avoid constraint failures, or keep existing IDs to preserve order
                    val newT = t.copy(id = 0)
                    repository.insertTransaction(newT)
                }
                
                _eventFlow.emit(UiEvent.ShowToast("Restorasi Berhasil! Data telah dipulihkan."))
                triggerAutoSyncIfConfigured()
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Gagal memulihkan data. File tidak valid atau rusak."))
            }
        }
    }

    fun exportToCsv(context: android.content.Context, selectedFundSource: String? = null) {
        viewModelScope.launch {
            val all = allTransactions.value
            val secretSources = customFundSources.value.filter { it.isSecret }.map { it.name }.toSet()

            // Jika pos dana yang dipilih adalah rahasia, pastikan hanya Kepsek yang bisa ekspor
            if (selectedFundSource != null && selectedFundSource in secretSources && currentRole.value != UserRole.KEPALA_SEKOLAH) {
                _eventFlow.emit(UiEvent.ShowToast("🔒 Akses Ditolak: Pos Sumber Dana Rahasia hanya dapat diekspor oleh Kepala Sekolah!"))
                return@launch
            }

            val filteredList = if (selectedFundSource != null) {
                all.filter { it.fundSource == selectedFundSource }
            } else {
                if (currentRole.value == UserRole.KEPALA_SEKOLAH) all else all.filter { it.fundSource !in secretSources }
            }

            val result = bkuSyncRepository?.exportToCsv(context, filteredList)
            if (result?.isFailure == true) {
                _eventFlow.emit(UiEvent.ShowToast("Gagal ekspor CSV: ${result.exceptionOrNull()?.localizedMessage}"))
            } else if (result?.isSuccess == true) {
                val label = selectedFundSource?.let { " untuk pos $it" } ?: ""
                _eventFlow.emit(UiEvent.ShowToast("Berhasil ekspor CSV BKU$label ke Memori HP"))
            }
        }
    }

    fun syncAllToGoogleSheets() {
        viewModelScope.launch {
            val url = googleSheetsUrl.value.trim()
            if (url.isBlank()) {
                _eventFlow.emit(UiEvent.ShowToast("Silakan masukkan URL Google Apps Script Web App terlebih dahulu."))
                return@launch
            }
            if (url.contains("docs.google.com/spreadsheets")) {
                _eventFlow.emit(UiEvent.ShowToast("⚠️ Gunakan URL Web App (script.google.com/.../exec), bukan URL Spreadsheet!"))
                return@launch
            }

            val repo = bkuSyncRepository as? GoogleSheetsSyncRepository

            // Langkah 1: Tarik data terbaru dari Google Sheets terlebih dahulu agar pengeluaran dari perangkat lain tidak tertimpa
            val fetchResult = repo?.fetchDetailedFromGoogleSheets(url)
            val fetchedSources = fetchResult?.getOrNull()?.fundSources ?: emptyList()
            if (fetchedSources.isNotEmpty()) {
                mergeCustomFundSources(fetchedSources)
            }

            // Langkah 2: Ambil semua transaksi lokal terbaru (sudah ter-merge dengan Google Sheets)
            val all = repository.allTransactions.first()
            val secretSources = customFundSources.value.filter { it.isSecret }.map { it.name }.toSet()
            val nonSecretTransactions = all.filter { it.fundSource !in secretSources }
            val nonSecretFundNames = customFundSources.value.filter { !it.isSecret }.map { it.name }

            if (nonSecretTransactions.isNotEmpty()) {
                val result = repo?.syncToGoogleSheets(url, nonSecretTransactions, nonSecretFundNames) ?: bkuSyncRepository?.syncToGoogleSheets(url, nonSecretTransactions)
                if (result?.isSuccess == true) {
                    _eventFlow.emit(UiEvent.ShowToast("Sinkronisasi berhasil! ${nonSecretTransactions.size} transaksi terhubung dengan Google Sheets."))
                } else if (result?.isFailure == true) {
                    _eventFlow.emit(UiEvent.ShowToast("Gagal Sync Google Sheets: ${result.exceptionOrNull()?.localizedMessage}"))
                }
            } else {
                _eventFlow.emit(UiEvent.ShowToast("Sinkronisasi selesai. Belum ada transaksi kas publik untuk disinkronkan."))
            }
        }
    }

    fun syncRkasToGoogleSheets(rkasItems: List<com.example.data.model.RkasItem>) {
        viewModelScope.launch {
            val url = googleSheetsUrl.value
            if (url.isBlank()) {
                _eventFlow.emit(UiEvent.ShowToast("Silakan atur URL Google Sheets Web App di menu Laporan/Dashboard terlebih dahulu."))
                return@launch
            }
            val secretSources = customFundSources.value.filter { it.isSecret }.map { it.name }.toSet()
            // Filter out secret fund RKAS items from syncing to Google Sheets
            val nonSecretRkas = rkasItems.filter { it.fundSource !in secretSources }
            val nonSecretFundNames = customFundSources.value.filter { !it.isSecret }.map { it.name }

            val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
            val result = repo?.syncRkasToGoogleSheets(url, nonSecretRkas, nonSecretFundNames)
            if (result?.isSuccess == true) {
                _eventFlow.emit(UiEvent.ShowToast("Berhasil ekspor ${result.getOrNull()} item RKAS ke Tab masing-masing Sumber Dana di Google Sheets!"))
            } else if (result?.isFailure == true) {
                _eventFlow.emit(UiEvent.ShowToast("Gagal Sync RKAS: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun fetchRkasFromGoogleSheets(onSuccess: (List<com.example.data.model.RkasItem>) -> Unit) {
        viewModelScope.launch {
            val url = googleSheetsUrl.value
            if (url.isBlank()) {
                _eventFlow.emit(UiEvent.ShowToast("Silakan atur URL Google Sheets Web App terlebih dahulu."))
                return@launch
            }
            val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
            val result = repo?.fetchRkasFromGoogleSheets(url)
            if (result?.isSuccess == true) {
                val items = result.getOrNull() ?: emptyList()
                if (items.isNotEmpty()) {
                    val sourcesFromRkas = items.map { it.fundSource.trim() }.filter { it.isNotBlank() }.distinct()
                    if (sourcesFromRkas.isNotEmpty()) {
                        mergeCustomFundSources(sourcesFromRkas)
                    }
                    onSuccess(items)
                    _eventFlow.emit(UiEvent.ShowToast("Berhasil menarik ${items.size} item RKAS revisi dari Google Sheets!"))
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("Tab RKAS di Google Sheets belum memiliki data."))
                }
            } else if (result?.isFailure == true) {
                _eventFlow.emit(UiEvent.ShowToast("Gagal Tarik RKAS: ${result.exceptionOrNull()?.localizedMessage}"))
            }
        }
    }

    fun fetchFromGoogleSheets() {
        viewModelScope.launch {
            val url = googleSheetsUrl.value
            if (url.isBlank()) {
                _eventFlow.emit(UiEvent.ShowToast("Silakan masukkan URL Google Apps Script Web App terlebih dahulu."))
                return@launch
            }
            val repo = bkuSyncRepository as? GoogleSheetsSyncRepository
            val detailedResult = repo?.fetchDetailedFromGoogleSheets(url)
            if (detailedResult?.isSuccess == true) {
                val syncData = detailedResult.getOrNull()
                val txCount = syncData?.transactions?.size ?: 0
                val fetchedSources = syncData?.fundSources ?: emptyList()

                if (fetchedSources.isNotEmpty()) {
                    mergeCustomFundSources(fetchedSources)
                }

                _eventFlow.emit(UiEvent.ShowToast("Berhasil mengunduh $txCount transaksi & ${customFundSources.value.size} Pos Sumber Dana dari Google Sheets"))
            } else {
                val fallbackResult = bkuSyncRepository?.fetchFromGoogleSheets(url)
                if (fallbackResult?.isSuccess == true) {
                    val list = fallbackResult.getOrNull() ?: emptyList()
                    val sources = list.map { it.fundSource.trim() }.filter { it.isNotBlank() }.distinct()
                    if (sources.isNotEmpty()) {
                        mergeCustomFundSources(sources)
                    }
                    _eventFlow.emit(UiEvent.ShowToast("Berhasil mengunduh ${list.size} transaksi dari Google Sheets"))
                } else {
                    _eventFlow.emit(UiEvent.ShowToast("Gagal mengunduh Google Sheets: ${fallbackResult?.exceptionOrNull()?.localizedMessage ?: detailedResult?.exceptionOrNull()?.localizedMessage}"))
                }
            }
        }
    }
}
