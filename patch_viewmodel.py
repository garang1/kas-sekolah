import re

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'r') as f:
    content = f.read()

# Remove cloudMailboxRepository declaration and initialization
content = re.sub(r'    private val cloudMailboxRepository: com\.example\.data\.repository\.CloudMailboxRepository\n', '', content)
content = re.sub(r'        cloudMailboxRepository = com\.example\.data\.repository\.CloudMailboxRepository\(repository\)\n', '', content)

# Remove pairingKey and relayUrl state
content = re.sub(r'    val schoolPairingKey = MutableStateFlow\("BKU-10103214"\)\n', '', content)
content = re.sub(r'    val cloudMailboxRelayUrl = MutableStateFlow\(""\)\n', '', content)

# Remove preference initialization for pairing key and relay
content = re.sub(r'            val sPairingKey = prefs\.getString\("school_pairing_key", "BKU-\$sNpsn"\) \?: "BKU-\$sNpsn"\n            schoolPairingKey\.value = sPairingKey\n', '', content)
content = re.sub(r'            val sRelayUrl = prefs\.getString\("cloud_mailbox_relay", ""\) \?: ""\n            cloudMailboxRelayUrl\.value = sRelayUrl\n', '', content)

# Remove setSchoolPairingKey and applyQrPairingData methods
content = re.sub(r'    fun setSchoolPairingKey\(newKey: String, customRelay: String = ""\) \{.*?(?=    fun setGoogleSheetsUrl)', '', content, flags=re.DOTALL)

# Remove sendToCloudMailbox, fetchFromCloudMailbox, syncCloudMailbox methods
content = re.sub(r'    fun fetchFromCloudMailbox\(silent: Boolean = false\) \{.*?(?=    fun syncFundSources\()', '', content, flags=re.DOTALL)

# Also remove references in mergeCustomFundSources if any (none expected)
# Also remove references in insertTransaction where it might auto-sync? Wait, we need to check insertTransaction
content = re.sub(r'        // Otomatis push ke cloud mailbox \(background\)\n        sendToCloudMailbox\(silent = true\)\n', '', content)
content = re.sub(r'        sendToCloudMailbox\(silent = true\)\n', '', content)

with open('app/src/main/java/com/example/viewmodel/MainViewModel.kt', 'w') as f:
    f.write(content)

print("MainViewModel patched")
