package com.uniroot.patch

import com.uniroot.provider.RootProvider

/**
 * Boot镜像修补结果
 */
data class PatchResult(
    val success: Boolean,
    val outputPath: String? = null,
    val error: String? = null
)

/**
 * Boot镜像修补器
 * 根据不同的Root方案执行不同的修补逻辑
 */
object BootPatcher {

    /**
     * 修补Boot镜像
     */
    fun patch(
        bootImagePath: String,
        outputPath: String,
        provider: RootProvider,
        superKey: String? = null,
        kpmModules: List<String>? = null,
        onProgress: (Float) -> Unit = {}
    ): PatchResult {
        return try {
            onProgress(0.1f)

            // 验证输入
            if (bootImagePath.isEmpty()) {
                return PatchResult(false, error = "Boot镜像路径为空")
            }

            if (provider.requiresSuperKey && superKey.isNullOrBlank()) {
                return PatchResult(false, error = "APatch需要设置超级密钥")
            }

            onProgress(0.2f)

            // 根据不同方案执行修补
            val success = when (provider) {
                RootProvider.KERNELSU -> patchKernelSU(bootImagePath, outputPath, onProgress)
                RootProvider.KERNELSU_NEXT -> patchKernelSUNext(bootImagePath, outputPath, onProgress)
                RootProvider.SUKISU_ULTRA -> patchSukiSUUltra(bootImagePath, outputPath, kpmModules, onProgress)
                RootProvider.MAGISK -> patchMagisk(bootImagePath, outputPath, onProgress)
                RootProvider.MAGISK_KITSUNE -> patchMagiskKitsune(bootImagePath, outputPath, onProgress)
                RootProvider.APATCH -> patchAPatch(bootImagePath, outputPath, superKey!!, kpmModules, onProgress)
            }

            onProgress(1.0f)

            if (success) {
                PatchResult(true, outputPath)
            } else {
                PatchResult(false, error = "修补失败")
            }
        } catch (e: Exception) {
            PatchResult(false, error = e.message ?: "未知错误")
        }
    }

    private fun patchKernelSU(
        bootImagePath: String,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // KernelSU修补：注入kernelsu.ko到boot镜像
        val cmd = "ksud boot-patch --boot $bootImagePath --out $outputPath"
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun patchKernelSUNext(
        bootImagePath: String,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // KernelSU Next修补：支持SuList功能
        val cmd = "ksud boot-patch --boot $bootImagePath --out $outputPath --sulist"
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun patchSukiSUUltra(
        bootImagePath: String,
        outputPath: String,
        kpmModules: List<String>?,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // SukiSU Ultra修补：支持KPM模块嵌入
        var cmd = "ksud boot-patch --boot $bootImagePath --out $outputPath --kpm"
        kpmModules?.forEach { kpm ->
            cmd += " --embed-kpm $kpm"
        }
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun patchMagisk(
        bootImagePath: String,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // Magisk修补：修改ramdisk注入magiskinit
        val cmd = "magiskboot unpack $bootImagePath && " +
                "magiskboot cpio ramdisk.cpio 'magisk' && " +
                "magiskboot repack $bootImagePath $outputPath"
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun patchMagiskKitsune(
        bootImagePath: String,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // Magisk Kitsune修补：增强隐藏
        val cmd = "magiskboot unpack $bootImagePath && " +
                "magiskboot cpio ramdisk.cpio 'magisk' && " +
                "magiskboot cpio ramdisk.cpio 'kitsune-patch' && " +
                "magiskboot repack $bootImagePath $outputPath"
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun patchAPatch(
        bootImagePath: String,
        outputPath: String,
        superKey: String,
        kpmModules: List<String>?,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        // APatch修补：kprobe修补 + SuperKey + KPM嵌入
        var cmd = "kptools patch --boot $bootImagePath --out $outputPath --skey $superKey"
        kpmModules?.forEach { kpm ->
            cmd += " --embed-kpm $kpm"
        }
        onProgress(0.5f)
        val result = executeRootCommand(cmd)
        onProgress(0.8f)
        return result
    }

    private fun executeRootCommand(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }
}
