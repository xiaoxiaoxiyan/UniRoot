package com.uniroot.native

/**
 * Native桥接接口 - 与内核模块和Root守护进程通信
 */
object NativeBridge {

    init {
        System.loadLibrary("uniroot")
    }

    // ===== KernelSU 接口 =====

    external fun isKernelSUAvaliable(): Boolean
    external fun getKernelSUVersion(): Int
    external fun becomeManager(pkg: String): Boolean

    // ===== APatch 接口 =====

    external fun isAPatchAvailable(): Boolean
    external fun getAPatchVersion(): Int
    external fun getSuperKey(): String?
    external fun setSuperKey(key: String): Boolean

    // ===== Magisk 接口 =====

    external fun isMagiskAvailable(): Boolean
    external fun getMagiskVersion(): Int
    external fun isMagiskKitsune(): Boolean

    // ===== 通用接口 =====

    external fun getKernelVersion(): String
    external fun getSelinuxStatus(): String
    external fun getSeccompStatus(): String
    external fun hasSuList(): Boolean
    external fun hasKPM(): Boolean

    // ===== App Profile =====

    external fun getAppProfile(pkg: String, uid: Int): String?
    external fun setAppProfile(profile: String): Boolean

    // ===== 模块管理 =====

    external fun listModules(): String?
    external fun enableModule(id: String): Boolean
    external fun disableModule(id: String): Boolean
    external fun uninstallModule(id: String): Boolean
    external fun mountModule(id: String): Boolean

    // ===== Boot修补 =====

    external fun patchBootImage(
        bootImagePath: String,
        outputPath: String,
        providerId: String,
        superKey: String?
    ): Boolean

    external fun restoreBootImage(bootImagePath: String): Boolean

    // ===== KPM模块 =====

    external fun loadKPMModule(kpmPath: String): Boolean
    external fun unloadKPMModule(name: String): Boolean
    external fun listKPMModules(): String?
    external fun embedKPMToBoot(kpmPath: String, bootImagePath: String): Boolean

    // ===== AK3刷入 =====

    external fun flashAK3Zip(zipPath: String): Boolean

    // ===== 重启 =====

    external fun reboot(reason: String): Boolean

    // ===== SU日志 =====

    external fun getSuLog(): String?
    external fun clearSuLog(): Boolean
}
