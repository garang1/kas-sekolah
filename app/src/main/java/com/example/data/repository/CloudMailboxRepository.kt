package com.example.data.repository

import com.example.data.model.DecryptedMailboxPayload
import com.example.data.model.EncryptedMailboxPackage
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionPayloadItem
import com.example.data.model.UserRole
import com.example.util.MailboxEncryptionHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CloudMailboxRepository(
    private val transactionRepository: TransactionRepository
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(DecryptedMailboxPayload::class.java)
    private val packageAdapter = moshi.adapter(EncryptedMailboxPackage::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Base URL for Cloud Mailbox Key-Value Relay
    // Uses KVDB / Cloud Relay bucket with isolated channel derived from SHA-256(NPSN + PairingKey)
    private val defaultRelayBaseUrl = "https://kvdb.io/9p8xQ2mF6uV3kL1z7/"

    /**
     * Titipkan paket data BKU terenkripsi ke Kotak Surat Cloud (Cloud Mailbox).
     */
    suspend fun sendPackageToMailbox(
        npsn: String,
        pairingKey: String,
        senderRole: UserRole,
        senderName: String,
        transactions: List<TransactionEntity>,
        fundSources: List<String>,
        actionType: String = "FULL_SYNC",
        customRelayUrl: String = ""
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing

            val channelId = MailboxEncryptionHelper.deriveMailboxChannelId(npsn, pairingKey)
            val roleStr = if (senderRole == UserRole.BENDAHARA) "BENDAHARA" else "KEPALA_SEKOLAH"

            // Convert transactions to serializable payload items
            val payloadItems = transactions.map { tx ->
                TransactionPayloadItem(
                    remoteId = tx.id.toString(),
                    title = tx.title,
                    amount = tx.amount,
                    type = tx.type,
                    fundSource = tx.fundSource,
                    category = tx.category,
                    volume = tx.volume,
                    unitName = tx.unitName,
                    unitPrice = tx.unitPrice,
                    date = tx.date,
                    notes = tx.notes,
                    receiptUri = tx.receiptUri,
                    approvalStatus = tx.approvalStatus,
                    recordedByRole = tx.recordedByRole,
                    createdAt = tx.createdAt
                )
            }

            val decryptedPayload = DecryptedMailboxPayload(
                schoolNpsn = npsn.trim(),
                transactions = payloadItems,
                fundSources = fundSources,
                timestamp = System.currentTimeMillis(),
                senderRole = roleStr,
                actionType = actionType
            )

            val rawJson = payloadAdapter.toJson(decryptedPayload)

            // AES-256 Encryption
            val (encryptedBase64, ivBase64) = MailboxEncryptionHelper.encrypt(rawJson, npsn, pairingKey)

            val envelopePackage = EncryptedMailboxPackage(
                schoolNpsn = npsn.trim(),
                senderRole = roleStr,
                senderName = senderName,
                timestamp = System.currentTimeMillis(),
                encryptedPayloadBase64 = encryptedBase64,
                ivBase64 = ivBase64,
                packageHash = channelId,
                dataVersion = 2
            )

            val packageJson = packageAdapter.toJson(envelopePackage)

            val targetUrl = if (customRelayUrl.isNotBlank()) {
                if (customRelayUrl.endsWith("/")) "$customRelayUrl$channelId" else "$customRelayUrl/$channelId"
            } else {
                "$defaultRelayBaseUrl$channelId"
            }

            val requestBody = packageJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                _syncState.value = SyncState.Success("Paket data berhasil dititipkan ke Kotak Surat Cloud (${transactions.size} transaksi)")
                Result.success(true)
            } else {
                // Fallback attempt with PUT
                val putRequest = Request.Builder()
                    .url(targetUrl)
                    .put(requestBody)
                    .build()
                val putResponse = okHttpClient.newCall(putRequest).execute()
                if (putResponse.isSuccessful) {
                    _syncState.value = SyncState.Success("Paket data berhasil dititipkan ke Kotak Surat Cloud (${transactions.size} transaksi)")
                    Result.success(true)
                } else {
                    val err = "Gagal kirim ke Cloud Mailbox: HTTP ${response.code}"
                    _syncState.value = SyncState.Error(err)
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            val err = "Gagal terhubung ke Kotak Surat Cloud: ${e.localizedMessage ?: "Periksa koneksi internet"}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }

    /**
     * Mengambil paket data BKU dari Kotak Surat Cloud (Cloud Mailbox),
     * mendekripsi dan menggabungkan langsung ke database lokal Room DB.
     */
    suspend fun fetchAndMergeFromMailbox(
        npsn: String,
        pairingKey: String,
        currentRole: UserRole,
        customRelayUrl: String = "",
        onFundSourcesReceived: (List<String>) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing

            val channelId = MailboxEncryptionHelper.deriveMailboxChannelId(npsn, pairingKey)
            val targetUrl = if (customRelayUrl.isNotBlank()) {
                if (customRelayUrl.endsWith("/")) "$customRelayUrl$channelId" else "$customRelayUrl/$channelId"
            } else {
                "$defaultRelayBaseUrl$channelId"
            }

            val request = Request.Builder()
                .url(targetUrl)
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                val msg = if (response.code == 404) "Kotak Surat Cloud sekolah ini masih kosong (belum ada paket yang dikirim)." else "Gagal mengambil paket: HTTP ${response.code}"
                _syncState.value = SyncState.Error(msg)
                return@withContext Result.failure(Exception(msg))
            }

            val responseBodyString = response.body!!.string()
            if (responseBodyString.isBlank()) {
                val msg = "Kotak Surat Cloud masih kosong."
                _syncState.value = SyncState.Success(msg)
                return@withContext Result.success(0)
            }

            val envelopePackage = packageAdapter.fromJson(responseBodyString)
                ?: return@withContext Result.failure(Exception("Format paket kotak surat tidak valid"))

            // Decrypt with AES-256
            val decryptedJson = MailboxEncryptionHelper.decrypt(
                encryptedBase64 = envelopePackage.encryptedPayloadBase64,
                ivBase64 = envelopePackage.ivBase64,
                npsn = npsn,
                pairingKey = pairingKey
            )

            val payload = payloadAdapter.fromJson(decryptedJson)
                ?: return@withContext Result.failure(Exception("Gagal membaca isi paket data terenkripsi"))

            if (payload.schoolNpsn != npsn.trim()) {
                return@withContext Result.failure(Exception("NPSN pada paket (${payload.schoolNpsn}) tidak cocok dengan sekolah ini!"))
            }

            // Sync Fund Sources if any
            if (payload.fundSources.isNotEmpty()) {
                onFundSourcesReceived(payload.fundSources)
            }

            // Merge into local Room DB
            val existingList = transactionRepository.getAllTransactionsSync()
            var mergedCount = 0

            val incomingEntities = payload.transactions.map { item ->
                TransactionEntity(
                    title = item.title,
                    amount = item.amount,
                    type = item.type,
                    fundSource = item.fundSource,
                    category = item.category,
                    volume = item.volume,
                    unitName = item.unitName,
                    unitPrice = item.unitPrice,
                    date = item.date,
                    notes = item.notes,
                    receiptUri = item.receiptUri,
                    approvalStatus = item.approvalStatus,
                    recordedByRole = item.recordedByRole,
                    createdAt = item.createdAt
                )
            }

            // Smart Merge: For each incoming transaction, match by date, amount, fundSource, type, and title
            for (incoming in incomingEntities) {
                val existingMatch = existingList.firstOrNull { exist ->
                    exist.date == incoming.date &&
                    exist.amount == incoming.amount &&
                    exist.fundSource == incoming.fundSource &&
                    exist.type == incoming.type &&
                    exist.title.trim().equals(incoming.title.trim(), ignoreCase = true)
                }

                if (existingMatch != null) {
                    // Update approval status or notes if different
                    if (existingMatch.approvalStatus != incoming.approvalStatus ||
                        existingMatch.notes != incoming.notes ||
                        existingMatch.receiptUri != incoming.receiptUri
                    ) {
                        transactionRepository.updateTransaction(
                            existingMatch.copy(
                                approvalStatus = incoming.approvalStatus,
                                notes = incoming.notes,
                                receiptUri = incoming.receiptUri ?: existingMatch.receiptUri
                            )
                        )
                        mergedCount++
                    }
                } else {
                    // Insert new transaction
                    transactionRepository.insertTransaction(incoming)
                    mergedCount++
                }
            }

            val senderInfo = if (envelopePackage.senderRole == "BENDAHARA") "Bendahara (${envelopePackage.senderName})" else "Kepala Sekolah (${envelopePackage.senderName})"
            val successMsg = "Berhasil menyinkronkan data dari $senderInfo! ($mergedCount data baru/diperbarui)"
            _syncState.value = SyncState.Success(successMsg)

            Result.success(mergedCount)
        } catch (e: Exception) {
            val err = "Gagal memproses paket Kotak Surat: ${e.localizedMessage ?: "Kunci pairing atau data tidak valid"}"
            _syncState.value = SyncState.Error(err)
            Result.failure(e)
        }
    }
}
