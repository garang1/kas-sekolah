import re

with open('app/src/main/java/com/example/ui/components/SchoolAccountDialog.kt', 'r') as f:
    content = f.read()

# Remove parameters from signature
content = re.sub(r'    schoolPairingKey: String = "BKU-10103214",\n', '', content)
content = re.sub(r'    cloudMailboxRelayUrl: String = "",\n', '', content)
content = re.sub(r'    onSavePairingKey: \(pairingKey: String, customRelay: String\) -> Unit = \{ _, _ -> \},\n', '', content)
content = re.sub(r'    onSendToCloudMailbox: \(\) -> Unit = \{\},\n', '', content)
content = re.sub(r'    onFetchFromCloudMailbox: \(\) -> Unit = \{\},\n', '', content)
content = re.sub(r'    onSyncCloudMailbox: \(\) -> Unit = \{\},\n', '', content)

# Also TabRow still says Kotak Surat Cloud?
# Check tabs: // 0: Login, 1: Profil, 2: Pos Dana, 3: Kotak Surat Cloud, 4: Google Sheets, 5: Info
content = re.sub(r'// TAB 3: KOTAK SURAT CLOUD.*?(?=// TAB 3: TENTANG & LISENSI APLIKASI)', '', content, flags=re.DOTALL)
# Wait, let's just grep what's inside SchoolAccountDialog.kt
with open('app/src/main/java/com/example/ui/components/SchoolAccountDialog.kt', 'w') as f:
    f.write(content)
