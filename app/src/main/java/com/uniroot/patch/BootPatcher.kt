package com.uniroot.patch

import android.content.Context
import com.uniroot.UniRootApp
import com.uniroot.provider.RootProvider
import com.uniroot.util.BinaryExtractor
import java.io.File

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
 * 使用内嵌的二进制工具（ksud/kptools/magiskboot等）
 */
object BootPatcher {

    private val context: Context
        get() = UniRootApp.instance

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

            if (bootImagePath.isEmpty()) {
                return PatchResult(false, error = "Boot镜像路径为空")
            }

            if (provider.requiresSuperKey && superKey.isNullOrBlank()) {
                return PatchResult(false, error = "APatch需要设置超级密钥")
            }

            // 提取内嵌二进制到工作目录
            BinaryExtractor.extractProviderBinaries(context, provider.id)
            onProgress(0.2f)

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
        val ksud = BinaryExtractor.getKsudPath(context)
        val ksuKo = BinaryExtractor.selectKernelSUKo(context, "kernelsu")
        val magiskboot = BinaryExtractor.getMagiskbootPath(context)
        val mb = if (magiskboot.isNotEmpty()) magiskboot else "magiskboot"

        val workDir = "/data/local/tmp/uniroot_ksu"

        val cmd = buildString {
            append("mkdir -p $workDir && cd $workDir && ")
            if (ksud.isNotEmpty()) {
                // 使用ksud修补（优先）
                append("$ksud boot-patch --boot $bootImagePath --out $outputPath")
            } else if (ksuKo.isNotEmpty()) {
                // 使用magiskboot + 内核模块直接注入
                append("cp $ksuKo $workDir/kernelsu.ko && ")
                append("$mb unpack $bootImagePath && ")
                append("$mb cpio ramdisk.cpio 'mkdir kernelsu' && ")
                append("$mb cpio ramdisk.cpio 'add 0644 kernelsu/kernelsu.ko $workDir/kernelsu.ko' && ")
                append("$mb repack $bootImagePath $outputPath")
            } else {
                append("ksud boot-patch --boot $bootImagePath --out $outputPath")
            }
            append(" && cd / && rm -rf $workDir")
        }
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
        val ksud = BinaryExtractor.getKsudPath(context)
        val cmd = if (ksud.isNotEmpty()) {
            "$ksud boot-patch --boot $bootImagePath --out $outputPath --sulist"
        } else {
            "ksud boot-patch --boot $bootImagePath --out $outputPath --sulist"
        }
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
        val ksud = BinaryExtractor.getKsudPath(context)
        val cmd = buildString {
            if (ksud.isNotEmpty()) {
                append("$ksud boot-patch --boot $bootImagePath --out $outputPath --kpm")
            } else {
                append("ksud boot-patch --boot $bootImagePath --out $outputPath --kpm")
            }
            kpmModules?.forEach { kpm ->
                append(" --embed-kpm $kpm")
            }
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
        val magiskboot = BinaryExtractor.getMagiskbootPath(context)
        val magisk64 = BinaryExtractor.getMagisk64Path(context)
        val magiskinit = BinaryExtractor.getMagiskinitPath(context)
        val magiskpolicy = BinaryExtractor.getMagiskpolicyPath(context)
        val mb = if (magiskboot.isNotEmpty()) magiskboot else "magiskboot"

        val workDir = "/data/local/tmp/uniroot_magisk"
        val magiskDir = BinaryExtractor.getBinDir(context).absolutePath + "/magisk"

        val cmd = buildString {
            append("mkdir -p $workDir && cd $workDir && ")
            // 复制所有magisk二进制到工作目录
            append("cp $magiskDir/* $workDir/ 2>/dev/null; ")
            // 使用magiskboot解包boot镜像
            append("$mb unpack $bootImagePath && ")
            // magiskinit作为init注入
            if (magiskinit.isNotEmpty()) {
                append("$mb cpio ramdisk.cpio backup ramdisk.cpio.orig && ")
                append("$mb cpio ramdisk.cpio 'add 0750 init $magiskinit' && ")
            } else {
                append("$mb cpio ramdisk.cpio 'magisk' && ")
            }
            // 重新打包
            append("$mb repack $bootImagePath $outputPath && ")
            append("cd / && rm -rf $workDir")
        }
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
        // Kitsune使用与Magisk相同的修补逻辑
        return patchMagisk(bootImagePath, outputPath, onProgress)
    }

    private fun patchAPatch(
        bootImagePath: String,
        outputPath: String,
        superKey: String,
        kpmModules: List<String>?,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(0.3f)
        val kptools = BinaryExtractor.getKptoolsPath(context)
        val kpimg = BinaryExtractor.getKpimgPath(context)
        val kpatch = BinaryExtractor.getKpatchPath(context)
        val apatchMagiskboot = BinaryExtractor.getAPatchMagiskbootPath(context)
        val kpt = if (kptools.isNotEmpty()) kptools else "kptools"

        val workDir = "/data/local/tmp/uniroot_apatch"
        val apatchDir = BinaryExtractor.getBinDir(context).absolutePath + "/apatch"

        val cmd = buildString {
            append("mkdir -p $workDir && cd $workDir && ")
            // 复制所有apatch二进制到工作目录
            append("cp $apatchDir/* $workDir/ 2>/dev/null; ")
            // 使用kptools修补boot镜像
            append("$kpt patch --boot $bootImagePath --out $outputPath --skey $superKey")
            if (kpimg.isNotEmpty()) {
                append(" --kpimg $kpimg")
            }
            if (kpatch.isNotEmpty()) {
                append(" --kpatch $kpatch")
            }
            kpmModules?.forEach { kpm ->
                append(" --embed-kpm $kpm")
            }
            append(" && cd / && rm -rf $workDir")
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
