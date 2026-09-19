import re

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'r') as f:
    content = f.read()

# Remove UI block
content = re.sub(
    r'// 5\. Integrasi Google Sheets & Apps Script.*?// Footer Deskripsi Singkat Aplikasi',
    '// Footer Deskripsi Singkat Aplikasi',
    content,
    flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/screens/AboutScreen.kt', 'w') as f:
    f.write(content)

print("Removed GS block from AboutScreen")
