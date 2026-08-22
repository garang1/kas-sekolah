package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.TransactionEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionEntity,
    fundSources: List<String>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(transaction.title) }
    var type by remember { mutableStateOf(transaction.type) }
    var fundSource by remember { mutableStateOf(transaction.fundSource) }
    var notes by remember { mutableStateOf(transaction.notes) }

    var volumeQtyText by remember {
        val vol = transaction.volume
        mutableStateOf(if (vol % 1.0 == 0.0) vol.toInt().toString() else vol.toString())
    }
    var unitText by remember { mutableStateOf(transaction.unitName) }
    var unitPriceText by remember {
        val price = if (transaction.unitPrice > 0) transaction.unitPrice else transaction.amount
        mutableStateOf(price.toLong().toString())
    }
    var selectedDateMillis by remember { mutableStateOf(transaction.date) }
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

    val parsedVolume by remember(volumeQtyText) {
        derivedStateOf {
            val cleaned = volumeQtyText.replace(",", ".").trim()
            cleaned.toDoubleOrNull() ?: 1.0
        }
    }

    val parsedUnitPrice by remember(unitPriceText) {
        derivedStateOf {
            val cleaned = unitPriceText.replace("[^0-9]".toRegex(), "").trim()
            cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    val calculatedTotalAmount by remember(parsedVolume, parsedUnitPrice) {
        derivedStateOf {
            parsedVolume * parsedUnitPrice
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Transaksi",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Edit Transaksi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Jenis Transaksi Chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "PENGELUARAN",
                        onClick = { type = "PENGELUARAN" },
                        label = { Text("Pengeluaran", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFE4E6),
                            selectedLabelColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = type == "PEMASUKAN",
                        onClick = { type = "PEMASUKAN" },
                        label = { Text("Pemasukan", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFD1FAE5),
                            selectedLabelColor = Color(0xFF059669)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Uraian Belanja
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Uraian Transaksi") },
                    placeholder = { Text("Contoh: Pembelian ATK") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Sumber Dana Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedFundDropdown,
                    onExpandedChange = { expandedFundDropdown = !expandedFundDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = fundSource,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sumber Dana") },
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
                        fundSources.forEach { src ->
                            DropdownMenuItem(
                                text = { Text(src) },
                                onClick = {
                                    fundSource = src
                                    expandedFundDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Volume, Satuan, Harga Satuan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = volumeQtyText,
                        onValueChange = { volumeQtyText = it },
                        label = { Text("Volume") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Satuan") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text("Harga Satuan (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Total Nominal Display
                Surface(
                    color = if (type == "PENGELUARAN") Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (type == "PENGELUARAN") Color(0xFFFECACA) else Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Text(
                            text = currencyFormatter.format(calculatedTotalAmount),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (type == "PENGELUARAN") Color(0xFFDC2626) else Color(0xFF059669)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date Picker Button
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, dayOfMonth)
                                selectedDateMillis = newCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tanggal: ${dateFormatter.format(Date(selectedDateMillis))}", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Catatan / Keterangan
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Keterangan") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    val isValid = title.isNotBlank() && calculatedTotalAmount > 0
                    Button(
                        onClick = {
                            if (isValid) {
                                val updated = transaction.copy(
                                    title = title.trim(),
                                    amount = calculatedTotalAmount,
                                    type = type,
                                    fundSource = fundSource,
                                    volume = if (parsedVolume > 0) parsedVolume else 1.0,
                                    unitName = unitText.ifBlank { "buah" },
                                    unitPrice = if (parsedUnitPrice > 0) parsedUnitPrice else calculatedTotalAmount,
                                    notes = notes.trim(),
                                    date = selectedDateMillis
                                )
                                onSave(updated)
                            }
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Edit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
