package com.aipos.aipospm.security

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        try {
            val format = image.format
            if (format == ImageFormat.YUV_420_888 || format == ImageFormat.YV12) {
                val buffer = image.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                val source = PlanarYUVLuminanceSource(
                    data,
                    image.width,
                    image.height,
                    0,
                    0,
                    image.width,
                    image.height,
                    false
                )

                val bitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    val result = reader.decode(bitmap)
                    onQrCodeScanned(result.text)
                } catch (e: Exception) {
                    // Decode failed, which is expected if no QR code is present in this frame.
                }
            }
        } finally {
            image.close()
        }
    }
}
