import re
import os

with open('app/src/main/java/com/example/ui/components/HeaderSchoolBanner.kt', 'r') as f:
    content = f.read()
content = content.replace('onOpenQrPairing', 'onOpenGoogleSheetsSetup')
content = content.replace('Icons.Default.QrCodeScanner', 'Icons.Default.CloudSync')
content = content.replace('"QR Pairing"', '"Google Sheets Setup"')
with open('app/src/main/java/com/example/ui/components/HeaderSchoolBanner.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'r') as f:
    content = f.read()
content = content.replace('onOpenQrPairing', 'onOpenGoogleSheetsSetup')
with open('app/src/main/java/com/example/ui/screens/DashboardScreen.kt', 'w') as f:
    f.write(content)

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()
content = content.replace('showQrPairingDialog', 'showGoogleSheetsDialog')
content = content.replace('onOpenQrPairing = {', 'onOpenGoogleSheetsSetup = {')
content = content.replace('viewModel.sendToCloudMailbox(silent = true)', '')
with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)

