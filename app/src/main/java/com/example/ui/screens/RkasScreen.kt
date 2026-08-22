package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.repository.GoogleSheetsSyncRepository
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.platform.LocalUriHandler
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundSourceDefaults
import com.example.data.model.RkasDefaults
import com.example.data.model.RkasHeader
import com.example.data.model.RkasItem
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Halaman Rencana Belanja (Kertas Kerja RKAS) Per Buku Kas
 * Memungkinkan penyusunan, penyaringan, dan penginputan Rencana Belanja yang terintegrasi
 * secara rinci dengan setiap Buku Kas / Sumber Dana (e.g. Koperasi Guru, BOS Reguler, BOS Kinerja, Dana Komite, dll).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RkasScreen(
    fundSources: List<String> = FundSourceDefaults.SOURCES,
    onSyncRkasToSheets: (List<RkasItem>) -> Unit = {},
    onFetchRkasFromSheets: ((List<RkasItem>) -> Unit) -> Unit = {},
    googleSheetsUrl: String = "",
    spreadsheetDocUrl: String = "",
    onSaveGoogleSheetsUrl: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var rkasHeader by remember { mutableStateOf(RkasDefaults.HEADER) }
    
    // Master state item Rencana Belanja
    var rkasItems by remember { mutableStateOf(RkasDefaults.ITEMS) }
    var isPullingFromSheets by remember { mutableStateOf(false) }

    // Opsi Buku Kas
    val allBukuKasOptions = remember(fundSources) {
        fundSources.ifEmpty { FundSourceDefaults.SOURCES }
    }

    // Filter Buku Kas / Sumber Dana
    var selectedBukuKasFilter by remember(allBukuKasOptions) { 
        mutableStateOf(allBukuKasOptions.firstOrNull() ?: "BOS Reguler") 
    }

    // Persistensi Anggaran per Buku Kas via SharedPreferences
    val prefs = remember { context.getSharedPreferences("rkas_budgets_prefs", Context.MODE_PRIVATE) }

    fun loadSavedBudgets(): Map<String, Double> {
        val defaultBudgets = mapOf(
            "BOS Reguler" to 60160000.0,
            "BOS Kinerja" to 15000000.0,
            "Dana Komite" to 10000000.0,
            "Koperasi Guru" to 8000000.0,
            "Hibah / Lainnya" to 5000000.0
        )
        val result = defaultBudgets.toMutableMap()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("budget_")) {
                val fund = key.removePrefix("budget_")
                val amount = when (value) {
                    is Float -> value.toDouble()
                    is Long -> value.toDouble()
                    is Int -> value.toDouble()
                    is Double -> value
                    is String -> value.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
                result[fund] = amount
            }
        }
        return result
    }

    // Map Anggaran / Revenue per Buku Kas (Tersimpan Permanen)
    var fundSourceBudgets by remember {
        mutableStateOf(loadSavedBudgets())
    }

    fun saveBudgetForFund(fund: String, amount: Double) {
        fundSourceBudgets = fundSourceBudgets + (fund to amount)
        prefs.edit().putString("budget_$fund", amount.toString()).apply()
    }

    var searchQuery by remember { mutableStateOf("") }

    // Dialog state
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<RkasItem?>(null) }
    var itemToDelete by remember { mutableStateOf<RkasItem?>(null) }
    var showResetRkasDialog by remember { mutableStateOf(false) }
    var showEditRevenueDialog by remember { mutableStateOf(false) }

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    // Filter list item belanja berdasarkan Buku Kas & Search Query
    val filteredItems = remember(rkasItems, selectedBukuKasFilter, searchQuery) {
        rkasItems.filter { item ->
            val matchesBukuKas = item.fundSource.equals(selectedBukuKasFilter, ignoreCase = true) ||
                    (selectedBukuKasFilter == "BOS Reguler" && item.fundSource.isBlank())

            val matchesSearch = searchQuery.isBlank() ||
                    item.uraian.contains(searchQuery, ignoreCase = true) ||
                    item.kodeRekening.contains(searchQuery, ignoreCase = true) ||
                    item.fundSource.contains(searchQuery, ignoreCase = true)

            matchesBukuKas && matchesSearch
        }
    }

    // Perhitungan Anggaran & Belanja Per Buku Kas
    val currentAllocatedRevenue = fundSourceBudgets[selectedBukuKasFilter] ?: 0.0

    val totalPlannedExpenditure = filteredItems.filter { !it.isHeader }.sumOf { it.jumlah }
    val remainingBalance = currentAllocatedRevenue - totalPlannedExpenditure

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    itemToEdit = null
                    showAddItemDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Belanja")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Belanja", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Selector Tab Buku Kas (Sumber Dana Filter Bar)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PILIH SUMBER DANA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = selectedBukuKasFilter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Chips Pilihan Buku Kas
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allBukuKasOptions) { option ->
                                val isSelected = selectedBukuKasFilter == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBukuKasFilter = option },
                                    label = {
                                        Text(
                                            text = option,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = Color(0xFFF1F5F9),
                                        labelColor = Color(0xFF334155)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Official Header & Financial Summary Card for Selected Buku Kas
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RENCANA KEGIATAN & BELANJA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            ),
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = selectedBukuKasFilter.uppercase(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Financial Overview Statistics (Anggaran Kas, Rencana Belanja, Sisa Anggaran) - Vertikal
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RkasSummaryCard(
                                title = "Anggaran Kas",
                                amount = currencyFormat.format(currentAllocatedRevenue),
                                color = Color(0xFF059669),
                                modifier = Modifier.fillMaxWidth()
                            )
                            RkasSummaryCard(
                                title = "Rencana Belanja",
                                amount = currencyFormat.format(totalPlannedExpenditure),
                                color = Color(0xFF0284C7),
                                modifier = Modifier.fillMaxWidth()
                            )
                            RkasSummaryCard(
                                title = "Sisa Anggaran",
                                amount = currencyFormat.format(remainingBalance),
                                color = if (remainingBalance >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Balance / Edit Revenue Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (remainingBalance >= 0) Color(0xFFEFF6FF) else Color(0xFFFEF2F2))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (remainingBalance >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (remainingBalance >= 0) "Status: Sesuai Anggaran" else "Status: Melebihi Anggaran",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingBalance >= 0) Color(0xFF1E40AF) else Color(0xFF991B1B)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (remainingBalance >= 0) Color(0xFFDBEAFE) else Color(0xFFFEE2E2),
                                modifier = Modifier.clickable { showEditRevenueDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Atur",
                                        tint = if (remainingBalance >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Atur",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingBalance >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Action Buttons & Export / Sync Bar
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Prominent Direct Open & Edit in Google Sheets Button
                        val directOpenUrl = if (spreadsheetDocUrl.isNotBlank()) spreadsheetDocUrl else googleSheetsUrl
                        Button(
                            onClick = {
                                if (directOpenUrl.isBlank()) {
                                    Toast.makeText(context, "Silakan masukkan Link Google Sheets pada menu 'Pengaturan Akun & Sekolah'", Toast.LENGTH_LONG).show()
                                } else {
                                    try {
                                        val targetUrl = if (!directOpenUrl.startsWith("http://") && !directOpenUrl.startsWith("https://")) {
                                            "https://$directOpenUrl"
                                        } else {
                                            directOpenUrl
                                        }
                                        uriHandler.openUri(targetUrl)
                                        Toast.makeText(context, "Membuka Google Sheets...", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal membuka link. Silakan periksa Link Google Sheets Anda.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "📊 Buka & Edit di Google Sheets",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (directOpenUrl.isNotBlank()) "Buka spreadsheet di browser / app Google Sheets untuk edit langsung" else "Link Spreadsheet belum diset (Klik untuk mengatur)",
                                        fontSize = 10.sp,
                                        color = Color(0xFFD1FAE5)
                                    )
                                }
                            }
                        }

                        // Auto-Sync Status Bar
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (googleSheetsUrl.isNotBlank()) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, if (googleSheetsUrl.isNotBlank()) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (googleSheetsUrl.isNotBlank()) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isPullingFromSheets) "Sedang mengambil data dari Google Sheets..." else if (googleSheetsUrl.isNotBlank()) "Koneksi Google Sheets Aktif" else "Koneksi Google Sheets Nonaktif • Atur URL Web App terlebih dahulu",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (googleSheetsUrl.isNotBlank()) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                            }
                        }

                        // Sync Action Buttons: Ambil dari Sheet & Kirim ke Sheet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (googleSheetsUrl.isNotBlank()) {
                                        isPullingFromSheets = true
                                        onFetchRkasFromSheets { fetchedItems ->
                                            isPullingFromSheets = false
                                            if (fetchedItems.isNotEmpty()) {
                                                rkasItems = fetchedItems
                                                Toast.makeText(context, "Berhasil memuat ${fetchedItems.size} item RKAS dari Google Sheets", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Tidak ada data RKAS ditemukan di spreadsheet", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "URL Database Google Sheets belum diatur", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPullingFromSheets) "Memuat..." else "Ambil dari Sheet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (googleSheetsUrl.isNotBlank()) {
                                        onSyncRkasToSheets(rkasItems)
                                        Toast.makeText(context, "Data RKAS sedang dikirim ke Google Sheets", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "URL Database Google Sheets belum diatur", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kirim ke Sheet", fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportRkasToCsv(context, rkasHeader, rkasItems) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Ekspor CSV", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { showResetRkasDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("Reset Data", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 4. Search Filter
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari item belanja / uraian / kode...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. Daftar Item Rencana Belanja (Per Buku Kas Card View & Table)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAFTAR RENCANA BELANJA",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${filteredItems.count { !it.isHeader }} Item Rincian",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Belum Ada Rencana Belanja untuk ${selectedBukuKasFilter}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tekan tombol (+ Tambah Belanja) di bawah untuk menginput barang/kegiatan belanja khusus ${selectedBukuKasFilter}.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredItems) { item ->
                    RkasItemCard(
                        item = item,
                        currencyFormat = currencyFormat,
                        onEdit = {
                            itemToEdit = item
                            showAddItemDialog = true
                        },
                        onDelete = {
                            itemToDelete = item
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Modal Add / Edit Rencana Belanja Item
    if (showAddItemDialog) {
        val editingItem = itemToEdit
        var inputBukuKas by remember {
            mutableStateOf(
                editingItem?.fundSource?.takeIf { fundSources.contains(it) }
                    ?: (if (fundSources.contains(selectedBukuKasFilter)) selectedBukuKasFilter else (fundSources.firstOrNull() ?: "BOS Reguler"))
            )
        }
        var expandedBukuKasDropdown by remember { mutableStateOf(false) }

        var inputUraian by remember { mutableStateOf(editingItem?.uraian ?: "") }
        var inputVolume by remember { mutableStateOf(editingItem?.volume ?: "1") }
        var inputSatuan by remember { mutableStateOf(editingItem?.satuan ?: "buah") }
        var inputTarif by remember { mutableStateOf(editingItem?.tarifHarga?.toLong()?.toString() ?: "") }

        val volDouble = inputVolume.toDoubleOrNull() ?: 1.0
        val tarifDouble = inputTarif.toDoubleOrNull() ?: 0.0
        val calculatedJumlah = volDouble * tarifDouble

        AlertDialog(
            onDismissRequest = {
                showAddItemDialog = false
                itemToEdit = null
            },
            title = {
                Text(
                    text = if (editingItem == null) "Tambah Rencana Belanja (${inputBukuKas})" else "Edit Rencana Belanja",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Selector Buku Kas / Sumber Dana (Silo Keuangan)
                    Text("Buku Kas / Sumber Dana:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedBukuKasDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(inputBukuKas, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedBukuKasDropdown,
                            onDismissRequest = { expandedBukuKasDropdown = false }
                        ) {
                            fundSources.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source, fontSize = 12.sp) },
                                    onClick = {
                                        inputBukuKas = source
                                        expandedBukuKasDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputUraian,
                        onValueChange = { inputUraian = it },
                        label = { Text("Uraian Barang / Belanja Kegiatan", fontSize = 11.sp) },
                        placeholder = { Text("mis. Belanja ATK / Konsumsi Rapat", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = inputVolume,
                            onValueChange = { inputVolume = it },
                            label = { Text("Volume", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = inputSatuan,
                            onValueChange = { inputSatuan = it },
                            label = { Text("Satuan", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Pilihan Cepat Satuan
                    val rkasUnits = listOf("buah", "rim", "lembar", "kotak", "siswa", "paket", "set", "lusin", "triwulan", "tahap", "orang", "bulan", "hari", "kegiatan")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(rkasUnits) { u ->
                            val isSel = inputSatuan.equals(u, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSel) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { inputSatuan = u }
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputTarif,
                        onValueChange = { inputTarif = it },
                        label = { Text("Tarif / Harga Satuan (Rp)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Display Calculated Total
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Estimasi Belanja:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                            Text(
                                currencyFormat.format(calculatedJumlah),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF047857)
                            )
                        }
                    }

                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingItem == null) {
                            // Create New Rencana Belanja
                            val newId = (rkasItems.maxOfOrNull { it.id } ?: 0) + 1
                            val newItem = RkasItem(
                                id = newId,
                                noUrut = newId.toString(),
                                kodeRekening = "",
                                kodeProgram = "",
                                uraian = inputUraian.ifBlank { "Rencana Belanja $inputBukuKas" },
                                volume = inputVolume,
                                satuan = inputSatuan,
                                tarifHarga = tarifDouble,
                                jumlah = calculatedJumlah,
                                isHeader = false,
                                fundSource = inputBukuKas
                            )
                            rkasItems = rkasItems + newItem
                            Toast.makeText(context, "Berhasil menambah belanja di Buku Kas: $inputBukuKas", Toast.LENGTH_SHORT).show()
                        } else {
                            // Update Existing Item
                            val updatedItems = rkasItems.map { old ->
                                if (old.id == editingItem.id) {
                                    old.copy(
                                        uraian = inputUraian.ifBlank { old.uraian },
                                        volume = inputVolume,
                                        satuan = inputSatuan,
                                        tarifHarga = tarifDouble,
                                        jumlah = calculatedJumlah,
                                        fundSource = inputBukuKas
                                    )
                                } else old
                            }
                            rkasItems = updatedItems
                            Toast.makeText(context, "Rencana belanja berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                        }

                        showAddItemDialog = false
                        itemToEdit = null
                    }
                ) {
                    Text(if (editingItem == null) "Simpan ke $inputBukuKas" else "Perbarui")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddItemDialog = false
                        itemToEdit = null
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Rencana Belanja", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin menghapus item '${target.uraian}' dari Buku Kas ${target.fundSource}?") },
            confirmButton = {
                Button(
                    onClick = {
                        rkasItems = rkasItems.filterNot { it.id == target.id }
                        itemToDelete = null
                        Toast.makeText(context, "Item belanja berhasil dihapus", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Reset / Mulai RKAS Baru dari Nol
    if (showResetRkasDialog) {
        AlertDialog(
            onDismissRequest = { showResetRkasDialog = false },
            title = { Text("Kosongkan Rencana Belanja", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Apakah Anda ingin mengosongkan seluruh daftar rencana belanja (Rp 0) untuk menyusun anggaran sekolah dari awal?",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetRkasDialog = false
                        rkasItems = emptyList()
                        Toast.makeText(context, "Seluruh daftar belanja dikosongkan (Rp 0).", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Kosongkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetRkasDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Edit Revenue & Anggaran Kas per Buku Kas
    if (showEditRevenueDialog) {
        var selectedKasToEdit by remember(selectedBukuKasFilter) { 
            mutableStateOf(if (fundSources.contains(selectedBukuKasFilter)) selectedBukuKasFilter else (fundSources.firstOrNull() ?: "BOS Reguler")) 
        }
        var inputRevenueAmount by remember(selectedKasToEdit, fundSourceBudgets) { 
            mutableStateOf((fundSourceBudgets[selectedKasToEdit] ?: 10000000.0).toLong().toString()) 
        }
        var expandedKasDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditRevenueDialog = false },
            title = { Text("Atur Anggaran", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pilih Buku Kas & tentukan total alokasi penerimaan/anggaran:", fontSize = 12.sp)

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedKasDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedKasToEdit, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedKasDropdown,
                            onDismissRequest = { expandedKasDropdown = false }
                        ) {
                            fundSources.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 12.sp) },
                                    onClick = {
                                        selectedKasToEdit = option
                                        inputRevenueAmount = (fundSourceBudgets[option] ?: 10000000.0).toLong().toString()
                                        expandedKasDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputRevenueAmount,
                        onValueChange = { inputRevenueAmount = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Total Anggaran Penerimaan (Rp)", fontSize = 11.sp) },
                        placeholder = { Text("Contoh: 75000000", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountDouble = inputRevenueAmount.toDoubleOrNull() ?: 0.0
                        saveBudgetForFund(selectedKasToEdit, amountDouble)
                        showEditRevenueDialog = false
                        Toast.makeText(context, "Anggaran Kas $selectedKasToEdit berhasil disimpan ke ${currencyFormat.format(amountDouble)}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditRevenueDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

/**
 * Card Component untuk Item Rencana Belanja
 */
@Composable
fun RkasItemCard(
    item: RkasItem,
    currencyFormat: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    if (item.isHeader) {
        // Display Section Headers
        val headerBg = when (item.headerLevel) {
            1 -> Color(0xFFF1F5F9)
            2 -> Color(0xFFECFDF5)
            else -> Color(0xFFFEF9C3)
        }
        val headerTextColor = when (item.headerLevel) {
            1 -> Color(0xFF0F172A)
            2 -> Color(0xFF047857)
            else -> Color(0xFF854D0E)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = headerBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.kodeProgram.isNotBlank()) {
                        Text(
                            text = item.kodeProgram,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = headerTextColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.uraian,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = currencyFormat.format(item.jumlah).replace(",00", ""),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = headerTextColor
                )
            }
        }
    } else {
        // Display Regular Belanja Item
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge Buku Kas / Sumber Dana
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = item.fundSource.ifBlank { "BOS Reguler" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.uraian,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val cleanSatuan = item.satuan.trim().ifBlank { "buah" }
                        Text(
                            text = "${item.volume} $cleanSatuan @ ${currencyFormat.format(item.tarifHarga).replace(",00", "")}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currencyFormat.format(item.jumlah).replace(",00", ""),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RkasSummaryCard(
    title: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
            Text(text = amount, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Helper to export full RKAS document as CSV for Google Sheets
private fun exportRkasToCsv(context: Context, header: RkasHeader, items: List<RkasItem>) {
    try {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "RKAS_Rencana_Belanja_${dateFormat.format(Date())}.csv"
        
        // Simpan ke direktori Download penyimpanan internal HP jika tersedia
        val publicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
            File(publicDir, fileName)
        } else {
            File(context.cacheDir, fileName)
        }

        val csvBuilder = StringBuilder()
        csvBuilder.append("RENCANA KEGIATAN DAN BELANJA\n")
        csvBuilder.append("Nama Sekolah: ${header.schoolName}, NPSN: ${header.npsn}\n\n")

        csvBuilder.append("No,Buku Kas,Uraian Belanja,Volume,Satuan,Tarif Satuan (Rp),Jumlah Pagu (Rp)\n")

        items.forEachIndexed { index, item ->
            val escapedUraian = "\"${item.uraian.replace("\"", "\"\"")}\""
            val escapedKas = "\"${item.fundSource.replace("\"", "\"\"")}\""
            csvBuilder.append("${index + 1},$escapedKas,$escapedUraian,\"${item.volume}\",\"${item.satuan}\",${item.tarifHarga.toLong()},${item.jumlah.toLong()}\n")
        }

        file.writeText(csvBuilder.toString(), Charsets.UTF_8)

        // Beritahukan Android Media Scanner agar file langsung muncul di File Manager HP (Storage Internal)
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("text/csv")
            ) { _, _ -> }
        } catch (ignored: Exception) {}

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Dokumen Rencana Belanja Sekolah ${header.schoolName}")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Simpan / Buka File CSV RKAS (Tersimpan di Download)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        Toast.makeText(context, "Dokumen RKAS tersimpan di Download HP: $fileName", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal ekspor CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareRkasSummary(
    context: Context,
    header: RkasHeader,
    totalRevenue: Double,
    totalExpenditure: Double,
    bukuKasName: String
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    val summaryText = """
        *RENCANA KEGIATAN & BELANJA*
        🏫 *${header.schoolName}* (NPSN: ${header.npsn})
        📍 Buku Kas / Sumber Dana: *${bukuKasName}*
        
        💰 *ANGGARAN & REKAPITULASI*
        - Anggaran Kas: ${currencyFormat.format(totalRevenue)}
        - Total Rencana Belanja: ${currencyFormat.format(totalExpenditure)}
        - Sisa Anggaran: ${currencyFormat.format(totalRevenue - totalExpenditure)}
        
        ✅ Status: Terverifikasi & Sesuai BKU
        
        Kepala Sekolah: ${header.kepsekName}
        Bendahara: ${header.bendaharaName}
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Ringkasan Rencana Belanja Kas ${bukuKasName}")
        putExtra(Intent.EXTRA_TEXT, summaryText)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan Ringkasan Belanja Kas"))
}
