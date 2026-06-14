#!/bin/bash
# 下载各Root方案的内核模块和工具二进制
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
GH_TOKEN="${GITHUB_TOKEN:-}"

# 确保目录存在
for d in kernelsu kernelsu_next sukisu_ultra apatch magisk; do
    mkdir -p "$ASSETS_DIR/$d"
done

gh_curl() {
    curl -sL -H "Authorization: token $GH_TOKEN" -H "Accept: application/vnd.github+json" "$1" -o "$2"
}

echo "=== 下载 KernelSU 二进制 ==="
KSU_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/tiann/KernelSU/releases/latest")
KSU_VER=$(echo "$KSU_RELEASE" | python3 -c "import json,sys;print(json.load(sys.stdin)['tag_name'])")
echo "KernelSU version: $KSU_VER"

# 下载kernelsu.ko内核模块
for entry in $(echo "$KSU_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('assets',[]):
    if a['name'].endswith('.ko') or a['name'] == 'ksuinit':
        print(a['name'] + '|' + a['browser_download_url'])"); do
    fname=$(echo "$entry" | cut -d'|' -f1)
    url=$(echo "$entry" | cut -d'|' -f2)
    echo "  下载 $fname"
    gh_curl "$url" "$ASSETS_DIR/kernelsu/$fname"
done

echo "=== 下载 KernelSU Next 二进制 ==="
KSU_NEXT_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/KernelSU-Next/KernelSU-Next/releases/latest")
KSU_NEXT_VER=$(echo "$KSU_NEXT_RELEASE" | python3 -c "import json,sys;print(json.load(sys.stdin)['tag_name'])")
echo "KernelSU Next version: $KSU_NEXT_VER"

# 下载ksud和ko
for entry in $(echo "$KSU_NEXT_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('assets',[]):
    n=a['name']
    if 'aarch64-linux-android' in n or n.endswith('.ko'):
        print(n.replace('-aarch64-linux-android','') + '|' + a['browser_download_url'])"); do
    fname=$(echo "$entry" | cut -d'|' -f1)
    url=$(echo "$entry" | cut -d'|' -f2)
    echo "  下载 $fname"
    gh_curl "$url" "$ASSETS_DIR/kernelsu_next/$fname"
done

echo "=== 下载 SukiSU Ultra 二进制 ==="
SUKISU_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/SukiSU-Ultra/SukiSU-Ultra/releases?per_page=1")
SUKISU_VER=$(echo "$SUKISU_RELEASE" | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['tag_name'])")
echo "SukiSU Ultra version: $SUKISU_VER"

for entry in $(echo "$SUKISU_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d[0].get('assets',[]):
    if a['name'].endswith('.ko'):
        print(a['name'] + '|' + a['browser_download_url'])"); do
    fname=$(echo "$entry" | cut -d'|' -f1)
    url=$(echo "$entry" | cut -d'|' -f2)
    echo "  下载 $fname"
    gh_curl "$url" "$ASSETS_DIR/sukisu_ultra/$fname"
done

echo "=== 下载 APatch 二进制 ==="
APATCH_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/bmax121/APatch/releases/latest")
APATCH_APK_URL=$(echo "$APATCH_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('assets',[]):
    if a['name'].endswith('.apk'):
        print(a['browser_download_url'])
        break")
if [ -n "$APATCH_APK_URL" ]; then
    echo "  下载 APatch APK"
    gh_curl "$APATCH_APK_URL" "/tmp/apatch.apk"
    mkdir -p /tmp/apatch_extracted
    unzip -o /tmp/apatch.apk -d /tmp/apatch_extracted 2>/dev/null || true
    # 从assets提取kpimg
    if [ -f "/tmp/apatch_extracted/assets/kpimg" ]; then
        cp "/tmp/apatch_extracted/assets/kpimg" "$ASSETS_DIR/apatch/kpimg"
        chmod +x "$ASSETS_DIR/apatch/kpimg"
        echo "  提取 kpimg (assets) 成功"
    fi
    # 从lib/arm64-v8a提取kptools和kpatch（核心二进制以.so形式存储）
    for pair in "libkptools.so:kptools" "libkpatch.so:kpatch" "libmagiskboot.so:magiskboot" "libbootctl.so:bootctl"; do
        src=$(echo "$pair" | cut -d: -f1)
        dst=$(echo "$pair" | cut -d: -f2)
        if [ -f "/tmp/apatch_extracted/lib/arm64-v8a/$src" ]; then
            cp "/tmp/apatch_extracted/lib/arm64-v8a/$src" "$ASSETS_DIR/apatch/$dst"
            chmod +x "$ASSETS_DIR/apatch/$dst"
            echo "  提取 $dst (lib/arm64-v8a/$src) 成功"
        else
            echo "  警告: $dst (lib/arm64-v8a/$src) 未在APatch APK中找到"
        fi
    done
    rm -rf /tmp/apatch.apk /tmp/apatch_extracted
else
    echo "  警告: 未找到APatch APK下载链接"
fi

echo "=== 下载 Magisk 二进制 ==="
MAGISK_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/topjohnwu/Magisk/releases/latest")
MAGISK_APK_URL=$(echo "$MAGISK_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('assets',[]):
    if a['name'].endswith('.apk') and not a['name'].startswith('app-'):
        print(a['browser_download_url'])
        break")
if [ -n "$MAGISK_APK_URL" ]; then
    echo "  下载 Magisk APK"
    gh_curl "$MAGISK_APK_URL" "/tmp/magisk.apk"
    mkdir -p /tmp/magisk_extracted
    unzip -o /tmp/magisk.apk -d /tmp/magisk_extracted 2>/dev/null || true
    # 从lib/arm64-v8a提取核心二进制（Magisk以.so形式存储）
    for pair in "libmagiskboot.so:magiskboot" "libmagisk.so:magisk64" "libmagiskinit.so:magiskinit" "libmagiskpolicy.so:magiskpolicy" "libbusybox.so:busybox"; do
        src=$(echo "$pair" | cut -d: -f1)
        dst=$(echo "$pair" | cut -d: -f2)
        if [ -f "/tmp/magisk_extracted/lib/arm64-v8a/$src" ]; then
            cp "/tmp/magisk_extracted/lib/arm64-v8a/$src" "$ASSETS_DIR/magisk/$dst"
            chmod +x "$ASSETS_DIR/magisk/$dst"
            echo "  提取 $dst (lib/arm64-v8a/$src) 成功"
        else
            echo "  警告: $dst (lib/arm64-v8a/$src) 未在Magisk APK中找到"
        fi
    done
    # 从assets提取stub.apk
    if [ -f "/tmp/magisk_extracted/assets/stub.apk" ]; then
        cp "/tmp/magisk_extracted/assets/stub.apk" "$ASSETS_DIR/magisk/stub.apk"
        echo "  提取 stub.apk 成功"
    fi
    rm -rf /tmp/magisk.apk /tmp/magisk_extracted
else
    echo "  警告: 未找到Magisk APK下载链接"
fi

echo "=== 下载完成 ==="
echo "Assets目录结构:"
find "$ASSETS_DIR" -type f ! -name '.gitkeep' | sort
