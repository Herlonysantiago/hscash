package hssolutions.hscash.cloud

import android.content.Context
import android.webkit.MimeTypeMap
import hssolutions.hscash.ui.ContaSalva
import hssolutions.hscash.ui.ContaStatus
import hssolutions.hscash.ui.ContaStorage
import hssolutions.hscash.ui.LocalSpace
import hssolutions.hscash.ui.ReceiptHelper
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.File
import java.time.Instant
import java.util.Locale

internal data class CloudAttachmentSyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0
)

internal object CloudAttachmentSyncRepository {
    private const val BUCKET = "hs-cash-documents"
    private const val MAX_FILE_BYTES = 20L * 1024L * 1024L
    private const val TYPE_BOLETO = "BOLETO"
    private const val TYPE_RECEIPT = "COMPROVANTE"

    suspend fun sync(
        context: Context,
        spaces: List<LocalSpace>,
        localAccounts: List<ContaSalva>,
        remoteAccounts: List<CloudAccount>
    ): CloudAttachmentSyncResult {
        val client = SupabaseProvider.client ?: return CloudAttachmentSyncResult()
        val bucket = client.storage.from(BUCKET)
        val cloudSpaceByLocal = spaces.mapNotNull { space -> space.cloudId?.let { space.id to it } }.toMap()
        val remoteAccountByLegacy = remoteAccounts.associateBy { it.spaceId to (it.legacyId ?: "cloud-${it.id}") }
        var remoteAttachments = client.from("attachments").select().decodeList<CloudAttachment>()
        var uploaded = 0
        var downloaded = 0
        var deleted = 0
        var skipped = 0

        localAccounts.filter { it.status != ContaStatus.EXCLUIDO }.forEach { local ->
            val cloudSpaceId = cloudSpaceByLocal[local.spaceId] ?: return@forEach
            val remoteAccount = remoteAccountByLegacy[cloudSpaceId to local.id] ?: return@forEach
            var receiptPath = local.comprovantePath
            var boletoPath = local.boletoPdfPath
            var receiptCloudPath = local.comprovanteCloudPath
            var boletoCloudPath = local.boletoPdfCloudPath
            var localPathsChanged = false
            var cloudPathsChanged = false

            listOf(TYPE_RECEIPT, TYPE_BOLETO).forEach { type ->
                val currentPath = if (type == TYPE_RECEIPT) receiptPath else boletoPath
                var currentCloudPath = if (type == TYPE_RECEIPT) receiptCloudPath else boletoCloudPath
                val localFile = currentPath?.let(::File)?.takeIf(File::isFile)
                val remote = newestAttachment(remoteAttachments, remoteAccount.id, type)

                if (localFile != null) {
                    val expectedName = cloudFileName(type, localFile.name)
                    val matchesTrackedCloud = currentCloudPath != null && (
                        currentCloudPath!!.substringAfterLast('/') == expectedName ||
                            currentCloudPath!!.substringAfterLast('/') == localFile.name
                        )
                    if (remote?.deletedAt != null && remote.storagePath == currentCloudPath && matchesTrackedCloud) {
                        ReceiptHelper.deleteStoredFile(currentPath)
                        if (type == TYPE_RECEIPT) receiptPath = null else boletoPath = null
                        currentCloudPath = null
                        localPathsChanged = true
                        cloudPathsChanged = true
                        deleted++
                    } else if (remote?.deletedAt == null && remote?.storagePath == currentCloudPath && matchesTrackedCloud) {
                        // Arquivo local e remoto já representam a mesma versão.
                    } else if (remote?.deletedAt == null && currentCloudPath == null &&
                        remote!!.storagePath.substringAfterLast('/') == expectedName &&
                        remote.sizeInBytes == localFile.length()
                    ) {
                        currentCloudPath = remote.storagePath
                        cloudPathsChanged = true
                    } else if (localFile.length() !in 1..MAX_FILE_BYTES || mimeTypeFor(localFile) == "application/octet-stream") {
                        skipped++
                    } else {
                        val mimeType = mimeTypeFor(localFile)
                        val storagePath = "$cloudSpaceId/${remoteAccount.id}/$expectedName"
                        val uploadedNow = runCatching {
                            bucket.upload(storagePath, localFile.readBytes()) {
                                upsert = true
                                contentType = ContentType.parse(mimeType)
                            }
                            client.from("attachments").insert(
                                CloudAttachmentWrite(
                                    spaceId = cloudSpaceId,
                                    accountId = remoteAccount.id,
                                    tipo = type,
                                    storagePath = storagePath,
                                    mimeType = mimeType,
                                    sizeInBytes = localFile.length()
                                )
                            )
                            true
                        }.getOrElse {
                            runCatching { bucket.delete(storagePath) }
                            false
                        }
                        if (uploadedNow) {
                            remote?.takeIf { it.deletedAt == null && it.storagePath != storagePath }?.let { old ->
                                tombstone(client, bucket, old)
                                remoteAttachments = remoteAttachments.map {
                                    if (it.id == old.id) it.copy(deletedAt = Instant.now().toString()) else it
                                }
                            }
                            currentCloudPath = storagePath
                            cloudPathsChanged = true
                            uploaded++
                            remoteAttachments = remoteAttachments + CloudAttachment(
                                id = "local-$storagePath",
                                spaceId = cloudSpaceId,
                                accountId = remoteAccount.id,
                                tipo = type,
                                storagePath = storagePath,
                                mimeType = mimeType,
                                sizeInBytes = localFile.length(),
                                createdAt = Instant.now().toString()
                            )
                        } else {
                            skipped++
                        }
                    }
                } else {
                    if (currentCloudPath != null) {
                        if (remote?.deletedAt == null && remote?.storagePath == currentCloudPath) {
                            if (tombstone(client, bucket, remote!!)) {
                                remoteAttachments = remoteAttachments.map {
                                    if (it.id == remote.id) it.copy(deletedAt = Instant.now().toString()) else it
                                }
                                deleted++
                            } else {
                                skipped++
                                return@forEach
                            }
                        }
                        currentCloudPath = null
                        cloudPathsChanged = true
                    } else if (remote?.deletedAt == null) {
                        val newPath = runCatching {
                            val bytes = bucket.downloadAuthenticated(remote!!.storagePath)
                            ReceiptHelper.saveCloudAttachment(
                                context = context,
                                contaId = local.id,
                                bytes = bytes,
                                mimeType = remote!!.mimeType,
                                boleto = type == TYPE_BOLETO,
                                preferredName = remote!!.storagePath.substringAfterLast('/')
                            )
                        }.getOrNull()
                        if (newPath == null) {
                            skipped++
                        } else {
                            if (type == TYPE_RECEIPT) receiptPath = newPath else boletoPath = newPath
                            currentCloudPath = remote!!.storagePath
                            downloaded++
                            localPathsChanged = true
                            cloudPathsChanged = true
                        }
                    }
                }

                if (type == TYPE_RECEIPT) receiptCloudPath = currentCloudPath else boletoCloudPath = currentCloudPath
            }

            if (localPathsChanged) {
                ContaStorage.atualizarConta(
                    context,
                    local.copy(comprovantePath = receiptPath, boletoPdfPath = boletoPath),
                    saveAfterUpdate = false
                )
            }
            if (cloudPathsChanged) {
                ContaStorage.atualizarCaminhosNuvem(
                    context, local.id, receiptCloudPath, boletoCloudPath
                )
            }
        }
        if (downloaded > 0 || deleted > 0) ContaStorage.finalizarAlteracoesEmLote(context)
        return CloudAttachmentSyncResult(uploaded, downloaded, deleted, skipped)
    }

    private fun newestAttachment(all: List<CloudAttachment>, accountId: String, type: String): CloudAttachment? =
        all.asSequence()
            .filter { it.accountId == accountId && it.tipo == type }
            .maxByOrNull { cloudInstantToMillisOrNull(it.createdAt) ?: 0L }

    private suspend fun tombstone(
        client: io.github.jan.supabase.SupabaseClient,
        bucket: io.github.jan.supabase.storage.BucketApi,
        attachment: CloudAttachment
    ): Boolean = runCatching {
        bucket.delete(attachment.storagePath)
        client.from("attachments").update(CloudAttachmentDeletedUpdate(Instant.now().toString())) {
            filter { eq("id", attachment.id) }
        }
        true
    }.getOrDefault(false)

    private fun mimeTypeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: when (extension) {
                "pdf" -> "application/pdf"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
    }

    internal fun cloudFileName(type: String, localName: String): String {
        val safeName = localName
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-', '.')
            .takeLast(120)
            .ifBlank { "arquivo" }
        return "${type.lowercase(Locale.US)}-$safeName"
    }
}
