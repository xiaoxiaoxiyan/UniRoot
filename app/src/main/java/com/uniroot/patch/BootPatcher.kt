package com.uniroot.patch

import android.content.Context
import com.uniroot.UniRootApp
import com.uniroot.provider.RootProvider
import com.uniroot.util.BinaryExtractor
import java.io.File
import java.io.FileOutputStream

/**
 * Boot镜像修补结果
 */
data class PatchResult(
    val success: Boolean,
    val outputPath: String? = null,
    val error: String? = null,
    val logs: String = ""
)

/**
 * Boot镜像修补器
 *
 * 照抄KernelSU/Magisk/APatch的实际修补逻辑：
 * - KernelSU: 使用ksud boot-patch修补内核，或通过magiskboot注入.ko到ramdisk
 * - Magisk: magiskboot解包 → 修补ramdisk(magiskinit+overlay.d) → 重新打包
 * - APatch: kptools解包 → 修补kernel(注入kpimg) → 重新打包
 *
 * 关键：不使用su，通过ProcessBuilder直接运行二进制（设备未root也能修补）
 */
object BootPatcher {

    private val context: Context
        get() = UniRootApp.instance

    /**
     * 修补Boot镜像
     *
     * @param bootImagePath 用户提供的boot/init_boot镜像路径
     * @param outputPath 修补后镜像输出路径
     * @param provider Root方案
     * @param superKey APatch超级密钥
     * @param kpmModules KPM模块列表
     * @param keepForceEncrypt Magisk: 保持强制加密
     * @param keepVerity Magisk: 保持dm-verity
     * @param onProgress 进度回调
     * @param onLog 日志回调
     */
    fun patch(
        bootImagePath: String,
        outputPath: String,
        provider: RootProvider,
        superKey: String? = null,
        kpmModules: List<String>? = null,
        keepForceEncrypt: Boolean = true,
        keepVerity: Boolean = true,
        onProgress: (Float) -> Unit = {},
        onLog: (String) -> Unit = {}
    ): PatchResult {
        val logs = StringBuilder()

        fun log(msg: String) {
            logs.appendLine(msg)
            onLog(msg)
        }

        return try {
            onProgress(0.05f)
            log("=== UniRoot 修补器 ===")
            log("方案: ${provider.displayName}")
            log("输入: $bootImagePath")

            if (bootImagePath.isEmpty() || !File(bootImagePath).exists()) {
                return PatchResult(false, error = "Boot镜像文件不存在: $bootImagePath")
            }

            if (provider.requiresSuperKey && superKey.isNullOrBlank()) {
                return PatchResult(false, error = "APatch需要设置超级密钥")
            }

            // 创建独立工作目录
            val workDir = File(context.cacheDir, "patch_${provider.id}_${System.currentTimeMillis()}")
            workDir.deleteRecursively()
            workDir.mkdirs()
            onProgress(0.1f)
            log("工作目录: ${workDir.absolutePath}")

            // 提取二进制到工作目录
            BinaryExtractor.extractToWorkDir(context, provider.id, workDir)
            // Magisk修补还需要apatch的magiskboot（如果有的话）
            if (provider == RootProvider.MAGISK || provider == RootProvider.MAGISK_KITSUNE) {
                BinaryExtractor.extractToWorkDir(context, "apatch", workDir)
            }
            onProgress(0.15f)
            log("二进制提取完成")

            // 复制boot镜像到工作目录
            val bootFile = File(workDir, "boot.img")
            File(bootImagePath).inputStream().use { input ->
                bootFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            onProgress(0.2f)
            log("Boot镜像已复制 (${bootFile.length()} bytes)")

            // 执行修补
            val success = when (provider) {
                RootProvider.KERNELSU -> patchKernelSU(workDir, bootFile, onProgress, ::log)
                RootProvider.KERNELSU_NEXT -> patchKernelSUNext(workDir, bootFile, onProgress, ::log)
                RootProvider.SUKISU_ULTRA -> patchSukiSUUltra(workDir, bootFile, kpmModules, onProgress, ::log)
                RootProvider.MAGISK -> patchMagisk(workDir, bootFile, keepForceEncrypt, keepVerity, onProgress, ::log)
                RootProvider.MAGISK_KITSUNE -> patchMagisk(workDir, bootFile, keepForceEncrypt, keepVerity, onProgress, ::log)
                RootProvider.APATCH -> patchAPatch(workDir, bootFile, superKey!!, kpmModules, onProgress, ::log)
            }

            onProgress(0.9f)

            if (success) {
                // 查找修补后的镜像
                val patchedFile = findPatchedImage(workDir)
                if (patchedFile != null && patchedFile.exists() && patchedFile.length() > 0) {
                    // 复制到输出路径
                    val outFile = File(outputPath)
                    outFile.parentFile?.mkdirs()
                    patchedFile.inputStream().use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    onProgress(1.0f)
                    log("修补成功! 输出: $outputPath (${outFile.length()} bytes)")
                    PatchResult(true, outputPath, logs = logs.toString())
                } else {
                    log("错误: 修补完成但未找到输出镜像")
                    PatchResult(false, error = "修补完成但未找到输出镜像", logs = logs.toString())
                }
            } else {
                log("修补失败")
                PatchResult(false, error = "修补失败", logs = logs.toString())
            }
        } catch (e: Exception) {
            log("异常: ${e.message}")
            PatchResult(false, error = e.message ?: "未知错误", logs = logs.toString())
        }
    }

    /**
     * 查找修补后的镜像文件
     * Magisk输出new-boot.img, APatch输出new-boot.img, KSU直接输出到指定路径
     */
    private fun findPatchedImage(workDir: File): File? {
        val candidates = listOf(
            File(workDir, "new-boot.img"),
            File(workDir, "patched-boot.img")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    // ==================== 命令执行 ====================

    data class CommandResult(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * 使用ProcessBuilder直接运行二进制（不需要su）
     */
    private fun runCommand(
        workDir: File,
        vararg command: String,
        env: Map<String, String> = emptyMap()
    ): CommandResult {
        return try {
            val processBuilder = ProcessBuilder(*command)
            processBuilder.directory(workDir)
            processBuilder.redirectErrorStream(true)

            val envMap = processBuilder.environment()
            envMap["PATH"] = "${workDir.absolutePath}:/system/bin:/system/xbin"
            envMap["HOME"] = workDir.absolutePath
            env.forEach { (k, v) -> envMap[k] = v }

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(exitCode, output.trim())
        } catch (e: Exception) {
            CommandResult(-1, e.message ?: "执行失败")
        }
    }

    /**
     * 运行shell脚本（用于Magisk boot_patch.sh等）
     */
    private fun runShellScript(
        workDir: File,
        scriptContent: String,
        env: Map<String, String> = emptyMap()
    ): CommandResult {
        val scriptFile = File(workDir, "run_patch.sh")
        scriptFile.writeText(scriptContent)
        scriptFile.setExecutable(true)

        return runCommand(workDir, "/system/bin/sh", scriptFile.absolutePath, env = env)
    }

    // ==================== KernelSU 修补 ====================

    /**
     * KernelSU修补逻辑：
     * 1. 优先使用ksud boot-patch（内部实现了完整的内核修补）
     * 2. 回退：使用magiskboot解包 + 注入.ko到ramdisk + 重新打包
     */
    private fun patchKernelSU(
        workDir: File, bootFile: File,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("--- KernelSU 修补 ---")

        // 方式1: 使用ksud boot-patch（优先）
        val ksud = File(workDir, "ksud")
        if (ksud.exists()) {
            onProgress(0.3f)
            log("使用ksud修补boot镜像...")

            val outputFile = File(workDir, "new-boot.img")
            val result = runCommand(workDir,
                ksud.absolutePath, "boot-patch",
                "--boot", bootFile.absolutePath,
                "--out", outputFile.absolutePath
            )

            log(result.output)
            onProgress(0.7f)

            if (result.isSuccess && outputFile.exists()) {
                log("ksud修补成功")
                return true
            }
            log("ksud修补失败(exit=${result.exitCode})，尝试回退方案...")
        }

        // 方式2: 使用magiskboot + .ko模块注入
        return patchKernelSUWithKo(workDir, bootFile, "kernelsu", onProgress, log)
    }

    /**
     * 使用magiskboot注入.ko内核模块到ramdisk
     * 照抄KernelSU的ramdisk修补逻辑
     */
    private fun patchKernelSUWithKo(
        workDir: File, bootFile: File, providerDir: String,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("使用内核模块注入方案...")

        val magiskboot = findMagiskboot(workDir)
        if (magiskboot == null) {
            log("错误: magiskboot未找到")
            return false
        }

        // 查找匹配的.ko文件
        val koName = BinaryExtractor.selectKernelSUKoName(context, providerDir)
        if (koName.isEmpty()) {
            log("错误: 未找到匹配的kernelsu.ko")
            return false
        }
        val koFile = File(workDir, koName)
        if (!koFile.exists()) {
            log("错误: $koName 不在工作目录中")
            return false
        }
        log("使用内核模块: $koName")

        // Step 1: 解包boot镜像
        onProgress(0.3f)
        log("解包boot镜像...")
        var result = runCommand(workDir, magiskboot, "unpack", bootFile.absolutePath)
        log(result.output)
        if (!result.isSuccess) {
            log("解包失败: exit=${result.exitCode}")
            return false
        }

        // Step 2: 检查ramdisk
        onProgress(0.4f)
        val ramdisk = findRamdisk(workDir)
        log("Ramdisk: $ramdisk")

        if (ramdisk != null) {
            // 测试ramdisk状态
            result = runCommand(workDir, magiskboot, "cpio", ramdisk, "test")
            val status = result.exitCode
            log("Ramdisk状态: $status (0=原厂, 1=已修补)")

            when (status) {
                0 -> {
                    // 原厂boot - 备份并修补
                    log("原厂boot镜像，开始修补...")
                    runCommand(workDir, magiskboot, "cpio", ramdisk,
                        "backup", "${ramdisk}.orig")
                }
                1 -> {
                    // 已被KernelSU修补 - 恢复后重新修补
                    log("已修补的boot镜像，恢复后重新修补...")
                    runCommand(workDir, magiskboot, "cpio", ramdisk, "restore")
                    runCommand(workDir, magiskboot, "cpio", ramdisk,
                        "backup", "${ramdisk}.orig")
                }
                2 -> {
                    log("错误: boot镜像被不支持的程序修补过")
                    return false
                }
            }

            // Step 3: 注入.ko到overlay.d
            onProgress(0.5f)
            log("注入内核模块到ramdisk...")
            runCommand(workDir, magiskboot, "cpio", ramdisk,
                "mkdir 0750 overlay.d")
            runCommand(workDir, magiskboot, "cpio", ramdisk,
                "mkdir 0750 overlay.d/sbin")

            // 压缩.ko以节省空间
            result = runCommand(workDir, magiskboot, "compress=xz", koName, "${koName}.xz")
            if (result.isSuccess) {
                runCommand(workDir, magiskboot, "cpio", ramdisk,
                    "add 0644 overlay.d/sbin/${koName}.xz", "${koName}.xz")
            } else {
                // 压缩失败，直接添加
                runCommand(workDir, magiskboot, "cpio", ramdisk,
                    "add 0644 overlay.d/sbin/$koName", koName)
            }

            // 添加ksuinit作为init（如果有）
            val ksuinit = File(workDir, "ksuinit")
            if (ksuinit.exists()) {
                log("添加ksuinit作为init...")
                runCommand(workDir, magiskboot, "cpio", ramdisk,
                    "add 0750 init", ksuinit.absolutePath)
            }
        }

        // Step 4: 重新打包
        onProgress(0.7f)
        log("重新打包boot镜像...")
        val outputFile = File(workDir, "new-boot.img")
        result = runCommand(workDir, magiskboot, "repack",
            bootFile.absolutePath, outputFile.absolutePath)
        log(result.output)

        if (!result.isSuccess) {
            log("重新打包失败: exit=${result.exitCode}")
            return false
        }

        onProgress(0.8f)
        log("内核模块注入完成")
        return true
    }

    private fun patchKernelSUNext(
        workDir: File, bootFile: File,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("--- KernelSU Next 修补 ---")

        val ksud = File(workDir, "ksud")
        if (ksud.exists()) {
            onProgress(0.3f)
            log("使用ksud修补boot镜像(含sulist)...")

            val outputFile = File(workDir, "new-boot.img")
            val result = runCommand(workDir,
                ksud.absolutePath, "boot-patch",
                "--boot", bootFile.absolutePath,
                "--out", outputFile.absolutePath,
                "--sulist"
            )
            log(result.output)
            onProgress(0.7f)

            if (result.isSuccess && outputFile.exists()) {
                log("ksud修补成功")
                return true
            }
            log("ksud修补失败，尝试回退方案...")
        }

        return patchKernelSUWithKo(workDir, bootFile, "kernelsu_next", onProgress, log)
    }

    private fun patchSukiSUUltra(
        workDir: File, bootFile: File,
        kpmModules: List<String>?,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("--- SukiSU Ultra 修补 ---")

        val ksud = File(workDir, "ksud")
        if (ksud.exists()) {
            onProgress(0.3f)
            log("使用ksud修补boot镜像(含kpm)...")

            val cmd = mutableListOf(
                ksud.absolutePath, "boot-patch",
                "--boot", bootFile.absolutePath,
                "--out", File(workDir, "new-boot.img").absolutePath,
                "--kpm"
            )
            kpmModules?.forEach { cmd.addAll(listOf("--embed-kpm", it)) }

            val result = runCommand(workDir, *cmd.toTypedArray())
            log(result.output)
            onProgress(0.7f)

            if (result.isSuccess) {
                log("ksud修补成功")
                return true
            }
            log("ksud修补失败，尝试回退方案...")
        }

        return patchKernelSUWithKo(workDir, bootFile, "sukisu_ultra", onProgress, log)
    }

    // ==================== Magisk 修补 ====================

    /**
     * Magisk修补逻辑 - 完全照抄boot_patch.sh:
     * 1. magiskboot unpack 解包boot镜像
     * 2. magiskboot cpio test 测试ramdisk状态
     * 3. 修补ramdisk: backup + add init magiskinit + overlay.d结构
     * 4. 压缩magisk64/stub.apk为xz并添加到overlay.d/sbin
     * 5. magiskboot repack 重新打包
     */
    private fun patchMagisk(
        workDir: File, bootFile: File,
        keepForceEncrypt: Boolean, keepVerity: Boolean,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("--- Magisk 修补 ---")

        val magiskboot = findMagiskboot(workDir)
        if (magiskboot == null) {
            log("错误: magiskboot未找到")
            return false
        }

        val magiskinit = File(workDir, "magiskinit")
        val magisk64 = File(workDir, "magisk64")
        val stubApk = File(workDir, "stub.apk")

        if (!magiskinit.exists()) {
            log("错误: magiskinit未找到，Magisk修补必需此文件")
            return false
        }

        // Step 1: 解包boot镜像
        onProgress(0.3f)
        log("解包boot镜像...")
        var result = runCommand(workDir, magiskboot, "unpack", bootFile.absolutePath)
        log(result.output)
        if (!result.isSuccess && result.exitCode != 2) {
            log("解包失败: exit=${result.exitCode}")
            return false
        }
        if (result.exitCode == 2) {
            log("检测到ChromeOS boot镜像")
        }

        // Step 2: 查找ramdisk
        onProgress(0.35f)
        val ramdisk = findRamdisk(workDir)
        log("Ramdisk文件: $ramdisk")

        if (ramdisk == null) {
            // 没有ramdisk，创建一个空的
            log("未找到ramdisk，将创建新的ramdisk")
        }

        val actualRamdisk = ramdisk ?: "ramdisk.cpio"

        // Step 3: 测试ramdisk状态
        onProgress(0.4f)
        log("检查ramdisk状态...")
        result = runCommand(workDir, magiskboot, "cpio", actualRamdisk, "test")
        val status = result.exitCode
        log("Ramdisk状态: $status (0=原厂, 1=Magisk已修补, 2=不支持的修补)")

        when (status) {
            0 -> {
                // 原厂boot
                log("原厂boot镜像")
            }
            1 -> {
                // 已被Magisk修补 - 恢复后重新修补
                log("已被Magisk修补，恢复原厂ramdisk...")
                runCommand(workDir, magiskboot, "cpio", actualRamdisk, "restore")
            }
            2 -> {
                log("错误: boot镜像被不支持的程序修补过")
                return false
            }
        }

        // Step 4: 修补ramdisk - 照抄Magisk boot_patch.sh
        onProgress(0.5f)
        log("修补ramdisk...")

        // 压缩magisk64为xz
        if (magisk64.exists()) {
            log("压缩magisk64...")
            runCommand(workDir, magiskboot, "compress=xz", "magisk64", "magisk64.xz")
        }

        // 压缩stub.apk为xz
        if (stubApk.exists()) {
            log("压缩stub.apk...")
            runCommand(workDir, magiskboot, "compress=xz", "stub.apk", "stub.xz")
        }

        // 创建配置文件
        val configFile = File(workDir, "config")
        configFile.writeText(buildString {
            appendLine("KEEPVERITY=$keepVerity")
            appendLine("KEEPFORCEENCRYPT=$keepForceEncrypt")
            appendLine("RECOVERYMODE=false")
        })

        // 构建cpio命令 - 完全照抄Magisk boot_patch.sh的ramdisk修补
        val cpioArgs = mutableListOf<String>()
        cpioArgs.add("add 0750 init ${magiskinit.absolutePath}")
        cpioArgs.add("mkdir 0750 overlay.d")
        cpioArgs.add("mkdir 0750 overlay.d/sbin")

        if (File(workDir, "magisk64.xz").exists()) {
            cpioArgs.add("add 0644 overlay.d/sbin/magisk64.xz magisk64.xz")
        }
        if (File(workDir, "stub.xz").exists()) {
            cpioArgs.add("add 0644 overlay.d/sbin/stub.xz stub.xz")
        }

        cpioArgs.add("patch")
        if (status == 0) {
            cpioArgs.add("backup ${actualRamdisk}.orig")
        }
        cpioArgs.add("mkdir 000 .backup")
        cpioArgs.add("add 000 .backup/.magisk config")

        val cpioCmd = mutableListOf(magiskboot, "cpio", actualRamdisk)
        cpioCmd.addAll(cpioArgs)

        result = runCommand(workDir, *cpioCmd.toTypedArray())
        log(result.output)
        if (!result.isSuccess) {
            log("ramdisk修补失败: exit=${result.exitCode}")
            return false
        }

        // Step 5: 修补dtb/kernel（照抄Magisk的binary patches部分）
        onProgress(0.6f)
        for (dt in listOf("dtb", "kernel_dtb", "extra")) {
            val dtFile = File(workDir, dt)
            if (dtFile.exists()) {
                runCommand(workDir, magiskboot, "dtb", dt, "patch")
            }
        }

        // Step 6: 重新打包
        onProgress(0.7f)
        log("重新打包boot镜像...")
        val outputFile = File(workDir, "new-boot.img")
        result = runCommand(workDir, magiskboot, "repack",
            bootFile.absolutePath, outputFile.absolutePath)
        log(result.output)

        if (!result.isSuccess) {
            log("重新打包失败: exit=${result.exitCode}")
            return false
        }

        onProgress(0.8f)
        log("Magisk修补完成")
        return true
    }

    // ==================== APatch 修补 ====================

    /**
     * APatch修补逻辑 - 照抄APatch boot_patch.sh:
     * 1. kptools unpack 解包boot镜像
     * 2. kptools -p -i kernel.ori -k kpimg -o kernel 修补内核
     * 3. kptools repack 重新打包
     */
    private fun patchAPatch(
        workDir: File, bootFile: File,
        superKey: String, kpmModules: List<String>?,
        onProgress: (Float) -> Unit, log: (String) -> Unit
    ): Boolean {
        log("--- APatch 修补 ---")

        val kptools = File(workDir, "kptools")
        val kpimg = File(workDir, "kpimg")

        if (!kptools.exists()) {
            log("错误: kptools未找到")
            return false
        }
        if (!kpimg.exists()) {
            log("错误: kpimg未找到")
            return false
        }

        // Step 1: 解包boot镜像
        onProgress(0.3f)
        log("解包boot镜像...")
        var result = runCommand(workDir, kptools.absolutePath,
            "unpack", bootFile.absolutePath)
        log(result.output)
        if (!result.isSuccess) {
            log("解包失败: exit=${result.exitCode}")
            return false
        }

        // Step 2: 检查内核是否支持kallsyms（APatch必需）
        onProgress(0.35f)
        val kernelFile = File(workDir, "kernel")
        if (!kernelFile.exists()) {
            log("错误: 解包后未找到kernel文件")
            return false
        }

        result = runCommand(workDir, kptools.absolutePath,
            "-i", "kernel", "-f")
        val kallsymsCheck = result.output
        if (!kallsymsCheck.contains("CONFIG_KALLSYMS=y")) {
            log("警告: 内核可能未启用CONFIG_KALLSYMS，APatch可能无法工作")
        }

        // Step 3: 备份原始kernel
        val kernelOri = File(workDir, "kernel.ori")
        kernelFile.copyTo(kernelOri, overwrite = true)

        // Step 4: 修补内核 - 照抄APatch: kptools -p -i kernel.ori -S superkey -k kpimg -o kernel
        onProgress(0.5f)
        log("修补内核...")

        val patchCmd = mutableListOf(
            kptools.absolutePath,
            "-p",
            "-i", "kernel.ori",
            "-k", kpimg.absolutePath,
            "-o", "kernel"
        )
        // 超级密钥（如果不是默认的"su"）
        if (superKey != "su") {
            patchCmd.addAll(listOf("-S", superKey))
        }
        // KPM模块嵌入
        kpmModules?.forEach { kpm ->
            patchCmd.addAll(listOf("--embed-kpm", kpm))
        }

        result = runCommand(workDir, *patchCmd.toTypedArray())
        log(result.output)
        if (!result.isSuccess) {
            log("内核修补失败: exit=${result.exitCode}")
            return false
        }

        // Step 5: 重新打包
        onProgress(0.7f)
        log("重新打包boot镜像...")
        result = runCommand(workDir, kptools.absolutePath,
            "repack", bootFile.absolutePath)
        log(result.output)

        if (!result.isSuccess) {
            log("重新打包失败: exit=${result.exitCode}")
            return false
        }

        onProgress(0.8f)
        log("APatch修补完成")
        return true
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找magiskboot路径（可能在magisk/或apatch/目录下）
     */
    private fun findMagiskboot(workDir: File): String? {
        val candidates = listOf(
            File(workDir, "magiskboot"),
            File(workDir, "apatch/magiskboot")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }

    /**
     * 查找ramdisk文件
     * 照抄Magisk: 依次检查 ramdisk.cpio, vendor_ramdisk/init_boot.cpio, vendor_ramdisk/ramdisk.cpio
     */
    private fun findRamdisk(workDir: File): String? {
        val candidates = listOf(
            "ramdisk.cpio",
            "vendor_ramdisk/init_boot.cpio",
            "vendor_ramdisk/ramdisk.cpio"
        )
        for (name in candidates) {
            if (File(workDir, name).exists()) return name
        }
        return null
    }
}
