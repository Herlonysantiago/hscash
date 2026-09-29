package hssolutions.hscash.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale

data class PixPayloadInfo(
    val payload: String,
    val valid: Boolean,
    val dynamic: Boolean,
    val key: String? = null,
    val recipient: String? = null,
    val city: String? = null,
    val txId: String? = null,
    val amountInCents: Long? = null,
    val error: String? = null
)

object PixUtils {
    private const val GUI = "BR.GOV.BCB.PIX"

    fun parse(raw: String): PixPayloadInfo {
        val payload = normalize(raw)
        if (!payload.startsWith("000201")) return invalid(payload, "Código Pix não reconhecido.")
        if (payload.length < 20 || !payload.endsWithCrcField()) return invalid(payload, "Estrutura do Pix incompleta.")
        if (!crcMatches(payload)) return invalid(payload, "Código Pix inválido: verificação de segurança não confere.")

        val fields = parseTlv(payload) ?: return invalid(payload, "Não foi possível interpretar o código Pix.")
        val merchant = fields["26"]?.let(::parseTlv).orEmpty()
        val guiValid = merchant["00"].equals(GUI, ignoreCase = true)
        if (!guiValid) return invalid(payload, "Este QR Code não pertence ao padrão Pix.")

        val additional = fields["62"]?.let(::parseTlv).orEmpty()
        val amount = fields["54"]?.toBigDecimalOrNull()
            ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()
        return PixPayloadInfo(
            payload = payload,
            valid = true,
            dynamic = merchant["25"]?.isNotBlank() == true,
            key = merchant["01"]?.takeIf(String::isNotBlank),
            recipient = fields["59"]?.takeIf(String::isNotBlank),
            city = fields["60"]?.takeIf(String::isNotBlank),
            txId = additional["05"]?.takeIf { it.isNotBlank() && it != "***" },
            amountInCents = amount
        )
    }

    fun isLikelyPix(raw: String): Boolean = normalize(raw).startsWith("000201")

    // Espaços podem fazer parte do nome e da cidade dentro do BR Code. Remover
    // todos eles altera os comprimentos TLV e invalida o CRC do Pix.
    fun normalize(raw: String): String = raw.trim().replace("\r", "").replace("\n", "")

    fun generateStatic(
        key: String,
        recipient: String,
        city: String,
        amountInCents: Long?,
        txId: String? = null
    ): String {
        require(key.isNotBlank()) { "Informe a chave Pix." }
        val merchant = field("00", GUI) + field("01", key.trim())
        val amount = amountInCents?.takeIf { it > 0 }?.let {
            field("54", BigDecimal(it).movePointLeft(2).setScale(2).toPlainString())
        }.orEmpty()
        val additional = field("62", field("05", sanitize(txId.orEmpty()).take(25).ifBlank { "***" }))
        val withoutCrc = buildString {
            append("000201")
            append(field("26", merchant))
            append("52040000")
            append("5303986")
            append(amount)
            append("5802BR")
            append(field("59", sanitize(recipient).take(25).ifBlank { "HS CASH" }))
            append(field("60", sanitize(city).take(15).ifBlank { "SAO PAULO" }))
            append(additional)
            append("6304")
        }
        return withoutCrc + crc16(withoutCrc)
    }

    fun moneyText(cents: Long): String = java.text.NumberFormat
        .getCurrencyInstance(Locale("pt", "BR"))
        .format(BigDecimal(cents).movePointLeft(2))

    fun moneyToCents(value: String): Long? {
        val clean = value.replace("R$", "", ignoreCase = true).replace("\u00A0", "").replace(" ", "")
        val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.longValueExact()
    }

    private fun invalid(payload: String, error: String) = PixPayloadInfo(payload, false, false, error = error)

    private fun String.endsWithCrcField(): Boolean = length >= 8 && substring(length - 8, length - 4) == "6304"

    private fun crcMatches(payload: String): Boolean {
        val expected = payload.takeLast(4).uppercase(Locale.ROOT)
        return crc16(payload.dropLast(4)) == expected
    }

    private fun parseTlv(value: String): Map<String, String>? {
        val result = linkedMapOf<String, String>()
        var index = 0
        while (index < value.length) {
            if (index + 4 > value.length) return null
            val id = value.substring(index, index + 2)
            val length = value.substring(index + 2, index + 4).toIntOrNull() ?: return null
            val start = index + 4
            val end = start + length
            if (end > value.length) return null
            result[id] = value.substring(start, end)
            index = end
        }
        return result
    }

    private fun field(id: String, value: String): String {
        require(value.length <= 99) { "Campo Pix muito longo." }
        return id + value.length.toString().padStart(2, '0') + value
    }

    private fun sanitize(value: String): String {
        val noAccents = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return noAccents.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9 .-]"), " ").replace(Regex("\\s+"), " ")
    }

    private fun crc16(value: String): String {
        var crc = 0xFFFF
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase(Locale.ROOT).padStart(4, '0')
    }
}
