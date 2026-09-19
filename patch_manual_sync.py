import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

replacement = """    fun manualSync() {
        val sheetsUrl = googleSheetsUrl.value.trim()
        if (sheetsUrl.isNotBlank() && !sheetsUrl.contains("docs.google.com/spreadsheets")) {
            syncAllToGoogleSheets()
        } else {
            viewModelScope.launch {
                _eventFlow.emit(UiEvent.ShowToast("Silakan atur URL Google Sheets terlebih dahulu di menu pengaturan."))
            }
        }
    }"""

content = re.sub(
    r'    fun manualSync\(\) \{.*?fetchFromCloudMailbox\(silent = false\)\n        }\n    \}',
    replacement,
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)
