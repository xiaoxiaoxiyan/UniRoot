#!/bin/bash
# 下载各Root方案的内核模块和工具二进制
set -e

ASSETS_DIR="app/src/main/assets"
GH_TOKEN="${GITHUB_TOKEN:-}"

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
    if a['name'].endswith('.apk') and 'release' in a['name']:
        print(a['browser_download_url'])
        break")
echo "  下载 APatch APK"
gh_curl "$APATCH_APK_URL" "/tmp/apatch.apk"

# 从APK中提取kptools和kpimg
cd /tmp && unzip -o apatch.apk -d apatch_extracted 2>/dev/null || true
for bin in kptools kpimg kpatch; do
    if [ -f "/tmp/apatch_extracted/assets/$bin" ]; then
        cp "/tmp/apatch_extracted/assets/$bin" "$ASSETS_DIR/apatch/$bin"
        chmod +x "$ASSETS_DIR/apatch/$bin"
        echo "  提取 $bin 成功"
    fi
done
rm -rf /tmp/apatch.apk /tmp/apatch_extracted

echo "=== 下载 Magisk 二进制 ==="
MAGISK_RELEASE=$(curl -sL -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/topjohnwu/Magisk/releases/latest")
MAGISK_APK_URL=$(echo "$MAGISK_RELEASE" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for a in d.get('assets',[]):
    if a['name'].endswith('.apk') and not a['name'].startswith('app-'):
        print(a['browser_download_url'])
        break")
echo "  下载 Magisk APK"
gh_curl "$MAGISK_APK_URL" "/tmp/magisk.apk"

cd /tmp && unzip -o magisk.apk -d magisk_extracted 2>/dev/null || true
for bin in magiskboot magisk32 magisk64 magiskinit; do
    if [ -f "/tmp/magisk_extracted/assets/$bin" ]; then
        cp "/tmp/magisk_extracted/assets/$bin" "$ASSETS_DIR/magisk/$bin"
        chmod +x "$ASSETS_DIR/magisk/$bin"
        echo "  提取 $bin 成功"
    fi
done
if [ -f "/tmp/magisk_extracted/assets/stub.apk" ]; then
    cp "/tmp/magisk_extracted/assets/stub.apk" "$ASSETS_DIR/magisk/stub.apk"
    echo "  提取 stub.apk 成功"
fi
rm -rf /tmp/magisk.apk /tmp/magisk_extracted

echo "=== 下载完成 ==="
echo "Assets目录结构:"
find "$ASSETS_DIR" -type f | sort
