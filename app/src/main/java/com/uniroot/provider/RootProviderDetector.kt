package com.uniroot.provider

import com.uniroot.native.NativeBridge

/**
 * Root方案状态信息
 */
data class RootState(
    val isInstalled: Boolean = false,
    val provider: RootProvider? = null,
    val version: String = "",
    val versionCode: Int = 0,
    val kernelVersion: String = "",
    val selinuxStatus: String = "Unknown",
    val seccompStatus: String = "Unknown",
    val superuserCount: Int = 0,
    val moduleCount: Int = 0,
    val isSafeMode: Boolean = false,
    val mountMode: MountMode = MountMode.OVERLAYFS
)

enum class MountMode(val displayName: String) {
    OVERLAYFS("OverlayFS"),
    MAGIC_MOUNT("Magic Mount")
}

/**
 * Root方案检测器 - 检测当前设备安装的Root方案
 */
object RootProviderDetector {

    fun detect(): RootState {
        val native = NativeBridge

        // 检查KernelSU
        if (native.isKernelSUAvaliable()) {
            val version = native.getKernelSUVersion()
            return RootState(
                isInstalled = true,
                provider = detectKSUVariant(version),
                version = version.toString(),
                versionCode = version,
                kernelVersion = native.getKernelVersion(),
                selinuxStatus = native.getSelinuxStatus(),
                seccompStatus = native.getSeccompStatus()
            )
        }

        // 检查APatch
        if (native.isAPatchAvailable()) {
            val version = native.getAPatchVersion()
            return RootState(
                isInstalled = true,
                provider = RootProvider.APATCH,
                version = version.toString(),
                versionCode = version,
                kernelVersion = native.getKernelVersion(),
                selinuxStatus = native.getSelinuxStatus(),
                seccompStatus = native.getSeccompStatus()
            )
        }

        // 检查Magisk
        if (native.isMagiskAvailable()) {
            val version = native.getMagiskVersion()
            return RootState(
                isInstalled = true,
                provider = detectMagiskVariant(version),
                version = version.toString(),
                versionCode = version,
                kernelVersion = native.getKernelVersion(),
                selinuxStatus = native.getSelinuxStatus(),
                seccompStatus = native.getSeccompStatus()
            )
        }

        return RootState(
            isInstalled = false,
            kernelVersion = native.getKernelVersion(),
            selinuxStatus = native.getSelinuxStatus(),
            seccompStatus = native.getSeccompStatus()
        )
    }

    private fun detectKSUVariant(version: Int): RootProvider {
        // 通过特定属性区分KernelSU变体
        val native = NativeBridge
        val suList = native.hasSuList()
        val kpm = native.hasKPM()

        return when {
            kpm -> RootProvider.SUKISU_ULTRA
            suList -> RootProvider.KERNELSU_NEXT
            else -> RootProvider.KERNELSU
        }
    }

    private fun detectMagiskVariant(version: Int): RootProvider {
        val native = NativeBridge
        val isKitsune = native.isMagiskKitsune()
        return if (isKitsune) RootProvider.MAGISK_KITSUNE else RootProvider.MAGISK
    }
}
