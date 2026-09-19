package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FundSourceDefaults
import com.example.data.model.FundSourceModel
import com.example.data.model.SchoolProfile
import com.example.data.model.UserAccountSession
import com.example.data.model.UserRole
import com.example.data.repository.GoogleSheetsSyncRepository
import com.example.ui.screens.AboutScreen

@Composable
fun SchoolAccountDialog(
    schoolProfile: SchoolProfile,
    userSession: UserAccountSession,
    deviceRoleLock: String = "ALL",
    googleSheetsUrl: String,
    spreadsheetDocUrl: String = "",
    customFundSources: List<FundSourceModel> = FundSourceDefaults.DEFAULT_FUND_MODELS,
    onDismiss: () -> Unit,
    onLoginAccount: (UserRole, String, String) -> Boolean,
    onSetDeviceRoleLock: (String) -> Unit = {},
    onUpdateProfile: (SchoolProfile) -> Unit,
    onOpenGoogleSheetsSetup: () -> Unit = {},
    onSaveGoogleSheetsUrl: (String, String) -> Unit = { _, _ -> },
    onUpdatePin: (UserRole, String) -> Unit,
    onUpdateFundSources: (List<FundSourceModel>) -> Unit = {},
    onRenameFundSource: (oldName: String, newName: String) -> Unit = { _, _ -> },
    onSyncAllToGoogleSheets: () -> Unit = {},
    onFetchFromGoogleSheets: () -> Unit = {},
    onBackupData: (android.net.Uri) -> Unit = {},
    onRestoreData: (android.net.Uri) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Login, 1: Profil, 2: Pos Dana, 3: Info

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onBackupData(it) }
    }

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onRestoreData(it) }
    }

    // Login Form State
    var selectedRoleToLogin by remember { mutableStateOf(userSession.role) }
    var loginNameInput by remember(selectedRoleToLogin, schoolProfile) {
        mutableStateOf(if (selectedRoleToLogin == UserRole.BENDAHARA) schoolProfile.bendaharaName else schoolProfile.kepalaSekolahName)
    }
    var pinInput by remember { mutableStateOf("") }
    var isChangingPin by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }

    // School Profile Edit State
    var editSchoolName by remember(schoolProfile) { mutableStateOf(schoolProfile.schoolName) }
    var editNpsn by remember(schoolProfile) { mutableStateOf(schoolProfile.npsn) }
    var editAddress by remember(schoolProfile) { mutableStateOf(schoolProfile.address) }
    var editKepsekName by remember(schoolProfile) { mutableStateOf(schoolProfile.kepalaSekolahName) }
    var editKepsekNip by remember(schoolProfile) { mutableStateOf(schoolProfile.kepalaSekolahNip) }
    var editBendaharaName by remember(schoolProfile) { mutableStateOf(schoolProfile.bendaharaName) }
    var editBendaharaNip by remember(schoolProfile) { mutableStateOf(schoolProfile.bendaharaNip) }

    // Fund Sources Edit State
    var fundSourceListState by remember(customFundSources) { mutableStateOf(customFundSources.toList()) }
    var newFundSourceInput by remember { mutableStateOf("") }
    var isNewFundSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pengaturan Akun Dasar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Akun", modifier = Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(imageVector = Icons.Default.School, contentDescription = "Profil Sekolah", modifier = Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = "Pos Dana", modifier = Modifier.size(20.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Info Aplikasi", modifier = Modifier.size(20.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    // TAB 0: LOGIN & AKUN
                    0 -> {
                        val activeRole = userSession.role
                        val activeName = if (activeRole == UserRole.BENDAHARA) schoolProfile.bendaharaName else schoolProfile.kepalaSekolahName

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (activeRole == UserRole.BENDAHARA) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Akun Aktif: ${activeRole.label}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (activeRole == UserRole.BENDAHARA) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Change PIN Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Ganti PIN Keamanan (${activeRole.label}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newPinInput,
                                    onValueChange = { if (it.length <= 6) newPinInput = it },
                                    label = { Text("PIN Baru (6 Angka)") },
                                    placeholder = { Text("Masukkan 6 digit angka") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (newPinInput.length == 6) {
                                            onUpdatePin(activeRole, newPinInput)
                                            newPinInput = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = newPinInput.length == 6
                                ) {
                                    Text("Simpan PIN Baru", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Backup & Restore Section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Pencadangan Data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { backupLauncher.launch("bku_backup_${System.currentTimeMillis()}.json") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Backup", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        )
                                    ) {
                                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restore", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: PROFIL SEKOLAH (MULTI-SEKOLAH)
                    1 -> {
                        Text(
                            text = "Identitas Profil Sekolah (Dapat Digunakan Oleh Sekolah Mana Saja):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = editSchoolName,
                            onValueChange = { editSchoolName = it },
                            label = { Text("Nama Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editNpsn,
                            onValueChange = { editNpsn = it },
                            label = { Text("NPSN Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editAddress,
                            onValueChange = { editAddress = it },
                            label = { Text("Alamat / Lokasi Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Pejabat & Penandatangan BKU:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = editKepsekName,
                            onValueChange = { editKepsekName = it },
                            label = { Text("Nama Kepala Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = editKepsekNip,
                            onValueChange = { editKepsekNip = it },
                            label = { Text("NIP Kepala Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editBendaharaName,
                            onValueChange = { editBendaharaName = it },
                            label = { Text("Nama Bendahara Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = editBendaharaNip,
                            onValueChange = { editBendaharaNip = it },
                            label = { Text("NIP Bendahara Sekolah") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val updated = SchoolProfile(
                                    schoolName = editSchoolName,
                                    npsn = editNpsn,
                                    address = editAddress,
                                    kepalaSekolahName = editKepsekName,
                                    kepalaSekolahNip = editKepsekNip,
                                    bendaharaName = editBendaharaName,
                                    bendaharaNip = editBendaharaNip
                                )
                                onUpdateProfile(updated)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simpan Identitas Sekolah", fontWeight = FontWeight.Bold)
                        }
                    }

                    // TAB 2: POS SUMBER DANA (EDITABLE & SECRET POS SUPPORT)
                    2 -> {
                        Text(
                            text = "Atur & Kelola Pos Sumber Dana:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (userSession.role == UserRole.KEPALA_SEKOLAH) {
                                "Kepala Sekolah dapat aktifkan Pos Sumber Dana di bawah sebagai Rahasia"
                            } else {
                                "Kelola nama pos sumber dana resmi sekolah untuk pencatatan transaksi BKU."
                            },
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter list for display: If Bendahara, only display non-secret funds
                        val displayList = if (userSession.role == UserRole.KEPALA_SEKOLAH) {
                            fundSourceListState
                        } else {
                            fundSourceListState.filter { !it.isSecret }
                        }

                        displayList.forEachIndexed { index, oldSourceModel ->
                            var currentItemName by remember(oldSourceModel.name) { mutableStateOf(oldSourceModel.name) }
                            var isSecretItem by remember(oldSourceModel.isSecret) { mutableStateOf(oldSourceModel.isSecret) }

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (oldSourceModel.isSecret) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = if (oldSourceModel.isSecret) BorderStroke(1.dp, Color(0xFFFCA5A5)) else null,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = currentItemName,
                                            onValueChange = { currentItemName = it },
                                            label = {
                                                Text(
                                                    if (oldSourceModel.isSecret) "Pos Rahasia ${index + 1} 🔒" else "Pos ${index + 1}",
                                                    fontSize = 10.sp
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true
                                        )

                                        IconButton(
                                            onClick = {
                                                if (currentItemName.isNotBlank()) {
                                                    val trimmed = currentItemName.trim()
                                                    if (trimmed != oldSourceModel.name) {
                                                        onRenameFundSource(oldSourceModel.name, trimmed)
                                                    }
                                                    val updatedList = fundSourceListState.map {
                                                        if (it.name == oldSourceModel.name) it.copy(name = trimmed, isSecret = isSecretItem) else it
                                                    }
                                                    fundSourceListState = updatedList
                                                    onUpdateFundSources(updatedList)
                                                    Toast.makeText(context, "Pos dana berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = currentItemName.isNotBlank() && (currentItemName != oldSourceModel.name || isSecretItem != oldSourceModel.isSecret)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Simpan",
                                                tint = if (currentItemName.isNotBlank() && (currentItemName != oldSourceModel.name || isSecretItem != oldSourceModel.isSecret)) MaterialTheme.colorScheme.primary else Color.LightGray
                                            )
                                        }

                                        if (fundSourceListState.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val newList = fundSourceListState.filter { it.name != oldSourceModel.name }
                                                    fundSourceListState = newList
                                                    onUpdateFundSources(newList)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Hapus Pos",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }

                                    // Secret Toggle (Hanya untuk Kepala Sekolah)
                                    if (userSession.role == UserRole.KEPALA_SEKOLAH) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (isSecretItem) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = if (isSecretItem) Color(0xFFDC2626) else Color.Gray,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isSecretItem) "Pos Rahasia" else "Pos Bersama",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSecretItem) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSecretItem) Color(0xFFDC2626) else Color.DarkGray
                                                )
                                            }
                                            Switch(
                                                checked = isSecretItem,
                                                onCheckedChange = { newSecret ->
                                                    isSecretItem = newSecret
                                                    val updatedList = fundSourceListState.map {
                                                        if (it.name == oldSourceModel.name) it.copy(isSecret = newSecret) else it
                                                    }
                                                    fundSourceListState = updatedList
                                                    onUpdateFundSources(updatedList)
                                                },
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Tambah Pos Dana Baru:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newFundSourceInput,
                                        onValueChange = { newFundSourceInput = it },
                                        placeholder = { Text(if (isNewFundSecret) "Nama Pos Rahasia (cth: Dana Taktis)" else "Nama Pos Baru (cth: Koperasi Guru)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                    Button(
                                        onClick = {
                                            if (newFundSourceInput.isNotBlank()) {
                                                val cleanName = newFundSourceInput.trim()
                                                if (fundSourceListState.none { it.name.equals(cleanName, ignoreCase = true) }) {
                                                    val newList = fundSourceListState + FundSourceModel(name = cleanName, isSecret = isNewFundSecret)
                                                    fundSourceListState = newList
                                                    onUpdateFundSources(newList)
                                                    newFundSourceInput = ""
                                                    isNewFundSecret = false
                                                    Toast.makeText(context, "Pos dana berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Nama pos sudah ada", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = newFundSourceInput.isNotBlank()
                                    ) {
                                        Text("Tambah", fontSize = 11.sp)
                                    }
                                }

                                if (userSession.role == UserRole.KEPALA_SEKOLAH) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isNewFundSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = if (isNewFundSecret) Color(0xFFDC2626) else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isNewFundSecret) "Pos Rahasia" else "Pos Bersama",
                                                fontSize = 11.sp,
                                                fontWeight = if (isNewFundSecret) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isNewFundSecret) Color(0xFFDC2626) else Color.DarkGray
                                            )
                                        }
                                        Switch(
                                            checked = isNewFundSecret,
                                            onCheckedChange = { isNewFundSecret = it },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                onUpdateFundSources(fundSourceListState)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Selesai & Simpan Pos Dana", fontWeight = FontWeight.Bold)
                        }
                    }

                    // TAB 3: TENTANG & LISENSI APLIKASI
                    3 -> {
                        AboutScreen(
                            appName = "Kas Simapas",
                            npsn = schoolProfile.npsn
                        )
                    }
                }
            }
        }
    )
}

