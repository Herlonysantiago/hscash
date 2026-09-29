package hssolutions.hscash.cloud

import hssolutions.hscash.ui.ContaSalva
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudSyncRepositoryTest {
    @Test
    fun convertsBrazilianMoneyWithoutMovingDecimalPlaces() {
        assertEquals(9333L, cloudMoneyToCents("R$ 93,33"))
        assertEquals(2_379_236_702L, cloudMoneyToCents("R$ 23.792.367,02"))
    }

    @Test
    fun convertsDatesInBothDirections() {
        assertEquals("2026-09-21", cloudLocalDateToIso("21/09/2026"))
        assertEquals("21/09/2026", cloudIsoDateToLocal("2026-09-21"))
        assertNull(cloudLocalDateToIso("31/02/2026"))
    }

    @Test
    fun readsSupabaseTimestampWithUtcOffset() {
        assertEquals(
            1_788_558_430_726L,
            cloudInstantToMillisOrNull("2026-09-04T21:47:10.726+00:00")
        )
        assertEquals(
            1_788_558_430_726L,
            cloudInstantToMillisOrNull("2026-09-04T21:47:10.726Z")
        )
    }

    @Test
    fun createsSafeStableCloudAttachmentName() {
        assertEquals(
            "comprovante-conta-123.pdf",
            CloudAttachmentSyncRepository.cloudFileName("COMPROVANTE", "conta 123.pdf")
        )
    }

    @Test
    fun uploadsOnlyWhenLocalVersionIsNewer() {
        assertEquals(true, shouldUploadLocal(localUpdatedAt = 2_000L, remoteUpdatedAt = 1_000L))
        assertEquals(false, shouldUploadLocal(localUpdatedAt = 1_000L, remoteUpdatedAt = 2_000L))
        assertEquals(false, shouldUploadLocal(localUpdatedAt = 2_000L, remoteUpdatedAt = 2_000L))
        assertEquals(true, shouldUploadLocal(localUpdatedAt = 0L, remoteUpdatedAt = null))
    }

    @Test
    fun identicalRemoteAccountIsNotUploadedAgain() {
        val local = ContaSalva(
            id = "local-1",
            codigo = "manual-1",
            banco = "Cadastro manual",
            valor = "R$ 93,33",
            vencimento = "23/09/2026",
            tipo = "Conta manual",
            criadaEm = 1_000L,
            fornecedor = "Brisanet",
            categoria = "Internet",
            subcategoria = "Provedor",
            responsavel = "Maria"
        )
        val remote = CloudAccount(
            id = "remote-1",
            spaceId = "space-1",
            legacyId = "local-1",
            codigo = "manual-1",
            banco = "Cadastro manual",
            valueInCents = 9_333L,
            vencimento = "2026-09-23",
            tipo = "Conta manual",
            fornecedor = "Brisanet",
            categoria = "Internet",
            subcategoria = "Provedor",
            responsavel = "Maria"
        )

        assertEquals(true, remoteMatchesLocal(local, remote))
        assertEquals(false, remoteMatchesLocal(local.copy(valor = "R$ 100,00"), remote))
        assertEquals(false, shouldDownloadRemote(local, remote))
        assertEquals(true, shouldDownloadRemote(local.copy(valor = "R$ 100,00"), remote))
        assertEquals(true, shouldDownloadRemote(null, remote))
    }
}
