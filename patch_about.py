import re

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'r') as f:
    content = f.read()

# Add parameters
content = re.sub(
    r'facebookUrl: String = "https://www\.facebook\.com/opper\.antoni",\n    isScrollable: Boolean = false',
    r'facebookUrl: String = "https://www.facebook.com/opper.antoni",\n    isScrollable: Boolean = false,\n    googleSheetsUrl: String = "",\n    spreadsheetDocUrl: String = "",\n    onSaveGoogleSheetsUrl: (String, String) -> Unit = {_, _}',
    content
)

# Add imports
imports_to_add = """
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedTextField
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
"""
content = re.sub(r'import androidx.compose.material3.Text', 'import androidx.compose.material3.Text\n' + imports_to_add, content)

# Add the UI block
ui_block = """
        // 5. Integrasi Google Sheets & Apps Script
        var gsUrl by remember { mutableStateOf(googleSheetsUrl) }
        var docUrl by remember { mutableStateOf(spreadsheetDocUrl) }
        val clipboardManager = LocalClipboardManager.current
        
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
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTEGRASI GOOGLE SHEETS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "1. Salin Kode Apps Script (kode.gs)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(com.example.data.repository.GoogleSheetsSyncRepository.APPS_SCRIPT_TEMPLATE))
                        Toast.makeText(context, "Kode Apps Script disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salin Kode Apps Script")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "2. Masukkan URL Apps Script (Web App)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = gsUrl,
                    onValueChange = { gsUrl = it },
                    label = { Text("URL Web App (https://script.google.com/...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "3. Masukkan URL Spreadsheet (Opsional)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = docUrl,
                    onValueChange = { docUrl = it },
                    label = { Text("URL Spreadsheet (https://docs.google.com/...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSaveGoogleSheetsUrl(gsUrl, docUrl)
                        Toast.makeText(context, "Pengaturan Google Sheets disimpan!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Pengaturan")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
"""

content = re.sub(
    r'        // Footer Deskripsi Singkat Aplikasi',
    ui_block + r'        // Footer Deskripsi Singkat Aplikasi',
    content
)

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'w') as f:
    f.write(content)

print("Patch applied successfully")
