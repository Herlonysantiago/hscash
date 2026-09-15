package hssolutions.hscash.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    @Test
    fun `backup zip contem manifesto boleto e comprovante reais`() {
        val tempDir = Files.createTempDirectory("hs-cash-backup-test").toFile()
        try {
            val comprovante = tempDir.resolve("comprovante.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val boleto = tempDir.resolve("boleto.pdf").apply { writeBytes(byteArrayOf(4, 5, 6, 7)) }
            val conta = ContaSalva(
                id = "conta-1",
                codigo = "123",
                banco = "Banco",
                valor = "R$ 10,00",
                vencimento = "15/09/2026",
                tipo = "Boleto",
                criadaEm = 1L,
                pagoEm = 1_725_000_000_000L,
                formaPagamento = "PIX",
                observacaoPagamento = "Pago pelo aplicativo do banco",
                comprovantePath = comprovante.absolutePath,
                boletoPdfPath = boleto.absolutePath
            )
            val output = ByteArrayOutputStream()

            assertTrue(BackupHelper.writeArchive(listOf(conta), output))
            val entries = unzip(output.toByteArray())
            val manifestText = entries.getValue("manifest.json").toString(Charsets.UTF_8)
            val manifest = JSONObject(manifestText)
            val item = manifest.getJSONArray("contas").getJSONObject(0)

            assertEquals(2, manifest.getInt("version"))
            assertEquals("PIX", item.getString("formaPagamento"))
            assertEquals("Pago pelo aplicativo do banco", item.getString("observacaoPagamento"))
            assertFalse(manifestText.contains(tempDir.absolutePath))
            assertArrayEquals(comprovante.readBytes(), entries.getValue(item.getString("comprovanteBackupEntry")))
            assertArrayEquals(boleto.readBytes(), entries.getValue(item.getString("boletoPdfBackupEntry")))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `arquivo inexistente nao gera caminho quebrado no manifesto`() {
        val conta = ContaSalva(
            id = "sem-anexo",
            codigo = "123",
            banco = "Banco",
            valor = "R$ 10,00",
            vencimento = "15/09/2026",
            tipo = "Boleto",
            criadaEm = 1L,
            comprovantePath = "arquivo-que-nao-existe.jpg"
        )
        val output = ByteArrayOutputStream()

        BackupHelper.writeArchive(listOf(conta), output)
        val manifest = JSONObject(unzip(output.toByteArray()).getValue("manifest.json").toString(Charsets.UTF_8))
        val item = manifest.getJSONArray("contas").getJSONObject(0)

        assertFalse(item.has("comprovanteBackupEntry"))
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }
}
