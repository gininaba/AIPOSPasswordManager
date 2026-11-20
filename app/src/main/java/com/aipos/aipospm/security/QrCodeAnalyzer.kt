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

    private var reusableBuffer = ByteArray(0)
    private var lastScannedText: String? = null
    private var lastScannedTimeMs: Long = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val format = image.format
            if (format == ImageFormat.YUV_420_888 || format == ImageFormat.YV12) {
                val buffer = image.planes[0].buffer
                val remaining = buffer.remaining()
                if (reusableBuffer.size < remaining) {
                    reusableBuffer = ByteArray(remaining)
                }
                buffer.get(reusableBuffer, 0, remaining)

                val rowStride = image.planes[0].rowStride
                val source = PlanarYUVLuminanceSource(
                    reusableBuffer,
                    rowStride,
                    image.height,
                    0,
                    0,
                    image.width,
                    image.height,
                    false
                )

                val bitmap = BinaryBitmap(HybridBinarizer(source))

                try {
                    // Use decodeWithState so the QR_CODE-only hints are preserved across frames
                    val result = reader.decodeWithState(bitmap)
                    val text = result.text
                    val now = System.currentTimeMillis()
                    if (text != null && (text != lastScannedText || now - lastScannedTimeMs > 1000L)) {
                        lastScannedText = text
                        lastScannedTimeMs = now
                        onQrCodeScanned(text)
                    }
                } catch (_: Exception) {
                    // Decode failed, which is expected if no QR code is present in this frame.
                } finally {
                    reader.reset()
                }
            }
        } finally {
            image.close()
        }
    }
}
