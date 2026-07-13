#!/bin/bash
# 生成英语听音选图的单词发音（macOS）：res/raw/*.m4a
# 用法：./scripts/gen_audio.sh
# 优先使用已安装的高级语音（更自然）。想进一步提升音质：
#   系统设置 → 辅助功能 → 朗读内容 → 系统声音 → 管理声音，
#   下载 “Ava (Premium)” 或 “Samantha (Enhanced)” 后重跑本脚本。
set -e
cd "$(dirname "$0")/.."
RAW=app/src/main/res/raw
TMP=$(mktemp -d)

# 按优先级挑本机已安装的最好美音
VOICE=Samantha
for v in "Ava (Premium)" "Zoe (Premium)" "Samantha (Enhanced)" "Allison (Enhanced)"; do
    if say -v '?' | grep -qF "$v"; then VOICE="$v"; break; fi
done
echo "使用语音：$VOICE"

for f in "$RAW"/*.m4a; do
    name=$(basename "$f" .m4a)
    word=${name//_/ }
    # alphabet.m4a 是 26 个字母逐个朗读（字母题求助用），不是单词
    if [ "$name" = "alphabet" ]; then
        word="[[slnc 150]]"
        for L in A B C D E F G H I J K L M N O P Q R S T U V W X Y Z; do word="$word $L [[slnc 220]]"; done
        say -v "$VOICE" -r 115 -o "$TMP/$name.aiff" "$word"
        afconvert -f m4af -d aac@44100 -b 96000 --src-quality 127 "$TMP/$name.aiff" "$TMP/$name.m4a"
        mv "$TMP/$name.m4a" "$f"
        echo "  alphabet     (字母表)"
        continue
    fi
    # 语速放慢到 140、首尾各留 150/250ms 静音，避免播放器掐掉起始音
    say -v "$VOICE" -r 140 -o "$TMP/$name.aiff" "[[slnc 150]] $word [[slnc 250]]"
    afconvert -f m4af -d aac@44100 -b 96000 --src-quality 127 "$TMP/$name.aiff" "$TMP/$name.m4a"
    mv "$TMP/$name.m4a" "$f"
    printf "  %-12s %s\n" "$name" "$(afinfo "$f" | grep 'estimated duration' | awk '{print $3}')s"
done
rm -rf "$TMP"
echo "完成：$(ls "$RAW"/*.m4a | wc -l | tr -d ' ') 个文件"
