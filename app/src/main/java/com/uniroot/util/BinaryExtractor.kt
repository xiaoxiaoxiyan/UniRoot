package com.uniroot.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 二进制文件提取器
 * 从APK assets中提取工具二进制到应用私有目录或指定工作目录
 */
object BinaryExtractor {

    private const val BIN_DIR = "bin"

    fun getBinDir(context: Context): File = File(context.filesDir, BIN_DIR)

    /**
     * 提取指定方案的所有二进制文件到默认目录
     */
    fun extractProviderBinaries(context: Context, providerId: String): File {
        val targetDir = File(getBinDir(context), providerId)
        extractToDir(context, providerId, targetDir)
        return targetDir
    }

    /**
     * 提取指定方案的所有二进制文件到指定工作目录
     * 用于修补时创建独立的工作环境
     */
    fun extractToWorkDir(context: Context, providerId: String, workDir: File): File {
        extractToDir(context, providerId, workDir)
        return workDir
    }

    private fun extractToDir(context: Context, providerId: String, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()

        val assetDir = when (providerId) {
            "kernelsu" -> "kernelsu"
            "kernelsu_next" -> "kernelsu_next"
            "sukisu_ultra" -> "sukisu_ultra"
            "apatch" -> "apatch"
            "magisk", "magisk_kitsune" -> "magisk"
            else -> return
        }

        val assets = context.assets.list(assetDir) ?: emptyArray()
        for (assetName in assets) {
            val targetFile = File(targetDir, assetName)
            try {
                context.assets.open("$assetDir/$assetName").use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setExecutable(true, false)
                targetFile.setReadable(true, false)
                targetFile.setWritable(true, false)
            } catch (_: Exception) { }
        }
    }

    /**
     * 提取所有方案二进制
     */
    fun extractAll(context: Context) {
        for (provider in listOf("kernelsu", "kernelsu_next", "sukisu_ultra", "apatch", "magisk")) {
            extractProviderBinaries(context, provider)
        }
    }

    fun getBinaryPath(context: Context, providerId: String, binaryName: String): String {
        val file = File(getBinDir(context), "$providerId/$binaryName")
        return if (file.exists()) file.absolutePath else ""
    }

    fun getKsudPath(context: Context): String {
        for (dir in listOf("kernelsu_next", "kernelsu")) {
            val path = getBinaryPath(context, dir, "ksud")
            if (path.isNotEmpty()) return path
        }
        return ""
    }

    fun getKptoolsPath(context: Context): String = getBinaryPath(context, "apatch", "kptools")
    fun getKpatchPath(context: Context): String = getBinaryPath(context, "apatch", "kpatch")
    fun getKpimgPath(context: Context): String = getBinaryPath(context, "apatch", "kpimg")
    fun getAPatchMagiskbootPath(context: Context): String = getBinaryPath(context, "apatch", "magiskboot")
    fun getMagiskbootPath(context: Context): String = getBinaryPath(context, "magisk", "magiskboot")
    fun getMagisk64Path(context: Context): String = getBinaryPath(context, "magisk", "magisk64")
    fun getMagiskinitPath(context: Context): String = getBinaryPath(context, "magisk", "magiskinit")
    fun getMagiskpolicyPath(context: Context): String = getBinaryPath(context, "magisk", "magiskpolicy")

    /**
     * 获取匹配当前内核的kernelsu.ko
     */
    fun selectKernelSUKo(context: Context, providerId: String): String {
        val dir = File(getBinDir(context), providerId)
        if (!dir.exists()) return ""

        val androidVer = android.os.Build.VERSION.RELEASE.split(".").firstOrNull() ?: ""
        val modules = dir.listFiles()?.filter { it.name.endsWith("_kernelsu.ko") } ?: emptyList()

        // 优先匹配当前Android版本
        for (module in modules) {
            if (module.name.contains("android$androidVer")) return module.absolutePath
        }
        return modules.firstOrNull()?.absolutePath ?: ""
    }

    /**
     * 获取匹配当前内核的kernelsu.ko文件名（用于在workDir中查找）
     */
    fun selectKernelSUKoName(context: Context, providerId: String): String {
        val dir = File(getBinDir(context), providerId)
        if (!dir.exists()) return ""

        val androidVer = android.os.Build.VERSION.RELEASE.split(".").firstOrNull() ?: ""
        val modules = dir.listFiles()?.filter { it.name.endsWith("_kernelsu.ko") } ?: emptyList()

        for (module in modules) {
            if (module.name.contains("android$androidVer")) return module.name
        }
        return modules.firstOrNull()?.name ?: ""
    }
}
