package com.uniroot.patch

/**
 * KPM (Kernel Patch Module) 管理器
 * 支持加载、嵌入、卸载KPM模块（APatch和SukiSU Ultra支持）
 */
object KPMManager {

    data class KPMModule(
        val name: String,
        val path: String,
        val isLoaded: Boolean = false,
        val isEmbedded: Boolean = false
    )

    /**
     * 加载KPM模块到内核
     */
    fun loadKPM(kpmPath: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "insmod $kpmPath")
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 卸载KPM模块
     */
    fun unloadKPM(name: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "rmmod $name")
            )
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 将KPM模块嵌入到Boot镜像
     */
    fun embedKPM(kpmPath: String, bootImagePath: String): Boolean {
        return try {
            val cmd = "kptools embed-kpm --kpm $kpmPath --boot $bootImagePath"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 列出已加载的KPM模块
     */
    fun listLoadedKPMs(): List<KPMModule> {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "cat /proc/kpm/list 2>/dev/null || lsmod | grep kpm_")
            )
            process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            output.lines().filter { it.isNotBlank() }.map { line ->
                KPMModule(
                    name = line.substringBefore(" ").trim(),
                    path = "",
                    isLoaded = true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
