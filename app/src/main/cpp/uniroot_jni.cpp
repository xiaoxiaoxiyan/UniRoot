#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/system_properties.h>
#include <fstream>
#include <sstream>

#define LOG_TAG "UniRoot"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper: read file content
static std::string readFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) return "";
    std::stringstream ss;
    ss << file.rdbuf();
    return ss.str();
}

// Helper: get system property
static std::string getProp(const char* name) {
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get(name, value);
    return std::string(value);
}

// Helper: check if file exists
static bool fileExists(const std::string& path) {
    std::ifstream f(path);
    return f.good();
}

// Helper: execute command and get output
static std::string execCommand(const char* cmd) {
    char buffer[128];
    std::string result;
    FILE* pipe = popen(cmd, "r");
    if (!pipe) return "";
    while (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        result += buffer;
    }
    pclose(pipe);
    return result;
}

// ===== KernelSU Detection =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_isKernelSUAvaliable(JNIEnv *env, jobject thiz) {
    // Check for KernelSU by looking for the ksud binary and kernel module
    bool hasKsud = fileExists("/data/adb/ksud");
    bool hasKsuModule = fileExists("/proc/kernelsu");
    bool hasKsuProp = getProp("ro.kernel.kernelsu") == "1";

    LOGI("KernelSU check: ksud=%d, module=%d, prop=%d", hasKsud, hasKsuModule, hasKsuProp);
    return hasKsud || hasKsuModule || hasKsuProp ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_uniroot_native_NativeBridge_getKernelSUVersion(JNIEnv *env, jobject thiz) {
    std::string version = readFile("/proc/kernelsu/version");
    if (!version.empty()) {
        try { return std::stoi(version); } catch (...) {}
    }

    // Try via ksud
    std::string output = execCommand("/data/adb/ksud --version 2>/dev/null");
    if (!output.empty()) {
        try { return std::stoi(output); } catch (...) {}
    }

    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_becomeManager(JNIEnv *env, jobject thiz, jstring pkg) {
    // Try to become the KernelSU manager
    const char* pkgStr = env->GetStringUTFChars(pkg, nullptr);
    std::string cmd = "/data/adb/ksud module set-manager " + std::string(pkgStr);
    env->ReleaseStringUTFChars(pkg, pkgStr);

    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== APatch Detection =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_isAPatchAvailable(JNIEnv *env, jobject thiz) {
    bool hasKpimg = fileExists("/data/adb/kpimg");
    bool hasKptools = fileExists("/data/adb/kptools");
    bool hasKsuProp = getProp("ro.kernel.apatch") == "1";

    LOGI("APatch check: kpimg=%d, kptools=%d, prop=%d", hasKpimg, hasKptools, hasKsuProp);
    return hasKpimg || hasKptools || hasKsuProp ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_uniroot_native_NativeBridge_getAPatchVersion(JNIEnv *env, jobject thiz) {
    std::string version = readFile("/data/adb/.apatch_version");
    if (!version.empty()) {
        try { return std::stoi(version); } catch (...) {}
    }

    std::string output = execCommand("/data/adb/kptools --version 2>/dev/null");
    if (!output.empty()) {
        try { return std::stoi(output); } catch (...) {}
    }

    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getSuperKey(JNIEnv *env, jobject thiz) {
    // SuperKey is not readable, return empty
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_setSuperKey(JNIEnv *env, jobject thiz, jstring key) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    std::string cmd = "/data/adb/kptools --skey " + std::string(keyStr);
    env->ReleaseStringUTFChars(key, keyStr);

    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== Magisk Detection =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_isMagiskAvailable(JNIEnv *env, jobject thiz) {
    bool hasMagisk = fileExists("/data/adb/magisk");
    bool hasMagiskInit = fileExists("/data/adb/magiskinit");
    bool hasMagiskProp = getProp("ro.magisk.version") != "" ||
                         getProp("init.svc.magisk_pfs") != "";

    LOGI("Magisk check: magisk=%d, init=%d, prop=%d", hasMagisk, hasMagiskInit, hasMagiskProp);
    return hasMagisk || hasMagiskInit || hasMagiskProp ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_uniroot_native_NativeBridge_getMagiskVersion(JNIEnv *env, jobject thiz) {
    std::string version = getProp("ro.magisk.version");
    if (!version.empty()) {
        // Parse version like "30.7" -> 30700
        try { return (int)(std::stof(version) * 1000); } catch (...) {}
    }

    std::string output = execCommand("/data/adb/magisk/magisk --version 2>/dev/null");
    if (!output.empty()) {
        try { return std::stoi(output); } catch (...) {}
    }

    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_isMagiskKitsune(JNIEnv *env, jobject thiz) {
    std::string prop = getProp("ro.magisk.kitsune");
    return prop == "1" ? JNI_TRUE : JNI_FALSE;
}

// ===== Common Interfaces =====

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getKernelVersion(JNIEnv *env, jobject thiz) {
    std::string version = readFile("/proc/version");
    if (version.length() > 80) version = version.substr(0, 80);
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getSelinuxStatus(JNIEnv *env, jobject thiz) {
    std::string status = readFile("/sys/fs/selinux/enforce");
    if (status == "1") return env->NewStringUTF("Enforcing");
    if (status == "0") return env->NewStringUTF("Permissive");
    return env->NewStringUTF("Unknown");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getSeccompStatus(JNIEnv *env, jobject thiz) {
    std::string status = readFile("/proc/1/status");
    if (status.find("Seccomp:	2") != std::string::npos) {
        return env->NewStringUTF("Strict");
    }
    if (status.find("Seccomp:	1") != std::string::npos) {
        return env->NewStringUTF("Filter");
    }
    return env->NewStringUTF("Disabled");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_hasSuList(JNIEnv *env, jobject thiz) {
    return fileExists("/data/adb/ksu/sulist") ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_hasKPM(JNIEnv *env, jobject thiz) {
    return fileExists("/proc/kpm") || fileExists("/sys/kpm") ? JNI_TRUE : JNI_FALSE;
}

// ===== App Profile =====

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getAppProfile(JNIEnv *env, jobject thiz, jstring pkg, jint uid) {
    const char* pkgStr = env->GetStringUTFChars(pkg, nullptr);
    std::string path = "/data/adb/ksu/profiles/" + std::string(pkgStr);
    env->ReleaseStringUTFChars(pkg, pkgStr);
    std::string profile = readFile(path);
    return env->NewStringUTF(profile.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_setAppProfile(JNIEnv *env, jobject thiz, jstring profile) {
    // Write profile via ksud
    const char* profileStr = env->GetStringUTFChars(profile, nullptr);
    std::string cmd = "/data/adb/ksud profile set --stdin";
    env->ReleaseStringUTFChars(profile, profileStr);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== Module Management =====

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_listModules(JNIEnv *env, jobject thiz) {
    std::string output = execCommand("/data/adb/ksud module list 2>/dev/null || "
                                      "/data/adb/magisk/magisk --list-modules 2>/dev/null");
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_enableModule(JNIEnv *env, jobject thiz, jstring id) {
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    std::string cmd = "/data/adb/ksud module enable " + std::string(idStr) + " 2>/dev/null || "
                      "touch /data/adb/modules/" + std::string(idStr) + "/disable 2>/dev/null";
    env->ReleaseStringUTFChars(id, idStr);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_disableModule(JNIEnv *env, jobject thiz, jstring id) {
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    std::string cmd = "/data/adb/ksud module disable " + std::string(idStr) + " 2>/dev/null || "
                      "touch /data/adb/modules/" + std::string(idStr) + "/disable";
    env->ReleaseStringUTFChars(id, idStr);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_uninstallModule(JNIEnv *env, jobject thiz, jstring id) {
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    std::string cmd = "/data/adb/ksud module uninstall " + std::string(idStr) + " 2>/dev/null || "
                      "touch /data/adb/modules/" + std::string(idStr) + "/remove";
    env->ReleaseStringUTFChars(id, idStr);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_mountModule(JNIEnv *env, jobject thiz, jstring id) {
    const char* idStr = env->GetStringUTFChars(id, nullptr);
    std::string cmd = "/data/adb/ksud module mount " + std::string(idStr);
    env->ReleaseStringUTFChars(id, idStr);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== Boot Patching =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_patchBootImage(JNIEnv *env, jobject thiz,
    jstring bootImagePath, jstring outputPath, jstring providerId, jstring superKey) {
    const char* bootPath = env->GetStringUTFChars(bootImagePath, nullptr);
    const char* outPath = env->GetStringUTFChars(outputPath, nullptr);
    const char* provId = env->GetStringUTFChars(providerId, nullptr);
    const char* sKey = env->GetStringUTFChars(superKey, nullptr);

    std::string cmd;
    std::string provider(provId);

    if (provider == "kernelsu" || provider == "kernelsu_next" || provider == "sukisu_ultra") {
        cmd = "/data/adb/ksud boot-patch --boot " + std::string(bootPath) +
              " --out " + std::string(outPath);
        if (provider == "kernelsu_next") cmd += " --sulist";
        if (provider == "sukisu_ultra") cmd += " --kpm";
    } else if (provider == "apatch") {
        cmd = "/data/adb/kptools patch --boot " + std::string(bootPath) +
              " --out " + std::string(outPath) + " --skey " + std::string(sKey);
    } else if (provider == "magisk" || provider == "magisk_kitsune") {
        cmd = "/data/adb/magisk/magiskboot unpack " + std::string(bootPath) + " && "
              "/data/adb/magisk/magiskboot cpio ramdisk.cpio 'magisk' && "
              "/data/adb/magisk/magiskboot repack " + std::string(bootPath) + " " + std::string(outPath);
    }

    env->ReleaseStringUTFChars(bootImagePath, bootPath);
    env->ReleaseStringUTFChars(outputPath, outPath);
    env->ReleaseStringUTFChars(providerId, provId);
    env->ReleaseStringUTFChars(superKey, sKey);

    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_restoreBootImage(JNIEnv *env, jobject thiz, jstring bootImagePath) {
    const char* bootPath = env->GetStringUTFChars(bootImagePath, nullptr);
    std::string cmd = "/data/adb/ksud boot-restore --boot " + std::string(bootPath);
    env->ReleaseStringUTFChars(bootImagePath, bootPath);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== KPM Modules =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_loadKPMModule(JNIEnv *env, jobject thiz, jstring kpmPath) {
    const char* path = env->GetStringUTFChars(kpmPath, nullptr);
    std::string cmd = "insmod " + std::string(path);
    env->ReleaseStringUTFChars(kpmPath, path);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_unloadKPMModule(JNIEnv *env, jobject thiz, jstring name) {
    const char* n = env->GetStringUTFChars(name, nullptr);
    std::string cmd = "rmmod " + std::string(n);
    env->ReleaseStringUTFChars(name, n);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_listKPMModules(JNIEnv *env, jobject thiz) {
    std::string output = execCommand("cat /proc/kpm/list 2>/dev/null || lsmod | grep kpm_");
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_embedKPMToBoot(JNIEnv *env, jobject thiz,
    jstring kpmPath, jstring bootImagePath) {
    const char* kpm = env->GetStringUTFChars(kpmPath, nullptr);
    const char* boot = env->GetStringUTFChars(bootImagePath, nullptr);
    std::string cmd = "/data/adb/kptools embed-kpm --kpm " + std::string(kpm) +
                      " --boot " + std::string(boot);
    env->ReleaseStringUTFChars(kpmPath, kpm);
    env->ReleaseStringUTFChars(bootImagePath, boot);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== AK3 Flashing =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_flashAK3Zip(JNIEnv *env, jobject thiz, jstring zipPath) {
    const char* zip = env->GetStringUTFChars(zipPath, nullptr);
    std::string cmd = "cd /data/local/tmp && mkdir -p ak3_flash && cd ak3_flash && "
                      "unzip -o " + std::string(zip) + " && sh ak3-flash.sh && "
                      "cd .. && rm -rf ak3_flash";
    env->ReleaseStringUTFChars(zipPath, zip);
    int ret = system(cmd.c_str());
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}

// ===== Reboot =====

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_reboot(JNIEnv *env, jobject thiz, jstring reason) {
    const char* r = env->GetStringUTFChars(reason, nullptr);
    std::string cmd;

    if (strcmp(r, "normal") == 0) cmd = "reboot";
    else if (strcmp(r, "recovery") == 0) cmd = "reboot recovery";
    else if (strcmp(r, "bootloader") == 0) cmd = "reboot bootloader";
    else if (strcmp(r, "download") == 0) cmd = "reboot download";
    else if (strcmp(r, "edl") == 0) cmd = "reboot edl";
    else if (strcmp(r, "userspace") == 0) cmd = "setprop sys.powerctl reboot";
    else cmd = "reboot";

    env->ReleaseStringUTFChars(reason, r);
    system(cmd.c_str());
    return JNI_TRUE;
}

// ===== SU Log =====

extern "C" JNIEXPORT jstring JNICALL
Java_com_uniroot_native_NativeBridge_getSuLog(JNIEnv *env, jobject thiz) {
    std::string log = readFile("/data/adb/ksu/sulog");
    if (log.empty()) {
        log = execCommand("/data/adb/ksud debug su-log 2>/dev/null");
    }
    return env->NewStringUTF(log.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_uniroot_native_NativeBridge_clearSuLog(JNIEnv *env, jobject thiz) {
    int ret = system("rm -f /data/adb/ksu/sulog");
    return ret == 0 ? JNI_TRUE : JNI_FALSE;
}
