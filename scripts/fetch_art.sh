#!/bin/bash
# 一次性拉取微软 Fluent 3D emoji（MIT）作为小鸡/农场动物/礼盒的立体美术，
# 缩到 160px 存入 drawable-nodpi。重跑安全（已存在则跳过）。
set -u
BASE="https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets"
OUT="$(dirname "$0")/../app/src/main/res/drawable-nodpi"
mkdir -p "$OUT"

# 目录名|文件名|本地资源名
ITEMS=(
  "Egg|egg|art_egg"
  "Hatching chick|hatching_chick|art_hatching_chick"
  "Baby chick|baby_chick|art_baby_chick"
  "Front-facing baby chick|front-facing_baby_chick|art_chick_front"
  "Chicken|chicken|art_chicken"
  "Rabbit face|rabbit_face|art_rabbit"
  "Cat face|cat_face|art_cat"
  "Dog face|dog_face|art_dog"
  "Hamster|hamster|art_hamster"
  "Pig face|pig_face|art_pig"
  "Frog|frog|art_frog"
  "Fish|fish|art_fish"
  "Panda|panda|art_panda"
  "Fox|fox|art_fox"
  "Penguin|penguin|art_penguin"
  "Owl|owl|art_owl"
  "Turtle|turtle|art_turtle"
  "Butterfly|butterfly|art_butterfly"
  "Unicorn|unicorn|art_unicorn"
  "Dolphin|dolphin|art_dolphin"
  "Peacock|peacock|art_peacock"
  "Dragon|dragon|art_dragon"
  "Wrapped gift|wrapped_gift|art_gift"
  "Phoenix bird|phoenix_bird|art_phoenix"
)

missing=()
for item in "${ITEMS[@]}"; do
    IFS='|' read -r dir file res <<< "$item"
    dst="$OUT/$res.png"
    if [ -s "$dst" ]; then echo "  = ${res} (已存在)"; continue; fi
    url="$BASE/${dir// /%20}/3D/${file}_3d.png"
    curl -sfL "$url" -o "$dst"
    if [ -s "$dst" ] && file "$dst" | grep -q "PNG"; then
        sips -Z 160 "$dst" >/dev/null
        echo "  ✓ $res"
    else
        rm -f "$dst"; missing+=("$dir")
    fi
done

if [ ${#missing[@]} -gt 0 ]; then
    echo "缺失：${missing[*]}"; exit 1
fi
echo "全部 ${#ITEMS[@]} 张就位 → $OUT"
