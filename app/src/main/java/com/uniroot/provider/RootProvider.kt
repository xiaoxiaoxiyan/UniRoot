package com.uniroot.provider

/**
 * Root方案类型枚举
 */
enum class RootCategory {
    KERNELSU,   // KernelSU类：KernelSU, KernelSU Next, SukiSU Ultra
    MAGISK,     // Magisk类：Magisk, Magisk Kitsune
    APATCH      // APatch类：APatch
}

/**
 * 具体Root方案枚举
 */
enum class RootProvider(
    val id: String,
    val displayName: String,
    val category: RootCategory,
    val description: String,
    val supportsBootPatch: Boolean,
    val supportsAK3: Boolean,
    val supportsKPM: Boolean,
    val requiresSuperKey: Boolean,
    val supportsOverlayFS: Boolean,
    val supportsMagicMount: Boolean,
    val supportsSuList: Boolean
) {
    KERNELSU(
        id = "kernelsu",
        displayName = "KernelSU",
        category = RootCategory.KERNELSU,
        description = "基于内核的Root方案，支持GKI 2.0设备",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = false,
        requiresSuperKey = false,
        supportsOverlayFS = true,
        supportsMagicMount = true,
        supportsSuList = false
    ),
    KERNELSU_NEXT(
        id = "kernelsu_next",
        displayName = "KernelSU Next",
        category = RootCategory.KERNELSU,
        description = "KernelSU增强版，支持SuList和非GKI设备",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = false,
        requiresSuperKey = false,
        supportsOverlayFS = true,
        supportsMagicMount = true,
        supportsSuList = true
    ),
    SUKISU_ULTRA(
        id = "sukisu_ultra",
        displayName = "SukiSU Ultra",
        category = RootCategory.KERNELSU,
        description = "KernelSU分支，支持KPM模块和非GKI设备",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = true,
        requiresSuperKey = false,
        supportsOverlayFS = true,
        supportsMagicMount = true,
        supportsSuList = false
    ),
    MAGISK(
        id = "magisk",
        displayName = "Magisk",
        category = RootCategory.MAGISK,
        description = "经典用户空间Root方案，模块生态最丰富",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = false,
        requiresSuperKey = false,
        supportsOverlayFS = false,
        supportsMagicMount = true,
        supportsSuList = false
    ),
    MAGISK_KITSUNE(
        id = "magisk_kitsune",
        displayName = "Magisk Kitsune",
        category = RootCategory.MAGISK,
        description = "Magisk分支，增强隐藏和兼容性",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = false,
        requiresSuperKey = false,
        supportsOverlayFS = false,
        supportsMagicMount = true,
        supportsSuList = false
    ),
    APATCH(
        id = "apatch",
        displayName = "APatch",
        category = RootCategory.APATCH,
        description = "基于kprobe的内核修补方案，支持KPM和APM模块",
        supportsBootPatch = true,
        supportsAK3 = true,
        supportsKPM = true,
        requiresSuperKey = true,
        supportsOverlayFS = true,
        supportsMagicMount = false,
        supportsSuList = false
    );

    companion object {
        fun fromId(id: String): RootProvider? {
            return entries.find { it.id == id }
        }

        fun getByCategory(category: RootCategory): List<RootProvider> {
            return entries.filter { it.category == category }
        }
    }
}
