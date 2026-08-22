package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundSourceDefaults
import com.example.data.model.TransactionEntity
import com.example.data.repository.SyncState
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    transactions: List<TransactionEntity>,
    schoolProfile: com.example.data.model.SchoolProfile = com.example.data.model.SchoolProfile(),
    fundSources: List<String> = com.example.data.model.FundSourceDefaults.SOURCES,
    currentRole: com.example.data.model.UserRole = com.example.data.model.UserRole.BENDAHARA,
    bkuReportState: com.example.viewmodel.BkuReportState = com.example.viewmodel.BkuReportState(),
    onSelectFundSource: (String) -> Unit = {},
    syncState: SyncState = SyncState.Idle,
    googleSheetsUrl: String = "",
    spreadsheetDocUrl: String = "",
    onSaveGoogleSheetsUrl: (String, String) -> Unit = { _, _ -> },
    onExportCsv: (Context, String?) -> Unit = { _, _ -> },
    onSyncAllToGoogleSheets: () -> Unit = {},
    onFetchFromGoogleSheets: () -> Unit = {},
    onDeleteAllData: () -> Unit = {},
    onSyncRkasToSheets: (List<com.example.data.model.RkasItem>) -> Unit = {},
    onFetchRkasFromSheets: (((List<com.example.data.model.RkasItem>) -> Unit)) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var subTab by remember { mutableStateOf(0) }

    val selectedFundSource = bkuReportState.selectedFundSource.ifBlank { fundSources.firstOrNull() ?: "BOS Reguler" }
    var expandedFundDropdown by remember { mutableStateOf(false) }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))

    // Menggunakan data hasil kalkulasi background thread (Dispatchers.Default) dari ViewModel
    val listWithRunningBalance = bkuReportState.itemsWithBalance
    val totalIncome = bkuReportState.totalIncome
    val totalExpense = bkuReportState.totalExpense
    val netBalance = bkuReportState.netBalance

    fun generateAndPrintPdf() {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Layanan cetak dokumen tidak tersedia di perangkat ini", Toast.LENGTH_SHORT).show()
                return
            }

            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = webView.createPrintDocumentAdapter("BKU_${selectedFundSource.replace(" ", "_")}")
                    val jobName = "Laporan BKU - $selectedFundSource"
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }

            val htmlRows = StringBuilder()
            listWithRunningBalance.forEachIndexed { index, (item, runningBalance) ->
                val dateStr = dateFormatter.format(Date(item.date))
                val inStr = if (item.type == "PEMASUKAN") currencyFormatter.format(item.amount) else "-"
                val outStr = if (item.type == "PENGELUARAN") currencyFormatter.format(item.amount) else "-"
                val balStr = currencyFormatter.format(runningBalance)
                val roleStr = if (item.recordedByRole.contains("Kepala", ignoreCase = true)) "Kepsek" else "Bendahara"
                
                htmlRows.append("""
                    <tr>
                        <td style="text-align:center; padding:6px; border:1px solid #ccc;">${index + 1}</td>
                        <td style="text-align:center; padding:6px; border:1px solid #ccc;">$dateStr</td>
                        <td style="padding:6px; border:1px solid #ccc;">${item.title}</td>
                        <td style="text-align:right; padding:6px; border:1px solid #ccc; color:#059669;">$inStr</td>
                        <td style="text-align:right; padding:6px; border:1px solid #ccc; color:#dc2626;">$outStr</td>
                        <td style="text-align:right; padding:6px; border:1px solid #ccc; font-weight:bold;">$balStr</td>
                        <td style="text-align:center; padding:6px; border:1px solid #ccc;">$roleStr</td>
                    </tr>
                """.trimIndent())
            }

            val fullHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Buku Kas Umum - $selectedFundSource</title>
                    <style>
                        body { font-family: sans-serif; font-size: 11px; margin: 20px; color: #1e293b; }
                        h2, h3, p { margin: 2px 0; text-align: center; }
                        table { width: 100%; border-collapse: collapse; margin-top: 14px; font-size: 10px; }
                        th { background-color: #1e40af; color: white; padding: 8px; border: 1px solid #1e3a8a; }
                        .summary { margin-top: 14px; display: flex; justify-content: space-between; font-weight: bold; }
                        .signature-table { width: 100%; margin-top: 40px; border: none; }
                        .signature-table td { border: none; text-align: center; width: 50%; }
                    </style>
                </head>
                <body>
                    <h2>${schoolProfile.schoolName.uppercase()}</h2>
                    <p>NPSN: ${schoolProfile.npsn} • ${schoolProfile.address}</p>
                    <hr style="border: 1px solid #333; margin: 8px 0 14px 0;">
                    <h3>LAPORAN BUKU KAS UMUM (BKU)</h3>
                    <p>Sumber Dana: <strong>$selectedFundSource</strong></p>
                    
                    <table>
                        <thead>
                            <tr>
                                <th style="width:30px;">No</th>
                                <th style="width:70px;">Tanggal</th>
                                <th>Uraian Transaksi</th>
                                <th style="width:90px;">Pemasukan</th>
                                <th style="width:90px;">Pengeluaran</th>
                                <th style="width:95px;">Saldo</th>
                                <th style="width:65px;">Pencatat</th>
                            </tr>
                        </thead>
                        <tbody>
                            $htmlRows
                        </tbody>
                    </table>

                    <div style="margin-top:14px; padding:8px; background:#f1f5f9; border-radius:6px;">
                        <p style="text-align:left; margin:3px 0;"><strong>Total Pemasukan:</strong> ${currencyFormatter.format(totalIncome)}</p>
                        <p style="text-align:left; margin:3px 0;"><strong>Total Pengeluaran:</strong> ${currencyFormatter.format(totalExpense)}</p>
                        <p style="text-align:left; margin:3px 0;"><strong>Saldo Akhir:</strong> ${currencyFormatter.format(netBalance)}</p>
                    </div>

                    <table class="signature-table">
                        <tr>
                            <td>
                                Mengetahui,<br>
                                <strong>Kepala Sekolah</strong><br><br><br><br>
                                <u><strong>${schoolProfile.kepalaSekolahName}</strong></u><br>
                                NIP. ${schoolProfile.kepalaSekolahNip}
                            </td>
                            <td>
                                Dibuat oleh,<br>
                                <strong>Bendahara Sekolah</strong><br><br><br><br>
                                <u><strong>${schoolProfile.bendaharaName}</strong></u><br>
                                NIP. ${schoolProfile.bendaharaNip}
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mencetak PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sub-Tab Switcher (BKU Kas vs RKAS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (subTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { subTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Laporan Kas",
                    fontSize = 12.sp,
                    fontWeight = if (subTab == 0) FontWeight.Bold else FontWeight.Medium,
                    color = if (subTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (subTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { subTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Kertas Kerja",
                    fontSize = 12.sp,
                    fontWeight = if (subTab == 1) FontWeight.Bold else FontWeight.Medium,
                    color = if (subTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (subTab == 1) {
            RkasScreen(
                fundSources = fundSources,
                googleSheetsUrl = googleSheetsUrl,
                spreadsheetDocUrl = spreadsheetDocUrl,
                onSyncRkasToSheets = onSyncRkasToSheets,
                onFetchRkasFromSheets = onFetchRkasFromSheets,
                onSaveGoogleSheetsUrl = onSaveGoogleSheetsUrl
            )
        } else {
        // Action Row: Buka Sheet, Ekspor CSV, Cetak PDF, & Reset
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val target = if (spreadsheetDocUrl.isNotBlank()) {
                        spreadsheetDocUrl
                    } else if (googleSheetsUrl.isNotBlank()) {
                        googleSheetsUrl
                    } else {
                        ""
                    }
                    if (target.isNotBlank()) {
                        try {
                            val url = if (!target.startsWith("http://") && !target.startsWith("https://")) "https://$target" else target
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal membuka tautan Google Sheet", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Tautan Google Sheet belum diatur. Silakan atur di Pengaturan Akun & Sekolah.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = "Buka Google Sheets",
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = { onExportCsv(context, selectedFundSource) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Ekspor CSV",
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = { generateAndPrintPdf() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Cetak PDF",
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = { showDeleteAllConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filters Bar (Sumber Dana)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expandedFundDropdown,
                onExpandedChange = { expandedFundDropdown = !expandedFundDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedFundSource,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Filter Sumber Dana", fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFundDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedFundDropdown,
                    onDismissRequest = { expandedFundDropdown = false }
                ) {
                    fundSources.forEach { fund ->
                        DropdownMenuItem(
                            text = { Text(fund, fontSize = 11.sp) },
                            onClick = {
                                onSelectFundSource(fund)
                                expandedFundDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Pemasukan", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(currencyFormatter.format(totalIncome), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF059669)))
                    }
                    Column {
                        Text("Total Pengeluaran", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(currencyFormatter.format(totalExpense), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)))
                    }
                    Column {
                        Text("Saldo Kas BKU", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            currencyFormatter.format(netBalance),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = if (netBalance >= 0) Color(0xFF1D4ED8) else Color(0xFFDC2626)
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Printable BKU Table View
        Text(
            text = "Tabel Buku Kas Umum (BKU)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(6.dp))

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .horizontalScroll(scrollState)
        ) {
            Column(modifier = Modifier.width(920.dp)) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("No", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Tanggal", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Uraian Transaksi", modifier = Modifier.width(260.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Pemasukan", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Pengeluaran", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Saldo", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Pencatat", modifier = Modifier.width(84.dp), style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }

                if (listWithRunningBalance.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada data transaksi BKU", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(listWithRunningBalance) { index, (item, runningBalance) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color(0xFFF8FAFC) else Color.White)
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF1E293B)))
                                Text(dateFormatter.format(Date(item.date)), modifier = Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF334155)))
                                Text(item.title, modifier = Modifier.width(260.dp), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A)))
                                Text(
                                    if (item.type == "PEMASUKAN") currencyFormatter.format(item.amount) else "-",
                                    modifier = Modifier.width(120.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    if (item.type == "PENGELUARAN") currencyFormatter.format(item.amount) else "-",
                                    modifier = Modifier.width(120.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    currencyFormatter.format(runningBalance),
                                    modifier = Modifier.width(120.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (runningBalance >= 0) Color(0xFF1E3A8A) else Color(0xFFB91C1C)
                                    )
                                )
                                Text(
                                    text = if (item.recordedByRole.contains("Kepala", ignoreCase = true)) "Kepsek" else "Bendahara",
                                    modifier = Modifier.width(84.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.recordedByRole.contains("Kepala", ignoreCase = true)) Color(0xFF92400E) else Color(0xFF1E40AF)
                                    )
                                )
                            }
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
    }
    }

    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            title = { Text("Konfirmasi Reset Semua Data", fontWeight = FontWeight.Bold) },
            text = { Text("Apakah Anda yakin ingin mereset SEMUA data transaksi? Semua saldo kas akan kembali ke Rp 0.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirmDialog = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Ya, Reset Semua Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
