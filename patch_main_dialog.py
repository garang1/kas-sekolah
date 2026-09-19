import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

replacement = """        // Google Sheets Setup Dialog Modal
        if (showGoogleSheetsDialog) {
            val googleSheetsUrl by viewModel.googleSheetsUrl.collectAsStateWithLifecycle()
            val spreadsheetDocUrl by viewModel.spreadsheetDocUrl.collectAsStateWithLifecycle()
            
            com.example.ui.components.GoogleSheetsSetupDialog(
                googleSheetsUrl = googleSheetsUrl,
                spreadsheetDocUrl = spreadsheetDocUrl,
                onSaveGoogleSheetsUrl = { scriptUrl, docUrl -> 
                    viewModel.saveGoogleSheetsUrl(scriptUrl, docUrl) 
                },
                onDismiss = { showGoogleSheetsDialog = false }
            )
        }"""

content = re.sub(
    r'        // QR Code Pairing Dialog Modal \(Cara 1: Scan QR Code Pairing WA-Web style\).*?        // Receipt Preview Modal Component',
    replacement + '\n\n        // Receipt Preview Modal Component',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

print("Patched MainActivity.kt")
