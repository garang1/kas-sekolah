import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

# Let's find "if (isNpsnChanged)" which was inside applyQrPairingData
# Actually, let's just remove anything between `fun manualSync()` and the end of `fun syncCloudMailbox(silent: Boolean = false)` or similar.

def remove_method(content, method_name):
    pattern = r'    fun ' + method_name + r'\(.*?\).*?^    }'
    return re.sub(pattern, '', content, flags=re.MULTILINE|re.DOTALL)

# Because the formatting might have spaces, let's just use a robust regex
# We can find `fun sendToCloudMailbox` down to the next `fun manualSync`
content = re.sub(r'    fun sendToCloudMailbox\(.*?\)(?=    fun manualSync)', '', content, flags=re.DOTALL)

# Find `fun fetchFromCloudMailbox` down to `fun syncCloudMailbox`
content = re.sub(r'    fun fetchFromCloudMailbox\(.*?\)(?=    fun syncCloudMailbox)', '', content, flags=re.DOTALL)

# Find `fun syncCloudMailbox` down to `    private fun triggerAutoSyncIfConfigured()`
content = re.sub(r'    fun syncCloudMailbox\(.*?\)(?=    private fun triggerAutoSyncIfConfigured)', '', content, flags=re.DOTALL)

# also there's a loose chunk of applyQrPairingData:
content = re.sub(r'        viewModelScope.launch \{\s*if \(isNpsnChanged\) \{.*?(?=    fun sendToCloudMailbox|    fun manualSync)', '', content, flags=re.DOTALL)

# Wait, `applyQrPairingData` might not have been fully deleted, the signature was, leaving a broken block.
# Let's just fix it. We can just replace the whole file from `    // === EXPORT & SINKRONISASI GOOGLE SHEETS ===` downwards... No, these are before google sheets.

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)

