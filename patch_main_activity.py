import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Remove UI State collection
content = re.sub(r'    val schoolPairingKey by viewModel\.schoolPairingKey\.collectAsStateWithLifecycle\(\)\n', '', content)
content = re.sub(r'    val cloudMailboxRelayUrl by viewModel\.cloudMailboxRelayUrl\.collectAsStateWithLifecycle\(\)\n', '', content)

# Remove parameters from SchoolAccountDialog
content = re.sub(r'                schoolPairingKey = schoolPairingKey,\n', '', content)
content = re.sub(r'                cloudMailboxRelayUrl = cloudMailboxRelayUrl,\n', '', content)
content = re.sub(r'                onSavePairingKey = \{ key, relay -> viewModel\.setSchoolPairingKey\(key, relay\) \},\n', '', content)
content = re.sub(r'                onSendToCloudMailbox = \{ viewModel\.sendToCloudMailbox\(\) \},\n', '', content)
content = re.sub(r'                onFetchFromCloudMailbox = \{ viewModel\.fetchFromCloudMailbox\(\) \},\n', '', content)
content = re.sub(r'                onSyncCloudMailbox = \{ viewModel\.syncCloudMailbox\(\) \},\n', '', content)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
print("MainActivity patched")
