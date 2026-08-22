package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.model.SchoolProfile
import com.example.data.model.SchoolQrPairingData
import com.example.data.model.UserRole
import com.example.util.QrCodeGenerator
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Dialog Pairing QR Code mirip WhatsApp Web:
 * 1. Mode "Tampilkan QR Code" (misal di HP Bendahara / Pengirim)
 * 2. Mode "Scan QR Code" (di HP Kepala Sekolah / Penerima)
 */
@Composable
fun QrPairingDialog(
    schoolProfile: SchoolProfile,
    pairingKey: String,
    relayUrl: String,
    currentRole: UserRole,
    onPairingConfirmed: (newNpsn: String, newSchoolName: String, newPairingKey: String, newRelay: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(if (currentRole == UserRole.BENDAHARA) 0 else 1) } // 0: Tampilkan QR, 1: Scan QR / Input

    val moshi = remember {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    val pairingAdapter = remember { moshi.adapter(SchoolQrPairingData::class.java) }

    // Generate JSON payload for QR Code
    val pairingPayloadJson = remember(schoolProfile, pairingKey, relayUrl) {
        val data = SchoolQrPairingData(
            npsn = schoolProfile.npsn.trim(),
            schoolName = schoolProfile.schoolName.trim(),
            pairingKey = pairingKey.trim(),
            kepalaSekolahName = schoolProfile.kepalaSekolahName.trim(),
            bendaharaName = schoolProfile.bendaharaName.trim(),
            relayUrl = relayUrl.trim()
        )
        pairingAdapter.toJson(data)
    }

    // QR Code Bitmap
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pairingPayloadJson) {
        qrBitmap = QrCodeGenerator.generateQrBitmap(
            content = pairingPayloadJson,
            sizePx = 512,
            backgroundColor = android.graphics.Color.WHITE,
            foregroundColor = android.graphics.Color.parseColor("#0F172A")
        )
    }

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Izin kamera diperlukan untuk scan QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    // Camera scanner state
    var isCameraScanning by remember { mutableStateOf(false) }

    // Scanner / Manual input state
    var manualInputCode by remember { mutableStateOf("") }
    var scannedSuccessData by remember { mutableStateOf<SchoolQrPairingData?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }

    fun processPairingJson(jsonStr: String) {
        try {
            parseError = null
            val cleanStr = jsonStr.trim()
            val parsed = pairingAdapter.fromJson(cleanStr)
            if (parsed != null && parsed.npsn.isNotBlank()) {
                scannedSuccessData = parsed
            } else {
                // If it's a simple pairing key string like BKU-10103214
                if (cleanStr.startsWith("BKU-") || cleanStr.length >= 6) {
                    scannedSuccessData = SchoolQrPairingData(
                        npsn = cleanStr.removePrefix("BKU-").take(8).ifBlank { schoolProfile.npsn },
                        schoolName = schoolProfile.schoolName,
                        pairingKey = cleanStr,
                        relayUrl = relayUrl
                    )
                } else {
                    parseError = "Format QR Code tidak valid untuk aplikasi Kas Simpas."
                }
            }
        } catch (e: Exception) {
            // Check if user entered just a pairing key
            val cleanStr = jsonStr.trim()
            if (cleanStr.startsWith("BKU-") || cleanStr.length >= 6) {
                scannedSuccessData = SchoolQrPairingData(
                    npsn = schoolProfile.npsn,
                    schoolName = schoolProfile.schoolName,
                    pairingKey = cleanStr,
                    relayUrl = relayUrl
                )
            } else {
                parseError = "Gagal membaca QR Code: Format data tidak sesuai."
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pairing Kas Simapas",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Hubungkan HP Bendahara & Kepala Sekolah",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: 0: Tampilkan QR (HP 1), 1: Scan / Hubungkan (HP 2)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tampilkan QR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan / Sambungkan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TAB 0: TAMPILKAN QR CODE (Untuk di-scan oleh HP lain)
                if (selectedTab == 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Scan QR ini di HP Kepala Sekolah",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Buka menu Pairing di HP Kepala Sekolah lalu pilih tab 'Scan / Sambungkan'",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // QR Image Display
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .border(2.dp, Color(0xFF2563EB).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "QR Code Pairing",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // School Info Summary
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.School, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(schoolProfile.schoolName.ifBlank { "Sekolah" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text("NPSN: ${schoolProfile.npsn} • Kunci: $pairingKey", fontSize = 10.sp, color = Color(0xFF1E3A8A))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Copy Pairing String
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pairing JSON", pairingPayloadJson))
                                    Toast.makeText(context, "Kode Pairing berhasil disalin!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salin Kode Pairing Teks", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // TAB 1: SCAN / SAMBUNGKAN
                if (selectedTab == 1) {
                    if (scannedSuccessData == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cara 1: Scan Kamera Langsung",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Arahkan kamera ke layar HP Bendahara yang menampilkan QR Code.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isCameraScanning && hasCameraPermission) {
                                    CameraQrScannerView(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp),
                                        onQrDetected = { detectedCode ->
                                            processPairingJson(detectedCode)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedButton(
                                        onClick = { isCameraScanning = false },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Tutup Kamera", fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (!hasCameraPermission) {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            } else {
                                                isCameraScanning = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (hasCameraPermission) "Buka Kamera Scanner" else "Minta Izin Kamera & Buka Scanner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Atau Tempel / Masukkan Kode Pairing:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F766E)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = manualInputCode,
                                    onValueChange = {
                                        manualInputCode = it
                                        parseError = null
                                    },
                                    label = { Text("Kode Pairing atau JSON QR") },
                                    placeholder = { Text("cth: BKU-${schoolProfile.npsn}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = false,
                                    maxLines = 3
                                )

                                if (parseError != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = parseError!!,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                            if (clipText.isNotBlank()) {
                                                manualInputCode = clipText
                                                processPairingJson(clipText)
                                            } else {
                                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📋 Tempel", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            if (manualInputCode.isNotBlank()) {
                                                processPairingJson(manualInputCode)
                                            } else {
                                                parseError = "Silakan masukkan atau tempel kode pairing terlebih dahulu."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Hubungkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        // SCANNED SUCCESS CONFIRMATION CARD
                        val data = scannedSuccessData!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                            border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "QR Code Berhasil Terverifikasi!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Data sekolah siap dihubungkan dengan aman:",
                                    fontSize = 11.sp,
                                    color = Color(0xFF166534)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Nama Sekolah: ${data.schoolName.ifBlank { schoolProfile.schoolName }}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("NPSN: ${data.npsn}", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Kunci Pairing: ${data.pairingKey}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F766E))
                                        if (data.bendaharaName.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Bendahara: ${data.bendaharaName}", fontSize = 11.sp, color = Color.DarkGray)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        onPairingConfirmed(
                                            data.npsn,
                                            data.schoolName.ifBlank { schoolProfile.schoolName },
                                            data.pairingKey,
                                            data.relayUrl
                                        )
                                        Toast.makeText(context, "Pairing Berhasil! HP Terhubung ke ${data.schoolName}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Konfirmasi & Simpan Sambungan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = { scannedSuccessData = null }
                                ) {
                                    Text("Scan Ulang QR Lain", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
