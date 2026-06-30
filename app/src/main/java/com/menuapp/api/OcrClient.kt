package com.menuapp.api

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 中文 OCR 客户端（离线）
 * 从图像中提取文字，用于菜谱识别
 */
object OcrClient {

    /**
     * 使用 ML Kit 中文 OCR 从图像提取文字
     * @param bitmap 待识别的图像
     * @return 识别出的纯文本
     */
    suspend fun extractText(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        try {
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val task = recognizer.process(inputImage)

            // 等待识别完成
            while (!task.isComplete && !task.isCanceled) {
                Thread.sleep(50)
            }

            val resultText: String
            if (task.isSuccessful) {
                resultText = task.result.text
            } else {
                val error = task.exception ?: Exception("OCR 识别失败")
                recognizer.close()
                return@withContext Result.failure(error)
            }

            recognizer.close()
            Result.success(resultText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
