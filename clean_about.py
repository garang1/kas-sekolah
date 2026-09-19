import re

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r',\s*googleSheetsUrl: String = "",\s*spreadsheetDocUrl: String = "",\s*onSaveGoogleSheetsUrl: \(String, String\) -> Unit = \{ _, _ -> \}',
    '',
    content
)

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/components/SchoolAccountDialog.kt', 'r') as f:
    content = f.read()

content = re.sub(
    r',\s*googleSheetsUrl = googleSheetsUrl,\s*spreadsheetDocUrl = spreadsheetDocUrl,\s*onSaveGoogleSheetsUrl = onSaveGoogleSheetsUrl',
    '',
    content
)

with open('app/src/main/java/com/example/ui/components/SchoolAccountDialog.kt', 'w') as f:
    f.write(content)
