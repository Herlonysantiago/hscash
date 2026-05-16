package hssolutions.hscash.ui

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    private var ultimoCodigoValido: String? = null
    private var leiturasConfirmadas = 0

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val codigoValido = barcodes
                    .asSequence()
                    .mapNotNull { it.rawValue }
                    .map { it.filter(Char::isDigit) }
                    .firstOrNull { isLikelyBoletoCode(it) }

                if (codigoValido == null) {
                    leiturasConfirmadas = 0
                    ultimoCodigoValido = null
                    return@addOnSuccessListener
                }

                if (codigoValido == ultimoCodigoValido) {
                    leiturasConfirmadas += 1
                } else {
                    ultimoCodigoValido = codigoValido
                    leiturasConfirmadas = 1
                }

                if (leiturasConfirmadas >= 2) {
                    onBarcodeDetected(codigoValido)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
