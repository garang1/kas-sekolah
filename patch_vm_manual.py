import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

# Remove remaining references:
content = re.sub(r'    val lastMailboxSyncTime = MutableStateFlow\(0L\)\n', '', content)
content = re.sub(r'    val cloudMailboxSyncState: StateFlow<SyncState> get\(\) = cloudMailboxRepository\.syncState\n', '', content)
content = re.sub(r'            cloudMailboxRelayUrl\.value = prefs\.getString\("cloud_mailbox_url", ""\) \?: ""\n', '', content)
content = re.sub(r'                    fetchFromCloudMailbox\(silent = true\)\n', '', content)
content = re.sub(r'                        fetchFromCloudMailbox\(silent = true\)\n', '', content)
content = re.sub(r'            syncCloudMailbox\(silent = false\)\n', '', content)

# Remove setSchoolPairingKey, applyQrPairingData, sendToCloudMailbox, fetchFromCloudMailbox, syncCloudMailbox
content = re.sub(r'    fun setSchoolPairingKey.*?// === EXPORT & SINKRONISASI GOOGLE SHEETS ===', '// === EXPORT & SINKRONISASI GOOGLE SHEETS ===', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)

print("MainViewModel patched again")
