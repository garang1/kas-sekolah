package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Halaman Kredit / About Aplikasi
 * Menampilkan informasi logo, nama aplikasi, versi dinamis, dan kredit pengembang.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    appName: String = "Kas Simapas",
    npsn: String = "",
    developerName: String = "Opper Antoni",
    facebookUrl: String = "https://www.facebook.com/opper.antoni",
    isScrollable: Boolean = false
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Ambil versi aplikasi secara dinamis & aman dari PackageManager (API 33+ / Tiramisu compliant)
    val appVersion = remember(context) {
        getAppVersionName(context)
    }

    val columnModifier = if (isScrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(16.dp)
    }

    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. Logo Aplikasi (Ikon Aplikasi / Launcher Icon)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo Aplikasi",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Nama Aplikasi (Teks Tebal)
        Text(
            text = appName,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Versi Aplikasi & Status Lisensi (Trial gray / Full blue)
        var licenseInfo by remember(npsn) {
            mutableStateOf(com.example.util.LicenseManager.checkLicenseStatus(context, npsn))
        }

        androidx.compose.runtime.LaunchedEffect(npsn) {
            if (npsn.isNotBlank()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.example.util.LicenseManager.checkLicenseOnline(context, npsn)
                    } catch (ignored: Exception) {}
                }
                licenseInfo = com.example.util.LicenseManager.checkLicenseStatus(context, npsn)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Versi $appVersion",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = " • ",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isFull = !licenseInfo.isTrial && licenseInfo.isLicensed && !licenseInfo.isExpired
            Text(
                text = if (isFull) "Full" else "Trial",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFull) Color(0xFF1976D2) else Color(0xFF757575)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Kartu Kredit Pengembang
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KREDIT PENGEMBANG",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Nama Pengembang
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Nama: ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = developerName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tombol Tautan Facebook
                Button(
                    onClick = {
                        try {
                            uriHandler.openUri(facebookUrl)
                        } catch (e: Exception) {
                            // Handling jika browser tidak tersedia
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2) // Warna khas Facebook Blue
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Facebook",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kunjungi Facebook Pengembang",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer Deskripsi Singkat Aplikasi
        Text(
            text = "Aplikasi Pembukuan & Kas BKU Sekolah\nDirancang untuk kemudahan pencatatan dana BOS & Kas Sekolah.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Fungsi helper untuk mengambil versi aplikasi secara otomatis
 * Aman untuk Android API 33+ (Tiramisu) dan API lama tanpa deprecation warning.
 */
private fun getAppVersionName(context: Context): String {
    return try {
        val packageManager = context.packageManager
        val packageName = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName ?: "5.3"
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: "5.3"
        }
    } catch (e: Exception) {
        "5.3"
    }
}

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    MaterialTheme {
        Surface {
            AboutScreen(
                appName = "Kas Simapas",
                developerName = "Opper Antoni",
                facebookUrl = "https://www.facebook.com/opper.antoni"
            )
        }
    }
}
