#!/bin/bash
# 自动安装脚本：构建安装 + 学习/游戏数据防丢
# 数据备份原理：debug 包支持 adb run-as 读写应用私有目录。
# 每次安装前把 SharedPreferences 备份到本机 backups/；若检测到是
# 全新安装（卸载过、设备上没有旧数据），自动把备份恢复回去——
# 星星/农场/掌握度/答题进度都不会因重装丢失。
PKG=com.example.floating
BACKUP_DIR="$(dirname "$0")/backups"
PREFS=(QuestionBankPrefs PetPrefs FeishuPrefs QuizState)

mkdir -p "$BACKUP_DIR"

# 1) 安装前备份（设备上有旧版本才会成功；失败静默跳过）
echo "备份设备上的学习数据..."
# 先写临时文件，成功才覆盖——设备离线/失败时保住上一次的好备份
for f in "${PREFS[@]}"; do
    if adb exec-out run-as $PKG cat "shared_prefs/$f.xml" > "$BACKUP_DIR/$f.xml.tmp" 2>/dev/null \
       && [ -s "$BACKUP_DIR/$f.xml.tmp" ]; then
        mv "$BACKUP_DIR/$f.xml.tmp" "$BACKUP_DIR/$f.xml"
        echo "  ✓ $f"
    else
        rm -f "$BACKUP_DIR/$f.xml.tmp"
    fi
done

echo "正在构建并安装应用到手机..."
./gradlew installDebug --console=plain
if [ $? -ne 0 ]; then
    echo "安装失败，请检查设备连接或构建日志。"
    exit 1
fi

# 2) 全新安装（无旧数据）且本机有备份 → 自动恢复
if ! adb shell run-as $PKG ls shared_prefs/QuestionBankPrefs.xml >/dev/null 2>&1; then
    restored=0
    for f in "${PREFS[@]}"; do
        if [ -s "$BACKUP_DIR/$f.xml" ]; then
            adb push "$BACKUP_DIR/$f.xml" "/data/local/tmp/$f.xml" >/dev/null
            adb shell run-as $PKG mkdir -p shared_prefs
            adb shell run-as $PKG cp "/data/local/tmp/$f.xml" "shared_prefs/$f.xml"
            adb shell rm "/data/local/tmp/$f.xml"
            restored=1
            echo "  ↩︎ 已恢复 $f"
        fi
    done
    [ $restored -eq 1 ] && adb shell am force-stop $PKG && echo "检测到全新安装，学习数据已从备份恢复。"
fi

echo "安装成功！"
