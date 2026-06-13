package com.uniroot.patch

/**
 * AnyKernel3刷入器
 * 支持刷入AK3格式的ZIP压缩包
 */
object AK3Flasher {

    data class FlashResult(
        val success: Boolean,
        val message: String = ""
    )

    /**
     * 刷入AnyKernel3 ZIP
     */
    fun flash(zipPath: String, onProgress: (Float) -> Unit = {}): FlashResult {
        return try {
            onProgress(0.1f)

            // 验证ZIP文件
            if (!validateAK3Zip(zipPath)) {
                return FlashResult(false, "无效的AnyKernel3 ZIP文件")
            }

            onProgress(0.2f)

            // 执行刷入
            val cmd = """
                cd /data/local/tmp &&
                mkdir -p ak3_flash &&
                cd ak3_flash &&
                unzip -o $zipPath &&
                sh ak3-flash.sh
            """.trimIndent()

            onProgress(0.5f)

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()

            onProgress(0.9f)

            // 清理
            Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -rf /data/local/tmp/ak3_flash"))

            onProgress(1.0f)

            if (exitCode == 0) {
                FlashResult(true, "刷入成功，请重启设备")
            } else {
                val error = process.errorStream.bufferedReader().readText()
                FlashResult(false, "刷入失败: $error")
            }
        } catch (e: Exception) {
            FlashResult(false, "刷入失败: ${e.message}")
        }
    }

    /**
     * 验证AK3 ZIP文件格式
     */
    private fun validateAK3Zip(zipPath: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "unzip -l $zipPath | grep -q 'ak3-flash.sh'")
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
