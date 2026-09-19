package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundSourceDefaults
import com.example.network.ParsedTransactionResult
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryScreen(
    initialParsedResult: ParsedTransactionResult?,
    userSession: com.example.data.model.UserAccountSession = com.example.data.model.UserAccountSession(),
    currentRole: com.example.data.model.UserRole = com.example.data.model.UserRole.BENDAHARA,
    fundSources: List<String> = FundSourceDefaults.SOURCES,
    onBack: () -> Unit = {},
    onSubmitTransaction: (title: String, amount: Double, type: String, fundSource: String, category: String, volume: Double, unitName: String, unitPrice: Double, notes: String, receiptUri: String?, date: Long) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(initialParsedResult?.title ?: "") }
    var type by remember { mutableStateOf(initialParsedResult?.type ?: "PENGELUARAN") }
    var fundSource by remember(fundSources) { mutableStateOf(initialParsedResult?.fundSource?.takeIf { it in fundSources } ?: fundSources.firstOrNull() ?: "BOS Reguler") }
    var notes by remember { mutableStateOf(initialParsedResult?.notes ?: "") }
    
    // Volume & Harga Satuan input states
    var volumeQtyText by remember { mutableStateOf("1") }
    var unitText by remember { mutableStateOf("buah") }
    var unitPriceText by remember {
        mutableStateOf(
            if (initialParsedResult != null && initialParsedResult.amount > 0) {
                initialParsedResult.amount.toLong().toString()
            } else {
                ""
            }
        )
    }

    var kwitansiOption by remember { mutableStateOf("Ada") }
    var notaOption by remember { mutableStateOf("Ada") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var expandedFundDropdown by remember { mutableStateOf(false) }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
    }

    // Numeric parsing for Volume & Harga Satuan
    val parsedVolume by remember(volumeQtyText) {
        derivedStateOf {
            val cleaned = volumeQtyText.replace(",", ".").trim()
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    val parsedUnitPrice by remember(unitPriceText) {
        derivedStateOf {
            val cleaned = unitPriceText.replace("[^0-9]".toRegex(), "").trim()
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    // Hasil perkalian: Volume x Harga Satuan
    val calculatedTotalAmount by remember(parsedVolume, parsedUnitPrice) {
        derivedStateOf {
            parsedVolume * parsedUnitPrice
        }
    }

    val commonUnits = listOf("buah", "rim", "lembar", "kotak", "siswa", "paket", "set", "lusin", "triwulan", "tahap", "orang", "bulan", "hari", "kegiatan")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pencatatan Kas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Input BKU Real-Time & Transparan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Petugas Pencatat Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (currentRole == com.example.data.model.UserRole.BENDAHARA) Color(0xFFEFF6FF) else Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, if (currentRole == com.example.data.model.UserRole.BENDAHARA) Color(0xFFBFDBFE) else Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (currentRole == com.example.data.model.UserRole.BENDAHARA) Icons.Default.Payments else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (currentRole == com.example.data.model.UserRole.BENDAHARA) Color(0xFF1D4ED8) else Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Petugas Pencatat Kas: ${userSession.userName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentRole == com.example.data.model.UserRole.BENDAHARA) Color(0xFF1E40AF) else Color(0xFF92400E)
                        )
                        Text(
                            text = if (currentRole == com.example.data.model.UserRole.BENDAHARA)
                                "Otoritas Bendahara • Semua pengeluaran wajib verifikasi & persetujuan Kepala Sekolah"
                            else
                                "Otoritas Kepala Sekolah • Transaksi diverifikasi langsung",
                            fontSize = 10.sp,
                            color = if (currentRole == com.example.data.model.UserRole.BENDAHARA) Color(0xFF2563EB) else Color(0xFFB45309)
                        )
                    }
                }
            }

            // Transaction Type Toggle (Pengeluaran vs Pemasukan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (type == "PENGELUARAN") Color(0xFFDC2626) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { type = "PENGELUARAN" }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "PENGELUARAN",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (type == "PENGELUARAN") Color.White else Color.Gray
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (type == "PEMASUKAN") Color(0xFF059669) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { type = "PEMASUKAN" }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "PEMASUKAN",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (type == "PEMASUKAN") Color.White else Color.Gray
                            )
                        )
                    }
                }
            }

            // Tanggal Transaksi Card
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val selectedCal = Calendar.getInstance().apply {
                                set(year, month, dayOfMonth)
                            }
                            selectedDateMillis = selectedCal.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tanggal Transaksi: ${dateFormatter.format(Date(selectedDateMillis))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Title / Uraian Belanja
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Uraian / Deskripsi Transaksi *") },
                placeholder = {
                    Text(
                        if (type == "PEMASUKAN") "Contoh: Penerimaan Dana BOS Reguler Tahap 1"
                        else "Contoh: Pembelian Kertas HVS & ATK Kantor"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // =========================================================================
            // CARD PERHITUNGAN: HARGA SATUAN × VOLUME = HASIL TOTAL
            // =========================================================================
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (type == "PENGELUARAN") Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                ),
                border = BorderStroke(
                    1.dp,
                    if (type == "PENGELUARAN") Color(0xFFFECACA) else Color(0xFFBBF7D0)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (type == "PENGELUARAN") Color(0xFFDC2626) else Color(0xFF059669),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rincian (Harga Satuan x Volume)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (type == "PENGELUARAN") Color(0xFF991B1B) else Color(0xFF166534)
                            )
                        )
                    }

                    // 1. Input Harga Satuan (Rp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Harga Satuan (Rp) *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                        OutlinedTextField(
                            value = unitPriceText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }
                                unitPriceText = filtered
                            },
                            placeholder = { Text("Contoh: 50000") },
                            leadingIcon = {
                                Text("Rp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        if (parsedUnitPrice > 0) {
                            Text(
                                text = "Harga per satuan: ${currencyFormatter.format(parsedUnitPrice)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 2. Input Volume & Satuan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Input Volume (Banyaknya)
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Volume / Banyaknya *",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = volumeQtyText,
                                    onValueChange = { input ->
                                        // Allow digits and decimal dot/comma
                                        if (input.all { it.isDigit() || it == '.' || it == ',' }) {
                                            volumeQtyText = input
                                        }
                                    },
                                    placeholder = { Text("1") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )

                                // Quick Stepper - / +
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            val current = parsedVolume
                                            if (current > 1) {
                                                volumeQtyText = if ((current - 1) % 1.0 == 0.0) (current - 1).toInt().toString() else "%.2f".format(current - 1)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Remove, contentDescription = "Kurang 1", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            val current = parsedVolume
                                            val next = if (current <= 0) 1.0 else current + 1.0
                                            volumeQtyText = if (next % 1.0 == 0.0) next.toInt().toString() else "%.2f".format(next)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Add, contentDescription = "Tambah 1", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Input Satuan
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Satuan *",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.DarkGray
                            )
                            OutlinedTextField(
                                value = unitText,
                                onValueChange = { unitText = it },
                                placeholder = { Text("buah, rim...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }

                    // Quick Satuan Chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Pilihan Cepat Satuan:", fontSize = 10.sp, color = Color.Gray)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(commonUnits) { unit ->
                                val isSelected = unitText.equals(unit, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.secondary else Color.LightGray),
                                    modifier = Modifier.clickable { unitText = unit }
                                ) {
                                    Text(
                                        text = unit,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = if (type == "PENGELUARAN") Color(0xFFFECACA) else Color(0xFFBBF7D0))

                    // =================================================================
                    // HASIL PERKALIAN DISPLAY (ONLY NOMINAL Rp)
                    // =================================================================
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (type == "PENGELUARAN") Color(0xFFDC2626) else Color(0xFF059669),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currencyFormatter.format(calculatedTotalAmount),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Sumber Dana Dropdown (Buku Pembantu Kas)
            ExposedDropdownMenuBox(
                expanded = expandedFundDropdown,
                onExpandedChange = { expandedFundDropdown = !expandedFundDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = fundSource,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sumber Dana / Buku Kas *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFundDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedFundDropdown,
                    onDismissRequest = { expandedFundDropdown = false }
                ) {
                    fundSources.forEach { sourceOption ->
                        DropdownMenuItem(
                            text = { Text(sourceOption) },
                            onClick = {
                                fundSource = sourceOption
                                expandedFundDropdown = false
                            }
                        )
                    }
                }
            }

            // Catatan / Keterangan Toko
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Catatan / Nama Rekanan / No. Nota") },
                placeholder = { Text("Contoh: CV. Berkah Abadi, Toko Buku Sinar Jaya...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Kelengkapan Bukti (Kwitansi & Nota) Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Kelengkapan Bukti Transaksi",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Kwitansi
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Kwitansi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Ada", "Tidak").forEach { opt ->
                                    val isSel = (opt == "Ada" && kwitansiOption == "Ada") || (opt == "Tidak" && kwitansiOption != "Ada")
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { kwitansiOption = if (opt == "Ada") "Ada" else "Tidak Ada" }
                                    ) {
                                        Text(
                                            text = opt,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else Color.DarkGray,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Nota / Faktur
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Nota / Faktur", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Ada", "Tidak").forEach { opt ->
                                    val isSel = (opt == "Ada" && notaOption == "Ada") || (opt == "Tidak" && notaOption != "Ada")
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { notaOption = if (opt == "Ada") "Ada" else "Tidak Ada" }
                                    ) {
                                        Text(
                                            text = opt,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else Color.DarkGray,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Submit Button
            val isFormValid = title.isNotBlank() && calculatedTotalAmount > 0
            Button(
                onClick = {
                    val proofSummary = "Kwitansi: $kwitansiOption | Nota: $notaOption"
                    val parts = listOf(notes, proofSummary).filter { it.isNotBlank() }
                    val finalNotes = parts.joinToString(" • ")

                    onSubmitTransaction(
                        title.trim(),
                        calculatedTotalAmount,
                        type,
                        fundSource,
                        "Umum",
                        if (parsedVolume > 0) parsedVolume else 1.0,
                        unitText.ifBlank { "buah" },
                        if (parsedUnitPrice > 0) parsedUnitPrice else calculatedTotalAmount,
                        finalNotes,
                        proofSummary,
                        selectedDateMillis
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (type == "PENGELUARAN") Color(0xFFDC2626) else Color(0xFF059669)
                )
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFormValid) "Simpan Kas: ${currencyFormatter.format(calculatedTotalAmount)}" else "Lengkapi Uraian & Harga Satuan",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
